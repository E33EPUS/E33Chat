package com.niuqu.chatbubble;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public class ChatMessageStore {
    private static final int MAX = 10000;
    private static final List<ChatMessage> messages = new ArrayList<>();
    private static int unreadCount;
    private static boolean hasUnreadMentionFlag;
    private static boolean screenOpen;
    private static String pendingReplyContent;
    private static String pendingReplySender;
    private static final List<PreviewEntry> previews = new ArrayList<>();
    private static final int PREVIEW_TICKS = 100;
    private record HintEntry(Text text, boolean isMention) {}
    private static final LinkedList<HintEntry> strongHintQueue = new LinkedList<>();
    private static int strongHintTicks;
    public static final int STRONG_HINT_DURATION = 60;

    private static String currentWorldKey;
    private static final Map<String, String> worldTitles = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static boolean titlesLoaded;
    private static final Map<String, PendingMeta> pendingMetas = new HashMap<>();

    private static volatile boolean serverUseTpa;
    public static void setServerUseTpa(boolean v) { serverUseTpa = v; }
    public static boolean useTpa() { return serverUseTpa; }

    // Seen-player cache: tracks players we've encountered to help with name resolution
    public record SeenPlayer(UUID uuid, String profileName, String displayName) {}
    private static final int SEEN_PLAYERS_CAP = 512;
    private static final Map<UUID, SeenPlayer> seenPlayers = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, SeenPlayer> eldest) {
            return size() > SEEN_PLAYERS_CAP;
        }
    };

    public static void rememberPlayer(UUID uuid, String profileName, String displayName) {
        if (uuid == null || uuid.equals(new UUID(0, 0)) || profileName == null || profileName.isEmpty()) return;
        SeenPlayer existing = seenPlayers.get(uuid);
        String newDisplay = (displayName != null && !displayName.isEmpty()) ? displayName
            : (existing != null ? existing.displayName() : null);
        seenPlayers.put(uuid, new SeenPlayer(uuid, profileName, newDisplay));
    }

    public static List<String> knownNameVariants() {
        Set<String> out = new LinkedHashSet<>();
        for (SeenPlayer sp : seenPlayers.values()) {
            if (sp.profileName() != null && !sp.profileName().isEmpty()) {
                out.add(sp.profileName());
                String stripped = sp.profileName().replaceAll("§.", "");
                if (!stripped.isEmpty()) out.add(stripped);
            }
            if (sp.displayName() != null && !sp.displayName().isEmpty()) {
                out.add(sp.displayName());
                String stripped = sp.displayName().replaceAll("§.", "");
                if (!stripped.isEmpty()) out.add(stripped);
            }
        }
        return new ArrayList<>(out);
    }

    public static UUID findSeenUuid(String name) {
        if (name == null || name.isEmpty()) return null;
        String stripped = name.replaceAll("§.", "");
        for (SeenPlayer sp : seenPlayers.values()) {
            if (matchesSeenName(name, stripped, sp.profileName())) return sp.uuid();
            if (matchesSeenName(name, stripped, sp.displayName())) return sp.uuid();
        }
        return null;
    }

    private static boolean matchesSeenName(String raw, String stripped, String stored) {
        if (stored == null || stored.isEmpty()) return false;
        if (raw.equals(stored)) return true;
        if (!stripped.isEmpty() && stripped.equals(stored)) return true;
        String storedStripped = stored.replaceAll("§.", "");
        return raw.equals(storedStripped) || (!stripped.isEmpty() && stripped.equals(storedStripped));
    }

    public record SenderMeta(UUID senderUUID, Text senderName,
                             Text rawContent, boolean isSystem,
                             String rawPlayerName,
                             boolean whisper, String whisperPartner) {}

    private static final ThreadLocal<SenderMeta> PENDING_META = new ThreadLocal<>();
    private static long pendingMetaSetTime;

    public static void setPendingMeta(SenderMeta meta) {
        PENDING_META.set(meta);
        pendingMetaSetTime = System.currentTimeMillis();
    }

    public static SenderMeta consumePendingMeta() {
        SenderMeta m = PENDING_META.get();
        PENDING_META.remove();
        if (m != null && System.currentTimeMillis() - pendingMetaSetTime > 2_000) return null;
        return m;
    }

    private record PendingEcho(String text, long time) {}
    private static final List<PendingEcho> pendingEchoes = new ArrayList<>();

    private static long pendingWhisperEchoTime;
    private static String pendingWhisperEchoTarget;
    private static long suppressCaptureTime;

    public static void markPendingWhisperEcho(String target) {
        pendingWhisperEchoTime = System.currentTimeMillis();
        pendingWhisperEchoTarget = target;
    }
    public static void markSuppressCapture() { suppressCaptureTime = System.currentTimeMillis(); }

    public static boolean hasPendingWhisperEcho() {
        return pendingWhisperEchoTime != 0 && System.currentTimeMillis() - pendingWhisperEchoTime < 10_000;
    }
    public static String getPendingWhisperTarget() { return pendingWhisperEchoTarget; }
    public static void consumeWhisperEcho() { pendingWhisperEchoTime = 0; pendingWhisperEchoTarget = null; }

    public static boolean consumeSuppressCapture() {
        if (suppressCaptureTime == 0) return false;
        boolean fresh = System.currentTimeMillis() - suppressCaptureTime < 5_000;
        suppressCaptureTime = 0;
        return fresh;
    }

    private static void purgeStaleEchoes() {
        long cutoff = System.currentTimeMillis() - 10_000;
        pendingEchoes.removeIf(e -> e.time() < cutoff);
    }

    public static void incrementPendingEcho(String sentText) {
        purgeStaleEchoes();
        pendingEchoes.add(new PendingEcho(sentText, System.currentTimeMillis()));
    }

    public static boolean consumeEchoBySystemChat(String incomingText) {
        purgeStaleEchoes();
        for (int i = 0; i < pendingEchoes.size(); i++) {
            if (incomingText.equals(pendingEchoes.get(i).text())) {
                pendingEchoes.remove(i);
                return true;
            }
        }
        return false;
    }

    public static void debugLog(String msg) {
        debugLog(() -> msg);
    }

    public static void debugLog(java.util.function.Supplier<String> msg) {
        if (ChatBubbleClientSetup.config().debugLog())
            com.mojang.logging.LogUtils.getLogger().info(msg.get());
    }

    public static boolean consumeEchoIfSenderMatches(UUID senderUUID, Text senderName) {
        purgeStaleEchoes();
        if (pendingEchoes.isEmpty()) return false;
        var player = MinecraftClient.getInstance().player;
        if (player == null) return false;
        boolean match = senderUUID != null && senderUUID.equals(player.getUuid());
        if (!match) {
            String s = senderName.getString();
            match = containsWholeName(s, player.getName().getString());
            if (!match && player.networkHandler != null) {
                var info = player.networkHandler.getPlayerListEntry(player.getUuid());
                if (info != null && info.getDisplayName() != null) {
                    String tab = info.getDisplayName().getString().trim();
                    match = !tab.isEmpty() && containsWholeName(s, tab);
                }
            }
        }
        if (match) {
            pendingEchoes.remove(0);
            updateLatestOwnSenderName(senderName);
            return true;
        }
        return false;
    }

    static boolean containsWholeName(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return false;
        String h = haystack.replaceAll("§.", "");
        String n = needle.replaceAll("§.", "");
        if (n.isEmpty()) return false;
        int from = 0;
        while (true) {
            int idx = h.indexOf(n, from);
            if (idx < 0) return false;
            int end = idx + n.length();
            boolean leftOk = idx == 0 || !isNamePart(h.charAt(idx - 1));
            boolean rightOk = end >= h.length() || !isNamePart(h.charAt(end));
            if (leftOk && rightOk) return true;
            from = end;
        }
    }

    static boolean isNamePart(char c) {
        return (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z')
            || (c >= '0' && c <= '9') || c == '_';
    }

    private static void updateLatestOwnSenderName(Text senderName) {
        for (int i = messages.size() - 1; i >= 0 && i >= messages.size() - 5; i--) {
            ChatMessage m = messages.get(i);
            if (!m.isOwn()) continue;
            if (!m.senderName().getString().equals(senderName.getString())) {
                messages.set(i, new ChatMessage(
                    m.senderUUID(), senderName, m.content(), m.time(),
                    m.isOwn(), m.isSystem(), m.replyContent(), m.replySender(),
                    m.messageHash(), m.duplicateCount(), m.rawPlayerName(),
                    m.whisper(), m.whisperPartner()));
            }
            return;
        }
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
        Text senderName,
        Text content,
        LocalTime time,
        boolean isOwn,
        boolean isSystem,
        String replyContent,
        String replySender,
        String messageHash,
        int duplicateCount,
        String rawPlayerName,
        boolean whisper,
        String whisperPartner
    ) {}

    public static class PreviewEntry {
        public final Text text;
        public int ticks;
        public PreviewEntry(Text text, int ticks) {
            this.text = text;
            this.ticks = ticks;
        }
    }

    private static boolean isSameSender(ChatMessage last, Text senderName, String rawPlayerName) {
        if (rawPlayerName != null && !rawPlayerName.isEmpty()
            && last.rawPlayerName() != null && !last.rawPlayerName().isEmpty()) {
            return rawPlayerName.equals(last.rawPlayerName());
        }
        return last.senderName().getString().equals(senderName.getString());
    }

    public static void addMessage(Text content, UUID senderUUID, Text senderName, boolean isSystem, String rawPlayerName, boolean whisper, String whisperPartner) {
        if (content.getString().isBlank()) return;

        var client = MinecraftClient.getInstance();
        String playerName = client.player != null ? client.player.getName().getString() : "";
        boolean own;
        if (client.player != null) {
            if (senderUUID != null && senderUUID.equals(client.player.getUuid())) own = true;
            else if (rawPlayerName != null && !rawPlayerName.isEmpty()) own = rawPlayerName.equals(playerName);
            else own = senderName != null && senderName.getString().equals(playerName);
        } else {
            own = false;
        }
        String messageHash = String.valueOf(content.getString().hashCode());

        var cfg = ChatBubbleClientSetup.config();
        if (cfg.antiSpam() && !messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            if (!last.isSystem() && isSameSender(last, senderName, rawPlayerName)
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
                    last.rawPlayerName(),
                    last.whisper(), last.whisperPartner()
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
            senderName != null ? senderName : Text.literal(""),
            content,
            LocalTime.now(),
            own,
            isSystem,
            replyContent,
            replySender,
            messageHash,
            1,
            rawPlayerName,
            whisper,
            whisperPartner
        ));

        while (messages.size() > MAX)
            messages.remove(0);

        rememberPlayer(senderUUID, rawPlayerName,
            senderName != null ? senderName.getString() : null);

        boolean isMentionOrQuote = !own && !isSystem
            && com.niuqu.chatbubble.chat.MentionDetector.isMentioned(
                content.getString(), playerName,
                cfg.mentionRequireAt(), replySender);

        if (isMentionOrQuote) {
            hasUnreadMentionFlag = true;
            com.niuqu.chatbubble.chat.notification.MentionNotificationController.INSTANCE.onMessageCaptured(
                content, new SenderMeta(senderUUID, senderName, content, isSystem,
                    rawPlayerName, whisper, whisperPartner),
                messages.size(), replySender);
        }

        if (!own && whisper && rawPlayerName != null
            && cfg.mentionWhisperBanner()) {
            hasUnreadMentionFlag = true;
            com.niuqu.chatbubble.chat.notification.MentionNotificationController.INSTANCE.onWhisperReceived(
                senderUUID, senderName, content, messages.size());
        }

        boolean systemToHint = isSystem && cfg.strongHintEnabled();

        if (cfg.previewEnabled() && !systemToHint) {
            Text sName = senderName != null ? senderName : Text.literal("");
            Text pt = buildPreviewText(content, sName, isSystem);
            if (!pt.getString().isBlank()) {
                previews.add(new PreviewEntry(pt, PREVIEW_TICKS));
                while (previews.size() > cfg.previewLines()) previews.remove(0);
            }
        }

        boolean playSound = false;
        if (!own && client.player != null && !isMentionOrQuote && !whisper) {
            if (isSystem && cfg.soundSystem()) playSound = true;
            else if (!isSystem && cfg.soundPublic()) playSound = true;
        }
        if (playSound) {
            debugLog(() -> "[e33chat] Sound trigger | mention=" + isMentionOrQuote + " | whisper=" + whisper + " | system=" + isSystem);
            client.player.playSound(
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(),
                0.6F * cfg.soundVolume() / 100f, 1.0F);
        }

        if (systemToHint) {
            strongHintQueue.removeIf(e -> !e.isMention());
            strongHintQueue.add(new HintEntry(singleLineComponent(content), false));
            if (strongHintTicks <= 0) strongHintTicks = STRONG_HINT_DURATION;
        }

        if (!screenOpen) {
            unreadCount++;
        }

        if (whisper && whisperPartner != null && !own) {
            markWhisperUnread(whisperPartner);
        }
    }

    public static Text sliceStyled(Text src, int start, int end) {
        MutableText out = Text.empty();
        int[] pos = {0};
        src.visit((style, text) -> {
            int s = pos[0], e = s + text.length();
            pos[0] = e;
            int from = Math.max(start, s), to = Math.min(end, e);
            if (from < to)
                out.append(Text.literal(text.substring(from - s, to - s)).fillStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    private static Text singleLineComponent(Text c) {
        MutableText out = Text.empty();
        c.visit((style, text) -> {
            String cleaned = stripControls(text);
            if (!cleaned.isEmpty()) out.append(Text.literal(cleaned).fillStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    static String singleLine(String s) {
        return stripControls(s);
    }

    private static String stripControls(String s) {
        StringBuilder sb = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            sb.append(c < 0x20 || c == 0x7F ? ' ' : c);
        }
        return sb.toString();
    }

    public static List<ChatMessage> getMessages() { return messages; }

    public static List<ChatMessage> getWhisperMessages(String partnerName) {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg.whisper() && partnerName.equals(msg.whisperPartner())) result.add(msg);
        }
        return result;
    }

    public static List<ChatMessage> getPublicMessages() {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (!msg.whisper()) result.add(msg);
        }
        return result;
    }

    public static ChatMessage getLatestWhisperWith(String partnerName) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.whisper() && partnerName.equals(msg.whisperPartner())) return msg;
        }
        return null;
    }

    public static ChatMessage getLatestPublicMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (!msg.whisper()) return msg;
        }
        return null;
    }

    private static final Set<String> unreadWhisperPartners = new HashSet<>();

    public static void markWhisperUnread(String partner) {
        if (partner != null) unreadWhisperPartners.add(partner);
    }

    public static void clearUnreadWhisper(String partner) {
        unreadWhisperPartners.remove(partner);
    }

    public static boolean hasUnreadWhisper(String partner) {
        return unreadWhisperPartners.contains(partner);
    }

    public static int getUnreadCount() { return unreadCount; }

    public static void markAllRead() { unreadCount = 0; hasUnreadMentionFlag = false; }

    public static void setScreenOpen(boolean open) {
        screenOpen = open;
        if (open) { unreadCount = 0; hasUnreadMentionFlag = false; }
    }

    public static boolean hasUnreadMention(String playerName) {
        return hasUnreadMentionFlag;
    }

    public static Text quoteMessage(int index) {
        if (index < 0 || index >= messages.size()) return Text.literal("");
        ChatMessage msg = messages.get(index);
        String qName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
            ? msg.rawPlayerName() : msg.senderName().getString();
        MutableText quote = Text.literal("> " + qName + ": ");
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

    public static String getPendingReplySender() { return pendingReplySender; }

    public static List<PreviewEntry> getPreviews() { return previews; }

    private static Text buildPreviewText(Text content, Text name, boolean isSystem) {
        Text body = singleLineComponent(content);
        return name.getString().isEmpty()
            ? (isSystem
                ? Text.translatable("e33chat.sender.system").copy().append(Text.literal(": ")).append(body)
                : body)
            : Text.empty().append(name).append(Text.literal(": ")).append(body);
    }

    public static void tickPreview() {
        var it = previews.iterator();
        while (it.hasNext()) {
            if (--it.next().ticks <= 0) it.remove();
        }
    }

    public static Text getStrongHintText() {
        if (strongHintQueue.isEmpty()) return null;
        return strongHintTicks > 0 ? strongHintQueue.peek().text() : null;
    }

    public static int getStrongHintTicks() { return strongHintTicks; }

    public static void tickStrongHint() {
        if (strongHintTicks > 0) {
            strongHintTicks--;
            if (strongHintTicks <= 0) {
                strongHintQueue.poll();
                if (!strongHintQueue.isEmpty()) strongHintTicks = STRONG_HINT_DURATION;
            }
        }
    }

    public static int size() { return messages.size(); }

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
        if (v.isEmpty()) worldTitles.remove(currentWorldKey);
        else worldTitles.put(currentWorldKey, v);
        saveWorldTitles();
    }

    public static void setCurrentWorld(String name) {
        if (Objects.equals(name, currentWorldKey)) return;
        boolean wasFallback = "world".equals(currentWorldKey);
        boolean isSpecific = name != null && (name.startsWith("SP:") || name.startsWith("MP:"));
        boolean isRefinement = wasFallback && isSpecific;
        boolean hasPendingMessages = currentWorldKey == null && isSpecific && !messages.isEmpty();
        var cfg = ChatBubbleClientSetup.config();
        if (cfg.chatHistoryEnabled() && isWorldSpecific(currentWorldKey))
            saveMessages(currentWorldKey);
        currentWorldKey = name;
        if (isRefinement || hasPendingMessages) {
            if (cfg.chatHistoryEnabled() && isWorldSpecific(currentWorldKey))
                loadMessages(currentWorldKey);
            return;
        }
        messages.clear();
        unreadCount = 0;
        previews.clear();
        if (cfg.chatHistoryEnabled() && isWorldSpecific(currentWorldKey))
            loadMessages(currentWorldKey);
    }

    private static boolean isWorldSpecific(String key) {
        return key != null && (key.startsWith("SP:") || key.startsWith("MP:"));
    }

    private static File getHistoryFile(String worldKey) {
        String safe = worldKey.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        String hash = Integer.toHexString(worldKey.hashCode());
        return new File(MinecraftClient.getInstance().runDirectory, "e33chat/history/" + safe + "_" + hash + ".json");
    }

    private static net.minecraft.registry.RegistryWrapper.WrapperLookup registries() {
        var world = MinecraftClient.getInstance().world;
        if (world != null) return world.getRegistryManager();
        var conn = MinecraftClient.getInstance().getNetworkHandler();
        if (conn != null) return conn.getRegistryManager();
        return net.minecraft.registry.BuiltinRegistries.createWrapperLookup();
    }

    private static String toJsonSafe(Text c) {
        try {
            return Text.Serialization.toJsonString(c, registries());
        } catch (Exception e) {
            try {
                return Text.Serialization.toJsonString(Text.literal(c.getString()), registries());
            } catch (Exception e2) {
                return c.getString();
            }
        }
    }

    private static Text fromJsonSafe(String json) {
        if (json == null) return null;
        try {
            return Text.Serialization.fromJson(json, registries());
        } catch (Exception e) {
            return null;
        }
    }

    private static void saveMessages(String worldKey) {
        if (messages.isEmpty()) return;
        File f = getHistoryFile(worldKey);
        f.getParentFile().mkdirs();
        List<Object> list = new ArrayList<>();
        for (ChatMessage msg : messages) {
            try {
                var obj = new HashMap<String, Object>();
                obj.put("senderUUID", msg.senderUUID().toString());
                obj.put("senderName", msg.senderName().getString());
                obj.put("senderNameJson", toJsonSafe(msg.senderName()));
                obj.put("content", toJsonSafe(msg.content()));
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
                if (msg.whisper()) {
                    obj.put("whisper", true);
                    if (msg.whisperPartner() != null) obj.put("whisperPartner", msg.whisperPartner());
                }
                list.add(obj);
            } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
        }
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            GSON.toJson(list, w);
        } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
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
                    Text senderName = fromJsonSafe((String) obj.get("senderNameJson"));
                    if (senderName == null) senderName = Text.literal((String) obj.get("senderName"));
                    Text content = fromJsonSafe((String) obj.get("content"));
                    if (content == null) content = Text.literal((String) obj.getOrDefault("content", ""));
                    if (content.getString().isBlank()) continue;
                    LocalTime time = LocalTime.parse((String) obj.get("time"), DateTimeFormatter.ISO_LOCAL_TIME);
                    boolean isOwn = (Boolean) obj.getOrDefault("isOwn", false);
                    boolean isSystem = (Boolean) obj.getOrDefault("isSystem", false);
                    String replyContent = (String) obj.get("replyContent");
                    String replySender = (String) obj.get("replySender");
                    String rawPlayerName = (String) obj.get("rawPlayerName");
                    boolean whisper = Boolean.TRUE.equals(obj.get("whisper"));
                    String whisperPartner = (String) obj.get("whisperPartner");
                    messages.add(new ChatMessage(uuid, senderName, content, time,
                        isOwn, isSystem, replyContent, replySender, "", 1, rawPlayerName,
                        whisper, whisperPartner));
                } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
            }
            while (messages.size() > MAX) messages.remove(0);
        } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
    }

    private static File getTitlesFile() {
        return new File(MinecraftClient.getInstance().runDirectory, "e33chat/titles.json");
    }

    private static void loadWorldTitles() {
        if (titlesLoaded) return;
        titlesLoaded = true;
        File f = getTitlesFile();
        if (!f.exists()) return;
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            Map<String, String> data = GSON.fromJson(r, new TypeToken<Map<String, String>>(){}.getType());
            if (data != null) worldTitles.putAll(data);
        } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
    }

    private static void saveWorldTitles() {
        File f = getTitlesFile();
        f.getParentFile().mkdirs();
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), StandardCharsets.UTF_8)) {
            GSON.toJson(worldTitles, w);
        } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
    }

    public static void addHistoryMessages(List<com.niuqu.chatbubble.network.HistoryPayload.HistoryEntry> entries) {
        if (!messages.isEmpty() || entries.isEmpty()) return;
        for (var e : entries) {
            if (e.content().isBlank()) continue;
            messages.add(new ChatMessage(
                e.senderUUID(),
                Text.literal(e.senderName()),
                Text.literal(e.content()),
                e.time(),
                false,
                e.isSystem(),
                e.replyContent(),
                e.replySender(),
                String.valueOf(e.content().hashCode()),
                1,
                e.senderName(),
                false,
                null
            ));
        }
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
                        msg.duplicateCount(), msg.rawPlayerName(),
                        msg.whisper(), msg.whisperPartner()));
                    var client = MinecraftClient.getInstance();
                    String playerName = client.player != null
                        ? client.player.getName().getString() : "";
                    if (!playerName.isEmpty() && playerName.equals(quoteSender)
                        && !msg.content().getString().contains("@" + playerName)
                        && ChatBubbleClientSetup.config().mentionBannerEnabled()) {
                        com.niuqu.chatbubble.chat.notification.MentionNotificationBanner.INSTANCE.enqueue(
                            senderUUID, msg.senderName(), msg.content(), i);
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
