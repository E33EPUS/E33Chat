package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.config.ChatServerConfig;
import com.niuqu.chatbubble.server.ChatServerListener;
import com.niuqu.chatbubble.chat.TemplateMatcher;
import net.minecraft.ChatFormatting;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Client -> server: save the server-config GUI edits. The server re-validates
 * every template, persists to the toml, and rebroadcasts to all players.
 */
public class ServerConfigSavePacket {
    private final ServerConfigDto dto;

    public ServerConfigSavePacket(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                  boolean mediaEnabled, boolean mediaAutoClean,
                                  List<String> chatTemplates, List<String> whisperTemplates) {
        this.dto = new ServerConfigDto(useTpa, historyEnabled, templateDebug, mediaEnabled,
            mediaAutoClean, chatTemplates, whisperTemplates);
    }

    public static void encode(ServerConfigSavePacket packet, FriendlyByteBuf buf) {
        ServerConfigDto.encode(packet.dto, buf);
    }

    public static ServerConfigSavePacket decode(FriendlyByteBuf buf) {
        ServerConfigDto d = ServerConfigDto.decode(buf);
        return new ServerConfigSavePacket(d.useTpa(), d.historyEnabled(), d.templateDebug(),
            d.mediaEnabled(), d.mediaAutoClean(), d.chatTemplates(), d.whisperTemplates());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player == null) return;
            if (!player.hasPermissions(2)) {
                player.sendSystemMessage(Component.translatable("e33chat.server.op_required")
                    .withStyle(ChatFormatting.RED));
                return;
            }
            Component error = validateTemplates(true, dto.chatTemplates());
            if (error == null) error = validateTemplates(false, dto.whisperTemplates());
            if (error != null) {
                player.sendSystemMessage(Component.translatable("e33chat.server.save_failed", error)
                    .withStyle(ChatFormatting.RED));
                return;
            }
            ChatServerConfig.USE_TPA.set(dto.useTpa());
            ChatServerConfig.USE_TPA.clearCache();
            ChatServerConfig.HISTORY_ENABLED.set(dto.historyEnabled());
            ChatServerConfig.HISTORY_ENABLED.clearCache();
            ChatServerConfig.CHAT_TEMPLATES.set(new ArrayList<>(dto.chatTemplates()));
            ChatServerConfig.CHAT_TEMPLATES.clearCache();
            ChatServerConfig.WHISPER_TEMPLATES.set(new ArrayList<>(dto.whisperTemplates()));
            ChatServerConfig.WHISPER_TEMPLATES.clearCache();
            ChatServerConfig.TEMPLATE_DEBUG.set(dto.templateDebug());
            ChatServerConfig.TEMPLATE_DEBUG.clearCache();
            ChatServerConfig.MEDIA_ENABLED.set(dto.mediaEnabled());
            ChatServerConfig.MEDIA_ENABLED.clearCache();
            ChatServerConfig.MEDIA_AUTO_CLEAN.set(dto.mediaAutoClean());
            ChatServerConfig.MEDIA_AUTO_CLEAN.clearCache();
            ChatBubbleMod.saveServerConfig();
            ChatServerListener.broadcastServerConfig();
            player.sendSystemMessage(Component.translatable("e33chat.server.saved"));
        });
        ctx.get().setPacketHandled(true);
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
