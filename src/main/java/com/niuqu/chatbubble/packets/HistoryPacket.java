package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Supplier;

public class HistoryPacket {
    private final List<HistoryEntry> entries;

    public HistoryPacket(List<HistoryEntry> entries) {
        this.entries = entries;
    }

    public record HistoryEntry(
        UUID senderUUID,
        String senderName,
        String content,
        long time,
        boolean isSystem,
        String replyContent,
        String replySender
    ) {}

    public static void encode(HistoryPacket packet, FriendlyByteBuf buf) {
        buf.writeInt(packet.entries.size());
        for (var e : packet.entries) {
            buf.writeUUID(e.senderUUID());
            buf.writeUtf(e.senderName());
            buf.writeUtf(e.content());
            buf.writeLong(e.time());
            buf.writeBoolean(e.isSystem());
            buf.writeUtf(e.replyContent() != null ? e.replyContent() : "");
            buf.writeUtf(e.replySender() != null ? e.replySender() : "");
        }
    }

    public static HistoryPacket decode(FriendlyByteBuf buf) {
        // Guard against a hostile/broken packet: the server caps history at
        // HISTORY_MAX=50, so a huge count would OOM on preallocation
        int count = Math.min(Math.max(buf.readInt(), 0), 200);
        List<HistoryEntry> entries = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            entries.add(new HistoryEntry(
                buf.readUUID(),
                buf.readUtf(),
                buf.readUtf(),
                buf.readLong(),
                buf.readBoolean(),
                blankToNull(buf.readUtf()),
                blankToNull(buf.readUtf())
            ));
        }
        return new HistoryPacket(entries);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ChatMessageStore.addHistoryMessages(entries)
            )
        );
        ctx.get().setPacketHandled(true);
    }

    private static String blankToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
