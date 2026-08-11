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
import com.mojang.brigadier.ParseResults;
import net.fabricmc.api.ModInitializer;
//#if MC >= 11900
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
//#endif
//#if MC >= 12005
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
//#endif
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Formatting;

import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatBubbleMod implements ModInitializer {
    public static final String MOD_ID = "e33chat";

    // Align with Forge/Neo: \p{L}\p{N} covers non-ASCII names (cracked servers allow
    // Chinese player names); @(\w+) only matched ASCII and missed them
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_]+)");
    private static final int HISTORY_MAX = 50;

    private static final Map<UUID, QuotePending> pendingQuotes = new HashMap<>();
    private static final Deque<HistoryPayload.HistoryEntry> historyBuffer = new ArrayDeque<>();
    private static String serverWorldKey;

    // Server-side settings (loaded per-world from <world>/serverconfig/e33chat-server.json)
    private static boolean historyEnabled;
    private static boolean useTpa;
    private static boolean templateDebug;
    private static List<String> chatTemplates = List.of();
    private static List<String> whisperTemplates = List.of();
    private static boolean configLoaded;

    private record QuotePending(String quotedSenderName, String quotedContent, String messageHash, long time) {}
    // A quote that never made it into a sent message (e.g. an anti-spam plugin blocked
    // it) must not tag a later unrelated message — expire after 10s (parity with Forge)
    private static QuotePending takeQuote(UUID playerUUID) {
        QuotePending quote = pendingQuotes.remove(playerUUID);
        if (quote != null && System.currentTimeMillis() - quote.time() > 10_000) return null;
        return quote;
    }

    @Override
    public void onInitialize() {
        //#if MC >= 12005
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
                    new QuotePending(payload.quotedSenderName(), payload.quotedContent(), messageHash,
                        System.currentTimeMillis()));
            });
        });

        // Server-config GUI save: validate, persist to JSON, rebroadcast
        ServerPlayNetworking.registerGlobalReceiver(ServerConfigSavePayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                //#if MC >= 26000
                //$$ if (!player.permissions().hasPermission(new net.minecraft.server.permissions.Permission.HasCommandLevel(net.minecraft.server.permissions.PermissionLevel.GAMEMASTERS))) {
                //#else
                //#if MC >= 12111
                if (!player.getPermissions().hasPermission(
                    new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS))) {
                //#else
                //$$ if (!player.hasPermissionLevel(2)) {
                //#endif
                //#endif
                    //#if MC >= 26000
                    //$$ player.sendSystemMessage(com.niuqu.chatbubble.Txt.translatable("e33chat.server.op_required")
                    //$$     .formatted(Formatting.RED));
                    //#else
                    player.sendMessage(com.niuqu.chatbubble.Txt.translatable("e33chat.server.op_required")
                        .formatted(Formatting.RED), false);
                    //#endif
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
        //#endif

        //#if MC >= 11900
        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            //#if MC >= 26000
            //$$ String rawText = message.decoratedContent().getString();
            //#else
            String rawText = message.getContent().getString();
            //#endif
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

            QuotePending quote = takeQuote(sender.getUuid());
            String messageHash = quote != null ? quote.messageHash() : String.valueOf(rawText.hashCode());
            String quoteSender = quote != null ? quote.quotedSenderName() : "";
            String quoteContent = quote != null ? quote.quotedContent() : "";
            updateServerWorld(server);
            String senderDisplay = playerDisplayName(sender);

            if (quote != null || !mentions.isEmpty()) {
                ChatMetaPayload meta = new ChatMetaPayload(
                    sender.getUuid(), sender.getName().getString(), messageHash, quoteSender, quoteContent, mentions);
                //#if MC >= 12005
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(p, meta);
                }
                //#endif
            }

            addToHistory(new HistoryPayload.HistoryEntry(
                sender.getUuid(), senderDisplay, rawText,
                System.currentTimeMillis(), false,
                quote != null ? quote.quotedContent() : null,
                quote != null ? quote.quotedSenderName() : null));
        });
        //#endif

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            updateServerWorld(server);
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
            //#if MC >= 12005
            ServerPlayNetworking.send(handler.player,
                new ConfigSyncPayload(useTpa));
            ServerPlayNetworking.send(handler.player, buildConfigV2());

            if (!historyEnabled) return;
            if (historyBuffer.isEmpty()) return;
            ServerPlayNetworking.send(handler.player,
                new HistoryPayload(new ArrayList<>(historyBuffer)));
            //#endif
        });

        // /e33chat template commands + /e33chat gui
        //#if MC >= 11900
        com.niuqu.chatbubble.command.E33ChatCommands.register();
        //#endif
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
        //#if MC >= 12005
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, v2);
        }
        //#endif
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

    private static void updateServerWorld(net.minecraft.server.MinecraftServer server) {
        if (server == null) return;
        String key = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT).toAbsolutePath().normalize().toString();
        if (key.equals(serverWorldKey)) return;
        serverWorldKey = key;
        historyBuffer.clear();
        pendingQuotes.clear();
        configLoaded = false;
    }

    private static String playerDisplayName(ServerPlayerEntity player) {
        String id = player.getName().getString();
        String lpPrefix = luckPermsPrefix(player.getUuid());
        if (lpPrefix != null && !Formatting.strip(lpPrefix).isBlank()) {
            return lpPrefix + id;
        }
        String display = player.getDisplayName() != null ? player.getDisplayName().getString() : null;
        if (display != null && !display.isBlank() && !display.equals(id)) return display;
        return id;
    }

    private static String luckPermsPrefix(UUID uuid) {
        try {
            Class<?> provider = Class.forName("net.luckperms.api.LuckPermsProvider");
            Object api = provider.getMethod("get").invoke(null);
            Object userManager = api.getClass().getMethod("getUserManager").invoke(api);
            Object user = userManager.getClass().getMethod("getUser", UUID.class).invoke(userManager, uuid);
            if (user == null) return null;
            Object cachedData = user.getClass().getMethod("getCachedData").invoke(user);
            Object metaData = cachedData.getClass().getMethod("getMetaData").invoke(cachedData);
            Object prefix = metaData.getClass().getMethod("getPrefix").invoke(metaData);
            if (!(prefix instanceof String s) || s.isBlank()) return null;
            return s.replace('&', '§');
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static List<String> extractMentions(String text, int playerCount) {
        if (playerCount <= 1) return Collections.emptyList();
        List<String> mentions = new ArrayList<>();
        Matcher m = MENTION_PATTERN.matcher(text);
        while (m.find()) mentions.add(m.group(1));
        return mentions;
    }

    // Fabric API has no command-execution event, so /msg /tell /w /whisper quotes
    // would never be consumed server-side. Called by CommandManagerMixin before
    // dispatch — if the command is a private message and the sender has a pending
    // quote, broadcast the ChatMeta to all players so the recipient's client tags it.
    public static void consumePrivateMessageQuote(ParseResults<ServerCommandSource> parseResults, String command) {
        try {
            String[] parts = command.split(" ");
            if (parts.length < 3) return;
            String label = parts[0];
            if (label.startsWith("/")) label = label.substring(1);
            if (!label.equals("msg") && !label.equals("tell") && !label.equals("w") && !label.equals("whisper")) return;
            ServerCommandSource source = parseResults.getContext().getSource();
            ServerPlayerEntity sender = source.getPlayer();
            if (sender == null) return;
            QuotePending quote = takeQuote(sender.getUuid());
            if (quote == null) return;
            //#if MC >= 12005
            ChatMetaPayload meta = new ChatMetaPayload(sender.getUuid(), sender.getName().getString(),
                quote.messageHash(), quote.quotedSenderName(), quote.quotedContent(), Collections.emptyList());
            for (ServerPlayerEntity p : source.getServer().getPlayerManager().getPlayerList()) {
                ServerPlayNetworking.send(p, meta);
            }
            //#endif
        } catch (Exception ignored) {
        }
    }
}
