package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Server -> client: open the server-config GUI with the current server settings
 * snapshot. Triggered by /e33chat gui (OP). The handler (opening the client
 * Screen) lives in ChatBubbleClientSetup so a dedicated server never loads the
 * client-only Screen class.
 */
public record ServerConfigScreenPayload(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                        List<String> chatTemplates, List<String> whisperTemplates,
                                        boolean mediaEnabled, boolean mediaAutoClean, boolean easyBotCompat)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<ServerConfigScreenPayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "server_config_screen")
            //#else
            //$$ new Identifier("e33chat", "server_config_screen")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, ServerConfigScreenPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> {
        //#else
        //$$ (value, buf) -> {
        //#endif
            buf.writeBoolean(value.useTpa);
            buf.writeBoolean(value.historyEnabled);
            buf.writeBoolean(value.templateDebug);
            ConfigSyncV2Payload.writeList(buf, value.chatTemplates);
            ConfigSyncV2Payload.writeList(buf, value.whisperTemplates);
            buf.writeBoolean(value.mediaEnabled);
            buf.writeBoolean(value.mediaAutoClean);
            buf.writeBoolean(value.easyBotCompat);
        },
        buf -> new ServerConfigScreenPayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            ConfigSyncV2Payload.readList(buf),
            ConfigSyncV2Payload.readList(buf),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean()
        )
    );

    @Override
    public Id<ServerConfigScreenPayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "server_config_screen");
    //#endif
}
