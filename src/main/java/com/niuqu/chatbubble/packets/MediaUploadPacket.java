package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.config.ChatServerConfig;
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
            if (sender != null) com.niuqu.chatbubble.server.MediaService.handleUpload(
                sender, uploadId, index, totalChunks, totalBytes, contentType, chunk);
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
