package com.niuqu.chatbubble.store;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.minecraft.network.chat.Component;

/**
 * Cross-message timing state machine: echo suppression, whisper-echo dedup,
 * pending SenderMeta handoff and quote timestamps.
 *
 * Extracted from ChatMessageStore during the 2.3.14 restructure. The message
 * list itself and the operations that mutate it stay in ChatMessageStore.
 */
public final class EchoTracker {
    private EchoTracker() {}

    public static final long QUOTE_ECHO_WINDOW_MS = 5_000;
    public static final long REPOST_DEDUP_MS = 1_000;
    private static long lastQuoteSendTime;

    // True when a repost would duplicate one just sent: the server echoes a whisper
    // twice (signed outgoing + incoming variants) within ~15ms, and both would be
    // rewritten to the same <name>[私聊] line without this guard.
    public static boolean isRepostDuplicate(String lastRepostText, long lastRepostTime, String newText, long now) {
        return newText.equals(lastRepostText) && now - lastRepostTime < REPOST_DEDUP_MS;
    }

    private static final Map<String, PendingMeta> pendingMetas = new HashMap<>();

    private static final ThreadLocal<ChatMessageStore.SenderMeta> PENDING_META = new ThreadLocal<>();
    private static long pendingMetaSetTime;

    public static void setPendingMeta(ChatMessageStore.SenderMeta meta) {
        PENDING_META.set(meta);
        pendingMetaSetTime = System.currentTimeMillis();
    }

    // 2s TTL: if addMessage never runs (another mod cancelled it), a stale
    // note must not misattribute an unrelated later message
    public static ChatMessageStore.SenderMeta consumePendingMeta() {
        ChatMessageStore.SenderMeta m = PENDING_META.get();
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

    private record PendingWhisperEcho(String target, long time) {}
    private static final Deque<PendingWhisperEcho> pendingWhisperEchoes = new ArrayDeque<>();
    private static long suppressCaptureTime;
    private static boolean suppressQuoted;

    public static void markPendingWhisperEcho(String target) {
        pendingWhisperEchoes.addLast(new PendingWhisperEcho(target, System.currentTimeMillis()));
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

    static void purgeStaleWhisperEchoes() {
        long cutoff = System.currentTimeMillis() - 10_000;
        while (!pendingWhisperEchoes.isEmpty() && pendingWhisperEchoes.peekFirst().time() < cutoff) {
            pendingWhisperEchoes.pollFirst();
        }
    }

    public static boolean hasPendingWhisperEcho() {
        purgeStaleWhisperEchoes();
        return !pendingWhisperEchoes.isEmpty();
    }
    public static String getPendingWhisperTarget() {
        purgeStaleWhisperEchoes();
        PendingWhisperEcho head = pendingWhisperEchoes.peekFirst();
        return head != null ? head.target() : null;
    }
    public static void consumeWhisperEcho() { pendingWhisperEchoes.pollFirst(); }

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
    static void purgeStaleEchoes() {
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

    public static EchoMatch consumeEchoIfSenderMatches(UUID senderUUID, Component senderName, String incomingText) {
        purgeStaleEchoes();
        if (pendingEchoes.isEmpty()) return new EchoMatch(false, false);
        var player = net.minecraft.client.Minecraft.getInstance().player;
        if (player == null) return new EchoMatch(false, false);
        // Deterministic: signed-channel echoes carry the sender's real UUID
        boolean match = senderUUID != null && senderUUID.equals(player.getUUID());
        // Whole-word boundary match for decorated / color-translated servers
        // (substring contains misattributed e.g. SteveAdmin to Steve)
        if (!match) {
            String s = senderName.getString();
            match = containsWholeName(s, player.getName().getString());
            if (!match && player.connection != null) {
                var info = player.connection.getPlayerInfo(player.getUUID());
                if (info != null && info.getTabListDisplayName() != null) {
                    String tab = info.getTabListDisplayName().getString().trim();
                    match = !tab.isEmpty() && containsWholeName(s, tab);
                }
            }
        }
        if (match) {
            // The server echoes our own text back, so match by content (most recent
            // first) to pinpoint WHICH send produced this echo. Blind remove(0) would
            // grab an older unconsumed echo from a filtered message and mis-tag the
            // quote flag onto the wrong send.
            if (incomingText != null) {
                for (int i = pendingEchoes.size() - 1; i >= 0; i--) {
                    if (incomingText.equals(pendingEchoes.get(i).text())) {
                        boolean quoted = pendingEchoes.get(i).quoted();
                        pendingEchoes.remove(i);
                        ChatMessageStore.updateLatestOwnSenderName(senderName);
                        return new EchoMatch(true, quoted);
                    }
                }
            }
            // Fallback: the server decorated/translated the content — take the oldest.
            PendingEcho e = pendingEchoes.remove(0);
            ChatMessageStore.updateLatestOwnSenderName(senderName);
            return new EchoMatch(true, e.quoted());
        }
        return new EchoMatch(false, false);
    }

    // True when needle occurs in haystack with no name character (letter/digit/_)
    // adjacent — "[VIP]Steve" and "<Steve>" hit, "SteveAdmin" and "Steve2" do not.
    public static boolean containsWholeName(String haystack, String needle) {
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

    public static boolean isNamePart(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public static void markQuoteSent() {
        lastQuoteSendTime = System.currentTimeMillis();
    }

    // True when a quote reply was sent within the echo window: the local bubble's
    // addMessage consumes pendingReplyContent before the server echo returns, so
    // the vanilla-chat [引用] tag can't read it — this timestamp is the residue.
    public static boolean wasRecentQuoteAt(long quoteSendTime, long now) {
        return quoteSendTime != 0 && now - quoteSendTime < QUOTE_ECHO_WINDOW_MS;
    }

    public static boolean wasRecentQuote() {
        return wasRecentQuoteAt(lastQuoteSendTime, System.currentTimeMillis());
    }

    record PendingMeta(UUID senderUUID, String quoteSender, String quoteContent,
                       List<String> mentionTargets, long createdAt) {}

    public static PendingMeta removePendingMeta(String messageHash) {
        return pendingMetas.remove(messageHash);
    }

    public static void putPendingMeta(String messageHash, UUID senderUUID,
                                      String quoteSender, String quoteContent,
                                      List<String> mentionTargets) {
        pendingMetas.put(messageHash, new PendingMeta(senderUUID, quoteSender, quoteContent,
            mentionTargets, System.currentTimeMillis()));
    }

    public static void prunePendingMetas(long cutoff) {
        pendingMetas.entrySet().removeIf(e -> e.getValue().createdAt() < cutoff);
    }
}
