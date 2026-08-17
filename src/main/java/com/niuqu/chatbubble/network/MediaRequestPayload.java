package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;

/** Client -> server: request to download a server-hosted media file. */
//#if MC >= 12005
public record MediaRequestPayload(String mediaId) implements CustomPayload {
//#else
//$$ public record MediaRequestPayload(String mediaId) {
//#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<MediaRequestPayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "media_request")
            //#else
            //$$ new Identifier("e33chat", "media_request")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, MediaRequestPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> buf.writeString(value.mediaId),
        //#else
        //$$ (value, buf) -> buf.writeString(value.mediaId),
        //#endif
        buf -> new MediaRequestPayload(buf.readString())
    );

    @Override
    public Id<MediaRequestPayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "media_request");
    //#endif
}
