package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatMessageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public record HistoryPayload(List<HistoryEntry> entries) implements CustomPacketPayload {

    public static final Type<HistoryPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "chat_history"));

    public record HistoryEntry(
        UUID senderUUID,
        String senderName,
        String content,
        long time,
        boolean isSystem,
        String replyContent,
        String replySender
    ) {}

    public static final StreamCodec<ByteBuf, HistoryPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public HistoryPayload decode(ByteBuf buf) {
            int count = buf.readInt();
            List<HistoryEntry> entries = new ArrayList<>(count);
            for (int i = 0; i < count; i++) {
                entries.add(new HistoryEntry(
                    UUID.fromString(ByteBufCodecs.STRING_UTF8.decode(buf)),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    ByteBufCodecs.STRING_UTF8.decode(buf),
                    buf.readLong(),
                    buf.readBoolean(),
                    blankToNull(ByteBufCodecs.STRING_UTF8.decode(buf)),
                    blankToNull(ByteBufCodecs.STRING_UTF8.decode(buf))
                ));
            }
            return new HistoryPayload(entries);
        }

        @Override
        public void encode(ByteBuf buf, HistoryPayload payload) {
            buf.writeInt(payload.entries().size());
            for (var e : payload.entries()) {
                ByteBufCodecs.STRING_UTF8.encode(buf, e.senderUUID().toString());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.senderName());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.content());
                buf.writeLong(e.time());
                buf.writeBoolean(e.isSystem());
                ByteBufCodecs.STRING_UTF8.encode(buf, e.replyContent() != null ? e.replyContent() : "");
                ByteBufCodecs.STRING_UTF8.encode(buf, e.replySender() != null ? e.replySender() : "");
            }
        }
    };

    @Override
    public Type<HistoryPayload> type() { return TYPE; }

    public static void handleClient(HistoryPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChatMessageStore.addHistoryMessages(payload.entries()));
    }

    private static String blankToNull(String s) {
        return s.isEmpty() ? null : s;
    }
}
