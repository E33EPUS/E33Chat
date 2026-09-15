package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Client -> server: request to download a server-hosted media file. */
public record MediaRequestPayload(String mediaId) implements CustomPayload {

    public static final CustomPayload.Id<MediaRequestPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "media_request"));

    public static final PacketCodec<PacketByteBuf, MediaRequestPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeString(value.mediaId),
        buf -> new MediaRequestPayload(buf.readString())
    );

    @Override
    public Id<MediaRequestPayload> getId() { return ID; }
}
