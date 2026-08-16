package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.network.NetworkHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.List;

/**
 * Server -> client: open the server-config GUI with the current server settings
 * snapshot. Triggered by /e33chat gui (OP).
 *
 * Pure data + codec only — the client-side open logic lives in
 * {@link ClientServerConfigGui} so the server (which loads payload classes to
 * register them) never verifies a reference to the client-only Screen class.
 */
public record ServerConfigScreenPayload(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                        boolean mediaEnabled,
                                        List<String> chatTemplates, List<String> whisperTemplates)
        implements CustomPacketPayload {

    public static final Type<ServerConfigScreenPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "server_config_screen"));

    public static final StreamCodec<ByteBuf, ServerConfigScreenPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ServerConfigScreenPayload decode(ByteBuf buf) {
            ServerConfigDto d = ServerConfigDto.decode(buf);
            return new ServerConfigScreenPayload(d.useTpa(), d.historyEnabled(), d.templateDebug(),
                d.mediaEnabled(), d.chatTemplates(), d.whisperTemplates());
        }

        @Override
        public void encode(ByteBuf buf, ServerConfigScreenPayload payload) {
            ServerConfigDto.encode(new ServerConfigDto(payload.useTpa(), payload.historyEnabled(),
                payload.templateDebug(), payload.mediaEnabled(), payload.chatTemplates(),
                payload.whisperTemplates()), buf);
        }
    };

    @Override
    public Type<ServerConfigScreenPayload> type() { return TYPE; }
}
