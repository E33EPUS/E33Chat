package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.config.ChatServerConfig;
import com.niuqu.chatbubble.server.ChatServerListener;
import com.niuqu.chatbubble.chat.TemplateMatcher;
import io.netty.buffer.ByteBuf;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.codec.ByteBufCodecs;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> server: save the server-config GUI edits. The server re-validates
 * every template, persists to the toml, and rebroadcasts to all players.
 */
public record ServerConfigSavePayload(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                      boolean mediaEnabled,
                                      List<String> chatTemplates, List<String> whisperTemplates)
        implements CustomPacketPayload {

    public static final Type<ServerConfigSavePayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "server_config_save"));

    public static final StreamCodec<ByteBuf, ServerConfigSavePayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ServerConfigSavePayload decode(ByteBuf buf) {
            boolean useTpa = buf.readBoolean();
            boolean history = buf.readBoolean();
            boolean debug = buf.readBoolean();
            boolean media = buf.readBoolean();
            List<String> chat = ConfigSyncV2Payload.readList(buf);
            List<String> whisper = ConfigSyncV2Payload.readList(buf);
            return new ServerConfigSavePayload(useTpa, history, debug, media, chat, whisper);
        }

        @Override
        public void encode(ByteBuf buf, ServerConfigSavePayload payload) {
            buf.writeBoolean(payload.useTpa());
            buf.writeBoolean(payload.historyEnabled());
            buf.writeBoolean(payload.templateDebug());
            buf.writeBoolean(payload.mediaEnabled());
            ConfigSyncV2Payload.writeList(buf, payload.chatTemplates());
            ConfigSyncV2Payload.writeList(buf, payload.whisperTemplates());
        }
    };

    @Override
    public Type<ServerConfigSavePayload> type() { return TYPE; }

    public static void handleServer(ServerConfigSavePayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            ServerPlayer player = (ServerPlayer) context.player();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("e33chat.server.op_required")
                    .withStyle(ChatFormatting.RED));
                return;
            }
            Component error = validateTemplates(true, payload.chatTemplates());
            if (error == null) error = validateTemplates(false, payload.whisperTemplates());
            if (error != null) {
                player.sendSystemMessage(Component.translatable("e33chat.server.save_failed", error)
                    .withStyle(ChatFormatting.RED));
                return;
            }
            ChatServerConfig.USE_TPA.set(payload.useTpa());
            ChatServerConfig.USE_TPA.clearCache();
            ChatServerConfig.HISTORY_ENABLED.set(payload.historyEnabled());
            ChatServerConfig.HISTORY_ENABLED.clearCache();
            ChatServerConfig.CHAT_TEMPLATES.set(new ArrayList<>(payload.chatTemplates()));
            ChatServerConfig.CHAT_TEMPLATES.clearCache();
            ChatServerConfig.WHISPER_TEMPLATES.set(new ArrayList<>(payload.whisperTemplates()));
            ChatServerConfig.WHISPER_TEMPLATES.clearCache();
            ChatServerConfig.TEMPLATE_DEBUG.set(payload.templateDebug());
            ChatServerConfig.TEMPLATE_DEBUG.clearCache();
            ChatServerConfig.MEDIA_ENABLED.set(payload.mediaEnabled());
            ChatServerConfig.MEDIA_ENABLED.clearCache();
            ChatBubbleMod.saveServerConfig();
            ChatServerListener.broadcastServerConfig();
            player.sendSystemMessage(Component.translatable("e33chat.server.saved"));
        });
    }

    private static Component validateTemplates(boolean chat, List<String> templates) {
        for (int i = 0; i < templates.size(); i++) {
            TemplateMatcher.CompileResult result = TemplateMatcher.compile(templates.get(i));
            if (result.template() == null) {
                return Component.translatable("e33chat.server.template_invalid",
                    Component.translatable(chat ? "e33chat.server.kind_chat" : "e33chat.server.kind_whisper"),
                    i + 1, result.error());
            }
        }
        return null;
    }
}
