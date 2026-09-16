package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.store.ChatMessageStore;
import io.netty.buffer.ByteBuf;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record ChatMetaPayload(UUID senderUUID, String senderName, String messageHash,
                               String quoteSender, String quoteContent, List<String> mentionTargets)
        implements CustomPacketPayload {

    public static final Type<ChatMetaPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "chat_meta"));

    /** Mention lists are at most the player count; the count arrives off the
     *  wire, so it is clamped before allocating. The library list codec would
     *  still preallocate up to 65536 entries; parity with Forge's packet is 200.
     *  Wire format is unchanged (varint count + UTF strings). */
    private static final int MAX_MENTIONS = 200;

    private static final StreamCodec<ByteBuf, List<String>> MENTION_CODEC = new StreamCodec<>() {
        @Override
        public List<String> decode(ByteBuf buf) {
            int count = Math.min(Math.max(ByteBufCodecs.VAR_INT.decode(buf), 0), MAX_MENTIONS);
            List<String> out = new ArrayList<>(count);
            for (int i = 0; i < count; i++) out.add(ByteBufCodecs.STRING_UTF8.decode(buf));
            return out;
        }

        @Override
        public void encode(ByteBuf buf, List<String> list) {
            ByteBufCodecs.VAR_INT.encode(buf, list.size());
            for (String s : list) ByteBufCodecs.STRING_UTF8.encode(buf, s);
        }
    };

    public static final StreamCodec<ByteBuf, ChatMetaPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), ChatMetaPayload::senderUUID,
        ByteBufCodecs.STRING_UTF8, ChatMetaPayload::senderName,
        ByteBufCodecs.STRING_UTF8, ChatMetaPayload::messageHash,
        ByteBufCodecs.STRING_UTF8, ChatMetaPayload::quoteSender,
        ByteBufCodecs.STRING_UTF8, ChatMetaPayload::quoteContent,
        MENTION_CODEC, ChatMetaPayload::mentionTargets,
        ChatMetaPayload::new
    );

    @Override
    public Type<ChatMetaPayload> type() { return TYPE; }

    public static void handleClient(ChatMetaPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChatMessageStore.applyChatMeta(
            payload.senderUUID(),
            payload.senderName(),
            payload.messageHash(),
            payload.quoteSender(),
            payload.quoteContent(),
            payload.mentionTargets()
        ));
    }


}
