package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record ChatMetaPayload(UUID senderUUID, String senderName, String messageHash,
                               String quoteSender, String quoteContent, List<String> mentionTargets)
        implements CustomPayload {

    public static final CustomPayload.Id<ChatMetaPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "chat_meta"));

    public static final PacketCodec<PacketByteBuf, ChatMetaPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeString(value.senderUUID.toString());
            buf.writeString(value.senderName);
            buf.writeString(value.messageHash);
            buf.writeString(value.quoteSender);
            buf.writeString(value.quoteContent);
            buf.writeCollection(value.mentionTargets, PacketByteBuf::writeString);
        },
        buf -> new ChatMetaPayload(
            UUID.fromString(buf.readString()),
            buf.readString(),
            buf.readString(),
            buf.readString(),
            buf.readString(),
            buf.readList(PacketByteBuf::readString)
        )
    );

    @Override
    public Id<ChatMetaPayload> getId() { return ID; }
}
