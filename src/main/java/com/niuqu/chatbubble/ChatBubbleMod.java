package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ServerConfig;
import com.niuqu.chatbubble.config.ServerConfigManager;
import com.niuqu.chatbubble.network.ChatMetaPayload;
import com.niuqu.chatbubble.network.ConfigSyncPayload;
import com.niuqu.chatbubble.network.ConfigSyncV2Payload;
import com.niuqu.chatbubble.network.HistoryPayload;
import com.niuqu.chatbubble.network.QuoteSyncPayload;
import com.niuqu.chatbubble.network.ServerConfigSavePayload;
import com.niuqu.chatbubble.network.ServerConfigScreenPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatBubbleMod implements ModInitializer {
    public static final String MOD_ID = "e33chat";

    private static final Pattern MENTION_PATTERN = Pattern.compile("@(\\w+)");
    private static final int HISTORY_MAX = 50;

    private static final Map<UUID, QuotePending> pendingQuotes = new HashMap<>();
    private static final Deque<HistoryPayload.HistoryEntry> historyBuffer = new ArrayDeque<>();

    // Server-side settings (loaded per-world from <world>/serverconfig/e33chat-server.json)
    private static boolean historyEnabled;
    private static boolean useTpa;
    private static boolean templateDebug;
    private static List<String> chatTemplates = List.of();
    private static List<String> whisperTemplates = List.of();
    private static boolean configLoaded;

    private record QuotePending(String quotedSenderName, String quotedContent, String messageHash) {}

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(QuoteSyncPayload.ID, QuoteSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChatMetaPayload.ID, ChatMetaPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HistoryPayload.ID, HistoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncV2Payload.ID, ConfigSyncV2Payload.CODEC);
        PayloadTypeRegistry.playS2C().register(ServerConfigScreenPayload.ID, ServerConfigScreenPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(ServerConfigSavePayload.ID, ServerConfigSavePayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(QuoteSyncPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                String messageHash = payload.messageHash();
                pendingQuotes.put(player.getUuid(),
                    new QuotePending(payload.quotedSenderName(), payload.quotedContent(), messageHash));
            });
        });

        // Server-config GUI save: validate, persist to JSON, rebroadcast
        ServerPlayNetworking.registerGlobalReceiver(ServerConfigSavePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                //#if MC >= 12111
                if (!player.getPermissions().hasPermission(
                    new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS))) {
                //#else
                //$$ if (!player.hasPermissionLevel(2)) {
                //#endif
                    player.sendMessage(Text.translatable("e33chat.server.op_required")
                        .formatted(Formatting.RED), false);
                    return;
                }
                ServerConfigSavePayload.handleServer(payload, player, cfg -> {
                    var path = context.server().getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                        .resolve("serverconfig").resolve("e33chat-server.json");
                    ServerConfigManager.save(path, cfg);
                    loadConfig(cfg);
                    broadcastServerConfig(context.server());
                });
            });
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String rawText = message.getContent().getString();
            //#if MC >= 12106
            //#if MC < 12109
            //$$ var server = sender.getWorld().getServer();
            //#else
            var server = sender.getEntityWorld().getServer();
            //#endif
            //#else
            var server = sender.getEntityWorld().getServer();
            //#endif
            int playerCount = server != null
                ? server.getPlayerManager().getPlayerList().size() : 1;
            List<String> mentions = extractMentions(rawText, playerCount);

            QuotePending quote = pendingQuotes.remove(sender.getUuid());
            String messageHash = quote != null ? quote.messageHash() : String.valueOf(rawText.hashCode());
            String quoteSender = quote != null ? quote.quotedSenderName() : "";
            String quoteContent = quote != null ? quote.quotedContent() : "";

            if (quote != null || !mentions.isEmpty()) {
                ChatMetaPayload meta = new ChatMetaPayload(
                    sender.getUuid(), messageHash, quoteSender, quoteContent, mentions);
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(p, meta);
                }
            }

            addToHistory(new HistoryPayload.HistoryEntry(
                sender.getUuid(), sender.getName().getString(), rawText,
                System.currentTimeMillis(), false,
                quote != null ? quote.quotedContent() : null,
                quote != null ? quote.quotedSenderName() : null));
        });

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Load server config from <world>/serverconfig/ on first join (matching
            // NeoForge's per-world ModConfig.Type.SERVER convention)
            if (!configLoaded) {
                configLoaded = true;
                var configPath = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                    .resolve("serverconfig").resolve("e33chat-server.json");
                ServerConfig config = ServerConfigManager.load(configPath);
                loadConfig(config);
            }

            // Always sync server-side settings so the client head menu matches the server
            ServerPlayNetworking.send(handler.player,
                new ConfigSyncPayload(useTpa));
            ServerPlayNetworking.send(handler.player, buildConfigV2());

            if (!historyEnabled) return;
            if (historyBuffer.isEmpty()) return;
            ServerPlayNetworking.send(handler.player,
                new HistoryPayload(new ArrayList<>(historyBuffer)));
        });

        // /e33chat template commands + /e33chat gui
        com.niuqu.chatbubble.command.E33ChatCommands.register();
    }

    private static void loadConfig(ServerConfig config) {
        useTpa = config.use_tpa;
        historyEnabled = config.history_enabled;
        templateDebug = config.template_debug;
        chatTemplates = config.chat_templates != null ? config.chat_templates : List.of();
        whisperTemplates = config.whisper_templates != null ? config.whisper_templates : List.of();
    }

    public static void broadcastServerConfig(net.minecraft.server.MinecraftServer server) {
        var v2 = buildConfigV2();
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, v2);
        }
    }

    private static ConfigSyncV2Payload buildConfigV2() {
        return new ConfigSyncV2Payload(useTpa, new ArrayList<>(chatTemplates),
            new ArrayList<>(whisperTemplates), templateDebug);
    }

    // Server-side state accessors for the command handler
    public static boolean useTpa() { return useTpa; }
    public static boolean historyEnabled() { return historyEnabled; }
    public static boolean templateDebug() { return templateDebug; }
    public static List<String> chatTemplates() { return chatTemplates; }
    public static List<String> whisperTemplates() { return whisperTemplates; }
    public static void setTemplates(List<String> chat, List<String> whisper, boolean debug) {
        chatTemplates = chat;
        whisperTemplates = whisper;
        templateDebug = debug;
    }

    private static void addToHistory(HistoryPayload.HistoryEntry entry) {
        historyBuffer.addLast(entry);
        while (historyBuffer.size() > HISTORY_MAX)
            historyBuffer.removeFirst();
    }

    private static List<String> extractMentions(String text, int playerCount) {
        if (playerCount <= 1) return Collections.emptyList();
        List<String> mentions = new ArrayList<>();
        Matcher m = MENTION_PATTERN.matcher(text);
        while (m.find()) mentions.add(m.group(1));
        return mentions;
    }
}
