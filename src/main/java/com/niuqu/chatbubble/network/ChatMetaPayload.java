package com.niuqu.chatbubble.network;
import com.niuqu.chatbubble.GuiCompat;
import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
public record ChatMetaPayload(UUID senderUUID, String messageHash, String quoteSender,
                               String quoteContent, List<String> mentionTargets)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<ChatMetaPayload> ID =
        new CustomPayload.Id<>(GuiCompat.id("e33chat", "chat_meta"));
    public static final PacketCodec<PacketByteBuf, ChatMetaPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> {
        //#else
        //$$ (value, buf) -> {
        //#endif
            buf.writeString(value.senderUUID.toString());
            buf.writeString(value.messageHash);
            buf.writeString(value.quoteSender);
            buf.writeString(value.quoteContent);
            buf.writeCollection(value.mentionTargets, PacketByteBuf::writeString);
        },
        buf -> new ChatMetaPayload(
            UUID.fromString(buf.readString()),
            buf.readString(),
            buf.readString(),
            buf.readString(),
            buf.readList(PacketByteBuf::readString)
        )
    );
    @Override
    public Id<ChatMetaPayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "chat_meta");
    //#endif
}
