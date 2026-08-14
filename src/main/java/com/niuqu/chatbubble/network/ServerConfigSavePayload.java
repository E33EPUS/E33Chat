package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.config.ServerConfig;
import com.niuqu.chatbubble.config.ServerConfigManager;
import com.niuqu.chatbubble.chat.TemplateMatcher;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/**
 * Client -> server: save the server-config GUI edits. The server re-validates
 * every template, persists to the JSON file, and rebroadcasts to all players.
 */
public record ServerConfigSavePayload(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                      boolean mediaEnabled,
                                      List<String> chatTemplates, List<String> whisperTemplates)
        implements CustomPayload {

    public static final CustomPayload.Id<ServerConfigSavePayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "server_config_save"));

    public static final PacketCodec<PacketByteBuf, ServerConfigSavePayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeBoolean(value.useTpa);
            buf.writeBoolean(value.historyEnabled);
            buf.writeBoolean(value.templateDebug);
            buf.writeBoolean(value.mediaEnabled);
            ConfigSyncV2Payload.writeList(buf, value.chatTemplates);
            ConfigSyncV2Payload.writeList(buf, value.whisperTemplates);
        },
        buf -> new ServerConfigSavePayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            ConfigSyncV2Payload.readList(buf),
            ConfigSyncV2Payload.readList(buf)
        )
    );

    @Override
    public Id<ServerConfigSavePayload> getId() { return ID; }

    /** Server-side handler: validate, persist, rebroadcast (called from ChatBubbleMod). */
    public static void handleServer(ServerConfigSavePayload payload, ServerPlayerEntity player,
                                    java.util.function.Consumer<ServerConfig> applyAndSave) {
        Text error = validateTemplates(true, payload.chatTemplates());
        if (error == null) error = validateTemplates(false, payload.whisperTemplates());
        if (error != null) {
            player.sendMessage(Text.translatable("e33chat.server.save_failed", error)
                .formatted(Formatting.RED), false);
            return;
        }
        ServerConfig cfg = new ServerConfig();
        cfg.use_tpa = payload.useTpa();
        cfg.history_enabled = payload.historyEnabled();
        cfg.template_debug = payload.templateDebug();
        cfg.media_enabled = payload.mediaEnabled();
        cfg.chat_templates = new ArrayList<>(payload.chatTemplates());
        cfg.whisper_templates = new ArrayList<>(payload.whisperTemplates());
        applyAndSave.accept(cfg);
        player.sendMessage(Text.translatable("e33chat.server.saved"), false);
    }

    private static Text validateTemplates(boolean chat, List<String> templates) {
        for (int i = 0; i < templates.size(); i++) {
            TemplateMatcher.CompileResult result = TemplateMatcher.compile(templates.get(i));
            if (result.template() == null) {
                return Text.translatable("e33chat.server.template_invalid",
                    Text.translatable(chat ? "e33chat.server.kind_chat" : "e33chat.server.kind_whisper"),
                    i + 1, result.error());
            }
        }
        return null;
    }
}
