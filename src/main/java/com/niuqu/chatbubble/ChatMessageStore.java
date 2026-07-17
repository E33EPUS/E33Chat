package com.niuqu.chatbubble;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class ChatMessageStore {
    private static final int MAX = 10000;
    private static final List<ChatMessage> messages = new ArrayList<>();
    private static int unreadCount = 0;
    private static boolean screenOpen = false;
    private static String pendingReplyContent;
    private static String pendingReplySender;
    private static final List<PreviewEntry> previews = new ArrayList<>();
    private static final int PREVIEW_TICKS = 100;
    private static String strongHintText;
    private static int strongHintTicks;
    private static boolean strongHintIsMention;
    public static final int STRONG_HINT_DURATION = 60;

    private static String currentWorldKey;
    private static final Map<String, String> worldTitles = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static boolean titlesLoaded;
    private static final Map<String, PendingMeta> pendingMetas = new HashMap<>();

    public record SenderMeta(UUID senderUUID, Component senderName,
                             Component rawContent, boolean isSystem,
                             String rawPlayerName) {}

    private static final ThreadLocal<SenderMeta> PENDING_META = new ThreadLocal<>();

    public static void setPendingMeta(SenderMeta meta) { PENDING_META.set(meta); }

    public static SenderMeta consumePendingMeta() {
        SenderMeta m = PENDING_META.get();
        PENDING_META.remove();
        return m;
    }

    private static int pendingEchoCount;
    private static final List<String> pendingEchoTexts = new ArrayList<>();

    public static void incrementPendingEcho(String sentText) {
        pendingEchoCount++;
        pendingEchoTexts.add(sentText);
    }

    public static boolean consumeEchoBySystemChat(String incomingText) {
        if (pendingEchoCount <= 0) return false;
        for (int i = 0; i < pendingEchoTexts.size(); i++) {
            if (incomingText.equals(pendingEchoTexts.get(i))) {
                pendingEchoTexts.remove(i);
                pendingEchoCount--;
                return true;
            }
        }
        return false;
    }

    public static boolean consumeEchoIfSenderMatches(String senderName) {
        if (pendingEchoCount <= 0) return false;
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return false;
        if (senderName.contains(player.getName().getString())) {
            pendingEchoCount--;
            if (!pendingEchoTexts.isEmpty()) pendingEchoTexts.remove(0);
            return true;
        }
        return false;
    }

    public static UUID lookupPlayerUUID(String name) {
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return new UUID(0, 0);
        var connection = player.connection;
        if (connection == null) return new UUID(0, 0);
        for (var info : connection.getOnlinePlayers()) {
            if (info.getProfile().getName().equals(name))
                return info.getProfile().getId();
        }
        return new UUID(0, 0);
    }

    public static boolean isRecentDuplicate(String content) {
        int size = messages.size();
        for (int i = size - 1; i >= 0 && i >= size - 2; i--) {
            if (messages.get(i).content().getString().equals(content)) return true;
        }
        return false;
    }

    private record PendingMeta(UUID senderUUID, String quoteSender, String quoteContent,
                               List<String> mentionTargets, LocalTime createdAt) {}

    public record ChatMessage(
        UUID senderUUID,
        Component senderName,
        Component content,
        LocalTime time,
        boolean isOwn,
        boolean isSystem,
        String replyContent,
        String replySender,
        String messageHash,
        int duplicateCount,
        String rawPlayerName
    ) {}

    public static class PreviewEntry {
        public final String text;
        public int ticks;
        public PreviewEntry(String text, int ticks) {
            this.text = text;
            this.ticks = ticks;
        }
    }

    public static void addMessage(Component content, UUID senderUUID, Component senderName, boolean isSystem, String rawPlayerName) {
        content = addUnderlineToClicks(content);
        String messageHash = content.getString().hashCode() + ":" + System.currentTimeMillis();

        String playerName = net.minecraft.client.Minecraft.getInstance().player != null
            ? net.minecraft.client.Minecraft.getInstance().player.getName().getString() : "";
        boolean own = senderName != null && senderName.getString().equals(playerName);

        if (ChatBubbleConfig.ANTI_SPAM.get() && !messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            if (!last.isSystem() && last.senderName().getString().equals(senderName.getString())
                && last.content().getString().equals(content.getString())) {
                if (own && pendingReplyContent != null) {
                    pendingReplyContent = null;
                    pendingReplySender = null;
                }
                messages.set(messages.size() - 1, new ChatMessage(
                    last.senderUUID(), last.senderName(), last.content(),
                    LocalTime.now(),
                    last.isOwn(), last.isSystem(),
                    last.replyContent(), last.replySender(), last.messageHash(),
                    last.duplicateCount() + 1,
                    last.rawPlayerName()
                ));
                return;
            }
        }

        PendingMeta pending = pendingMetas.remove(messageHash);
        if (pending != null && pending.createdAt().isBefore(LocalTime.now().minusSeconds(10))) {
            pending = null;
        }

        String replyContent = null;
        String replySender = null;
        if (own && pendingReplyContent != null) {
            replyContent = pendingReplyContent;
            replySender = pendingReplySender;
            pendingReplyContent = null;
            pendingReplySender = null;
        } else if (pending != null && !pending.quoteContent().isEmpty()) {
            replyContent = pending.quoteContent();
            replySender = pending.quoteSender();
        }

        messages.add(new ChatMessage(
            senderUUID,
            senderName != null ? senderName : Component.literal(""),
            content,
            LocalTime.now(),
            own,
            isSystem,
            replyContent,
            replySender,
            messageHash,
            1,
            rawPlayerName
        ));

        while (messages.size() > MAX)
            messages.remove(0);

        boolean isMentionOrQuote = !own && !isSystem
            && (content.getString().contains("@" + playerName)
                || (replySender != null && replySender.equals(playerName)));

        if (isMentionOrQuote && Minecraft.getInstance().player != null) {
            Minecraft.getInstance().player.playSound(
                net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.0F);
        }

        if (!screenOpen) {
            unreadCount++;
            boolean mentionToHint = isMentionOrQuote && ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED.get();
            boolean systemToHint = isSystem && ChatBubbleConfig.STRONG_HINT_ENABLED.get();

            if (mentionToHint) {
                strongHintText = Component.translatable("e33chat.notif.mention").getString();
                strongHintTicks = STRONG_HINT_DURATION;
                strongHintIsMention = true;
            }

            if (ChatBubbleConfig.PREVIEW_ENABLED.get() && !systemToHint && !mentionToHint) {
                String sender = senderName != null ? senderName.getString() : "";
                if (sender.isEmpty() && isSystem) sender = Component.translatable("e33chat.sender.system").getString();
                String preview = sender.isEmpty() ? content.getString() : sender + ": " + content.getString();
                previews.add(new PreviewEntry(preview, PREVIEW_TICKS));
                while (previews.size() > ChatBubbleConfig.PREVIEW_LINES.get())
                    previews.remove(0);
            }
            if (systemToHint && !mentionToHint) {
                strongHintText = content.getString();
                strongHintTicks = STRONG_HINT_DURATION;
                strongHintIsMention = false;
            }
        }
    }

    private static Component addUnderlineToClicks(Component original) {
        MutableComponent result = Component.empty();
        original.visit((style, text) -> {
            Style newStyle = style.getClickEvent() != null
                ? style.withUnderlined(true)
                : style;
            result.append(Component.literal(text).withStyle(newStyle));
            return Optional.<Object>empty();
        }, Style.EMPTY);
        return result;
    }

    public static List<ChatMessage> getMessages() {
        return messages;
    }

    public static int getUnreadCount() {
        return unreadCount;
    }

    public static void markAllRead() {
        unreadCount = 0;
    }

    public static void setScreenOpen(boolean open) {
        screenOpen = open;
        if (open) {
            unreadCount = 0;
        }
    }

    public static boolean hasUnreadMention(String playerName) {
        if (playerName == null || playerName.isEmpty()) return false;
        for (int i = messages.size() - unreadCount; i < messages.size(); i++) {
            if (i < 0) continue;
            String text = messages.get(i).content().getString();
            if (text.contains("@" + playerName)) return true;
        }
        return false;
    }

    public static Component quoteMessage(int index) {
        if (index < 0 || index >= messages.size()) return Component.literal("");
        ChatMessage msg = messages.get(index);
        MutableComponent quote = Component.literal("> " + msg.senderName().getString() + ": ");
        quote.append(msg.content());
        return quote;
    }

    public static ChatMessage getMessageAt(int index) {
        if (index < 0 || index >= messages.size()) return null;
        return messages.get(index);
    }

    public static void setPendingReply(String content, String sender) {
        pendingReplyContent = content;
        pendingReplySender = sender;
    }

    public static List<PreviewEntry> getPreviews() {
        return previews.isEmpty() ? null : previews;
    }

    public static void tickPreview() {
        var it = previews.iterator();
        while (it.hasNext()) {
            PreviewEntry e = it.next();
            if (--e.ticks <= 0) it.remove();
        }
    }

    public static String getStrongHintText() { return strongHintTicks > 0 ? strongHintText : null; }

    public static boolean isStrongHintMention() { return strongHintIsMention; }

    public static int getStrongHintTicks() { return strongHintTicks; }

    public static void tickStrongHint() { if (strongHintTicks > 0) strongHintTicks--; }

    public static int size() {
        return messages.size();
    }

    public static String getCustomTitle() {
        if (currentWorldKey == null) return null;
        loadWorldTitles();
        String v = worldTitles.get(currentWorldKey);
        return (v != null && !v.isEmpty()) ? v : null;
    }

    public static void setCustomTitle(String title) {
        if (currentWorldKey == null) return;
        loadWorldTitles();
        String v = (title != null && !title.isEmpty()) ? title : "";
        if (v.isEmpty())
            worldTitles.remove(currentWorldKey);
        else
            worldTitles.put(currentWorldKey, v);
        saveWorldTitles();
    }

    private static boolean isWorldSpecific(String key) {
        return key != null && (key.startsWith("SP:") || key.startsWith("MP:"));
    }

    public static void setCurrentWorld(String name) {
        if (java.util.Objects.equals(name, currentWorldKey)) return;
        boolean wasFallback = "world".equals(currentWorldKey);
        boolean isSpecific = name != null && (name.startsWith("SP:") || name.startsWith("MP:"));
        boolean isRefinement = wasFallback && isSpecific;
        boolean hasPendingMessages = currentWorldKey == null && isSpecific && !messages.isEmpty();
        if (ChatBubbleConfig.CHAT_HISTORY_ENABLED.get() && isWorldSpecific(currentWorldKey))
            saveMessages(currentWorldKey);
        currentWorldKey = name;
        if (isRefinement || hasPendingMessages) {
            if (ChatBubbleConfig.CHAT_HISTORY_ENABLED.get() && isWorldSpecific(currentWorldKey))
                loadMessages(currentWorldKey);
            return;
        }
        messages.clear();
        unreadCount = 0;
        previews.clear();
        if (ChatBubbleConfig.CHAT_HISTORY_ENABLED.get() && isWorldSpecific(currentWorldKey))
            loadMessages(currentWorldKey);
    }

    private static File getHistoryFile(String worldKey) {
        String safe = worldKey.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        String hash = Integer.toHexString(worldKey.hashCode());
        return new File(Minecraft.getInstance().gameDirectory, "e33chat/history/" + safe + "_" + hash + ".json");
    }

    private static void saveMessages(String worldKey) {
        if (messages.isEmpty()) return;
        File f = getHistoryFile(worldKey);
        f.getParentFile().mkdirs();
        List<Object> list = new ArrayList<>();
        for (ChatMessage msg : messages) {
            var obj = new HashMap<String, Object>();
            obj.put("senderUUID", msg.senderUUID().toString());
            obj.put("senderName", msg.senderName().getString());
            try {
                var level = Minecraft.getInstance().level;
                obj.put("content", level != null
                    ? Component.Serializer.toJson(msg.content(), level.registryAccess())
                    : msg.content().getString());
            } catch (Exception e) {
                obj.put("content", msg.content().getString());
            }
            obj.put("time", msg.time().format(DateTimeFormatter.ISO_LOCAL_TIME));
            obj.put("isOwn", msg.isOwn());
            obj.put("isSystem", msg.isSystem());
            if (msg.replyContent() != null) {
                obj.put("replyContent", msg.replyContent());
                obj.put("replySender", msg.replySender());
            }
            if (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty()) {
                obj.put("rawPlayerName", msg.rawPlayerName());
            }
            list.add(obj);
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            GSON.toJson(list, w);
        } catch (Exception ignored) {}
    }

    private static void loadMessages(String worldKey) {
        File f = getHistoryFile(worldKey);
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            List<Map<String, Object>> list = GSON.fromJson(r, new TypeToken<List<Map<String, Object>>>(){}.getType());
            if (list == null) return;
            for (Map<String, Object> obj : list) {
                try {
                    UUID uuid = UUID.fromString((String) obj.get("senderUUID"));
                    Component senderName = Component.literal((String) obj.get("senderName"));
                    Component content;
                    try {
                        var level = Minecraft.getInstance().level;
                        content = level != null
                            ? Component.Serializer.fromJson((String) obj.get("content"), level.registryAccess())
                            : null;
                    } catch (Exception e) {
                        content = null;
                    }
                    if (content == null) content = Component.literal((String) obj.getOrDefault("content", ""));
                    LocalTime time = LocalTime.parse((String) obj.get("time"), DateTimeFormatter.ISO_LOCAL_TIME);
                    boolean isOwn = (Boolean) obj.getOrDefault("isOwn", false);
                    boolean isSystem = (Boolean) obj.getOrDefault("isSystem", false);
                    String replyContent = (String) obj.get("replyContent");
                    String replySender = (String) obj.get("replySender");
                    String rawPlayerName = (String) obj.get("rawPlayerName");
                    messages.add(new ChatMessage(uuid, senderName, content, time,
                        isOwn, isSystem, replyContent, replySender, "", 1, rawPlayerName));
                } catch (Exception ignored) {}
            }
            while (messages.size() > MAX) messages.remove(0);
        } catch (Exception ignored) {}
    }

    private static File getTitlesFile() {
        return new File(Minecraft.getInstance().gameDirectory, "e33chat/titles.json");
    }

    private static void loadWorldTitles() {
        if (titlesLoaded) return;
        titlesLoaded = true;
        File f = getTitlesFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Map<String, String> data = GSON.fromJson(r, new TypeToken<Map<String, String>>(){}.getType());
            if (data != null) worldTitles.putAll(data);
        } catch (Exception ignored) {}
    }

    private static void saveWorldTitles() {
        File f = getTitlesFile();
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            GSON.toJson(worldTitles, w);
        } catch (Exception ignored) {}
    }

    public static void applyChatMeta(UUID senderUUID, String messageHash, String quoteSender,
                                      String quoteContent, List<String> mentionTargets) {
        LocalTime cutoff = LocalTime.now().minusSeconds(5);
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.messageHash().equals(messageHash) && msg.senderUUID().equals(senderUUID)) {
                if (msg.replyContent() != null) continue;
                if (msg.time().isBefore(cutoff)) continue;
                if (!quoteContent.isEmpty()) {
                    messages.set(i, new ChatMessage(
                        msg.senderUUID(), msg.senderName(), msg.content(), msg.time(),
                        msg.isOwn(), msg.isSystem(), quoteContent, quoteSender, msg.messageHash(),
                        msg.duplicateCount(), msg.rawPlayerName()));
                    String playerName = Minecraft.getInstance().player != null
                        ? Minecraft.getInstance().player.getName().getString() : "";
                    if (!msg.isOwn() && !playerName.isEmpty()
                        && playerName.equals(quoteSender)
                        && !msg.content().getString().contains("@" + playerName)) {
                        Minecraft.getInstance().player.playSound(
                            net.minecraft.sounds.SoundEvents.NOTE_BLOCK_CHIME.value(), 0.6F, 1.0F);
                        if (!screenOpen && ChatBubbleConfig.MENTION_STRONG_HINT_ENABLED.get()) {
                            strongHintText = Component.translatable("e33chat.notif.mention").getString();
                            strongHintTicks = STRONG_HINT_DURATION;
                            strongHintIsMention = true;
                        }
                    }
                }
                return;
            }
        }
        if (!quoteContent.isEmpty()) {
            pendingMetas.put(messageHash, new PendingMeta(senderUUID, quoteSender, quoteContent,
                mentionTargets, LocalTime.now()));
        }
    }
}
