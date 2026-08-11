package com.niuqu.chatbubble;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.niuqu.chatbubble.chat.notification.MentionNotificationController;
import net.minecraft.util.Formatting;
import net.minecraft.client.MinecraftClient;
import net.minecraft.text.Text;
import net.minecraft.text.MutableText;
import net.minecraft.text.Style;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;


public class ChatMessageStore {
    private static final int MAX = 10000;
    private static final List<ChatMessage> messages = new ArrayList<>();
    private static int unreadCount = 0;
    private static boolean hasUnreadMentionFlag;
    private static boolean screenOpen = false;
    private static String pendingReplyContent;
    private static String pendingReplySender;
    private static long lastQuoteSendTime;
    static final long QUOTE_ECHO_WINDOW_MS = 5_000;
    static final long REPOST_DEDUP_MS = 1_000;

    // True when a repost would duplicate one just sent: the server echoes a whisper
    // twice (signed outgoing + incoming variants) within ~15ms, and both would be
    // rewritten to the same <name>[私聊] line without this guard.
    public static boolean isRepostDuplicate(String lastRepostText, long lastRepostTime, String newText, long now) {
        return newText.equals(lastRepostText) && now - lastRepostTime < REPOST_DEDUP_MS;
    }

    private static String currentWorldKey;
    private static final Map<String, String> worldTitles = new HashMap<>();
    private static final Gson GSON = new Gson();
    private static boolean titlesLoaded;
    private static final Map<String, PendingMeta> pendingMetas = new HashMap<>();

    public record SeenPlayer(UUID uuid, String profileName, String displayName) {}
    // LRU cap: bounds the per-message full scan in knownNameVariants/findSeenUuid
    private static final int SEEN_PLAYERS_CAP = 512;
    private static final Map<UUID, SeenPlayer> seenPlayers = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, SeenPlayer> eldest) {
            return size() > SEEN_PLAYERS_CAP;
        }
    };

    // Server-synced setting: head-menu teleport uses /tpa instead of /tp (default false)
    private static volatile boolean serverUseTpa = false;
    public static void setServerUseTpa(boolean v) { serverUseTpa = v; }
    public static boolean useTpa() { return serverUseTpa; }

    // Server-configured message-format templates (empty = disabled, heuristic guards only)
    private static volatile List<com.niuqu.chatbubble.chat.TemplateMatcher.CompiledTemplate> serverChatTemplates = List.of();
    private static volatile List<com.niuqu.chatbubble.chat.TemplateMatcher.CompiledTemplate> serverWhisperTemplates = List.of();
    private static volatile boolean serverTemplateDebug = false;

    public static void setServerConfig(boolean useTpa, List<String> chatTemplates,
                                       List<String> whisperTemplates, boolean templateDebug) {
        serverUseTpa = useTpa;
        serverChatTemplates = compileTemplates(chatTemplates);
        serverWhisperTemplates = compileTemplates(whisperTemplates);
        serverTemplateDebug = templateDebug;
    }

    // A template the server configured but the client rejects must not silently
    // vanish — log the reason once at sync time
    private static List<com.niuqu.chatbubble.chat.TemplateMatcher.CompiledTemplate> compileTemplates(List<String> raws) {
        if (raws == null || raws.isEmpty()) return List.of();
        List<com.niuqu.chatbubble.chat.TemplateMatcher.CompiledTemplate> out = new ArrayList<>();
        for (String raw : raws) {
            var result = com.niuqu.chatbubble.chat.TemplateMatcher.compile(raw);
            if (result.template() != null) {
                out.add(result.template());
                if (!result.template().unknownFields().isEmpty()) {
                    debugLog(() -> "[e33chat] template has unknown placeholders (treated as literal): "
                        + result.template().unknownFields() + " | template='" + raw + "'");
                }
            } else {
                debugLog(() -> "[e33chat] server template skipped: '" + raw + "' -> " + result.error());
            }
        }
        return out;
    }

    public static List<com.niuqu.chatbubble.chat.TemplateMatcher.CompiledTemplate> serverChatTemplates() {
        return serverChatTemplates;
    }

    public static List<com.niuqu.chatbubble.chat.TemplateMatcher.CompiledTemplate> serverWhisperTemplates() {
        return serverWhisperTemplates;
    }

    public static boolean serverTemplateDebug() { return serverTemplateDebug; }

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

    // player? Online candidates + self + seen players. Mirrors the mixin's gate.
    public static boolean isKnownPlayerName(String name) {
        if (name == null || name.isEmpty()) return false;
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) return false;
        String myName = player.getName().getString();
        if (!myName.isEmpty() && (name.equals(myName) || name.contains(myName))) return true;
        if (player.networkHandler != null) {
            for (var info : player.networkHandler.getPlayerList()) {
                String profile = info.getProfile().getName();
                if (!profile.isEmpty() && (name.equals(profile) || name.contains(profile))) return true;
                var tab = info.getDisplayName();
                if (tab != null) {
                    String ts = tab.getString();
                    if (!ts.isEmpty() && (name.equals(ts) || name.contains(ts))) return true;
                }
            }
        }
        return findSeenUuid(name) != null;
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

    // 2s TTL: if addMessage never runs (another mod cancelled it), a stale
    // note must not misattribute an unrelated later message
    public static SenderMeta consumePendingMeta() {
        SenderMeta m = PENDING_META.get();
        PENDING_META.remove();
        if (m != null && System.currentTimeMillis() - pendingMetaSetTime > 2_000) return null;
        return m;
    }

    // quoted: the sent message was a quote reply — carried on the echo record so a
    // later unrelated message can never inherit the [引用] tag (quote replies travel
    // as plain chat, so the echo's quoted flag is their only rewrite signal)
    private record PendingEcho(String text, long time, boolean quoted) {}
    private static final List<PendingEcho> pendingEchoes = new ArrayList<>();
    public record EchoMatch(boolean matched, boolean quoted) {}

    private static long pendingWhisperEchoTime;
    private static String pendingWhisperEchoTarget;
    private static long suppressCaptureTime;
    private static boolean suppressQuoted;

    public static void markPendingWhisperEcho(String target) {
        pendingWhisperEchoTime = System.currentTimeMillis();
        pendingWhisperEchoTarget = target;
    }
    public static void markSuppressCapture() {
        suppressCaptureTime = System.currentTimeMillis();
        // Snapshot the most recent send's quote flag: the suppress echo arrives right
        // after its own send, so the queue tail matches it better than the FIFO head
        // (which can hold an older unconsumed echo from a filtered message)
        suppressQuoted = !pendingEchoes.isEmpty() && pendingEchoes.get(pendingEchoes.size() - 1).quoted();
    }
    public static boolean consumeSuppressQuoted() {
        boolean q = suppressQuoted;
        suppressQuoted = false;
        return q;
    }

    public static boolean hasPendingWhisperEcho() {
        return pendingWhisperEchoTime != 0 && System.currentTimeMillis() - pendingWhisperEchoTime < 10_000;
    }
    public static String getPendingWhisperTarget() { return pendingWhisperEchoTarget; }
    public static void consumeWhisperEcho() { pendingWhisperEchoTime = 0; pendingWhisperEchoTarget = null; }

    // 5s TTL: if the outgoing-whisper echo never reaches addMessage (another
    // mod cancelled it), a stale flag must not swallow an unrelated message
    public static boolean consumeSuppressCapture() {
        if (suppressCaptureTime == 0) return false;
        boolean fresh = System.currentTimeMillis() - suppressCaptureTime < 5_000;
        suppressCaptureTime = 0;
        return fresh;
    }

    // Echoes not consumed within 10s (e.g. commands with no chat feedback) would
    // otherwise poison the counter and swallow later self-attributed messages
    private static void purgeStaleEchoes() {
        long cutoff = System.currentTimeMillis() - 10_000;
        pendingEchoes.removeIf(e -> e.time() < cutoff);
    }

    public static void incrementPendingEcho(String sentText) {
        purgeStaleEchoes();
        // Snapshot the quote residue onto this echo and clear it, so the next send
        // (e.g. a plain follow-up) does not inherit the [引用] marker
        boolean quoted = wasRecentQuoteAt(lastQuoteSendTime, System.currentTimeMillis());
        lastQuoteSendTime = 0;
        pendingEchoes.add(new PendingEcho(sentText, System.currentTimeMillis(), quoted));
    }

    public static EchoMatch consumeEchoBySystemChat(String incomingText) {
        purgeStaleEchoes();
        for (int i = 0; i < pendingEchoes.size(); i++) {
            if (incomingText.equals(pendingEchoes.get(i).text())) {
                boolean quoted = pendingEchoes.get(i).quoted();
                pendingEchoes.remove(i);
                return new EchoMatch(true, quoted);
            }
        }
        return new EchoMatch(false, false);
    }


    public static void debugLog(java.util.function.Supplier<String> msg) {
        if (ChatBubbleClientSetup.config().debugLog())
            com.mojang.logging.LogUtils.getLogger().info(msg.get());
    }

    public static void debugLog(String msg) {
        debugLog(() -> msg);
    }

    public static EchoMatch consumeEchoIfSenderMatches(UUID senderUUID, Text senderName) {
        purgeStaleEchoes();
        if (pendingEchoes.isEmpty()) return new EchoMatch(false, false);
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player == null) return new EchoMatch(false, false);
        // Deterministic: signed-channel echoes carry the sender's real UUID
        boolean match = senderUUID != null && senderUUID.equals(player.getUuid());
        // Whole-word boundary match for decorated / color-translated servers
        // (substring contains misattributed e.g. SteveAdmin to Steve)
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
            PendingEcho e = pendingEchoes.remove(0);
            updateLatestOwnSenderName(senderName);
            return new EchoMatch(true, e.quoted());
        }
        return new EchoMatch(false, false);
    }

    // True when needle occurs in haystack with no name character (letter/digit/_)
    // adjacent — "[VIP]Steve" and "<Steve>" hit, "SteveAdmin" and "Steve2" do not.
    static boolean containsWholeName(String haystack, String needle) {
        if (haystack == null || needle == null || needle.isEmpty()) return false;
        // §6Steve: the code's digit would read as a name character — strip codes first
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
            from = idx + 1;
        }
    }

    static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    // The local echo bubble is created with the bare name before the server's
    // decorated version (titles/prefixes) is known — patch it when the echo arrives
    public static void updateLatestOwnSenderName(Text senderName) {
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
                               List<String> mentionTargets, long createdAt) {}

    // time is epoch millis so history spans days/weeks without losing the date
    public record ChatMessage(
        UUID senderUUID,
        Text senderName,
        Text content,
        long time,
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

    // Display names can't identify a sender reliably: the local echo bubble's name
    // gets patched from bare to decorated once the server echo arrives, so the next
    // local echo would never match it — compare raw player names when both are known
    private static boolean isSameSender(ChatMessage last, Text senderName, String rawPlayerName) {
        if (rawPlayerName != null && !rawPlayerName.isEmpty()
            && last.rawPlayerName() != null && !last.rawPlayerName().isEmpty()) {
            return rawPlayerName.equals(last.rawPlayerName());
        }
        return last.senderName().getString().equals(senderName.getString());
    }

    // ==== Blocked players ====
    // Exact-name matching (case-insensitive, §-stripped). rawPlayerName is the
    // primary key — the profileless channel carries a nil UUID, so UUID-only
    // matching would leak blocked players through that path.
    public static boolean matchesBlocked(String name, List<? extends String> blocked) {
        if (name == null || name.isEmpty() || blocked == null || blocked.isEmpty()) return false;
        // Both sides §-stripped and trimmed so color-coded names and stray spaces
        // in either the message or the config list can't break the match
        String stripped = name.replaceAll("§.", "").trim();
        for (String b : blocked) {
            if (b == null || b.isBlank()) continue;
            String candidate = b.replaceAll("§.", "").trim();
            if (stripped.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    // senderName (tab-list display name) as fallback covers nickname plugins where
    // the chat line carries the decorated name and rawPlayerName is the profile name
    public static boolean isPlayerBlocked(String rawPlayerName, Text senderName, List<? extends String> blocked) {
        if (blocked == null || blocked.isEmpty()) return false;
        if (matchesBlocked(rawPlayerName, blocked)) return true;
        return senderName != null && matchesBlocked(senderName.getString(), blocked);
    }

    // Blocking must also drop already-loaded history, or the sender's old messages
    // keep showing in the chat panel after the block takes effect
    public static void purgeBlocked(List<? extends String> blocked) {
        if (blocked == null || blocked.isEmpty()) return;
        messages.removeIf(m -> !m.isOwn() && !m.isSystem()
            && isPlayerBlocked(m.rawPlayerName(), m.senderName(), blocked));
    }

    // History restored from disk / server packets must not re-import blocked
    // senders' messages, or they reappear on the next world join
    private static boolean isBlockedMessage(ChatMessage m) {
        return isPlayerBlocked(m.rawPlayerName(), m.senderName(),
            ChatBubbleClientSetup.config().blockedPlayers());
    }

    // package-private test seam: headless unit tests stub this to return null
    // so addMessage never touches MinecraftClient.getInstance()
    static java.util.function.Supplier<net.minecraft.entity.player.PlayerEntity> localPlayerSupplier =
        () -> net.minecraft.client.MinecraftClient.getInstance().player;

    public static void addMessage(Text content, UUID senderUUID, Text senderName, boolean isSystem, String rawPlayerName, boolean whisper, String whisperPartner, boolean localSend) {
        String messageHash = String.valueOf(content.getString().hashCode());

        // A message that is only whitespace/control chars — e.g. a server chat-clear
        // made of nothing but newlines — is dropped so it produces no bubble/preview.
        // Real newlines are kept in the stored content so the chat list renders them as
        // line breaks; single-line contexts (preview/hint) flatten them separately.
        if (content.getString().isBlank()) return;

        var localPlayer = localPlayerSupplier.get();
        String playerName = localPlayer != null ? localPlayer.getName().getString() : "";
        // UUID is deterministic; the name fallback covers system-channel messages
        // flattened by NCR where the UUID is nil. Name-only comparison misjudged
        // same-named players on offline (cracked) servers.
        boolean own = localPlayer != null && senderUUID != null
            && senderUUID.equals(localPlayer.getUuid());
        if (!own) {
            own = (rawPlayerName != null && !rawPlayerName.isEmpty())
                ? rawPlayerName.equals(playerName)
                : senderName != null && senderName.getString().equals(playerName);
        }

        // Remember our own decorated name (titles/prefixes) whenever it appears in
        // chat — the outgoing whisper echo has no self name to extract, and the tab
        // list display name is null on vanilla servers.
        if (own) cacheOwnDecoratedName(senderName);

        if (ChatBubbleClientSetup.config().antiSpam() && !messages.isEmpty()) {
            ChatMessage last = messages.get(messages.size() - 1);
            if (!last.isSystem() && isSameSender(last, senderName, rawPlayerName)
                && last.content().getString().equals(content.getString())) {
                // The merged bubble's quote block must reflect THIS send, not
                // inherit the previous one's — an unquoted identical follow-up
                // after a quoted send otherwise keeps a stale [引用] block.
                PendingMeta pending = pendingMetas.remove(messageHash);
                if (pending != null && System.currentTimeMillis() - pending.createdAt() > 10_000) {
                    pending = null;
                }
                String mergeReplyContent = null;
                String mergeReplySender = null;
                if (own && pendingReplyContent != null) {
                    mergeReplyContent = pendingReplyContent;
                    mergeReplySender = pendingReplySender;
                } else if (pending != null && !pending.quoteContent().isEmpty()) {
                    mergeReplyContent = pending.quoteContent();
                    mergeReplySender = pending.quoteSender();
                }
                pendingReplyContent = null;
                pendingReplySender = null;
                messages.set(messages.size() - 1, new ChatMessage(
                    last.senderUUID(), last.senderName(), last.content(),
                    System.currentTimeMillis(),
                    last.isOwn(), last.isSystem(),
                    mergeReplyContent, mergeReplySender, last.messageHash(),
                    last.duplicateCount() + 1,
                    last.rawPlayerName(),
                    last.whisper(), last.whisperPartner()
                ));
                return;
            }
        }

        PendingMeta pending = pendingMetas.remove(messageHash);
        if (pending != null && System.currentTimeMillis() - pending.createdAt() > 10_000) {
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
            System.currentTimeMillis(),
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

        if (!isSystem && senderUUID != null && !senderUUID.equals(new UUID(0, 0)))
            rememberPlayer(senderUUID, rawPlayerName, senderName.getString());

        while (messages.size() > MAX)
            messages.remove(0);
        historyDirty = true;

        boolean isMentionOrQuote = !isSystem
            && com.niuqu.chatbubble.chat.MentionDetector.isMentioned(
                content.getString(), playerName,
                ChatBubbleClientSetup.config().mentionRequireAt(), replySender);

        if (isMentionOrQuote) {
            if (!screenOpen) hasUnreadMentionFlag = true;
            MentionNotificationController.INSTANCE.onMessageCaptured(
                content, new SenderMeta(senderUUID, senderName, content, isSystem,
                    rawPlayerName, whisper, whisperPartner),
                messages.size(), replySender);
        }

        // localSend = the user's own send feedback bubble — normally not a
        // received whisper, so skip the (self-)whisper banner/sound; but
        // own-whisper notify explicitly wants a banner for self /msg, and the
        // controller gates on isOwn/selfNotify anyway.
        if (whisper && rawPlayerName != null
            && ChatBubbleClientSetup.config().mentionWhisperBanner()
            && (!localSend || ChatBubbleClientSetup.config().ownWhisperNotify())) {
            MentionNotificationController.INSTANCE.onWhisperReceived(
                senderUUID, senderName, content, messages.size());
        }

        // System messages pop as a banner like @/whisper/quote (no sender name —
        // the system label is enough, avoiding "[系统] 系统"). Independent toggle,
        // on by default.
        if (isSystem && ChatBubbleClientSetup.config().systemBannerEnabled()) {
            MentionNotificationController.INSTANCE.onSystemMessage(content, messages.size());
        }

        boolean playSound = false;
        if (!own && localPlayerSupplier.get() != null && !isMentionOrQuote && !whisper) {
            if (isSystem && ChatBubbleClientSetup.config().soundSystem()) playSound = true;
            else if (!isSystem && ChatBubbleClientSetup.config().soundPublic()) playSound = true;
        }
        if (playSound) {
            MinecraftClient.getInstance().player.playSound(
                net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.6F * ChatBubbleClientSetup.config().soundVolume() / 100f, 1.0F);
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
            return Optional.<Object>empty();
        }, Style.EMPTY);
        return out;
    }

    // Plain-text variant for the few Screen call sites that build a single-line String.
    public static String singleLine(String s) {
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

    public static List<ChatMessage> getMessages() {
        return messages;
    }

    public static List<ChatMessage> getWhisperMessages(String partnerName) {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (msg.whisper() && partnerName.equals(msg.whisperPartner())) {
                result.add(msg);
            }
        }
        return result;
    }

    public static List<ChatMessage> getPublicMessages() {
        List<ChatMessage> result = new ArrayList<>();
        for (ChatMessage msg : messages) {
            if (!msg.whisper()) {
                result.add(msg);
            }
        }
        return result;
    }

    public static ChatMessage getLatestWhisperWith(String partnerName) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (msg.whisper() && partnerName.equals(msg.whisperPartner())) {
                return msg;
            }
        }
        return null;
    }

    public static ChatMessage getLatestPublicMessage() {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            if (!msg.whisper()) {
                return msg;
            }
        }
        return null;
    }

    private static final Set<String> unreadWhisperPartners = new java.util.HashSet<>();

    public static void markWhisperUnread(String partner) {
        if (partner != null) unreadWhisperPartners.add(partner);
    }

    public static void clearUnreadWhisper(String partner) {
        unreadWhisperPartners.remove(partner);
    }

    public static boolean hasUnreadWhisper(String partner) {
        return unreadWhisperPartners.contains(partner);
    }

    public static int getUnreadCount() {
        return unreadCount;
    }

    public static void markAllRead() {
        unreadCount = 0;
        hasUnreadMentionFlag = false;
    }

    public static void setScreenOpen(boolean open) {
        screenOpen = open;
        if (open) {
            unreadCount = 0;
            hasUnreadMentionFlag = false;
        }
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
        lastQuoteSendTime = System.currentTimeMillis();
    }

    public static String getPendingReplySender() { return pendingReplySender; }

    // Epoch-minute bucket: carries the date, so a message crossing midnight
    // gets a new key and its own separator automatically
    public static String timeKey(long timeMillis, int interval) {
        if (interval <= 0) return "";
        return String.valueOf(timeMillis / (interval * 60_000L));
    }

    // WeChat-style separator: same day "15:30", other day "07-31 15:30",
    // other year "2025-12-31 15:30"
    public static String formatTime(long timeMillis) {
        var dt = java.time.Instant.ofEpochMilli(timeMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        java.time.LocalDate today = java.time.LocalDate.now();
        if (dt.toLocalDate().equals(today)) return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (dt.getYear() == today.getYear()) return dt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    // True when a quote reply was sent within the echo window: the local bubble's
    // addMessage consumes pendingReplyContent before the server echo returns, so
    // the vanilla-chat [引用] tag can't read it — this timestamp is the residue.
    static boolean wasRecentQuoteAt(long quoteSendTime, long now) {
        return quoteSendTime != 0 && now - quoteSendTime < QUOTE_ECHO_WINDOW_MS;
    }

    public static boolean wasRecentQuote() {
        return wasRecentQuoteAt(lastQuoteSendTime, System.currentTimeMillis());
    }

    // Content extraction from a vanilla whisper line ("你悄悄对 Steve 说: hi" -> "hi").
    // meta wins when it is trusted (incoming whisper sets it); the outgoing-echo path
    // never sets pending meta, so callers must pass null there to avoid stale residue.
    // Uses the FIRST separator: the structural colon sits before the content, so
    // content that itself contains ": " must not be truncated (lastIndexOf would).
    // Consistent with MessagePresentation.extractWhisperContent.
    public static String extractWhisperContent(String text, SenderMeta meta) {
        if (meta != null && meta.rawContent() != null) {
            String rc = meta.rawContent().getString();
            if (!rc.isBlank()) return rc;
        }
        int idx = text.indexOf(": ");
        if (idx < 0) idx = text.indexOf("：");
        if (idx < 0) return text;
        int start = idx + 1;
        while (start < text.length() && Character.isWhitespace(text.charAt(start))) start++;
        return text.substring(start).trim();
    }

    // Display-name extraction from a vanilla whisper line, keeping prefix decorations
    // and colors: "你悄悄地对[称号]E33EPUS说：hi" -> "[称号]E33EPUS".
    // Covers zh/en outgoing+incoming templates; falls back when no template matches.
    public static Text extractWhisperDisplayName(Text fullLine, Text fallback) {
        String fullStr = fullLine.getString();
        // zh incoming: "[称号]Steve悄悄地对你说：hi" -> name = [0, "悄悄地对你说")
        int qiaoIdx = fullStr.indexOf("悄悄地对你说");
        if (qiaoIdx > 0) {
            Text area = sliceStyled(fullLine, 0, qiaoIdx);
            if (!area.getString().isBlank()) return stripItalic(area);
        }
        // zh outgoing: "你悄悄地对[称号]Steve说：hi" — the name after "悄悄地对"
        // is the TARGET, not the sender (the sender is "你" = self); the caller's
        // fallback carries our own decorated name. Some plugins echo the outgoing
        // line with the sender's decorated name ("[称号]E33EPUS悄悄地对Steve说") —
        // extract that prefix instead of falling back to the bare name.
        int duiIdx = fullStr.indexOf("悄悄地对");
        if (duiIdx >= 0) {
            int sayIdx = fullStr.indexOf("说：", duiIdx);
            if (sayIdx > duiIdx) {
                String prefix = fullStr.substring(0, duiIdx).trim();
                if (!prefix.isEmpty() && !prefix.equals("你")) {
                    Text area = sliceStyled(fullLine, fullStr.indexOf(prefix), fullStr.indexOf(prefix) + prefix.length());
                    if (!area.getString().isBlank()) return stripItalic(area);
                }
                return fallback;
            }
        }
        // "X whisper to Y: hi" — X is the sender (decorated on plugin servers).
        // Vanilla English outgoing is "You whisper to X" (X = target), so "You"
        // is not a real name and falls back.
        int toIdx = fullStr.indexOf("whisper to ");
        if (toIdx >= 0) {
            int colonIdx = fullStr.indexOf(":", toIdx);
            if (colonIdx > toIdx) {
                String prefix = fullStr.substring(0, toIdx).trim();
                if (!prefix.isEmpty() && !prefix.equalsIgnoreCase("you")) {
                    Text area = sliceStyled(fullLine, fullStr.indexOf(prefix), fullStr.indexOf(prefix) + prefix.length());
                    if (!area.getString().isBlank()) return stripItalic(area);
                }
                return fallback;
            }
        }
        int whisperIdx = fullStr.indexOf(" whispers to you");
        if (whisperIdx > 0) {
            Text area = sliceStyled(fullLine, 0, whisperIdx);
            if (!area.getString().isBlank()) return stripItalic(area);
        }
        return fallback;
    }

    // Rebuild a component with italic cleared on every run — vanilla decorates
    // whisper lines gray+italic and the decoration style bleeds into extracted
    // names; 1.20.1 has no mapStyle, so walk the tree via visit.
    private static Text stripItalic(Text src) {
        MutableText out = Text.empty();
        src.visit((style, text) -> {
            out.append(Text.literal(text).fillStyle(style.withItalic(false)));
            return java.util.Optional.<Object>empty();
        }, net.minecraft.text.Style.EMPTY);
        return out;
    }

    private static Text ownDecoratedName;

    // Best available self name: tab list > decorated name seen in chat > scoreboard
    // team (color/prefix/suffix) > bare name. Vanilla servers send no tab-list
    // display name, so the chat cache is the reliable source for the outgoing
    // whisper repost; NCR servers add no cache before the first own line, so the
    // team color is the only blue-name source there.
    public static Text ownDisplayName() {
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player != null && player.networkHandler != null) {
            var info = player.networkHandler.getPlayerListEntry(player.getUuid());
            if (info != null && info.getDisplayName() != null) {
                return info.getDisplayName();
            }
        }
        if (ownDecoratedName != null) return ownDecoratedName;
        if (player != null && player.getScoreboardTeam() != null) {
            var team = player.getScoreboardTeam();
            Text pfx = team.getPrefix();
            Text sfx = team.getSuffix();
            Formatting col = team.getColor();
            boolean hasPfx = pfx != null && !pfx.getString().isEmpty();
            boolean hasSfx = sfx != null && !sfx.getString().isEmpty();
            if (hasPfx || hasSfx || col != null) {
                MutableText name = Text.literal(player.getName().getString());
                if (col != null) name = name.formatted(col);
                MutableText out = Text.empty();
                if (hasPfx) out.append(pfx);
                out.append(name);
                if (hasSfx) out.append(sfx);
                return out;
            }
        }
        return player != null ? player.getName() : Text.literal("?");
    }

    public static Text cachedOwnDisplayName() {
        return ownDecoratedName;
    }

    // Own-echoes skip addMessage entirely (consumeEchoIfSenderMatches returns
    // early), so callers on the message path must cache the decorated name too.
    public static void cacheOwnDecoratedName(Text senderName) {
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        String bare = player != null ? player.getName().getString() : "";
        if (senderName == null || bare.isEmpty()) return;
        String sn = senderName.getString();
        if (!sn.isEmpty() && !sn.equals(bare)) ownDecoratedName = senderName;
    }

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

    public static void setCurrentWorld(String name) {
        if (java.util.Objects.equals(name, currentWorldKey)) return;
        boolean wasFallback = "world".equals(currentWorldKey);
        boolean isSpecific = name != null && (name.startsWith("SP:") || name.startsWith("MP:"));
        boolean isRefinement = wasFallback && isSpecific;
        boolean hasPendingMessages = currentWorldKey == null && isSpecific && !messages.isEmpty();
        if (ChatBubbleClientSetup.config().chatHistoryEnabled() && isWorldSpecific(currentWorldKey))
            saveMessages(currentWorldKey);
        currentWorldKey = name;
        cleanupOldHistory();
        if (isRefinement || hasPendingMessages) {
            hasUnreadMentionFlag = false;
            if (ChatBubbleClientSetup.config().chatHistoryEnabled() && isWorldSpecific(currentWorldKey)) {
                // Messages that arrived before the world key was known (MOTD, join
                // notices) must stay newest — load saved history underneath them
                // instead of appending it after
                List<ChatMessage> early = new ArrayList<>(messages);
                messages.clear();
                loadMessages(currentWorldKey);
                messages.addAll(early);
            }
            return;
        }
        messages.clear();
        unreadCount = 0;
        hasUnreadMentionFlag = false;
        if (ChatBubbleClientSetup.config().chatHistoryEnabled() && isWorldSpecific(currentWorldKey))
            loadMessages(currentWorldKey);
    }

    private static boolean isWorldSpecific(String key) {
        return key != null && (key.startsWith("SP:") || key.startsWith("MP:"));
    }

    private static File getHistoryFile(String worldKey) {
        // Keep Unicode (Chinese world names stay readable); only strip characters
        // that break file systems / path parsing. The SHA-256 short hash disambiguates
        // worlds whose sanitized names collide.
        String safe = worldKey.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "_");
        return new File(MinecraftClient.getInstance().runDirectory,
            "e33chat/history/" + safe + "_" + sha256Short(worldKey) + ".json");
    }

    // Pre-2.2.3 files used an ASCII-only sanitizer + String.hashCode; load them for
    // migration when the new path does not exist yet
    private static File getLegacyHistoryFile(String worldKey) {
        String safe = worldKey.replaceAll("[^a-zA-Z0-9_.\\-]", "_");
        String hash = Integer.toHexString(worldKey.hashCode());
        return new File(MinecraftClient.getInstance().runDirectory,
            "e33chat/history/" + safe + "_" + hash + ".json");
    }

    private static String sha256Short(String s) {
        try {
            byte[] d = java.security.MessageDigest.getInstance("SHA-256")
                .digest(s.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 8; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(s.hashCode());
        }
    }

    // ---- Plain-text history lines: date-time \t sender \t content \t flags ----
    // Open in any text editor and it reads like a log. Plain text only — colors
    // and click/hover data are dropped; the decorated prefix still shows as literal
    // text (e.g. "[称号]E33EPUS"). Flags: M=own, S=system, W=whisper (combinable,
    // empty when none). Fields escape \t \n \\ so parsing is unambiguous.
    // Pre-2.2.3 JSONL lines (starting with '{') still load.

    // Commands that carry credentials must never land in the history file —
    // mirrors the AuthMe-family login/register aliases
    static boolean isSensitiveCommand(String text) {
        if (text == null) return false;
        String s = Formatting.strip(text);
        if (s == null) return false;
        s = s.trim();
        if (!s.startsWith("/")) return false;
        int sp = s.indexOf(' ');
        String cmd = sp < 0 ? s.substring(1) : s.substring(1, sp);
        if (cmd.isEmpty()) return false;
        switch (cmd.toLowerCase(java.util.Locale.ROOT)) {
            case "login": case "l": case "register": case "reg":
            case "auth": case "password": case "passwd":
            case "changepassword": case "changepass": case "cp":
                return true;
            default:
                return false;
        }
    }

    static String toLine(ChatMessage msg) {
        if (isSensitiveCommand(msg.content().getString())) return null;
        // JSONL, one message per line. senderJson/contentJson are full styled
        // components (colors, click/hover events survive the reload) and uuid
        // lets avatars resolve for offline players after re-joining.
        java.util.Map<String, Object> obj = new java.util.LinkedHashMap<>();
        obj.put("time", msg.time());
        obj.put("uuid", msg.senderUUID() != null ? msg.senderUUID().toString() : "");
        String senderJson = null, contentJson = null;
        try {
            senderJson = Text.Serialization.toJsonString(msg.senderName(), registries());
            contentJson = Text.Serialization.toJsonString(msg.content(), registries());
        } catch (Throwable ignored) {
            // Component codecs unavailable (headless test env / broken registries):
            // fall back to plain-text fields; styled fields are omitted.
        }
        if (senderJson != null) obj.put("senderJson", senderJson);
        else obj.put("sender", msg.senderName().getString());
        if (contentJson != null) obj.put("contentJson", contentJson);
        else obj.put("content", msg.content().getString());
        obj.put("own", msg.isOwn());
        obj.put("system", msg.isSystem());
        if (msg.replyContent() != null) obj.put("replyContent", msg.replyContent());
        if (msg.replySender() != null) obj.put("replySender", msg.replySender());
        if (msg.rawPlayerName() != null) obj.put("rawPlayerName", msg.rawPlayerName());
        if (msg.whisper()) obj.put("whisper", true);
        if (msg.whisperPartner() != null) obj.put("whisperPartner", msg.whisperPartner());
        return GSON.toJson(obj);
    }

    static ChatMessage fromLine(String line) {
        if (line.startsWith("{")) return fromJsonLine(line);
        String[] parts = line.split("\t", -1);
        if (parts.length < 3) return null;
        long millis;
        try {
            millis = java.time.LocalDateTime.parse(parts[0], DateTimeFormatter.ISO_LOCAL_DATE_TIME)
                .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
        } catch (Exception e) {
            return null;
        }
        String flags = parts.length > 3 ? parts[3] : "";
        String content = unescapeField(parts[2]);
        if (content.isBlank()) return null;
        boolean whisper = flags.contains("W");
        String partner = null;
        String replySender = null;
        String replyContent = null;
        if (whisper && parts.length > 4) partner = unescapeField(parts[4]);
        if (parts.length > 5) replySender = unescapeField(parts[5]);
        if (parts.length > 6) replyContent = unescapeField(parts[6]);
        return new ChatMessage(
            new UUID(0, 0),
            parseStyledText(unescapeField(parts[1])),
            ChatImageCompat.convert(parseStyledText(content)),
            millis,
            flags.contains("M"),
            flags.contains("S"),
            replyContent, replySender, "", 1, null,
            whisper, partner
        );
    }

    // Legacy JSONL branch: one message per line as {"sender":...,"content":...}
    private static ChatMessage fromJsonLine(String line) {
        Map<String, Object> obj;
        try {
            obj = GSON.fromJson(line, new TypeToken<Map<String, Object>>(){}.getType());
        } catch (Exception e) {
            return null;
        }
        if (obj == null) return null;
        Object timeObj = obj.get("time");
        if (!(timeObj instanceof Number)) return null;
        UUID uuid = null;
        try { uuid = UUID.fromString(String.valueOf(obj.get("uuid"))); } catch (Exception ignored) {}
        Text senderName = componentFrom(obj, "senderJson", "sender");
        Text content = componentFrom(obj, "contentJson", "content");
        if (content == null || content.getString().isBlank()) return null;
        return new ChatMessage(
            uuid != null ? uuid : new UUID(0, 0),
            senderName != null ? senderName : Text.literal(""),
            content,
            ((Number) timeObj).longValue(),
            Boolean.TRUE.equals(obj.get("own")),
            Boolean.TRUE.equals(obj.get("system")),
            (String) obj.get("replyContent"),
            (String) obj.get("replySender"),
            "",
            1,
            (String) obj.get("rawPlayerName"),
            Boolean.TRUE.equals(obj.get("whisper")),
            (String) obj.get("whisperPartner")
        );
    }

    private static Text componentFrom(Map<String, Object> obj, String jsonKey, String textKey) {
        String json = (String) obj.get(jsonKey);
        if (json != null) {
            try { return Text.Serialization.fromJson(json, registries()); } catch (Exception ignored) {}
        }
        String text = (String) obj.get(textKey);
        return text != null ? parseStyledText(text) : null;
    }

    // 1.21.1 Text codecs need a registry provider; fall back to the connection
    // registries, then static builtins, so styles survive the quit-to-title save
    private static net.minecraft.registry.RegistryWrapper.WrapperLookup registries() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc != null) {
            var world = mc.world;
            if (world != null) return world.getRegistryManager();
            var conn = mc.getNetworkHandler();
            if (conn != null) return conn.getRegistryManager();
        }
        try {
            return net.minecraft.registry.BuiltinRegistries.createWrapperLookup();
        } catch (Throwable ignored) {
            // Headless test fallback: an empty lookup serializes plain-text
            // components fine; registry-dependent hovers degrade instead of crashing
            return new net.minecraft.registry.RegistryWrapper.WrapperLookup() {
                @Override
                public java.util.stream.Stream<net.minecraft.registry.RegistryKey<? extends net.minecraft.registry.Registry<?>>> streamAllRegistryKeys() {
                    return java.util.stream.Stream.empty();
                }
                @Override
                public <T> java.util.Optional<net.minecraft.registry.RegistryWrapper.Impl<T>> getOptionalWrapper(
                        net.minecraft.registry.RegistryKey<? extends net.minecraft.registry.Registry<? extends T>> key) {
                    return java.util.Optional.empty();
                }
            };
        }
    }

    private static String escapeField(String s) {
        return s.replace("\\", "\\\\").replace("\t", "\\t").replace("\r", "\\r").replace("\n", "\\n");
    }

    private static String unescapeField(String s) {
        if (s.indexOf('\\') < 0) return s;
        StringBuilder out = new StringBuilder(s.length());
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '\\' && i + 1 < s.length()) {
                char n = s.charAt(i + 1);
                if (n == 't') { out.append('\t'); i++; continue; }
                if (n == 'n') { out.append('\n'); i++; continue; }
                if (n == 'r') { out.append('\r'); i++; continue; }
                if (n == '\\') { out.append('\\'); i++; continue; }
            }
            out.append(c);
        }
        return out.toString();
    }

    // Section-sign codes ("§6...§r") back into a styled component; unknown codes
    // (e.g. a stray §x from a plugin) fall through as literal text
    public static Text parseStyledText(String s) {
        MutableText out = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder buf = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char ch = s.charAt(i);
            if (ch == '§' && i + 1 < s.length()) {
                if (buf.length() > 0) {
                    out.append(Text.literal(buf.toString()).fillStyle(style));
                    buf.setLength(0);
                }
                Style next = applySectionCode(style, s.charAt(i + 1));
                if (next == null) {
                    // Unknown code: keep it as literal text instead of swallowing it
                    buf.append(ch).append(s.charAt(i + 1));
                } else {
                    style = next;
                }
                i++;
            } else {
                buf.append(ch);
            }
        }
        if (buf.length() > 0) out.append(Text.literal(buf.toString()).fillStyle(style));
        return out;
    }

    private static Style applySectionCode(Style style, char code) {
        switch (Character.toLowerCase(code)) {
            case '0': return style.withColor(Formatting.BLACK.getColorValue() != null ? Formatting.BLACK.getColorValue() : null);
            case '1': return style.withColor(Formatting.DARK_BLUE.getColorValue() != null ? Formatting.DARK_BLUE.getColorValue() : null);
            case '2': return style.withColor(Formatting.DARK_GREEN.getColorValue() != null ? Formatting.DARK_GREEN.getColorValue() : null);
            case '3': return style.withColor(Formatting.DARK_AQUA.getColorValue() != null ? Formatting.DARK_AQUA.getColorValue() : null);
            case '4': return style.withColor(Formatting.DARK_RED.getColorValue() != null ? Formatting.DARK_RED.getColorValue() : null);
            case '5': return style.withColor(Formatting.DARK_PURPLE.getColorValue() != null ? Formatting.DARK_PURPLE.getColorValue() : null);
            case '6': return style.withColor(Formatting.GOLD.getColorValue() != null ? Formatting.GOLD.getColorValue() : null);
            case '7': return style.withColor(Formatting.GRAY.getColorValue() != null ? Formatting.GRAY.getColorValue() : null);
            case '8': return style.withColor(Formatting.DARK_GRAY.getColorValue() != null ? Formatting.DARK_GRAY.getColorValue() : null);
            case '9': return style.withColor(Formatting.BLUE.getColorValue() != null ? Formatting.BLUE.getColorValue() : null);
            case 'a': return style.withColor(Formatting.GREEN.getColorValue() != null ? Formatting.GREEN.getColorValue() : null);
            case 'b': return style.withColor(Formatting.AQUA.getColorValue() != null ? Formatting.AQUA.getColorValue() : null);
            case 'c': return style.withColor(Formatting.RED.getColorValue() != null ? Formatting.RED.getColorValue() : null);
            case 'd': return style.withColor(Formatting.LIGHT_PURPLE.getColorValue() != null ? Formatting.LIGHT_PURPLE.getColorValue() : null);
            case 'e': return style.withColor(Formatting.YELLOW.getColorValue() != null ? Formatting.YELLOW.getColorValue() : null);
            case 'f': return style.withColor(Formatting.WHITE.getColorValue() != null ? Formatting.WHITE.getColorValue() : null);
            case 'k': return style.withObfuscated(true);
            case 'l': return style.withBold(true);
            case 'm': return style.withStrikethrough(true);
            case 'n': return style.withUnderline(true);
            case 'o': return style.withItalic(true);
            case 'r': return Style.EMPTY;
            default: return null;
        }
    }

    // Legacy file stores LocalTime (HH:mm:ss) with no date; anchor the file's
    // last-saved day on the file mtime and walk backwards: an earlier message
    // whose clock time is LATER than its successor crossed midnight
    private static List<ChatMessage> loadLegacyFile(File f) {
        List<ChatMessage> out = new ArrayList<>();
        try (Reader r = new InputStreamReader(new FileInputStream(f), StandardCharsets.UTF_8)) {
            List<Map<String, Object>> list = GSON.fromJson(r, new TypeToken<List<Map<String, Object>>>(){}.getType());
            if (list == null) return out;
            java.time.ZoneId zone = java.time.ZoneId.systemDefault();
            java.time.LocalDate day = java.time.Instant.ofEpochMilli(f.lastModified())
                .atZone(zone).toLocalDate();
            LocalTime latest = null;
            for (int i = list.size() - 1; i >= 0; i--) {
                Map<String, Object> obj = list.get(i);
                try {
                    UUID uuid = UUID.fromString((String) obj.get("senderUUID"));
                    Text senderName = null;
                    String snJson = (String) obj.get("senderNameJson");
                    if (snJson != null) {
                        try { senderName = Text.Serialization.fromJson(snJson, registries()); } catch (Exception ignored2) {}
                    }
                    if (senderName == null) senderName = Text.literal((String) obj.get("senderName"));
                    Text content = Text.Serialization.fromJson((String) obj.get("content"), registries());
                    if (content == null) content = Text.literal("");
                    if (content.getString().isBlank()) continue;
                    LocalTime t = LocalTime.parse((String) obj.get("time"), DateTimeFormatter.ISO_LOCAL_TIME);
                    if (latest != null && t.isAfter(latest)) day = day.minusDays(1);
                    latest = t;
                    long millis = java.time.LocalDateTime.of(day, t).atZone(zone).toInstant().toEpochMilli();
                    boolean isOwn = (Boolean) obj.getOrDefault("isOwn", false);
                    boolean isSystem = (Boolean) obj.getOrDefault("isSystem", false);
                    String replyContent = (String) obj.get("replyContent");
                    String replySender = (String) obj.get("replySender");
                    String rawPlayerName = (String) obj.get("rawPlayerName");
                    boolean whisper = Boolean.TRUE.equals(obj.get("whisper"));
                    String whisperPartner = (String) obj.get("whisperPartner");
                    out.add(0, new ChatMessage(uuid, senderName, content, millis,
                        isOwn, isSystem, replyContent, replySender, "", 1, rawPlayerName,
                        whisper, whisperPartner));
                } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
            }
        } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e); }
        return out;
    }

    private static void saveMessages(String worldKey) {
        if (messages.isEmpty()) return;
        File f = getHistoryFile(worldKey);
        f.getParentFile().mkdirs();
        // Atomic replace: write the tmp file fully, then move it over — a crash
        // mid-write leaves the previous file intact instead of a truncated one
        File tmp = new File(f.getParentFile(), f.getName() + ".tmp");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(tmp), StandardCharsets.UTF_8)) {
            for (ChatMessage msg : messages) {
                String line = toLine(msg);
                if (line == null) continue;
                w.write(line);
                w.write("\n");
            }
            w.flush();
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e);
            return;
        }
        try {
            java.nio.file.Files.move(tmp.toPath(), f.toPath(),
                java.nio.file.StandardCopyOption.REPLACE_EXISTING,
                java.nio.file.StandardCopyOption.ATOMIC_MOVE);
        } catch (Exception e) {
            try {
                java.nio.file.Files.move(tmp.toPath(), f.toPath(),
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (Exception e2) {
                com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e2);
            }
        }
    }

    // Periodic autosave: a crash only loses messages newer than the last flush.
    // Called from the client tick; the world switch path in setCurrentWorld still
    // saves on world change / quit. historyDirty skips rewrites when nothing new
    // arrived since the last save.
    private static final long AUTO_SAVE_MS = 30_000;
    private static long lastAutoSave;
    private static boolean historyDirty;

    // Retention cleanup: files older than the configured days are dropped on
    // world join (0 = keep forever, the default)
    static boolean isExpired(long fileMtime, long now, int retentionDays) {
        return retentionDays > 0 && now - fileMtime > retentionDays * 24L * 3600_000L;
    }

    private static void cleanupOldHistory() {
        int days = ChatBubbleClientSetup.config().historyRetentionDays();
        if (days <= 0) return;
        File dir = new File(MinecraftClient.getInstance().runDirectory, "e33chat/history");
        File[] files = dir.listFiles((d, n) -> n.endsWith(".json"));
        if (files == null) return;
        long now = System.currentTimeMillis();
        File current = currentWorldKey != null ? getHistoryFile(currentWorldKey) : null;
        for (File f : files) {
            if (f.equals(current)) continue;
            if (isExpired(f.lastModified(), now, days)) {
                com.mojang.logging.LogUtils.getLogger().info("[e33chat] History retention: deleting " + f.getName());
                f.delete();
            }
        }
    }

    public static void maybeAutoSave() {
        long now = System.currentTimeMillis();
        if (currentWorldKey == null || !historyDirty || now - lastAutoSave < AUTO_SAVE_MS) return;
        historyDirty = false;
        lastAutoSave = now;
        saveMessages(currentWorldKey);
    }

    private static void loadMessages(String worldKey) {
        File f = getHistoryFile(worldKey);
        if (!f.exists()) {
            File legacy = getLegacyHistoryFile(worldKey);
            if (legacy.exists()) f = legacy;
        }
        if (!f.exists()) return;
        // Stale tmp file from a crash between write and rename — safe to discard
        new File(f.getParentFile(), f.getName() + ".tmp").delete();
        String head;
        try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(
                new FileInputStream(f), StandardCharsets.UTF_8))) {
            head = br.readLine();
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e);
            return;
        }
        if (head == null) return;
        // Strip a UTF-8 BOM some editors write, which would break the JSON-array check
        if (head.startsWith("﻿")) head = head.substring(1);
        // Legacy files are a JSON array (starts with '['); new files are JSONL.
        // A legacy file migrates to JSONL on the next save (memory is the source).
        if (head.trim().startsWith("[")) {
            List<ChatMessage> legacy = loadLegacyFile(f);
            for (ChatMessage m : legacy) {
                if (isBlockedMessage(m)) continue;
                messages.add(m);
                if (!m.isSystem() && !m.senderUUID().equals(new UUID(0, 0)))
                    rememberPlayer(m.senderUUID(), m.rawPlayerName(), m.senderName().getString());
            }
        } else {
            try (java.io.BufferedReader br = new java.io.BufferedReader(new java.io.InputStreamReader(
                    new FileInputStream(f), StandardCharsets.UTF_8))) {
                String line;
                while ((line = br.readLine()) != null) {
                    if (line.isBlank()) continue;
                    try {
                        ChatMessage m = fromLine(line);
                        if (m == null || isBlockedMessage(m)) continue;
                        messages.add(m);
                        if (!m.isSystem() && !m.senderUUID().equals(new UUID(0, 0)))
                            rememberPlayer(m.senderUUID(), m.rawPlayerName(), m.senderName().getString());
                    } catch (Exception e) {
                        com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e);
                    }
                }
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to read/write chat history", e);
            }
        }
        while (messages.size() > MAX) messages.remove(0);
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
            if (isPlayerBlocked(e.senderName(), Text.literal(e.senderName()),
                ChatBubbleClientSetup.config().blockedPlayers())) continue;
            messages.add(new ChatMessage(
                e.senderUUID(),
                Text.literal(e.senderName()),
                ChatImageCompat.convert(Text.literal(e.content())),
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
            if (!e.isSystem() && !e.senderUUID().equals(new UUID(0, 0)))
                rememberPlayer(e.senderUUID(), e.senderName(), e.senderName());
        }
    }

    public static void applyChatMeta(UUID senderUUID, String senderName, String messageHash,
                                      String quoteSender, String quoteContent, List<String> mentionTargets) {
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage msg = messages.get(i);
            // Offline/cracked players fall back to UUID(0,0) on the receiving side,
            // so match by raw player name as well when the UUID doesn't line up
            boolean nameMatch = senderName != null && !senderName.isEmpty()
                && msg.rawPlayerName() != null && msg.rawPlayerName().equals(senderName);
            if (msg.messageHash().equals(messageHash)
                && (msg.senderUUID().equals(senderUUID) || nameMatch)) {
                if (msg.replyContent() != null) continue;
                // Anti-spam merge produced this bubble (the second send had no
                // quote) — a late ChatMeta for the first send must not tag it
                if (msg.duplicateCount() > 1) continue;
                if (System.currentTimeMillis() - msg.time() > 5_000) continue;
                if (!quoteContent.isEmpty()) {
                    messages.set(i, new ChatMessage(
                        msg.senderUUID(), msg.senderName(), msg.content(), msg.time(),
                        msg.isOwn(), msg.isSystem(), quoteContent, quoteSender, msg.messageHash(),
                        msg.duplicateCount(), msg.rawPlayerName(),
                        msg.whisper(), msg.whisperPartner()));
                    String playerName = localPlayerSupplier.get() != null
                        ? localPlayerSupplier.get().getName().getString() : "";
                    if (!msg.isOwn() && !playerName.isEmpty()
                        && playerName.equals(quoteSender)
                        && !msg.content().getString().contains("@" + playerName)
                        && ChatBubbleClientSetup.config().mentionSoundEnabled()) {
                        MinecraftClient.getInstance().player.playSound(
                            net.minecraft.sound.SoundEvents.BLOCK_NOTE_BLOCK_CHIME.value(), 0.6F * ChatBubbleClientSetup.config().soundVolume() / 100f, 1.0F);
                    }
                }
                return;
            }
        }
        if (!quoteContent.isEmpty()) {
            pendingMetas.put(messageHash, new PendingMeta(senderUUID, quoteSender, quoteContent,
                mentionTargets, System.currentTimeMillis()));
        }
    }
}
