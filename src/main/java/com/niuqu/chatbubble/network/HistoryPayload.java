package com.niuqu.chatbubble.network;
import com.niuqu.chatbubble.GuiCompat;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public record HistoryPayload(List<HistoryPayload.HistoryEntry> entries)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<HistoryPayload> ID =
        new CustomPayload.Id<>(GuiCompat.id("e33chat", "chat_history"));
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "chat_history");
    //#endif
    public record HistoryEntry(
        UUID senderUUID,
        String senderName,
        String content,
        long time,
        boolean isSystem,
        String replyContent,
        String replySender
    ) {}
    //#if MC >= 12005
    public static final PacketCodec<PacketByteBuf, HistoryPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> buf.writeCollection(value.entries, (b, e) -> {
        //#else
        //$$ (value, buf) -> buf.writeCollection(value.entries, (b, e) -> {
        //#endif
            b.writeString(e.senderUUID().toString());
            b.writeString(e.senderName());
            b.writeString(e.content());
            b.writeLong(e.time());
            b.writeBoolean(e.isSystem());
            b.writeString(e.replyContent() != null ? e.replyContent() : "");
            b.writeString(e.replySender() != null ? e.replySender() : "");
        }),
        buf -> new HistoryPayload(buf.readList(b -> new HistoryEntry(
            UUID.fromString(b.readString()),
            b.readString(),
            b.readString(),
            b.readLong(),
            b.readBoolean(),
            nullOrEmpty(b.readString()),
            nullOrEmpty(b.readString())
        )))
    );
    //#endif
    private static String nullOrEmpty(String s) { return s == null || s.isEmpty() ? null : s; }
    //#if MC >= 12005
    @Override
    public Id<HistoryPayload> getId() { return ID; }
    //#endif
}
