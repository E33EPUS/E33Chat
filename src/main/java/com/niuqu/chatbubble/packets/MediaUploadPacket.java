package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatServerConfig;
import com.niuqu.chatbubble.server.ChatServerListener;
import com.niuqu.chatbubble.network.NetworkHandler;
import com.niuqu.chatbubble.server.DiskMediaStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Client -> server: one chunk of a media upload (2.3.13 server-side media
 * hosting). The upload is split into DiskMediaStore.CHUNK_BYTES chunks so a
 * large image stays under the protocol packet size limit.
 */
public class MediaUploadPacket {
    private final long uploadId;
    private final int index;
    private final int totalChunks;
    private final int totalBytes;
    private final String contentType;
    private final byte[] chunk;

    public MediaUploadPacket(long uploadId, int index, int totalChunks,
                             int totalBytes, String contentType, byte[] chunk) {
        this.uploadId = uploadId;
        this.index = index;
        this.totalChunks = totalChunks;
        this.totalBytes = totalBytes;
        this.contentType = contentType != null ? contentType : "";
        this.chunk = chunk;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(uploadId);
        buf.writeInt(index);
        buf.writeInt(totalChunks);
        buf.writeInt(totalBytes);
        buf.writeUtf(contentType);
        buf.writeByteArray(chunk);
    }

    public static MediaUploadPacket decode(FriendlyByteBuf buf) {
        return new MediaUploadPacket(
            buf.readLong(), buf.readInt(), buf.readInt(), buf.readInt(),
            buf.readUtf(), buf.readByteArray());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null) return;
            if (!ChatServerConfig.MEDIA_ENABLED.get()) {
                NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
                    new MediaUploadAckPacket(uploadId, null, "disabled"));
                return;
            }
            DiskMediaStore store = ChatServerListener.mediaStore();
            String result;
            if (index == 0) {
                if (!store.allowTransfer(sender.getName().getString())) {
                    NetworkHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
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
                net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
                DiskMediaStore.isValidMediaId(result)
                    ? new MediaUploadAckPacket(uploadId, result, null)
                    : new MediaUploadAckPacket(uploadId, null, result));
        });
        ctx.get().setPacketHandled(true);
    }

    public long uploadId() { return uploadId; }
    public int index() { return index; }
    public int totalChunks() { return totalChunks; }
    public int totalBytes() { return totalBytes; }
    public String contentType() { return contentType; }
    public byte[] chunk() { return chunk; }
}
