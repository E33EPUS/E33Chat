package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;

/**
 * Server -> client: EasyBot compatibility toggle (2.4.3-beta port).
 *
 * A separate payload type on purpose: old clients drop unknown payloads
 * harmlessly, so a mixed-version client/server never desyncs. Absent payload =
 * disabled, matching the client default.
 */
//#if MC >= 12005
public record EasyBotConfigPayload(boolean easyBotCompat) implements CustomPayload {
//#else
//$$ public record EasyBotConfigPayload(boolean easyBotCompat) {
//#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<EasyBotConfigPayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "config_sync_easybot")
            //#else
            //$$ new Identifier("e33chat", "config_sync_easybot")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, EasyBotConfigPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> buf.writeBoolean(value.easyBotCompat),
        //#else
        //$$ (value, buf) -> buf.writeBoolean(value.easyBotCompat),
        //#endif
        buf -> new EasyBotConfigPayload(buf.readBoolean())
    );

    @Override
    public Id<EasyBotConfigPayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "config_sync_easybot");
    //#endif

    //#if MC >= 12005
    public static void handle(EasyBotConfigPayload payload) {
        ChatMessageStore.setEasyBotCompat(payload.easyBotCompat());
    }
    //#endif
}
