package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.ChatServerConfig;
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
    private final boolean useTpa;
    private final boolean historyEnabled;
    private final boolean templateDebug;
    private final boolean mediaEnabled;
    private final List<String> chatTemplates;
    private final List<String> whisperTemplates;

    public ServerConfigSavePacket(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                  boolean mediaEnabled,
                                  List<String> chatTemplates, List<String> whisperTemplates) {
        this.useTpa = useTpa;
        this.historyEnabled = historyEnabled;
        this.templateDebug = templateDebug;
        this.mediaEnabled = mediaEnabled;
        this.chatTemplates = chatTemplates;
        this.whisperTemplates = whisperTemplates;
    }

    public static void encode(ServerConfigSavePacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.useTpa);
        buf.writeBoolean(packet.historyEnabled);
        buf.writeBoolean(packet.templateDebug);
        buf.writeBoolean(packet.mediaEnabled);
        buf.writeCollection(packet.chatTemplates, FriendlyByteBuf::writeUtf);
        buf.writeCollection(packet.whisperTemplates, FriendlyByteBuf::writeUtf);
    }

    public static ServerConfigSavePacket decode(FriendlyByteBuf buf) {
        boolean useTpa = buf.readBoolean();
        boolean history = buf.readBoolean();
        boolean debug = buf.readBoolean();
        boolean media = buf.readBoolean();
        List<String> chat = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        List<String> whisper = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        return new ServerConfigSavePacket(useTpa, history, debug, media, chat, whisper);
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
            Component error = validateTemplates(true, chatTemplates);
            if (error == null) error = validateTemplates(false, whisperTemplates);
            if (error != null) {
                player.sendSystemMessage(Component.translatable("e33chat.server.save_failed", error)
                    .withStyle(ChatFormatting.RED));
                return;
            }
            ChatServerConfig.USE_TPA.set(useTpa);
            ChatServerConfig.USE_TPA.clearCache();
            ChatServerConfig.HISTORY_ENABLED.set(historyEnabled);
            ChatServerConfig.HISTORY_ENABLED.clearCache();
            ChatServerConfig.CHAT_TEMPLATES.set(new ArrayList<>(chatTemplates));
            ChatServerConfig.CHAT_TEMPLATES.clearCache();
            ChatServerConfig.WHISPER_TEMPLATES.set(new ArrayList<>(whisperTemplates));
            ChatServerConfig.WHISPER_TEMPLATES.clearCache();
            ChatServerConfig.TEMPLATE_DEBUG.set(templateDebug);
            ChatServerConfig.TEMPLATE_DEBUG.clearCache();
            ChatServerConfig.MEDIA_ENABLED.set(mediaEnabled);
            ChatServerConfig.MEDIA_ENABLED.clearCache();
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
