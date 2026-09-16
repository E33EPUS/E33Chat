package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record HistoryPayload(List<HistoryPayload.HistoryEntry> entries)
        implements CustomPayload {

    public static final CustomPayload.Id<HistoryPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "chat_history"));

    public record HistoryEntry(
        UUID senderUUID,
        String senderName,
        String content,
        long time,
        boolean isSystem,
        String replyContent,
        String replySender,
        String group
    ) {}

    public static final PacketCodec<PacketByteBuf, HistoryPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeCollection(value.entries, (b, e) -> {
            b.writeString(e.senderUUID().toString());
            b.writeString(e.senderName());
            b.writeString(e.content());
            b.writeLong(e.time());
            b.writeBoolean(e.isSystem());
            b.writeString(e.replyContent() != null ? e.replyContent() : "");
            b.writeString(e.replySender() != null ? e.replySender() : "");
            b.writeString(e.group() != null ? e.group() : "");
        }),
        buf -> {
            // Entry bound aligned with Forge/Neo (200): the count comes off the
            // wire, and even 1.21.1's readList still allows a 65536-entry
            // allocation per packet. Entries is the payload's only field, so
            // leftover bytes from an oversized count are harmless.
            int count = Math.min(Math.max(buf.readVarInt(), 0), 200);
            List<HistoryEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) entries.add(new HistoryEntry(
                UUID.fromString(buf.readString()),
                buf.readString(),
                buf.readString(),
                buf.readLong(),
                buf.readBoolean(),
                nullOrEmpty(buf.readString()),
                nullOrEmpty(buf.readString()),
                nullOrEmpty(buf.readString())
            ));
            return new HistoryPayload(entries);
        }
    );

    private static String nullOrEmpty(String s) { return s == null || s.isEmpty() ? null : s; }

    @Override
    public Id<HistoryPayload> getId() { return ID; }
}
