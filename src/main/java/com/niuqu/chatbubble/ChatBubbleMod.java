package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ServerConfig;
import com.niuqu.chatbubble.config.ServerConfigManager;
import com.niuqu.chatbubble.network.ChatMetaPayload;
import com.niuqu.chatbubble.network.ConfigSyncPayload;
import com.niuqu.chatbubble.network.HistoryPayload;
import com.niuqu.chatbubble.network.QuoteSyncPayload;
import net.fabricmc.api.ModInitializer;
import net.fabricmc.fabric.api.message.v1.ServerMessageEvents;
import net.fabricmc.fabric.api.networking.v1.PayloadTypeRegistry;
import net.fabricmc.fabric.api.networking.v1.ServerPlayConnectionEvents;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.server.network.ServerPlayerEntity;

import java.time.LocalTime;
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
    private static boolean configLoaded;

    private record QuotePending(String quotedSenderName, String quotedContent, String messageHash) {}

    @Override
    public void onInitialize() {
        PayloadTypeRegistry.playC2S().register(QuoteSyncPayload.ID, QuoteSyncPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ChatMetaPayload.ID, ChatMetaPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(HistoryPayload.ID, HistoryPayload.CODEC);
        PayloadTypeRegistry.playS2C().register(ConfigSyncPayload.ID, ConfigSyncPayload.CODEC);

        ServerPlayNetworking.registerGlobalReceiver(QuoteSyncPayload.ID, (payload, context) -> {
            ServerPlayerEntity player = context.player();
            context.server().execute(() -> {
                String messageHash = payload.messageHash();
                pendingQuotes.put(player.getUuid(),
                    new QuotePending(payload.quotedSenderName(), payload.quotedContent(), messageHash));
            });
        });

        ServerMessageEvents.CHAT_MESSAGE.register((message, sender, params) -> {
            String rawText = message.getContent().getString();
            int playerCount = sender.getServer() != null
                ? sender.getServer().getPlayerManager().getPlayerList().size() : 1;
            List<String> mentions = extractMentions(rawText, playerCount);

            QuotePending quote = pendingQuotes.remove(sender.getUuid());
            String messageHash = quote != null ? quote.messageHash() : String.valueOf(rawText.hashCode());
            String quoteSender = quote != null ? quote.quotedSenderName() : "";
            String quoteContent = quote != null ? quote.quotedContent() : "";

            if (quote != null || !mentions.isEmpty()) {
                ChatMetaPayload meta = new ChatMetaPayload(
                    sender.getUuid(), messageHash, quoteSender, quoteContent, mentions);
                for (ServerPlayerEntity p : sender.getServer().getPlayerManager().getPlayerList()) {
                    ServerPlayNetworking.send(p, meta);
                }
            }

            addToHistory(new HistoryPayload.HistoryEntry(
                sender.getUuid(), sender.getName().getString(), rawText,
                LocalTime.now(), false,
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
                useTpa = config.use_tpa;
                historyEnabled = config.history_enabled;
            }

            // Always sync server-side settings so the client head menu matches the server
            ServerPlayNetworking.send(handler.player,
                new ConfigSyncPayload(useTpa));

            if (!historyEnabled) return;
            if (historyBuffer.isEmpty()) return;
            ServerPlayNetworking.send(handler.player,
                new HistoryPayload(new ArrayList<>(historyBuffer)));
        });
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
