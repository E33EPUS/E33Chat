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

    public static final StreamCodec<ByteBuf, ChatMetaPayload> STREAM_CODEC = StreamCodec.composite(
        ByteBufCodecs.STRING_UTF8.map(UUID::fromString, UUID::toString), ChatMetaPayload::senderUUID,
        ByteBufCodecs.STRING_UTF8, ChatMetaPayload::senderName,
        ByteBufCodecs.STRING_UTF8, ChatMetaPayload::messageHash,
        ByteBufCodecs.STRING_UTF8, ChatMetaPayload::quoteSender,
        ByteBufCodecs.STRING_UTF8, ChatMetaPayload::quoteContent,
        ByteBufCodecs.STRING_UTF8.apply(ByteBufCodecs.list()).map(
            arrList -> arrList, arrList -> arrList
        ), ChatMetaPayload::mentionTargets,
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
