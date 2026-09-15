package com.niuqu.chatbubble.server;

import com.niuqu.chatbubble.network.MediaResponsePayload;
import com.niuqu.chatbubble.network.MediaUploadAckPayload;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

/**
 * Server-side media hosting business logic (rate limit, chunked download,
 * chunked upload session). The payload classes keep only codecs and
 * ChatBubbleMod routes into here.
 *
 * Extracted from ChatBubbleMod's MediaUploadPayload / MediaRequestPayload
 * receivers during the 2.3.14 restructure; behaviour unchanged.
 */
public final class MediaService {
    private MediaService() {}

    /** Client requests a download: rate-limit check, existence check, chunked reply. */
    public static void handleRequest(ServerPlayerEntity sender, DiskMediaStore store, String mediaId) {
        if (!store.allowTransfer(sender.getName().getString())) {
            sendNotFound(sender, mediaId);
            return;
        }
        long size = store.sizeOf(mediaId);
        if (size < 0) {
            sendNotFound(sender, mediaId);
            return;
        }
        int total = DiskMediaStore.totalChunksFor(size);
        for (int i = 0; i < total; i++) {
            byte[] chunk = store.readChunk(mediaId, i, total);
            if (chunk == null) {
                sendNotFound(sender, mediaId);
                return;
            }
            ServerPlayNetworking.send(sender, new MediaResponsePayload(mediaId, i, total, chunk));
        }
    }

    private static void sendNotFound(ServerPlayerEntity sender, String mediaId) {
        // not-found sentinel: MediaResponsePayload(mediaId, 0, 1, empty)
        ServerPlayNetworking.send(sender, new MediaResponsePayload(mediaId, 0, 1, new byte[0]));
    }

    /** One chunk of an upload; acks with the media id when the upload completes. */
    public static void handleUpload(ServerPlayerEntity sender, DiskMediaStore store, boolean mediaEnabled,
                                    boolean autoClean, long uploadId, int index, int totalChunks,
                                    int totalBytes, String contentType, byte[] chunk) {
        if (!mediaEnabled) {
            ServerPlayNetworking.send(sender, new MediaUploadAckPayload(uploadId, null, "disabled"));
            return;
        }
        String result;
        if (index == 0) {
            if (!store.allowTransfer(sender.getName().getString())) {
                ServerPlayNetworking.send(sender, new MediaUploadAckPayload(uploadId, null, "rate limited"));
                return;
            }
            result = store.beginUpload(uploadId, sender.getName().getString(),
                totalChunks, totalBytes, contentType);
            if (result == null) {
                // Chunk 0 also carries data — feed it through acceptChunk so a
                // single-chunk upload completes (and acks) instead of hanging.
                result = store.acceptChunk(uploadId, index, chunk);
            }
        } else {
            result = store.acceptChunk(uploadId, index, chunk);
        }
        if (result == null) return; // upload still in progress
        store.discardUpload(uploadId);
        if (DiskMediaStore.isValidMediaId(result) && autoClean)
            store.cleanupExpiredThrottled();
        ServerPlayNetworking.send(sender,
            DiskMediaStore.isValidMediaId(result)
                ? new MediaUploadAckPayload(uploadId, result, null)
                : new MediaUploadAckPayload(uploadId, null, result));
    }
}
