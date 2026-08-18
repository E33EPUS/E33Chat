package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.render.ChatBubbleTheme;

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

    /** 消息之间的垂直间距（手改配置文件也可能越界，钳制到 UI 范围）。 */
    public static int messageGap() {
        Integer g = ChatBubbleClientSetup.config().messageGap();
        return g == null ? 6 : Math.max(0, Math.min(12, g));
    }


    /** 消息气泡头像尺寸（像素，钳制到 UI 范围）。 */
    public static int avatarSize() {
        Integer a = ChatBubbleClientSetup.config().avatarSize();
        return a == null ? 20 : Math.max(12, Math.min(32, a));
    }

    /** 气泡大小百分比（钳制到 50-200）。 */
    public static int bubbleScalePercent() {
        Integer s = ChatBubbleClientSetup.config().bubbleScale();
        return s == null ? 100 : Math.max(50, Math.min(200, s));
    }

    /** 气泡缩放系数（0.5-2.0）。 */
    public static float bubbleScale() {
        return bubbleScalePercent() / 100f;
    }

    /** 指定缩放下气泡文字换行宽度（设计单位，更大气泡每行容纳更少字符；钳制保证可读）。 */
    public static int scaledWrapWidth(int bubbleMaxW, float scale) {
        return Math.max(16, (int) (bubbleMaxW / scale));
    }

    /** 当前缩放下气泡文字换行宽度。 */
    public static int bubbleWrapWidth(int bubbleMaxW) {
        return scaledWrapWidth(bubbleMaxW, bubbleScale());
    }
}
