package com.niuqu.chatbubble.image;

import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.network.NetworkHandler;
import com.niuqu.chatbubble.packets.MediaCapPacket;
import com.niuqu.chatbubble.packets.MediaRequestPacket;
import com.niuqu.chatbubble.packets.MediaResponsePacket;
import com.niuqu.chatbubble.packets.MediaUploadAckPacket;
import com.niuqu.chatbubble.packets.MediaUploadPacket;
import com.niuqu.chatbubble.server.DiskMediaStore;
import net.minecraftforge.network.NetworkEvent;
import org.slf4j.Logger;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * Client side of the server media hosting feature (2.3.13).
 *
 * Capability: the server advertises media hosting in MediaCapPacket
 * (mediaEnabled). When enabled, chat image uploads go to the server
 * (e33chat://media/<id>, permanent) instead of the third-party host; when
 * disabled or absent, callers fall back to the existing ImageUploader path.
 *
 * Both upload and fetch are blocking with a 30s timeout and must be called on
 * a worker thread (not the render thread). Replies are matched by uploadId /
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

    private MediaClient() {}

    public static void setServerEnabled(boolean b) { serverEnabled = b; }
    public static boolean serverEnabled() { return serverEnabled; }

    /** Client-side receiver; called from MediaCapPacket.handle (dist-guarded). */
    public static void handleCap(MediaCapPacket packet) {
        serverEnabled = packet.mediaEnabled();
    }

    /** Client-side receiver; called from MediaUploadAckPacket.handle (dist-guarded). */
    public static void handleAck(MediaUploadAckPacket packet) {
        CompletableFuture<String> f = UPLOADS.remove(packet.uploadId());
        if (f != null) {
            f.complete(packet.error() == null ? packet.mediaId() : null);
        }
    }

    /** Client-side receiver; called from MediaResponsePacket.handle (dist-guarded). */
    public static void handleResponse(MediaResponsePacket packet) {
        String id = packet.mediaId();
        if (packet.totalChunks() == 1 && packet.chunk().length == 0) {
            // Not-found sentinel
            FETCH_BUFFERS.remove(id);
            FETCH_COUNTS.remove(id);
            CompletableFuture<byte[]> f = FETCHES.remove(id);
            if (f != null) f.completeExceptionally(new RuntimeException("media not found: " + id));
            return;
        }
        byte[][] buf = FETCH_BUFFERS.computeIfAbsent(id, k -> new byte[packet.totalChunks()][]);
        if (packet.index() < 0 || packet.index() >= buf.length) return;
        buf[packet.index()] = packet.chunk();
        int got = FETCH_COUNTS.merge(id, 1, Integer::sum);
        if (got == packet.totalChunks()) {
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
        long uploadId = UUID.randomUUID().getMostSignificantBits() & Long.MAX_VALUE;
        int totalChunks = DiskMediaStore.totalChunksFor(bytes.length);
        CompletableFuture<String> done = new CompletableFuture<>();
        UPLOADS.put(uploadId, done);
        for (int i = 0; i < totalChunks; i++) {
            int from = i * DiskMediaStore.CHUNK_BYTES;
            int len = Math.min(DiskMediaStore.CHUNK_BYTES, bytes.length - from);
            byte[] chunk = new byte[len];
            System.arraycopy(bytes, from, chunk, 0, len);
            NetworkHandler.CHANNEL.sendToServer(new MediaUploadPacket(uploadId, i, totalChunks,
                bytes.length, contentType, chunk));
        }
        try {
            String mediaId = done.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
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
        CompletableFuture<byte[]> done = new CompletableFuture<>();
        FETCHES.put(mediaId, done);
        NetworkHandler.CHANNEL.sendToServer(new MediaRequestPacket(mediaId));
        try {
            return done.get(TIMEOUT_SECONDS, TimeUnit.SECONDS);
        } catch (Exception e) {
            FETCHES.remove(mediaId);
            FETCH_BUFFERS.remove(mediaId);
            FETCH_COUNTS.remove(mediaId);
            LOGGER.info("[e33chat] server media fetch {} timed out after {}s", mediaId, TIMEOUT_SECONDS);
            return null;
        }
    }
}
