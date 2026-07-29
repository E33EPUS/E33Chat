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

    public record PlayerLine(String playerName, String displayLabel, String content) {}

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
        int idx = text.indexOf(name);
        if (idx < 0) return Optional.empty();

        int minLen = 3;
        // angle-bracket wrapped short name: <a> hi
        if (idx > 0 && text.charAt(idx - 1) == '<') {
            int closeAngle = text.indexOf('>', idx + name.length());
            if (closeAngle >= 0 && closeAngle - (idx - 1) <= 64) minLen = 1;
        }
        // bracket-prefix short name followed by colon: [T]a: hi
        if (minLen == 3 && idx > 0) {
            int bracketClose = text.lastIndexOf(']', idx);
            if (bracketClose >= 0 && idx - bracketClose <= 2) {
                int bracketOpen = text.lastIndexOf('[', bracketClose);
                if (bracketOpen >= 0) {
                    int after = idx + name.length();
                    if (after < text.length()) {
                        char next = text.charAt(after);
                        if (next == ':' || next == '：') minLen = 1;
                    }
                }
            }
        }
        if (name.length() < minLen) return Optional.empty();

        int decorativeLen = countDecorativePrefix(text, idx);
        if (idx - decorativeLen >= 30) return Optional.empty();

        if (idx > 0) {
            char prev = text.charAt(idx - 1);
            // §6Steve: the preceding digit belongs to a legacy color code, not the name
            boolean prevIsColorCode = prev == '§' || (idx >= 2 && text.charAt(idx - 2) == '§');
            if (!prevIsColorCode && (Character.isLetterOrDigit(prev) || prev == '_')) {
                int openAngle = text.lastIndexOf('<', idx);
                int closeAngle = text.indexOf('>', idx + name.length());
                if (openAngle >= 0 && closeAngle >= 0 && closeAngle - openAngle <= 64) {
                    // inside angle brackets like <[VIP]Steve>
                } else {
                    int bracketClose = text.lastIndexOf(']', idx);
                    if (bracketClose >= 0) {
                        int bracketOpen = text.lastIndexOf('[', bracketClose);
                        if (bracketOpen < 0 || idx - bracketClose > 2) return Optional.empty();
                    } else {
                        return Optional.empty();
                    }
                }
            }
        }

        int after = idx + name.length();
        if (after < text.length()) {
            char next = text.charAt(after);
            if (Character.isLetterOrDigit(next) || next == '_') return Optional.empty();
        }

        int sep = after;
        while (sep < text.length()) {
            char ch = text.charAt(sep);
            // skip legacy color codes so "§6Steve§r: hi" finds its separator
            if (ch == '§' && sep + 1 < text.length()) { sep += 2; continue; }
            if (Character.isWhitespace(ch) || ch == '>' || ch == ':'
                || ch == '：' || ch == '»' || ch == '-' || ch == '|') sep++;
            else break;
        }
        if (sep <= after || sep >= text.length()) return Optional.empty();

        String displayLabel = text.substring(0, idx + name.length());
        return Optional.of(new PlayerLine(name, displayLabel, text.substring(sep).strip()));
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
