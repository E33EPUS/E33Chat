package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;

/**
 * Server -> client: result of a media upload. mediaId is a 32-hex UUID when
 * successful (URL becomes e33chat://media/<mediaId>); otherwise error holds a
 * short reason and mediaId is null.
 */
public record MediaUploadAckPayload(long uploadId, String mediaId, String error)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<MediaUploadAckPayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "media_upload_ack")
            //#else
            //$$ new Identifier("e33chat", "media_upload_ack")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, MediaUploadAckPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> {
        //#else
        //$$ (value, buf) -> {
        //#endif
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
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "media_upload_ack");
    //#endif

    private static String nullOrEmpty(String s) { return s == null || s.isEmpty() ? null : s; }

    //#if MC >= 12005
    @Override
    public Id<MediaUploadAckPayload> getId() { return ID; }
    //#endif
}
