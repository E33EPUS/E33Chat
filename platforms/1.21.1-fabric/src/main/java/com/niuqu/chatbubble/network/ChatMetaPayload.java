package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.store.ChatMessageStore;
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
            readMentions(buf)
        )
    );

    /** Mention lists are at most the player count; the count arrives off the
     *  wire, so it is clamped before allocating. The library list codec would
     *  still preallocate up to 65536 entries; parity with Forge's packet is 200.
     *  Wire format is unchanged (varint count + strings). */
    private static final int MAX_MENTIONS = 200;

    private static List<String> readMentions(PacketByteBuf buf) {
        int count = Math.min(Math.max(buf.readVarInt(), 0), MAX_MENTIONS);
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(buf.readString());
        return out;
    }

    @Override
    public Id<ChatMetaPayload> getId() { return ID; }
}
