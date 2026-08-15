package com.niuqu.chatbubble.server;

import com.niuqu.chatbubble.config.ChatServerConfig;
import com.niuqu.chatbubble.network.NetworkHandler;
import com.niuqu.chatbubble.packets.MediaResponsePacket;
import com.niuqu.chatbubble.packets.MediaUploadAckPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;

/**
 * Server-side media hosting business logic (rate limit, chunked download,
 * chunked upload session). The packet classes keep only encode/decode and
 * route into here.
 *
 * Extracted from MediaRequestPacket / MediaUploadPacket handlers during the
 * 2.3.14 restructure; behaviour unchanged.
 */
public final class MediaService {
    private MediaService() {}

    /** Client requests a download: rate-limit check, existence check, chunked reply. */
    public static void handleRequest(ServerPlayer sender, String mediaId) {
        DiskMediaStore store = ChatServerListener.mediaStore();
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
            NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> sender),
                new MediaResponsePacket(mediaId, i, total, chunk));
        }
    }

    private static void sendNotFound(ServerPlayer sender, String mediaId) {
        // not-found sentinel: MediaResponsePacket(mediaId, 0, 1, empty)
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> sender),
            new MediaResponsePacket(mediaId, 0, 1, new byte[0]));
    }

    /** One chunk of an upload; acks with the media id when the upload completes. */
    public static void handleUpload(ServerPlayer sender, long uploadId, int index,
                                    int totalChunks, int totalBytes, String contentType, byte[] chunk) {
        if (!ChatServerConfig.MEDIA_ENABLED.get()) {
            NetworkHandler.CHANNEL.send(
                PacketDistributor.PLAYER.with(() -> sender),
                new MediaUploadAckPacket(uploadId, null, "disabled"));
            return;
        }
        DiskMediaStore store = ChatServerListener.mediaStore();
        String result;
        if (index == 0) {
            if (!store.allowTransfer(sender.getName().getString())) {
                NetworkHandler.CHANNEL.send(
                    PacketDistributor.PLAYER.with(() -> sender),
                    new MediaUploadAckPacket(uploadId, null, "rate limited"));
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
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> sender),
            DiskMediaStore.isValidMediaId(result)
                ? new MediaUploadAckPacket(uploadId, result, null)
                : new MediaUploadAckPacket(uploadId, null, result));
    }
}
