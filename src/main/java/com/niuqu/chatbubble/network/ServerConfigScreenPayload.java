package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
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
                                        boolean mediaEnabled, boolean mediaAutoClean,
                                        List<String> chatTemplates, List<String> whisperTemplates)
        implements CustomPayload {

    public static final CustomPayload.Id<ServerConfigScreenPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "server_config_screen"));

    public static final PacketCodec<PacketByteBuf, ServerConfigScreenPayload> CODEC = PacketCodec.of(
        (value, buf) -> ServerConfigDto.encode(new ServerConfigDto(
            value.useTpa, value.historyEnabled, value.templateDebug, value.mediaEnabled,
            value.mediaAutoClean, value.chatTemplates, value.whisperTemplates), buf),
        buf -> {
            ServerConfigDto d = ServerConfigDto.decode(buf);
            return new ServerConfigScreenPayload(d.useTpa(), d.historyEnabled(), d.templateDebug(),
                d.mediaEnabled(), d.mediaAutoClean(), d.chatTemplates(), d.whisperTemplates());
        }
    );

    @Override
    public Id<ServerConfigScreenPayload> getId() { return ID; }
}
