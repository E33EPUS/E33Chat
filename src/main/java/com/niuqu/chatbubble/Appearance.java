package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ChatBubbleConfig;

/**
 * 外观快照：当前主题的颜色快照（取色统一入口）+ 布局参数宿主。
 */
public final class Appearance {
    private Appearance() {}

    /** 当前主题的颜色快照。 */
    public static ChatBubbleTheme.Colors snapshot() {
        ChatBubbleConfig cfg = ChatBubbleClientSetup.config();
        ChatBubbleTheme theme = "light".equalsIgnoreCase(cfg.theme()) ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
        return theme.colors();
    }

    /** 消息之间的垂直间距。 */
    public static int messageGap() {
        Integer g = ChatBubbleClientSetup.config().messageGap();
        return g == null ? 6 : g;
    }

    /** 消息气泡头像尺寸（像素）。 */
    public static int avatarSize() {
        Integer a = ChatBubbleClientSetup.config().avatarSize();
        return a == null ? 20 : a;
    }
}
