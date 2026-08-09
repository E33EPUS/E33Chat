package com.niuqu.chatbubble.network;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;
//#if MC >= 12005
public record QuoteSyncPayload(String quotedSenderName, String quotedContent, String messageHash)
        implements CustomPayload {
//#else
//$$ public record QuoteSyncPayload(String quotedSenderName, String quotedContent, String messageHash) {
//#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<QuoteSyncPayload> ID =
        new CustomPayload.Id<>(PayloadIds.of("quote_sync"));
    public static final PacketCodec<PacketByteBuf, QuoteSyncPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> {
        //#else
        //$$ (value, buf) -> {
        //#endif
            buf.writeString(value.quotedSenderName);
            buf.writeString(value.quotedContent);
            buf.writeString(value.messageHash);
        },
        buf -> new QuoteSyncPayload(buf.readString(), buf.readString(), buf.readString())
    );
    @Override
    public Id<QuoteSyncPayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "quote_sync");
    //#endif
}
