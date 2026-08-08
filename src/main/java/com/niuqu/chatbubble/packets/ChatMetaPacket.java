package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class ChatMetaPacket {
    private final UUID senderUUID;
    private final String senderName;
    private final String messageHash;
    private final String quoteSender;
    private final String quoteContent;
    private final List<String> mentionTargets;

    public ChatMetaPacket(UUID senderUUID, String senderName, String messageHash,
                          String quoteSender, String quoteContent, List<String> mentionTargets) {
        this.senderUUID = senderUUID;
        this.senderName = senderName;
        this.messageHash = messageHash;
        this.quoteSender = quoteSender;
        this.quoteContent = quoteContent;
        this.mentionTargets = mentionTargets;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUUID(senderUUID);
        buf.writeUtf(senderName);
        buf.writeUtf(messageHash);
        buf.writeUtf(quoteSender);
        buf.writeUtf(quoteContent);
        buf.writeInt(mentionTargets.size());
        for (String target : mentionTargets)
            buf.writeUtf(target);
    }

    public static ChatMetaPacket decode(FriendlyByteBuf buf) {
        UUID senderUUID = buf.readUUID();
        String senderName = buf.readUtf();
        String messageHash = buf.readUtf();
        String quoteSender = buf.readUtf();
        String quoteContent = buf.readUtf();
        int count = buf.readInt();
        List<String> mentionTargets = new ArrayList<>(count);
        for (int i = 0; i < count; i++)
            mentionTargets.add(buf.readUtf());
        return new ChatMetaPacket(senderUUID, senderName, messageHash, quoteSender, quoteContent, mentionTargets);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ChatMessageStore.applyChatMeta(senderUUID, senderName, messageHash, quoteSender, quoteContent, mentionTargets)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
