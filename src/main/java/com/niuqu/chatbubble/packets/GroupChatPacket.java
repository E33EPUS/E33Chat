package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * S2C group chat message, sent only to members running the mod (vanilla
 * members receive a plain formatted line instead). Carries the full content so
 * the client builds the bubble locally — no text-tag parsing, no echo.
 */
public class GroupChatPacket {
    // Server caps content; still guard the decode side against hostile servers
    private static final int MAX_TEXT = 2048;

    private final UUID senderUUID;
    private final String senderName;
    private final String groupName;
    private final String content;
    private final String quoteSender;
    private final String quoteContent;

    public GroupChatPacket(UUID senderUUID, String senderName, String groupName,
                           String content, String quoteSender, String quoteContent) {
        this.senderUUID = senderUUID;
        this.senderName = senderName;
        this.groupName = groupName;
        this.content = content;
        this.quoteSender = quoteSender;
        this.quoteContent = quoteContent;
    }

    public static void encode(GroupChatPacket p, FriendlyByteBuf buf) {
        buf.writeUUID(p.senderUUID);
        buf.writeUtf(p.senderName, 256);
        buf.writeUtf(p.groupName, 64);
        buf.writeUtf(p.content, MAX_TEXT);
        buf.writeUtf(p.quoteSender != null ? p.quoteSender : "", 256);
        buf.writeUtf(p.quoteContent != null ? p.quoteContent : "", MAX_TEXT);
    }

    public static GroupChatPacket decode(FriendlyByteBuf buf) {
        return new GroupChatPacket(
            buf.readUUID(),
            buf.readUtf(256),
            buf.readUtf(64),
            buf.readUtf(MAX_TEXT),
            blankToNull(buf.readUtf(256)),
            blankToNull(buf.readUtf(MAX_TEXT)));
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ChatMessageStore.addGroupMessage(
                    Component.literal(content), senderUUID, Component.literal(senderName),
                    groupName, quoteSender, quoteContent)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
