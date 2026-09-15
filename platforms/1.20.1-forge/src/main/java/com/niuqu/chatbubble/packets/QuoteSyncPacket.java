package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.server.ChatServerListener;
import com.niuqu.chatbubble.network.NetworkHandler;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

public class QuoteSyncPacket {
    private final String quotedSenderName;
    private final String quotedContent;
    private final String messageHash;

    public QuoteSyncPacket(String quotedSenderName, String quotedContent, String messageHash) {
        this.quotedSenderName = quotedSenderName;
        this.quotedContent = quotedContent;
        this.messageHash = messageHash;
    }

    public static void send(String quotedSenderName, String quotedContent, String messageText) {
        String hash = String.valueOf(messageText.hashCode());
        NetworkHandler.CHANNEL.sendToServer(
            new QuoteSyncPacket(quotedSenderName, quotedContent, hash));
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(quotedSenderName);
        buf.writeUtf(quotedContent);
        buf.writeUtf(messageHash);
    }

    public static QuoteSyncPacket decode(FriendlyByteBuf buf) {
        return new QuoteSyncPacket(buf.readUtf(), buf.readUtf(), buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        // Server-side: store pending quote for matching in ChatServerListener
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender != null) {
                ChatServerListener.onQuoteReceived(sender.getUUID(), quotedSenderName, quotedContent, messageHash);
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String quotedSenderName() { return quotedSenderName; }
    public String quotedContent() { return quotedContent; }
    public String messageHash() { return messageHash; }
}
