package com.niuqu.chatbubble.chat;

import com.niuqu.chatbubble.ChatMessageStore.ChatMessage;

public final class MessageGrouping {
    private static final long GROUP_TIME_MS = 5 * 60 * 1000L;

    private MessageGrouping() {}

    public static boolean isSameGroup(ChatMessage prev, ChatMessage msg) {
        if (prev == null || msg == null) return false;
        if (prev.isSystem() || msg.isSystem()) return false;
        String a = prev.rawPlayerName() != null && !prev.rawPlayerName().isEmpty()
            ? prev.rawPlayerName() : prev.senderName().getString();
        String b = msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty()
            ? msg.rawPlayerName() : msg.senderName().getString();
        if (!a.equals(b)) return false;
        return msg.time() - prev.time() <= GROUP_TIME_MS;
    }

    public static int groupGap(int messageGap) {
        return Math.max(2, messageGap * 2 / 3);
    }

    public static int sectionGap(int messageGap) {
        return messageGap * 2;
    }

    public static int gapBetween(ChatMessage prev, ChatMessage msg, int messageGap) {
        return isSameGroup(prev, msg) ? groupGap(messageGap) : sectionGap(messageGap);
    }
}
