package com.niuqu.chatbubble;

import com.niuqu.chatbubble.packets.ChatMetaPacket;
import com.niuqu.chatbubble.packets.ConfigSyncPacket;
import com.niuqu.chatbubble.packets.HistoryPacket;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.CommandEvent;
import net.minecraftforge.event.ServerChatEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.time.LocalTime;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class ChatServerListener {
    // \p{L}\p{N} covers non-ASCII names — cracked servers allow Chinese player
    // names, and the client MentionDetector already treats any letter/digit as
    // a name character, so the server regex must match its behavior
    private static final Pattern MENTION_PATTERN = Pattern.compile("@([\\p{L}\\p{N}_]+)");
    private static final int HISTORY_MAX = 50;

    private static final Map<UUID, QuotePending> pendingQuotes = new HashMap<>();
    private static final Deque<HistoryPacket.HistoryEntry> historyBuffer = new ArrayDeque<>();

    private record QuotePending(String quotedSenderName, String quotedContent, String messageHash, long time) {}

    // A quote that never made it into a sent message (e.g. an anti-spam plugin
    // blocked it) must not tag a later unrelated message — expire after 10s
    private static QuotePending takeQuote(UUID playerUUID) {
        QuotePending quote = takeQuote(playerUUID);
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
            ChatMetaPacket meta = new ChatMetaPacket(
                player.getUUID(), messageHash, quoteSender, quoteContent, mentions);
            NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), meta);
        }

        addToHistory(new HistoryPacket.HistoryEntry(
            player.getUUID(), player.getName().getString(), rawText,
            LocalTime.now(), false,
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
        ChatMetaPacket meta = new ChatMetaPacket(
            sender.getUUID(), messageHash,
            quote.quotedSenderName(), quote.quotedContent(),
            Collections.emptyList());
        NetworkHandler.CHANNEL.send(PacketDistributor.ALL.noArg(), meta);
    }

    @SubscribeEvent
    public void onPlayerLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)) return;

        // Always sync server-side settings so the client head menu matches the server
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new ConfigSyncPacket(ChatServerConfig.USE_TPA.get()));

        if (!ChatServerConfig.HISTORY_ENABLED.get()) return;
        if (historyBuffer.isEmpty()) return;
        NetworkHandler.CHANNEL.send(
            PacketDistributor.PLAYER.with(() -> player),
            new HistoryPacket(new ArrayList<>(historyBuffer)));
    }

    public static void onQuoteReceived(UUID senderUUID, String quotedSenderName,
                                        String quotedContent, String messageHash) {
        pendingQuotes.put(senderUUID, new QuotePending(quotedSenderName, quotedContent,
            messageHash, System.currentTimeMillis()));
    }

    private static void addToHistory(HistoryPacket.HistoryEntry entry) {
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
