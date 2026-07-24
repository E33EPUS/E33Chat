package com.niuqu.chatbubble.chat;

import java.util.Collection;
import java.util.Comparator;
import java.util.Optional;

public final class MessagePresentation {
    private MessagePresentation() {}

    public record PlayerLine(String playerName, String displayLabel, String content) {}

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

    static Optional<PlayerLine> parseGeneric(String text, String name) {
        if (text == null || name == null) return Optional.empty();
        int idx = text.indexOf(name);
        if (idx < 0) return Optional.empty();

        // Support short names (1-2 chars) when they appear in specific contexts
        boolean allowShortName = false;
        if (name.length() < 3) {
            // Allow if name is in angle brackets like <a>
            if (idx > 0 && text.charAt(idx - 1) == '<' &&
                idx + name.length() < text.length() && text.charAt(idx + name.length()) == '>') {
                allowShortName = true;
            }
            // Allow if name is in brackets like [a]
            else if (idx > 0 && text.charAt(idx - 1) == '[' &&
                     idx + name.length() < text.length() && text.charAt(idx + name.length()) == ']') {
                allowShortName = true;
            }
        }

        if (!allowShortName && name.length() < 3) return Optional.empty();

        // Skip decorative prefixes when checking the 30-char limit
        int decorativeLen = countDecorativePrefix(text, idx);
        int effectiveIdx = idx - decorativeLen;
        if (effectiveIdx >= 30) return Optional.empty();

        // Check if this looks like a valid player message
        if (idx > 0) {
            char prev = text.charAt(idx - 1);
            if (Character.isLetterOrDigit(prev) || prev == '_') {
                // Allow if inside angle brackets like <[VIP]Steve>
                int openAngle = text.lastIndexOf('<', idx);
                int closeAngle = text.indexOf('>', idx + name.length());
                if (openAngle >= 0 && closeAngle >= 0 && closeAngle - openAngle <= 64) {
                    // Inside angle brackets, OK
                } else {
                    // Allow if inside brackets like [VIP]Steve
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
            if (Character.isWhitespace(ch) || ch == '>' || ch == ':'
                || ch == '：' || ch == '»' || ch == '-' || ch == '|') sep++;
            else break;
        }
        if (sep <= after || sep >= text.length()) return Optional.empty();

        String displayLabel = text.substring(0, idx + name.length());
        return Optional.of(new PlayerLine(name, displayLabel, text.substring(sep).strip()));
    }

    /**
     * Count the length of decorative prefixes (like [VIP], <admin>, color codes, etc.)
     * that should be skipped when calculating the effective position of a player name.
     */
    private static int countDecorativePrefix(String text, int upTo) {
        int count = 0;
        int i = 0;
        while (i < upTo) {
            char c = text.charAt(i);

            // Skip bracket content like [VIP] or <admin>
            if (c == '[' || c == '<') {
                char close = c == '[' ? ']' : '>';
                int closeIdx = text.indexOf(close, i + 1);
                if (closeIdx >= 0 && closeIdx < upTo) {
                    count += closeIdx - i + 1;
                    i = closeIdx + 1;
                    continue;
                }
            }

            // Skip color codes like §a, §l, etc.
            if (c == '§' && i + 1 < text.length()) {
                count += 2;
                i += 2;
                continue;
            }

            // Skip whitespace and punctuation
            if (Character.isWhitespace(c) || c == ':' || c == '：' || c == '»' || c == '-' || c == '|') {
                count++;
                i++;
                continue;
            }

            // Stop at first non-decorative character
            break;
        }
        return count;
    }
}
