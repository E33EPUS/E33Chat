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

}
