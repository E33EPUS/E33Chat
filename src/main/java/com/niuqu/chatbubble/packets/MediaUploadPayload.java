package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.server.DiskMediaStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
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
                readUtf(buf),
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

    private static final int MAX_CHUNK_BYTES = DiskMediaStore.CHUNK_BYTES;
    private static final int MAX_STRING_LEN = 256;

    static byte[] readByteArray(ByteBuf buf) {
        int len = Math.min(Math.max(buf.readInt(), 0), MAX_CHUNK_BYTES);
        byte[] out = new byte[len];
        buf.readBytes(out);
        return out;
    }

    static String readUtf(ByteBuf buf) {
        int len = Math.min(Math.max(buf.readInt(), 0), MAX_STRING_LEN);
        return buf.readCharSequence(len, java.nio.charset.StandardCharsets.UTF_8).toString();
    }

    @Override
    public Type<MediaUploadPayload> type() { return TYPE; }

    public static void handleServer(MediaUploadPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sender) {
                com.niuqu.chatbubble.server.MediaService.handleUpload(sender, payload.uploadId(),
                    payload.index(), payload.totalChunks(), payload.totalBytes(),
                    payload.contentType(), payload.chunk());
            }
        });
    }
}
