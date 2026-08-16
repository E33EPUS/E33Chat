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

    /** 同组（同一发送者连续）消息间距：message_gap × 2/3，下限 2（D07）。 */
    public static int groupGap() {
        return MessageGrouping.groupGap(messageGap());
    }

    /** 组间消息间距：message_gap × 2（D07）。 */
    public static int sectionGap() {
        return MessageGrouping.sectionGap(messageGap());
    }

    /** 消息气泡头像尺寸（像素，钳制到 UI 范围）。 */
    public static int avatarSize() {
        Integer a = ChatBubbleClientSetup.config().avatarSize();
        return a == null ? 20 : Math.max(12, Math.min(32, a));
    }
}
