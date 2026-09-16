package com.niuqu.chatbubble.image;

import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.network.MediaRequestPayload;
import com.niuqu.chatbubble.network.MediaResponsePayload;
import com.niuqu.chatbubble.network.MediaUploadAckPayload;
import com.niuqu.chatbubble.network.MediaUploadPayload;
import com.niuqu.chatbubble.server.DiskMediaStore;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * Client side of the server media hosting feature (2.3.13).
 *
 * Capability: the server advertises media hosting in ConfigSyncV2
 * (mediaEnabled). When enabled, chat image uploads go to the server
 * (e33chat://media/<id>, permanent) instead of the third-party host; when
 * disabled or absent, callers fall back to the existing ImageUploader path.
 *
 * Both upload and fetch are blocking with a 30s timeout and must be called on
 * a worker thread (not the render thread). Packets are sent on the render
 * thread via execute() to stay thread-safe; replies are matched by uploadId /
 * mediaId futures.
 */
public final class MediaClient {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final long TIMEOUT_SECONDS = 30;

    private static volatile boolean serverEnabled;
    private static final Map<Long, CompletableFuture<String>> UPLOADS = new ConcurrentHashMap<>();
    private static final Map<String, CompletableFuture<byte[]>> FETCHES = new ConcurrentHashMap<>();
    private static final Map<String, byte[][]> FETCH_BUFFERS = new ConcurrentHashMap<>();
    private static final Map<String, Integer> FETCH_COUNTS = new ConcurrentHashMap<>();

    /**
     * What we uploaded, kept locally so we never have to download it back.
     *
     * <p>Every download costs one of the server's four-per-ten-seconds slots, and
     * re-reading our own upload is pure waste: it cannot be missing, and a small
     * LRU makes repeat views (scrolling back, relogging, reopening history) free.
     * Before this, pasting a few images in a row exhausted the quota with the
     * sender's <em>own</em> pictures — the first thing anyone tries.
     */
    private static final int OWN_UPLOAD_CACHE_ENTRIES = 24;
    private static final Map<String, byte[]> OWN_UPLOADS = new ConcurrentHashMap<>();
    private static final java.util.Deque<String> OWN_UPLOAD_ORDER = new java.util.ArrayDeque<>();

    private static void rememberOwnUpload(String mediaId, byte[] bytes) {
        if (mediaId == null || bytes == null || bytes.length == 0) return;
        synchronized (OWN_UPLOAD_ORDER) {
            OWN_UPLOADS.put(mediaId, bytes);
            OWN_UPLOAD_ORDER.remove(mediaId);
            OWN_UPLOAD_ORDER.addLast(mediaId);
            while (OWN_UPLOAD_ORDER.size() > OWN_UPLOAD_CACHE_ENTRIES) {
                OWN_UPLOADS.remove(OWN_UPLOAD_ORDER.removeFirst());
            }
        }
    }

    private MediaClient() {}

    public static void setServerEnabled(boolean b) { serverEnabled = b; }
    public static boolean serverEnabled() { return serverEnabled; }

    /** Client-side receivers; registered from ChatBubbleClientSetup. */
    public static void registerReceivers() {
        ClientPlayNetworking.registerGlobalReceiver(MediaUploadAckPayload.ID, (payload, context) -> {
            CompletableFuture<String> f = UPLOADS.remove(payload.uploadId());
            if (f != null) {
                f.complete(payload.error() == null ? payload.mediaId() : null);
            }
        });
        ClientPlayNetworking.registerGlobalReceiver(MediaResponsePayload.ID, (payload, context) ->
            handleResponse(payload));
    }

    /** Shared failure path: drop per-id assembly state and surface the error. */
    private static void failFetch(String id, String message) {
        FETCH_BUFFERS.remove(id);
        FETCH_COUNTS.remove(id);
        CompletableFuture<byte[]> f = FETCHES.remove(id);
        if (f != null) f.completeExceptionally(new RuntimeException(message));
    }

    /** Client-side receiver for media chunks (registered in setup). */
    public static void handleResponse(MediaResponsePayload payload) {
        String id = payload.mediaId();
        if (payload.totalChunks() == 1 && payload.chunk().length == 0) {
            // Not-found sentinel
            failFetch(id, "media not found: " + id);
            return;
        }
        // Allocation bounds: totalChunks and the reassembled size both come off
        // the wire, so an unclamped array lets one hostile response OOM the
        // client. The legitimate max is whatever the server allows per upload
        // (8 MB in 512 KB chunks).
        int totalChunks = payload.totalChunks();
        if (totalChunks < 1 || totalChunks > DiskMediaStore.totalChunksFor(DiskMediaStore.MAX_SINGLE_BYTES)) {
            failFetch(id, "media chunk count out of range: " + id);
            return;
        }
        byte[][] buf = FETCH_BUFFERS.computeIfAbsent(id, k -> new byte[totalChunks][]);
        if (payload.index() < 0 || payload.index() >= buf.length) return;
        buf[payload.index()] = payload.chunk();
        int got = FETCH_COUNTS.merge(id, 1, Integer::sum);
        if (got == totalChunks) {
            FETCH_BUFFERS.remove(id);
            FETCH_COUNTS.remove(id);
            CompletableFuture<byte[]> f = FETCHES.remove(id);
            if (f != null) {
                int total = 0;
                boolean complete = true;
                for (byte[] c : buf) {
                    if (c == null) { complete = false; break; }
                    total += c.length;
                }
                if (!complete) {
                    f.completeExceptionally(new RuntimeException("media chunk missing: " + id));
                } else if (total > DiskMediaStore.MAX_SINGLE_BYTES) {
                    f.completeExceptionally(new RuntimeException("media too large: " + id));
                } else {
                    byte[] all = new byte[total];
                    int off = 0;
                    for (byte[] c : buf) {
                        System.arraycopy(c, 0, all, off, c.length);
                        off += c.length;
                    }
                    f.complete(all);
                }
            }
        }
    }

    /**
     * Upload bytes to the server. Worker-thread only. Returns the
     * e33chat://media/<id> URL, or null on any failure (caller falls back).
     */
    public static String upload(byte[] bytes, String contentType) {
        if (!serverEnabled || bytes == null || bytes.length == 0) return null;
        if (!ClientPlayNetworking.canSend(MediaUploadPayload.ID)) return null;
        long uploadId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        int totalChunks = DiskMediaStore.totalChunksFor(bytes.length);
        CompletableFuture<String> done = new CompletableFuture<>();
        UPLOADS.put(uploadId, done);
        for (int i = 0; i < totalChunks; i++) {
            int from = i * DiskMediaStore.CHUNK_BYTES;
            int len = Math.min(DiskMediaStore.CHUNK_BYTES, bytes.length - from);
            byte[] chunk = new byte[len];
            System.arraycopy(bytes, from, chunk, 0, len);
            final int idx = i;
            MinecraftClient.getInstance().execute(() -> {
                try {
                    ClientPlayNetworking.send(new MediaUploadPayload(uploadId, idx, totalChunks,
                        bytes.length, contentType, chunk));
                } catch (Throwable t) {
                    UPLOADS.remove(uploadId);
                    done.complete(null);
                }
            });
        }
        try {
            String mediaId = done.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
            // Keep our own bytes: the local view then needs no download at all.
            if (mediaId != null) rememberOwnUpload(mediaId, bytes);
            return mediaId != null ? "e33chat://media/" + mediaId : null;
        } catch (Exception e) {
            UPLOADS.remove(uploadId);
            LOGGER.info("[e33chat] server media upload timed out after {}s", TIMEOUT_SECONDS);
            return null;
        }
    }

    /** Download a server-hosted file. Worker-thread only. Returns raw bytes or null. */
    public static byte[] fetch(String mediaId) {
        if (!DiskMediaStore.isValidMediaId(mediaId)) return null;
        if (!ClientPlayNetworking.canSend(MediaRequestPayload.ID)) return null;
        // Our own upload: answer from memory, so it costs no download quota and
        // can never be the thing that "randomly failed to load".
        byte[] own = OWN_UPLOADS.get(mediaId);
        if (own != null) return own;
        // computeIfAbsent is atomic: the first caller owns the request and the
        // rest attach to the same future. This loader previously did a plain
        // put(), so the second caller (the animated probe and the static loader
        // both fetch every e33chat:// URL) replaced the first caller's future —
        // the first then waited out its full 30s timeout and reported a failure
        // for an image that had downloaded fine. The merge fix in 2.4.11 only
        // ever landed on the Forge side.
        CompletableFuture<byte[]> done = FETCHES.computeIfAbsent(mediaId, id -> {
            CompletableFuture<byte[]> fresh = new CompletableFuture<>();
            MinecraftClient.getInstance().execute(() -> {
                try {
                    ClientPlayNetworking.send(new MediaRequestPayload(id));
                } catch (Throwable t) {
                    FETCHES.remove(id, fresh);
                    fresh.completeExceptionally(t);
                }
            });
            return fresh;
        });
        try {
            return done.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (java.util.concurrent.TimeoutException e) {
            FETCHES.remove(mediaId, done);
            FETCH_BUFFERS.remove(mediaId);
            FETCH_COUNTS.remove(mediaId);
            LOGGER.info("[e33chat] server media fetch {} timed out after {}s", mediaId, TIMEOUT_SECONDS);
            return null;
        } catch (Exception e) {
            // The server answers a refused request with the same sentinel it uses
            // for a missing file, so this is nearly always the rate limit rather
            // than a timeout. Reporting it as one sent every investigation down
            // the wrong path ("it said 30s but only took 6ms").
            FETCHES.remove(mediaId, done);
            FETCH_BUFFERS.remove(mediaId);
            FETCH_COUNTS.remove(mediaId);
            LOGGER.info("[e33chat] server media fetch {} refused: {} (most likely the per-player "
                + "transfer rate limit, 16 per 10s)", mediaId, e.getMessage());
            return null;
        }
    }
}
