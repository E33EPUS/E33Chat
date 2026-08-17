package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;

/**
 * Server -> client: one chunk of a media download. The special form
 * (index 0, totalChunks 1, empty chunk) signals "not found" so the client can
 * fail the fetch instead of hanging.
 */
public record MediaResponsePayload(String mediaId, int index, int totalChunks, byte[] chunk)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<MediaResponsePayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "media_response")
            //#else
            //$$ new Identifier("e33chat", "media_response")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, MediaResponsePayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> {
        //#else
        //$$ (value, buf) -> {
        //#endif
            buf.writeString(value.mediaId);
            buf.writeInt(value.index);
            buf.writeInt(value.totalChunks);
            buf.writeByteArray(value.chunk);
        },
        buf -> new MediaResponsePayload(
            buf.readString(),
            buf.readInt(),
            buf.readInt(),
            buf.readByteArray()
        )
    );

    @Override
    public Id<MediaResponsePayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "media_response");
    //#endif
}
