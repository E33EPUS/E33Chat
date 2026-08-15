package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.network.NetworkHandler;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;

import java.util.ArrayList;
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
            boolean useTpa = buf.readBoolean();
            boolean history = buf.readBoolean();
            boolean debug = buf.readBoolean();
            boolean media = buf.readBoolean();
            List<String> chat = ConfigSyncV2Payload.readList(buf);
            List<String> whisper = ConfigSyncV2Payload.readList(buf);
            return new ServerConfigScreenPayload(useTpa, history, debug, media, chat, whisper);
        }

        @Override
        public void encode(ByteBuf buf, ServerConfigScreenPayload payload) {
            buf.writeBoolean(payload.useTpa());
            buf.writeBoolean(payload.historyEnabled());
            buf.writeBoolean(payload.templateDebug());
            buf.writeBoolean(payload.mediaEnabled());
            ConfigSyncV2Payload.writeList(buf, payload.chatTemplates());
            ConfigSyncV2Payload.writeList(buf, payload.whisperTemplates());
        }
    };

    @Override
    public Type<ServerConfigScreenPayload> type() { return TYPE; }
}
