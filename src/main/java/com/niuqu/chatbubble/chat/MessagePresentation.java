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
        var plain = parseGeneric(text, name);
        if (plain.isPresent() || name == null) return plain;
        // §6Steve: the server sends color-coded names; try the color-stripped variant
        String stripped = name.replaceAll("§.", "");
        if (!stripped.isEmpty() && !stripped.equals(name))
            return parseGeneric(text, stripped);
        return Optional.empty();
    }

    static Optional<PlayerLine> parseGeneric(String text, String name) {
        if (text == null || name == null) return Optional.empty();
        int idx = text.indexOf(name);
        if (idx < 0) return Optional.empty();

        int minLen = 3;
        if (idx > 0 && text.charAt(idx - 1) == '<') {
            int closeAngle = text.indexOf('>', idx + name.length());
            if (closeAngle >= 0 && closeAngle - (idx - 1) <= 64) minLen = 1;
        }
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
        if (minLen == 3) {
            int after = idx + name.length();
            if (after < text.length()) {
                char next = text.charAt(after);
                if (next == ':' || next == '：') minLen = 1;
            }
        }
        if (name.length() < minLen) return Optional.empty();

        int decorativeLen = countDecorativePrefix(text, idx);
        if (idx - decorativeLen >= 30) return Optional.empty();

        if (idx > 0) {
            char prev = text.charAt(idx - 1);
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

        int sep = skipSeparators(text, after);
        if (sep <= after || sep >= text.length()) return Optional.empty();

        String displayLabel = text.substring(0, idx + name.length());
        return Optional.of(new PlayerLine(name, displayLabel, text.substring(sep).strip()));
    }

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
                || ch == '：' || ch == '»' || ch == '-' || ch == '|') { sep++; continue; }
            break;
        }
        return sep;
    }

    public static boolean isWhitespaceOnlyGap(String text, int from, int to) {
        if (text == null || to <= from) return false;
        for (int i = from; i < to && i < text.length(); i++) {
            if (!Character.isWhitespace(text.charAt(i))) return false;
        }
        return true;
    }

    public static boolean hasWhisperKeywordBeforeColon(String text) {
        if (text == null) return false;
        int colon = -1;
        for (int i = 0; i < text.length(); i++) {
            char ch = text.charAt(i);
            if (ch == ':' || ch == '：') { colon = i; break; }
        }
        String zone = colon < 0 ? text : text.substring(0, colon);
        return zone.contains("悄悄") || zone.contains("whisper") || zone.contains("对你说")
            || zone.contains("to you") || zone.contains("私聊") || zone.contains("密语") || zone.contains("密聊");
    }

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
            if (c == '§' && i + 1 < text.length()) { i += 2; continue; }
            if (Character.isWhitespace(c) || c == ':' || c == '：' || c == '»' || c == '-' || c == '|') {
                i++; continue;
            }
            break;
        }
        return i;
    }
}
