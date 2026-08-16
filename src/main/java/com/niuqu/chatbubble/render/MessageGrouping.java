package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.store.ChatMessageStore;

/**
 * 消息分组（D07，2.3.16）：组内/组间间距的纯判定逻辑。
 * 与 Forge/Neo 的 ChatMessageRenderer 同名方法语义一致（Fabric 渲染内联，故独立成类以便测试）。
 */
public final class MessageGrouping {
    private MessageGrouping() {}

    /** 组内时间窗：同一发送者间隔超过 5 分钟视为新组（07 §1.2 惯例）。 */
    public static final long GROUP_TIME_MS = 5 * 60_000L;

    /** 两条消息是否同组：同一发送者（rawPlayerName 优先）+ 5 分钟窗口内 + 非系统消息。 */
    public static boolean isSameGroup(ChatMessageStore.ChatMessage prev, ChatMessageStore.ChatMessage msg) {
        if (prev == null || msg == null) return false;
        if (prev.isSystem() || msg.isSystem()) return false;
        String a = prev.rawPlayerName() != null && !prev.rawPlayerName().isEmpty()
            ? prev.rawPlayerName() : prev.senderName().getString();
        String b = msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty()
            ? msg.rawPlayerName() : msg.senderName().getString();
        if (!a.equals(b)) return false;
        return msg.time() - prev.time() <= GROUP_TIME_MS;
    }

    /** 组内间距 = message_gap × 2/3，下限 2（D07 内部比例，不动 message_gap 键）。 */
    public static int groupGap(int messageGap) {
        return Math.max(UiTokens.GROUP_GAP_MIN,
            messageGap * UiTokens.GROUP_GAP_FACTOR_NUM / UiTokens.GROUP_GAP_FACTOR_DEN);
    }

    /** 组间间距 = message_gap × 2。 */
    public static int sectionGap(int messageGap) {
        return messageGap * UiTokens.SECTION_GAP_FACTOR;
    }

    /** 相邻两条消息之间的垂直间距：同组用组内档，否则组间档。 */
    public static int gapBetween(ChatMessageStore.ChatMessage prev, ChatMessageStore.ChatMessage msg, int messageGap) {
        return isSameGroup(prev, msg) ? groupGap(messageGap) : sectionGap(messageGap);
    }
}
