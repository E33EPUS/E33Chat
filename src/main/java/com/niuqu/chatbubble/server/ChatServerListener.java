package com.niuqu.chatbubble.server;
import com.niuqu.chatbubble.network.NetworkHandler;

import com.niuqu.chatbubble.packets.ChatMetaPayload;
import com.niuqu.chatbubble.packets.ConfigSyncPayload;
import com.niuqu.chatbubble.packets.ConfigSyncV2Payload;
import com.niuqu.chatbubble.packets.HistoryPayload;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.event.CommandEvent;
import net.neoforged.neoforge.event.ServerChatEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.network.PacketDistributor;

public class ChatServerListener {
    // \p{L}\p{N} covers non-ASCII names — cracked servers allow Chinese player
    // names, and the client MentionDetector already treats any letter/digit as
    // a name character, so the server regex must match its behavior
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_]+)");
    private static final int HISTORY_MAX = 50;

    private static final Map<UUID, QuotePending> pendingQuotes = new HashMap<>();
    private static final Deque<HistoryPayload.HistoryEntry> historyBuffer = new ArrayDeque<>();

    private record QuotePending(String quotedSenderName, String quotedContent, String messageHash, long time) {}

    // A quote that never made it into a sent message (e.g. an anti-spam plugin
    // blocked it) must not tag a later unrelated message — expire after 10s
    private static QuotePending takeQuote(UUID playerUUID) {
        QuotePending quote = pendingQuotes.remove(playerUUID);
        if (quote != null && System.currentTimeMillis() - quote.time() > 10_000) return null;
        return quote;
    }

    @SubscribeEvent
    public void onServerChat(ServerChatEvent event) {
        ServerPlayer player = event.getPlayer();
        String rawText = event.getRawText();
        List<String> mentions = extractMentions(rawText, player.getServer().getPlayerList().getPlayerCount());

        QuotePending quote = takeQuote(player.getUUID());
        String messageHash = quote != null ? quote.messageHash() : String.valueOf(rawText.hashCode());
        String quoteSender = quote != null ? quote.quotedSenderName() : "";
        String quoteContent = quote != null ? quote.quotedContent() : "";

        if (quote != null || !mentions.isEmpty()) {
            ChatMetaPayload meta = new ChatMetaPayload(
                player.getUUID(), player.getName().getString(), messageHash,
                quoteSender, quoteContent, mentions);
            PacketDistributor.sendToAllPlayers(meta);
        }

        addToHistory(new HistoryPayload.HistoryEntry(
            player.getUUID(), player.getName().getString(), rawText,
            System.currentTimeMillis(), false,
            quote != null ? quote.quotedContent() : null,
            quote != null ? quote.quotedSenderName() : null));
    }

    @SubscribeEvent
    public void onCommand(CommandEvent event) {
        String cmd = event.getParseResults().getReader().getString();
        String[] parts = cmd.split(" ");
        if (parts.length < 3) return;
        String label = parts[0];
        if (label.startsWith("/")) label = label.substring(1);
        if (!label.equals("msg") && !label.equals("tell") && !label.equals("w") && !label.equals("whisper")) return;

        var sender = event.getParseResults().getContext().getSource().getPlayer();
        if (sender == null) return;

        QuotePending quote = takeQuote(sender.getUUID());
        if (quote == null) return;

        String messageHash = quote.messageHash();
        ChatMetaPayload meta = new ChatMetaPayload(
            sender.getUUID(), sender.getName().getString(), messageHash,
            quote.quotedSenderName(), quote.quotedContent(),
            Collections.emptyList());
        PacketDistributor.sendToAllPlayers(meta);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Always sync server-side settings so the client head menu matches the server.
        // Both payloads are sent: old clients only know ConfigSyncPayload (use_tpa),
        // new clients pick up the templates from ConfigSyncV2Payload; unknown types
        // are dropped harmlessly by old clients.
        PacketDistributor.sendToPlayer(player,
            new ConfigSyncPayload(ChatServerConfig.USE_TPA.get()));
        PacketDistributor.sendToPlayer(player, buildConfigV2());
        // Separate capability type: old clients drop unknown payloads, so
        // mediaEnabled never desyncs mixed client/server versions.
        PacketDistributor.sendToPlayer(player,
            new com.niuqu.chatbubble.packets.MediaCapPayload(ChatServerConfig.MEDIA_ENABLED.get()));

        if (!ChatServerConfig.HISTORY_ENABLED.get()) return;
        if (historyBuffer.isEmpty()) return;
        PacketDistributor.sendToPlayer(player,
            new HistoryPayload(new ArrayList<>(historyBuffer)));
    }

    /** Broadcast the full server config (templates included) to every player. */
    public static void broadcastServerConfig() {
        PacketDistributor.sendToAllPlayers(buildConfigV2());
    }

    private static volatile com.niuqu.chatbubble.server.DiskMediaStore mediaStore;

    /** Lazily-created media store next to the server config (<world>/serverconfig/, like Fabric). */
    public static com.niuqu.chatbubble.server.DiskMediaStore mediaStore() {
        com.niuqu.chatbubble.server.DiskMediaStore s = mediaStore;
        if (s == null) {
            synchronized (ChatServerListener.class) {
                s = mediaStore;
                if (s == null) {
                    net.minecraft.server.MinecraftServer server =
                        net.neoforged.neoforge.server.ServerLifecycleHooks.getCurrentServer();
                    java.nio.file.Path dir = server != null
                        ? server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
                            .resolve("serverconfig").resolve("e33chat-media")
                        : net.neoforged.fml.loading.FMLLoader.getGamePath()
                            .resolve("config").resolve("e33chat-media");
                    s = new com.niuqu.chatbubble.server.DiskMediaStore(dir);
                    mediaStore = s;
                }
            }
        }
        return s;
    }

    @SubscribeEvent
    public void onServerStopping(net.neoforged.neoforge.event.server.ServerStoppingEvent event) {
        com.niuqu.chatbubble.server.DiskMediaStore s = mediaStore;
        if (s != null) s.discardAllUploads();
        mediaStore = null;
    }

    @SubscribeEvent
    public void onPlayerLogout(PlayerEvent.PlayerLoggedOutEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;
        com.niuqu.chatbubble.server.DiskMediaStore s = mediaStore;
        if (s != null) s.discardUploadsFor(player.getName().getString());
    }

    private static ConfigSyncV2Payload buildConfigV2() {
        return new ConfigSyncV2Payload(
            ChatServerConfig.USE_TPA.get(),
            new ArrayList<>(ChatServerConfig.CHAT_TEMPLATES.get()),
            new ArrayList<>(ChatServerConfig.WHISPER_TEMPLATES.get()),
            ChatServerConfig.TEMPLATE_DEBUG.get());
    }

    public static void onQuoteReceived(UUID senderUUID, String quotedSenderName,
                                        String quotedContent, String messageHash) {
        pendingQuotes.put(senderUUID, new QuotePending(quotedSenderName, quotedContent,
            messageHash, System.currentTimeMillis()));
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
        while (m.find()) {
            mentions.add(m.group(1));
        }
        return mentions;
    }
}
