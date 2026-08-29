package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client: EasyBot compatibility toggle (2.4.3-beta).
 *
 * A separate payload type on purpose: old clients drop unknown payloads
 * harmlessly, so a mixed-version client/server never desyncs. Absent payload =
 * disabled, matching the server config default.
 */
public record EasyBotConfigPayload(boolean easyBotCompat) implements CustomPayload {

    public static final CustomPayload.Id<EasyBotConfigPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "config_sync_easybot"));

    public static final PacketCodec<PacketByteBuf, EasyBotConfigPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeBoolean(value.easyBotCompat),
        buf -> new EasyBotConfigPayload(buf.readBoolean())
    );

    @Override
    public Id<EasyBotConfigPayload> getId() { return ID; }

    public static void handle(EasyBotConfigPayload payload) {
        ChatMessageStore.setEasyBotCompat(payload.easyBotCompat());
    }
}
