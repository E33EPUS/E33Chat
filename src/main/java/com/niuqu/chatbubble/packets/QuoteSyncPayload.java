package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.server.ChatServerListener;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

public record QuoteSyncPayload(String quotedSenderName, String quotedContent, String messageHash)
        implements CustomPacketPayload {

    public static final Type<QuoteSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "quote_sync"));

    public static final StreamCodec<ByteBuf, QuoteSyncPayload> STREAM_CODEC = StreamCodec.composite(
        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, QuoteSyncPayload::quotedSenderName,
        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, QuoteSyncPayload::quotedContent,
        net.minecraft.network.codec.ByteBufCodecs.STRING_UTF8, QuoteSyncPayload::messageHash,
        QuoteSyncPayload::new
    );

    @Override
    public Type<QuoteSyncPayload> type() { return TYPE; }

    public static void handleServer(QuoteSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChatServerListener.onQuoteReceived(
            context.player().getUUID(),
            payload.quotedSenderName(),
            payload.quotedContent(),
            payload.messageHash()
        ));
    }

    public static void send(String quotedSenderName, String quotedContent, String messageText) {
        String hash = String.valueOf(messageText.hashCode());
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new QuoteSyncPayload(quotedSenderName, quotedContent, hash));
    }
}
