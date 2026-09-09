package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.UUID;

/**
 * S2C group chat message, sent only to members running the mod (vanilla
 * members receive a plain formatted line instead). Carries the full content so
 * the client builds the bubble locally — no text-tag parsing, no echo.
 */
public record GroupChatPayload(UUID senderUUID, String senderName, String groupName,
                               String content, String quoteSender, String quoteContent)
        implements CustomPayload {

    // Server caps content; the codec below caps reads via readString(max)
    private static final int MAX_TEXT = 2048;

    public static final CustomPayload.Id<GroupChatPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "group_chat"));

    public static final PacketCodec<PacketByteBuf, GroupChatPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeUuid(value.senderUUID);
            buf.writeString(value.senderName, 256);
            buf.writeString(value.groupName, 64);
            buf.writeString(value.content, MAX_TEXT);
            buf.writeString(value.quoteSender != null ? value.quoteSender : "", 256);
            buf.writeString(value.quoteContent != null ? value.quoteContent : "", MAX_TEXT);
        },
        buf -> new GroupChatPayload(
            buf.readUuid(),
            buf.readString(256),
            buf.readString(64),
            buf.readString(MAX_TEXT),
            blankToNull(buf.readString(256)),
            blankToNull(buf.readString(MAX_TEXT))
        )
    );

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Client-side hook, invoked from ChatBubbleClientSetup's receiver. */
    public static void handleClient(GroupChatPayload payload) {
        com.niuqu.chatbubble.store.ChatMessageStore.addGroupMessage(
            net.minecraft.text.Text.literal(payload.content()),
            payload.senderUUID(),
            net.minecraft.text.Text.literal(payload.senderName()),
            payload.groupName(), payload.quoteSender(), payload.quoteContent());
    }
}
