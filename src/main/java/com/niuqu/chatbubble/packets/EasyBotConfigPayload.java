package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.store.ChatMessageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: EasyBot compatibility toggle (2.4.3-beta).
 *
 * A separate payload type on purpose: old clients drop unknown payloads
 * harmlessly, so a mixed-version client/server never desyncs. Absent payload =
 * disabled, matching the server config default.
 */
public record EasyBotConfigPayload(boolean easyBotCompat) implements CustomPacketPayload {

    public static final Type<EasyBotConfigPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "config_sync_easybot"));

    public static final StreamCodec<ByteBuf, EasyBotConfigPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public EasyBotConfigPayload decode(ByteBuf buf) {
            return new EasyBotConfigPayload(buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, EasyBotConfigPayload payload) {
            buf.writeBoolean(payload.easyBotCompat());
        }
    };

    @Override
    public Type<EasyBotConfigPayload> type() { return TYPE; }

    public static void handleClient(EasyBotConfigPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChatMessageStore.setEasyBotCompat(payload.easyBotCompat()));
    }
}
