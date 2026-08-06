package com.niuqu.chatbubble.chat;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

/**
 * Parses decorated server chat lines into structured player-name + content pairs.
 * Handles both colon-based formats (Steve: hi) and generic separator formats
 * (Steve >> hi, &lt;Steve&gt; hi) produced by NCR and other server plugins.
 */
public final class MessagePresentation {
    private MessagePresentation() {}

    public record PlayerLine(String playerName, String displayLabel, String content,
                             int nameStart, int nameEnd, int contentStart) {}

    /**
     * Tries every online name (longest first to avoid substring mismatches) against
     * the raw chat line. Returns the first successful parse.
     */
    public static Optional<PlayerLine> parseDecoratedPlayerLine(
        String text, Collection<String> onlineNames
    ) {
        if (text == null || onlineNames == null) return Optional.empty();
        return onlineNames.stream()
            .filter(n -> n != null && !n.isBlank())
            .sorted(Comparator.comparingInt(String::length).reversed())
            .flatMap(name -> parseForName(text, name).stream())
            .findFirst();
    }

    private static Optional<PlayerLine> parseForName(String text, String name) {
        return parseGeneric(text, name);
    }

    /**
     * Generic separator-skipping approach. Finds a player name with word-boundary
     * checks, then skips any mix of whitespace and common separator characters
     * (>, :, ：, », -, |) to locate the message content.
     *
     * <p>Handles bracket-wrapped decorations like &lt;[VIP]Steve&gt; and [VIP]Steve
     * by allowing non-letter neighbours when the name sits inside angle brackets or
     * is prefixed by a short bracket group.
     */
    static Optional<PlayerLine> parseGeneric(String text, String name) {
        if (text == null || name == null) return Optional.empty();
        // 名字可能嵌 legacy 色码（S§6t§beve），text 侧也可能嵌——双侧剥 §，
        // 在 clean 文本上做锚点匹配，偏移经映射表转回原文（供样式切片）。
        String cleanName = name.replaceAll("§.", "");
        if (cleanName.isEmpty()) return Optional.empty();
        // stripColor(text) + 偏移映射：map[cleanIdx] = 原文 idx
        int[] map = new int[text.length()];
        StringBuilder clean = new StringBuilder(text.length());
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == '§' && i + 1 < text.length()) { i++; continue; }
            map[clean.length()] = i;
            clean.append(ch);
        }
        String ct = clean.toString();
        int idx = ct.indexOf(cleanName);
        if (idx < 0) return Optional.empty();

        // Broadcast-spoof guard: separators (>>, », |, :) before the name mean a
        // label like "系统>>Steve" — real chat keeps only decorations ([VIP],
        // <clan>, §codes, title text) before the name, never chat separators.
        String beforeName = ct.substring(0, idx);
        if (beforeName.indexOf('>') >= 0 || beforeName.indexOf('»') >= 0
            || beforeName.indexOf('|') >= 0 || beforeName.indexOf(':') >= 0
            || beforeName.indexOf('：') >= 0) return Optional.empty();
        // Broadcast label words (系统/公告/Server/...) before the name — bracket
        // wrapped or space separated — are labels too, not a player line
        if (isBroadcastLabelPrefix(ct, idx)) return Optional.empty();

        int minLen = 3;
        // angle-bracket wrapped short name: <a> hi
        if (idx > 0 && ct.charAt(idx - 1) == '<') {
            int closeAngle = ct.indexOf('>', idx + cleanName.length());
            if (closeAngle >= 0 && closeAngle - (idx - 1) <= 64) minLen = 1;
        }
        // bracket-prefix short name followed by colon: [T]a: hi
        if (minLen == 3 && idx > 0) {
            int bracketClose = ct.lastIndexOf(']', idx);
            if (bracketClose >= 0 && idx - bracketClose <= 2) {
                int bracketOpen = ct.lastIndexOf('[', bracketClose);
                if (bracketOpen >= 0) {
                    int after = idx + cleanName.length();
                    if (after < ct.length()) {
                        char next = ct.charAt(after);
                        if (next == ':' || next == '：') minLen = 1;
                    }
                }
            }
        }
        // bare short name followed directly by a colon: 小明: 你好 / a: hi —
        // cracked servers allow short/Chinese names; the online-list anchor
        // plus a strong colon makes misattribution very unlikely
        if (minLen == 3) {
            int after = idx + cleanName.length();
            if (after < ct.length()) {
                char next = ct.charAt(after);
                if (next == ':' || next == '：') minLen = 1;
            }
        }
        if (cleanName.length() < minLen) return Optional.empty();

        int decorativeLen = countDecorativePrefix(ct, idx);
        if (idx - decorativeLen >= 30) return Optional.empty();

        if (idx > 0) {
            char prev = ct.charAt(idx - 1);
            // §6Steve: the preceding digit belongs to a legacy color code, not the name
            boolean prevIsColorCode = prev == '§' || (idx >= 2 && ct.charAt(idx - 2) == '§');
            if (!prevIsColorCode && (Character.isLetterOrDigit(prev) || prev == '_')) {
                int openAngle = ct.lastIndexOf('<', idx);
                int closeAngle = ct.indexOf('>', idx + cleanName.length());
                if (openAngle >= 0 && closeAngle >= 0 && closeAngle - openAngle <= 64) {
                    // inside angle brackets like <[VIP]Steve>
                } else {
                    int bracketClose = ct.lastIndexOf(']', idx);
                    if (bracketClose >= 0) {
                        int bracketOpen = ct.lastIndexOf('[', bracketClose);
                        if (bracketOpen < 0 || idx - bracketClose > 2) return Optional.empty();
                    } else {
                        return Optional.empty();
                    }
                }
            }
        }

        int after = idx + cleanName.length();
        if (after < ct.length()) {
            char next = ct.charAt(after);
            if (Character.isLetterOrDigit(next) || next == '_') return Optional.empty();
        }

        int sep = skipSeparators(ct, after);
        if (sep <= after || sep >= ct.length()) return Optional.empty();

        int origNameStart = map[idx];
        int origNameEnd = map[idx + cleanName.length()];
        int origContentStart = map[sep];
        String displayLabel = text.substring(0, origNameEnd);
        return Optional.of(new PlayerLine(name, displayLabel, text.substring(origContentStart).strip(),
            origNameStart, origNameEnd, origContentStart));
    }

    /**
     * Skips the separator run between name and content: whitespace, common
     * chat separators, § color pairs, and whole bracket pairs ([LV.10],
     * (VIP), &lt;clan&gt;, 【title】) so name-suffix decorations parse the
     * same way prefix decorations do. Shared by the parser and every caller
     * that locates content start.
     */
    public static int skipSeparators(String text, int from) {
        int sep = from;
        while (sep < text.length()) {
            char ch = text.charAt(sep);
            if (ch == '§' && sep + 1 < text.length()) { sep += 2; continue; }
            if (ch == '[' || ch == '(' || ch == '<' || ch == '【') {
                char close = ch == '[' ? ']' : ch == '(' ? ')' : ch == '<' ? '>' : '】';
                int end = text.indexOf(close, sep + 1);
                if (end > sep && end - sep <= 32) { sep = end + 1; continue; }
            }
            if (Character.isWhitespace(ch) || ch == '>' || ch == ':'
                || ch == '：' || ch == '»' || ch == '-' || ch == '|') sep++;
            else break;
        }
        return sep;
    }

    /**
     * Whisper-format keywords must come before the first chat colon: a keyword
     * after the colon sits inside public chat content ("Steve: 不能用私聊") —
     * on NCR servers public chat reaches the whisper layer with its key stripped.
     */
    private static final java.util.regex.Pattern EN_WHISPER_WORDS =
        java.util.regex.Pattern.compile("\\b(?:pm|message|msg|tell)\\b");

    public static boolean hasWhisperKeywordBeforeColon(String text) {
        if (text == null) return false;
        int colon = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ':' || ch == '：') { colon = i; break; }
        }
        String zone = colon < 0 ? text : text.substring(0, colon);
        String lower = zone.toLowerCase();
        if (zone.contains("悄悄") || zone.contains("whisper") || zone.contains("对你说")
            || zone.contains("to you") || zone.contains("私聊") || zone.contains("密语")
            || zone.contains("密聊") || zone.contains("私信") || zone.contains("密谈"))
            return true;
        // 短英文词（pm/msg/tell/message）易撞玩家名/前缀（Msg: hi、[PM]Steve）——
        // 剥掉 []() 装饰块后，词命中且 zone 里还有别的 token 才算真私聊格式
        // （"Steve PM you"/"PM Steve" 有名字+词；名字恰是词或 [PM] 纯前缀不算）
        String zoneNoBrackets = zone.replaceAll("\\[[^\\]]*\\]|\\([^\\)]*\\)", "");
        if (!EN_WHISPER_WORDS.matcher(zoneNoBrackets.toLowerCase()).find()) return false;
        String rest = zoneNoBrackets.toLowerCase().replaceAll("\\b(?:pm|message|msg|tell)\\b", " ").trim();
        return !rest.isEmpty();
    }

    /**
     * Extracts the whisper content after the sender name. First separator
     * after the name wins — lastIndexOf truncated content that itself contains
     * ": "; colon-family first since "Steve -&gt; you: hi" still extracts at
     * the colon.
     */
    public static String extractWhisperContent(String fullText, String senderName) {
        if (senderName == null || senderName.isEmpty()) return fullText;
        int idx = fullText == null ? -1 : fullText.indexOf(senderName);
        if (idx < 0) return fullText;
        String after = fullText.substring(idx + senderName.length());
        for (String sep : new String[]{": ", "：", " :", " ：", " -> ", " >> ", " » ", " | "}) {
            int i = after.indexOf(sep);
            if (i >= 0) return after.substring(i + sep.length());
        }
        return after.trim();
    }

    /**
     * True when the gap between name and content holds only whitespace —
     * the shape of a broadcast sentence (Steve joined the game), not chat:
     * server chat formats always carry a separator between name and content.
     */
    public static boolean isWhitespaceOnlyGap(String text, int from, int to) {
        if (text == null || to <= from) return false;
        for (int i = from; i < to && i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) return false;
        }
        return true;
    }

    // Broadcast label words: when one of these sits directly before a player name
    // (with optional []【】<>() wrapping), the line is a broadcast label like
    // "[系统]Steve: xxx" or "公告 Steve: xxx", not player chat. Whole-label match
    // only — a real title containing the word ("[系统管理员]", "Serveradmin")
    // is never rejected.
    private static final java.util.Set<String> BROADCAST_LABELS = java.util.Set.of(
        "系统", "公告", "服务器", "广播", "提示", "通知",
        "system", "server", "notice", "broadcast", "announcement", "alert");

    static boolean isBroadcastLabelPrefix(String cleanText, int nameIdx) {
        String zone = cleanText.substring(0, nameIdx).trim();
        if (zone.isEmpty()) return false;
        while (zone.length() >= 2) {
            char open = zone.charAt(0);
            char close = zone.charAt(zone.length() - 1);
            if ((open == '[' && close == ']') || (open == '【' && close == '】')
                || (open == '<' && close == '>') || (open == '(' && close == ')')) {
                zone = zone.substring(1, zone.length() - 1).trim();
            } else {
                break;
            }
        }
        if (zone.isEmpty()) return false;
        return BROADCAST_LABELS.contains(zone.toLowerCase(java.util.Locale.ROOT));
    }

    private static int countDecorativePrefix(String text, int upTo) {
        int i = 0;
        while (i < upTo) {
            char c = text.charAt(i);
            if (c == '[') {
                int close = text.indexOf(']', i + 1);
                if (close >= 0 && close < upTo) { i = close + 1; continue; }
            }
            if (c == '<') {
                int close = text.indexOf('>', i + 1);
                if (close >= 0 && close < upTo) { i = close + 1; continue; }
            }
            if (c == '§' && i + 1 < upTo) { i += 2; continue; }
            if (Character.isWhitespace(c) || !Character.isLetterOrDigit(c)) { i++; continue; }
            break;
        }
        return i;
    }
}