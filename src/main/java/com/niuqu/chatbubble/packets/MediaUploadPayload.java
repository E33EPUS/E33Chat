package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatServerConfig;
import com.niuqu.chatbubble.ChatServerListener;
import com.niuqu.chatbubble.server.DiskMediaStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Client -> server: one chunk of a media upload (2.3.13 server-side media
 * hosting). The upload is split into DiskMediaStore.CHUNK_BYTES chunks so a
 * large image stays under the protocol packet size limit.
 */
public record MediaUploadPayload(long uploadId, int index, int totalChunks,
                                 int totalBytes, String contentType, byte[] chunk)
        implements CustomPacketPayload {

    public static final Type<MediaUploadPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "media_upload"));

    public static final StreamCodec<ByteBuf, MediaUploadPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MediaUploadPayload decode(ByteBuf buf) {
            return new MediaUploadPayload(
                buf.readLong(),
                buf.readInt(),
                buf.readInt(),
                buf.readInt(),
                buf.readCharSequence(buf.readInt(), java.nio.charset.StandardCharsets.UTF_8).toString(),
                readByteArray(buf)
            );
        }

        @Override
        public void encode(ByteBuf buf, MediaUploadPayload payload) {
            buf.writeLong(payload.uploadId());
            buf.writeInt(payload.index());
            buf.writeInt(payload.totalChunks());
            buf.writeInt(payload.totalBytes());
            String ct = payload.contentType() != null ? payload.contentType() : "";
            buf.writeInt(ct.length());
            buf.writeCharSequence(ct, java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(payload.chunk().length);
            buf.writeBytes(payload.chunk());
        }
    };

    static byte[] readByteArray(ByteBuf buf) {
        int len = buf.readInt();
        byte[] out = new byte[len];
        buf.readBytes(out);
        return out;
    }

    @Override
    public Type<MediaUploadPayload> type() { return TYPE; }

    public static void handleServer(MediaUploadPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (!ChatServerConfig.MEDIA_ENABLED.get()) {
                sendAck(ctx, payload.uploadId(), null, "disabled");
                return;
            }
            DiskMediaStore store = ChatServerListener.mediaStore();
            String result = payload.index() == 0
                ? store.beginUpload(payload.uploadId(), ctx.player() != null
                    ? ctx.player().getName().getString() : "?",
                    payload.totalChunks(), payload.totalBytes(), payload.contentType())
                : store.acceptChunk(payload.uploadId(), payload.index(), payload.chunk());
            if (result == null) return; // upload still in progress
            store.discardUpload(payload.uploadId());
            if (DiskMediaStore.isValidMediaId(result)) {
                sendAck(ctx, payload.uploadId(), result, null);
            } else {
                sendAck(ctx, payload.uploadId(), null, result);
            }
        });
    }

    private static void sendAck(IPayloadContext ctx, long uploadId, String mediaId, String error) {
        PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) ctx.player(),
            new MediaUploadAckPayload(uploadId, mediaId, error));
    }
}
