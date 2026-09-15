package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client: result of a media upload. mediaId is a 32-hex UUID when
 * successful (URL becomes e33chat://media/<mediaId>); otherwise error holds a
 * short reason and mediaId is null.
 */
public record MediaUploadAckPayload(long uploadId, String mediaId, String error)
        implements CustomPayload {

    public static final CustomPayload.Id<MediaUploadAckPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "media_upload_ack"));

    public static final PacketCodec<PacketByteBuf, MediaUploadAckPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeLong(value.uploadId);
            buf.writeString(value.mediaId != null ? value.mediaId : "");
            buf.writeString(value.error != null ? value.error : "");
        },
        buf -> new MediaUploadAckPayload(
            buf.readLong(),
            nullOrEmpty(buf.readString()),
            nullOrEmpty(buf.readString())
        )
    );

    private static String nullOrEmpty(String s) { return s == null || s.isEmpty() ? null : s; }

    @Override
    public Id<MediaUploadAckPayload> getId() { return ID; }
}
