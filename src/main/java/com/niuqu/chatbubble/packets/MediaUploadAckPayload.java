package com.niuqu.chatbubble.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

/**
 * Server -> client: result of a media upload. mediaId is a 32-hex UUID when
 * successful (URL becomes e33chat://media/<mediaId>); otherwise error holds a
 * short reason and mediaId is null.
 */
public record MediaUploadAckPayload(long uploadId, String mediaId, String error)
        implements CustomPacketPayload {

    public static final Type<MediaUploadAckPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "media_upload_ack"));

    public static final StreamCodec<ByteBuf, MediaUploadAckPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MediaUploadAckPayload decode(ByteBuf buf) {
            return new MediaUploadAckPayload(
                buf.readLong(),
                nullOrEmpty(readUtf(buf)),
                nullOrEmpty(readUtf(buf))
            );
        }

        @Override
        public void encode(ByteBuf buf, MediaUploadAckPayload payload) {
            buf.writeLong(payload.uploadId());
            writeUtf(buf, payload.mediaId());
            writeUtf(buf, payload.error());
        }
    };

    private static String readUtf(ByteBuf buf) {
        return MediaUploadPayload.readUtf(buf);
    }

    private static void writeUtf(ByteBuf buf, String s) {
        String v = s != null ? s : "";
        buf.writeInt(v.length());
        buf.writeCharSequence(v, java.nio.charset.StandardCharsets.UTF_8);
    }

    private static String nullOrEmpty(String s) { return s == null || s.isEmpty() ? null : s; }

    @Override
    public Type<MediaUploadAckPayload> type() { return TYPE; }
}
