package com.niuqu.chatbubble.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: one chunk of a media download. The special form
 * (index 0, totalChunks 1, empty chunk) signals "not found" so the client can
 * fail the fetch instead of hanging.
 */
public record MediaResponsePayload(String mediaId, int index, int totalChunks, byte[] chunk)
        implements CustomPacketPayload {

    public static final Type<MediaResponsePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "media_response"));

    public static final StreamCodec<ByteBuf, MediaResponsePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MediaResponsePayload decode(ByteBuf buf) {
            return new MediaResponsePayload(
                MediaUploadPayload.readUtf(buf),
                buf.readInt(),
                buf.readInt(),
                MediaUploadPayload.readByteArray(buf)
            );
        }

        @Override
        public void encode(ByteBuf buf, MediaResponsePayload payload) {
            buf.writeInt(payload.mediaId().length());
            buf.writeCharSequence(payload.mediaId(), java.nio.charset.StandardCharsets.UTF_8);
            buf.writeInt(payload.index());
            buf.writeInt(payload.totalChunks());
            buf.writeInt(payload.chunk().length);
            buf.writeBytes(payload.chunk());
        }
    };

    @Override
    public Type<MediaResponsePayload> type() { return TYPE; }
}
