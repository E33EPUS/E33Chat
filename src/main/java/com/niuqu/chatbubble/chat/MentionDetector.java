package com.niuqu.chatbubble.chat;

import java.util.ArrayList;
import java.util.List;

public final class MentionDetector {
    private MentionDetector() {}

    public static boolean isMentioned(String text, String localPlayerName,
                                      boolean requireAt, String replySender) {
        if (text == null || localPlayerName == null || localPlayerName.isBlank()) return false;
        String lower = text.toLowerCase();
        String needle = localPlayerName.toLowerCase();
        int idx = 0;
        while ((idx = lower.indexOf(needle, idx)) >= 0) {
            int end = idx + needle.length();
            boolean hasAt = idx > 0 && text.charAt(idx - 1) == '@';
            if (hasAt) {
                if (end >= text.length() || !isNameCharacter(text.charAt(end))) {
                    return true;
                }
            } else if (!requireAt) {
                boolean leftOk = idx == 0 || !isNameCharacter(text.charAt(idx - 1));
                boolean rightOk = end >= text.length() || !isNameCharacter(text.charAt(end));
                if (leftOk && rightOk && text.charAt(idx - 1) != '@') return true;
            }
            idx = end;
        }
        if (replySender != null && replySender.equals(localPlayerName)) return true;
        return false;
    }

    public static List<MentionRange> findMentionRanges(String text, String localPlayerName,
                                                        boolean requireAt) {
        List<MentionRange> ranges = new ArrayList<>();
        if (text == null || localPlayerName == null || localPlayerName.isBlank()) return ranges;
        String lower = text.toLowerCase();
        String needle = localPlayerName.toLowerCase();
        int idx = 0;
        while ((idx = lower.indexOf(needle, idx)) >= 0) {
            int end = idx + needle.length();
            boolean hasAt = idx > 0 && text.charAt(idx - 1) == '@';
            int matchStart = hasAt ? idx - 1 : idx;
            if ((!requireAt || hasAt) && (end >= text.length() || !isNameCharacter(text.charAt(end)))) {
                ranges.add(new MentionRange(matchStart, end));
            }
            idx = end;
        }
        return ranges;
    }

    private static boolean isNameCharacter(char c) {
        return Character.isLetterOrDigit(c) || c == '_';
    }

    public record MentionRange(int start, int end) {}
}
