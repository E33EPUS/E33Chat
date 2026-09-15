package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

public record QuoteSyncPayload(String quotedSenderName, String quotedContent, String messageHash)
        implements CustomPayload {

    public static final CustomPayload.Id<QuoteSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "quote_sync"));

    public static final PacketCodec<PacketByteBuf, QuoteSyncPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeString(value.quotedSenderName);
            buf.writeString(value.quotedContent);
            buf.writeString(value.messageHash);
        },
        buf -> new QuoteSyncPayload(buf.readString(), buf.readString(), buf.readString())
    );

    @Override
    public Id<QuoteSyncPayload> getId() { return ID; }
}
