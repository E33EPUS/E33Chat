package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client: one chunk of a media download. The special form
 * (index 0, totalChunks 1, empty chunk) signals "not found" so the client can
 * fail the fetch instead of hanging.
 */
public record MediaResponsePayload(String mediaId, int index, int totalChunks, byte[] chunk)
        implements CustomPayload {

    public static final CustomPayload.Id<MediaResponsePayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "media_response"));

    public static final PacketCodec<PacketByteBuf, MediaResponsePayload> CODEC = PacketCodec.of(
        (value, buf) -> {
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
}
