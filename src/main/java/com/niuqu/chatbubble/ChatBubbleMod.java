package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ServerConfig;
import com.niuqu.chatbubble.config.ServerConfigManager;
import com.niuqu.chatbubble.network.ChatMetaPayload;
import com.niuqu.chatbubble.network.ConfigSyncPayload;
import com.niuqu.chatbubble.network.ConfigSyncV2Payload;
import com.niuqu.chatbubble.network.HistoryPayload;
import com.niuqu.chatbubble.network.MediaRequestPayload;
import com.niuqu.chatbubble.network.MediaResponsePayload;
import com.niuqu.chatbubble.network.MediaUploadAckPayload;
import com.niuqu.chatbubble.network.MediaUploadPayload;
import com.niuqu.chatbubble.network.EasyBotConfigPayload;
import com.niuqu.chatbubble.network.MediaCapPayload;
import com.niuqu.chatbubble.network.QuoteSyncPayload;
import com.niuqu.chatbubble.network.ServerConfigSavePayload;
import com.niuqu.chatbubble.network.ServerConfigScreenPayload;
import com.niuqu.chatbubble.server.DiskMediaStore;
import net.fabricmc.api.ModInitializer;
import com.mojang.brigadier.ParseResults;
//#if MC >= 12000
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
//#endif
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
import net.minecraft.text.Text;
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

    // Server-side settings (loaded per-world from <world>/serverconfig/e33chat-server.json)
    private static boolean historyEnabled;
    private static boolean useTpa;
    private static boolean templateDebug;
    private static boolean mediaEnabled;
    private static boolean mediaAutoClean = true;
    private static boolean easyBotCompat = true;
    private static List<String> chatTemplates = List.of();
    private static List<String> whisperTemplates = List.of();
    private static boolean configLoaded;
    private static volatile com.niuqu.chatbubble.server.DiskMediaStore mediaStore;

    /** Lazily-created per-world media store (next to the server config). */
    private static com.niuqu.chatbubble.server.DiskMediaStore mediaStore(net.minecraft.server.MinecraftServer server) {
        com.niuqu.chatbubble.server.DiskMediaStore s = mediaStore;
        if (s == null) {
            synchronized (ChatBubbleMod.class) {
                s = mediaStore;
                if (s == null) {
                    s = new com.niuqu.chatbubble.server.DiskMediaStore(
                        server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                            .resolve("serverconfig").resolve("e33chat-media"));
                    mediaStore = s;
                }
            }
        }
        return s;
    }

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
        PayloadTypeRegistry.playC2S().register(MediaUploadPayload.ID, MediaUploadPayload.CODEC);
        PayloadTypeRegistry.playC2S().register(MediaRequestPayload.ID, MediaRequestPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MediaUploadAckPayload.ID, MediaUploadAckPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MediaResponsePayload.ID, MediaResponsePayload.CODEC);
        PayloadTypeRegistry.playS2C().register(MediaCapPayload.ID, MediaCapPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(EasyBotConfigPayload.ID, EasyBotConfigPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(MediaUploadPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                if (!mediaEnabled) {
                    ServerPlayNetworking.send(player,
                        new MediaUploadAckPayload(payload.uploadId(), null, "disabled"));
                    return;
                }
                DiskMediaStore store = mediaStore(context.server());
                String result;
                if (payload.index() == 0) {
                    result = store.beginUpload(payload.uploadId(), player.getName().getString(),
                        payload.totalChunks(), payload.totalBytes(), payload.contentType());
                    if (result == null) {
                        result = store.acceptChunk(payload.uploadId(), payload.index(), payload.chunk());
                    }
                } else {
                    result = store.acceptChunk(payload.uploadId(), payload.index(), payload.chunk());
                }
                if (result == null) return; // upload still in progress
                store.discardUpload(payload.uploadId());
                if (DiskMediaStore.isValidMediaId(result)) {
                    // 2.4.0 sync: 6h-throttled sweep after a finished upload (opt-in)
                    if (mediaAutoClean) store.cleanupExpiredThrottled();
                    ServerPlayNetworking.send(player,
                        new MediaUploadAckPayload(payload.uploadId(), result, null));
                } else {
                    ServerPlayNetworking.send(player,
                        new MediaUploadAckPayload(payload.uploadId(), null, result));
                }
            });
        });

        ServerPlayNetworking.registerGlobalReceiver(MediaRequestPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                String id = payload.mediaId();
                DiskMediaStore store = mediaStore(context.server());
                long size = store.sizeOf(id);
                if (size < 0) {
                    ServerPlayNetworking.send(player,
                        new MediaResponsePayload(id, 0, 1, new byte[0]));
                    return;
                }
                int total = DiskMediaStore.totalChunksFor(size);
                for (int i = 0; i < total; i++) {
                    byte[] chunk = store.readChunk(id, i, total);
                    if (chunk == null) {
                        ServerPlayNetworking.send(player,
                            new MediaResponsePayload(id, 0, 1, new byte[0]));
                        return;
                    }
                    ServerPlayNetworking.send(player, new MediaResponsePayload(id, i, total, chunk));
                }
            });
        });

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
                //$$ if (!player.getPermissions().hasPermission(
                //$$     new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.GAMEMASTERS))) {
                //#else
                if (!player.hasPermissionLevel(2)) {
                //#endif
                //#endif
                    //#if MC >= 26000
                    //$$ player.sendSystemMessage(Text.translatable("e33chat.server.op_required")
                    //$$     .formatted(Formatting.RED));
                    //#else
                    player.sendMessage(Text.translatable("e33chat.server.op_required")
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
            //$$ var server = sender.getEntityWorld().getServer();
            //#endif
            //#else
            var server = sender.getServer();
            //#endif
            int playerCount = server != null
                ? server.getPlayerManager().getPlayerList().size() : 1;
            List<String> mentions = extractMentions(rawText, playerCount);

            QuotePending quote = takeQuote(sender.getUuid());
            String messageHash = quote != null ? quote.messageHash() : String.valueOf(rawText.hashCode());
            String quoteSender = quote != null ? quote.quotedSenderName() : "";
            String quoteContent = quote != null ? quote.quotedContent() : "";

            if (quote != null || !mentions.isEmpty()) {
                ChatMetaPayload meta = new ChatMetaPayload(
                    sender.getUuid(), sender.getName().getString(), messageHash,
                    quoteSender, quoteContent, mentions);
                //#if MC >= 12005
                for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(p, meta);
                }
                //#endif
            }

            addToHistory(new HistoryPayload.HistoryEntry(
                sender.getUuid(), sender.getName().getString(), rawText,
                System.currentTimeMillis(), false,
                quote != null ? quote.quotedContent() : null,
                quote != null ? quote.quotedSenderName() : null));
        });
        //#endif

        ServerPlayConnectionEvents.JOIN.register((handler, sender, server) -> {
            // Load server config from <world>/serverconfig/ on first join (matching
            // NeoForge's per-world ModConfig.Type.SERVER convention)
            if (!configLoaded) {
                configLoaded = true;
                var configPath = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
                    .resolve("serverconfig").resolve("e33chat-server.json");
                ServerConfig config = ServerConfigManager.load(configPath);
                loadConfig(config);
                // 2.4.0 sync: sweep expired media once per server start (opt-in)
                if (mediaAutoClean) mediaStore(server).cleanupExpired();
            }

            // Always sync server-side settings so the client head menu matches the server
            //#if MC >= 12005
            ServerPlayNetworking.send(handler.player,
                new ConfigSyncPayload(useTpa));
            ServerPlayNetworking.send(handler.player, buildConfigV2());
            // Separate capability type: old clients drop unknown payloads, so
            // mediaEnabled never desyncs mixed client/server versions.
            ServerPlayNetworking.send(handler.player,
                new MediaCapPayload(mediaEnabled));
            ServerPlayNetworking.send(handler.player,
                new EasyBotConfigPayload(easyBotCompat));

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

    // Called from CommandManagerMixin.execute (parity with Forge ChatServerListener.onCommand):
    // /msg /tell /w /whisper carry a quote the client synced (QuoteSyncPayload); consume it
    // here and broadcast the quote meta, because vanilla private messages never hit
    // ServerMessageEvents.CHAT_MESSAGE
    public static void consumePrivateMessageQuote(ParseResults<ServerCommandSource> parseResults, String command) {
        String[] parts = command.split(" ");
        if (parts.length < 3) return;
        String label = parts[0];
        if (label.startsWith("/")) label = label.substring(1);
        if (!label.equals("msg") && !label.equals("tell") && !label.equals("w") && !label.equals("whisper")) return;
        ServerCommandSource source = parseResults.getContext().getSource();
        //#if MC >= 11900
        ServerPlayerEntity sender = source.getPlayer();
        //#else
        //$$ ServerPlayerEntity sender;
        //$$ try {
        //$$     sender = source.getPlayer();
        //$$ } catch (com.mojang.brigadier.exceptions.CommandSyntaxException e) {
        //$$     return;
        //$$ }
        //#endif
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
    }

    private static void loadConfig(ServerConfig config) {
        useTpa = config.use_tpa;
        historyEnabled = config.history_enabled;
        templateDebug = config.template_debug;
        mediaEnabled = config.media_enabled;
        mediaAutoClean = config.media_auto_clean != null ? config.media_auto_clean : true;
        easyBotCompat = config.easy_bot_compat != null ? config.easy_bot_compat : true;
        chatTemplates = config.chat_templates != null ? config.chat_templates : List.of();
        whisperTemplates = config.whisper_templates != null ? config.whisper_templates : List.of();
    }

    public static void broadcastServerConfig(net.minecraft.server.MinecraftServer server) {
        var v2 = buildConfigV2();
        //#if MC >= 12005
        for (ServerPlayerEntity p : server.getPlayerManager().getPlayerList()) {
            ServerPlayNetworking.send(p, v2);
            // Re-broadcast the media-hosting capability so toggling media_enabled
            // from the GUI takes effect for already-connected clients immediately
            // (it is otherwise only sent on join).
            ServerPlayNetworking.send(p, new MediaCapPayload(mediaEnabled));
            // Re-broadcast the EasyBot toggle for already-connected clients
            ServerPlayNetworking.send(p, new EasyBotConfigPayload(easyBotCompat));
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
    public static boolean mediaEnabled() { return mediaEnabled; }
    public static boolean mediaAutoClean() { return mediaAutoClean; }
    public static boolean easyBotCompat() { return easyBotCompat; }
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
