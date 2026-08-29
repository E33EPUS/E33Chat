package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.config.ServerConfig;
import com.niuqu.chatbubble.config.ServerConfigManager;
import com.niuqu.chatbubble.chat.TemplateMatcher;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
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
                                      List<String> chatTemplates, List<String> whisperTemplates,
                                      boolean mediaEnabled, boolean mediaAutoClean, boolean easyBotCompat)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<ServerConfigSavePayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "server_config_save")
            //#else
            //$$ new Identifier("e33chat", "server_config_save")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, ServerConfigSavePayload> CODEC = PacketCodec.of(
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
        buf -> new ServerConfigSavePayload(
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
    public Id<ServerConfigSavePayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "server_config_save");
    //#endif

    /** Server-side handler: validate, persist, rebroadcast (called from ChatBubbleMod). */
    public static void handleServer(ServerConfigSavePayload payload, ServerPlayerEntity player,
                                    java.util.function.Consumer<ServerConfig> applyAndSave) {
        Text error = validateTemplates(true, payload.chatTemplates());
        if (error == null) error = validateTemplates(false, payload.whisperTemplates());
        if (error != null) {
            //#if MC >= 26000
            //$$ player.sendSystemMessage(Text.translatable("e33chat.server.save_failed", error)
            //$$     .formatted(Formatting.RED));
            //#else
            player.sendMessage(Text.translatable("e33chat.server.save_failed", error)
                .formatted(Formatting.RED), false);
            //#endif
            return;
        }
        ServerConfig cfg = new ServerConfig();
        cfg.use_tpa = payload.useTpa();
        cfg.history_enabled = payload.historyEnabled();
        cfg.template_debug = payload.templateDebug();
        cfg.chat_templates = new ArrayList<>(payload.chatTemplates());
        cfg.whisper_templates = new ArrayList<>(payload.whisperTemplates());
        cfg.media_enabled = payload.mediaEnabled();
        cfg.media_auto_clean = payload.mediaAutoClean();
        cfg.easy_bot_compat = payload.easyBotCompat();
        applyAndSave.accept(cfg);
        //#if MC >= 26000
        //$$ player.sendSystemMessage(Text.translatable("e33chat.server.saved"));
        //#else
        player.sendMessage(Text.translatable("e33chat.server.saved"), false);
        //#endif
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
