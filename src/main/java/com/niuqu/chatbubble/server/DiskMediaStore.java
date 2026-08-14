package com.niuqu.chatbubble.server;

import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Pattern;

/**
 * Server-side media store for the 2.3.13 server media hosting feature.
 *
 * Files land in <dir>/<mediaId> where mediaId is a random 32-hex UUID (no
 * dashes) — unguessable, so chat images are only reachable by players who saw
 * the URL, without adding an auth layer. Uploads stream through a per-session
 * temp file and are renamed into place on the final chunk.
 *
 * Limits (grilled with the user): max 8 MB per file, 512 MB total quota,
 * TTL off by default. A failed/out-of-order upload discards its session.
 * All methods are safe to call from any thread; session state is synchronized
 * on the store instance.
 */
public final class DiskMediaStore {
    public static final long MAX_SINGLE_BYTES = 8L * 1024 * 1024;
    public static final long QUOTA_BYTES = 512L * 1024 * 1024;
    public static final int CHUNK_BYTES = 512 * 1024;
    public static final long TTL_MILLIS = 0; // 0 = keep forever

    private static final Pattern MEDIA_ID = Pattern.compile("[0-9a-f]{32}");

    private final Path dir;
    private final long maxSingle;
    private final long quota;
    private final long ttlMillis;
    private final Map<Long, Session> sessions = new ConcurrentHashMap<>();

    private record Session(long uploadId, String playerName, int totalChunks,
                           long totalBytes, String contentType, Path tmpFile, int received) {}

    public DiskMediaStore(Path dir) {
        this(dir, MAX_SINGLE_BYTES, QUOTA_BYTES, TTL_MILLIS);
    }

    /** Test hook: injectable limits. */
    DiskMediaStore(Path dir, long maxSingle, long quota, long ttlMillis) {
        this.dir = dir;
        this.maxSingle = maxSingle;
        this.quota = quota;
        this.ttlMillis = ttlMillis;
    }

    public static String newMediaId() {
        return UUID.randomUUID().toString().replace("-", "");
    }

    public static boolean isValidMediaId(String id) {
        return id != null && MEDIA_ID.matcher(id).matches();
    }

    public static int totalChunksFor(long size) {
        return (int) ((size + CHUNK_BYTES - 1) / CHUNK_BYTES);
    }

    /** First chunk: validate size/quota and create the session. Null on success, else error reason. */
    public synchronized String beginUpload(long uploadId, String playerName, int totalChunks,
                                           long totalBytes, String contentType) {
        if (totalBytes <= 0 || totalBytes > maxSingle) return "too large";
        if (totalChunks < 1 || totalChunks > totalChunksFor(maxSingle)) return "bad chunks";
        if (dirSize() + totalBytes > quota) return "quota exceeded";
        try {
            Files.createDirectories(dir);
            Session s = new Session(uploadId, playerName, totalChunks, totalBytes,
                contentType, dir.resolve(".upload-" + uploadId), 0);
            sessions.put(uploadId, s);
            return null;
        } catch (IOException e) {
            return "io";
        }
    }

    /**
     * Subsequent chunk: append to the session temp file. Returns null while
     * the upload is incomplete; on the final chunk returns the new mediaId
     * (success) or an error reason (failure, session discarded).
     */
    public synchronized String acceptChunk(long uploadId, int index, byte[] chunk) {
        Session s = sessions.get(uploadId);
        if (s == null) return null;
        if (index != s.received()) {
            discardSession(s);
            return "chunk out of order";
        }
        try {
            Path tmp = s.tmpFile();
            try (FileOutputStream out = new FileOutputStream(tmp.toFile(), true)) {
                out.write(chunk);
            }
            Session next = new Session(s.uploadId(), s.playerName(), s.totalChunks(),
                s.totalBytes(), s.contentType(), tmp, s.received() + 1);
            sessions.put(uploadId, next);
            if (next.received() == next.totalChunks()) {
                sessions.remove(uploadId);
                try {
                    if (Files.size(tmp) != s.totalBytes()) {
                        Files.deleteIfExists(tmp);
                        return "size mismatch";
                    }
                    String id = newMediaId();
                    Files.move(tmp, dir.resolve(id));
                    return id;
                } catch (IOException e) {
                    return "io";
                }
            }
            return null;
        } catch (IOException e) {
            discardSession(s);
            return "io";
        }
    }

    public synchronized void discardUpload(long uploadId) {
        Session s = sessions.remove(uploadId);
        if (s != null) {
            try { Files.deleteIfExists(s.tmpFile()); } catch (IOException ignored) {}
        }
    }

    /** Discard every in-flight upload (server stop). Removes temp files. */
    public synchronized void discardAllUploads() {
        for (Session s : sessions.values()) {
            try { Files.deleteIfExists(s.tmpFile()); } catch (IOException ignored) {}
        }
        sessions.clear();
    }

    /** Discard in-flight uploads started by a player who left (disconnect). */
    public synchronized void discardUploadsFor(String playerName) {
        for (Session s : sessions.values()) {
            if (playerName != null && playerName.equals(s.playerName())) {
                sessions.remove(s.uploadId());
                try { Files.deleteIfExists(s.tmpFile()); } catch (IOException ignored) {}
            }
        }
    }

    private static final int RATE_LIMIT_PER_WINDOW = 4;
    private static final long RATE_WINDOW_MS = 10_000;
    private final Map<String, java.util.ArrayDeque<Long>> rateWindows = new ConcurrentHashMap<>();

    /**
     * Per-player sliding-window throttle for media transfers (one upload session
     * or one download request = one call). Call once per upload session (index 0),
     * not per chunk.
     */
    public boolean allowTransfer(String playerName) {
        long now = System.currentTimeMillis();
        java.util.ArrayDeque<Long> q = rateWindows.computeIfAbsent(playerName, k -> new java.util.ArrayDeque<>());
        synchronized (q) {
            while (!q.isEmpty() && now - q.peekFirst() > RATE_WINDOW_MS) q.removeFirst();
            if (q.size() >= RATE_LIMIT_PER_WINDOW) return false;
            q.addLast(now);
            return true;
        }
    }

    /** Size in bytes of a stored file, or -1 when absent. */
    public long sizeOf(String mediaId) {
        Path f = dir.resolve(mediaId);
        if (!isValidMediaId(mediaId) || !Files.isRegularFile(f)) return -1;
        try {
            return Files.size(f);
        } catch (IOException e) {
            return -1;
        }
    }

    /** One chunk of a stored file, or null when absent/unreadable. */
    public byte[] readChunk(String mediaId, int index, int totalChunks) {
        long size = sizeOf(mediaId);
        if (size < 0 || index < 0 || index >= totalChunks) return null;
        long start = (long) index * CHUNK_BYTES;
        if (start >= size) return null;
        int len = (int) Math.min(CHUNK_BYTES, size - start);
        try (InputStream in = Files.newInputStream(dir.resolve(mediaId))) {
            in.skipNBytes(start);
            byte[] out = new byte[len];
            int off = 0;
            while (off < len) {
                int r = in.read(out, off, len - off);
                if (r < 0) break;
                off += r;
            }
            return off == len ? out : null;
        } catch (IOException e) {
            return null;
        }
    }

    /** Remove files older than the TTL (no-op when TTL is 0). Returns files removed. */
    public int cleanupExpired() {
        return cleanupExpired(System.currentTimeMillis());
    }

    synchronized int cleanupExpired(long now) {
        if (ttlMillis <= 0) return 0;
        int removed = 0;
        try {
            if (!Files.isDirectory(dir)) return 0;
            try (var stream = Files.list(dir)) {
                for (Path p : (Iterable<Path>) stream::iterator) {
                    String name = p.getFileName().toString();
                    boolean orphanTmp = name.startsWith(".upload-");
                    if (!isValidMediaId(name) && !orphanTmp) continue;
                    long modified = Files.getLastModifiedTime(p).toMillis();
                    if (now - modified > ttlMillis) {
                        Files.deleteIfExists(p);
                        removed++;
                    }
                }
            }
        } catch (IOException ignored) {}
        return removed;
    }

    private long dirSize() {
        try {
            if (!Files.isDirectory(dir)) return 0;
            long[] total = {0};
            try (var stream = Files.walk(dir)) {
                stream.forEach(p -> {
                    if (Files.isRegularFile(p)) {
                        try { total[0] += Files.size(p); } catch (IOException ignored) {}
                    }
                });
            }
            return total[0];
        } catch (IOException e) {
            return 0;
        }
    }

    private void discardSession(Session s) {
        sessions.remove(s.uploadId());
        try { Files.deleteIfExists(s.tmpFile()); } catch (IOException ignored) {}
    }
}
