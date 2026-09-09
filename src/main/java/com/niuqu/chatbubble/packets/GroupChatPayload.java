package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.store.ChatMessageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.UUID;

/**
 * S2C group chat message, sent only to members running the mod (vanilla
 * members receive a plain formatted line instead). Carries the full content so
 * the client builds the bubble locally — no text-tag parsing, no echo.
 */
public record GroupChatPayload(UUID senderUUID, String senderName, String groupName,
                               String content, String quoteSender, String quoteContent)
        implements CustomPacketPayload {

    // Server caps content; still guard the decode side against hostile servers
    private static final int MAX_TEXT = 2048;

    public static final Type<GroupChatPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "group_chat"));

    public static final StreamCodec<ByteBuf, GroupChatPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GroupChatPayload decode(ByteBuf buf) {
            return new GroupChatPayload(
                readUuid(buf),
                readString(buf, 256),
                readString(buf, 64),
                readString(buf, MAX_TEXT),
                blankToNull(readString(buf, 256)),
                blankToNull(readString(buf, MAX_TEXT)));
        }

        @Override
        public void encode(ByteBuf buf, GroupChatPayload p) {
            writeUuid(buf, p.senderUUID());
            writeString(buf, p.senderName(), 256);
            writeString(buf, p.groupName(), 64);
            writeString(buf, p.content(), MAX_TEXT);
            writeString(buf, p.quoteSender() != null ? p.quoteSender() : "", 256);
            writeString(buf, p.quoteContent() != null ? p.quoteContent() : "", MAX_TEXT);
        }
    };

    // Raw ByteBuf lacks the varint/utf/uuid helpers — route through FriendlyByteBuf
    private static void writeUuid(ByteBuf buf, UUID id) {
        new FriendlyByteBuf(buf).writeUUID(id);
    }

    private static UUID readUuid(ByteBuf buf) {
        return new FriendlyByteBuf(buf).readUUID();
    }

    private static void writeString(ByteBuf buf, String s, int cap) {
        new FriendlyByteBuf(buf).writeUtf(s, cap);
    }

    private static String readString(ByteBuf buf, int cap) {
        return new FriendlyByteBuf(buf).readUtf(cap);
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    @Override
    public Type<GroupChatPayload> type() { return TYPE; }

    public static void handleClient(GroupChatPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChatMessageStore.addGroupMessage(
            net.minecraft.network.chat.Component.literal(payload.content()),
            payload.senderUUID(),
            net.minecraft.network.chat.Component.literal(payload.senderName()),
            payload.groupName(), payload.quoteSender(), payload.quoteContent()));
    }
}
