package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.render.ChatBubbleScreen;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.render.ChatBubbleTheme;

/**
 * 外观快照：当前主题的颜色集合统一取色入口。
 * 消费点（ChatBubbleScreen.c() 等）只读快照，不再散落魔法数字。
 */
public final class Appearance {
    private Appearance() {}

    /** 当前主题的颜色快照（取色统一入口）。 */
    public static ChatBubbleTheme.Colors snapshot() {
        return ChatBubbleConfig.THEME.get().colors();
    }

    /** 消息之间的垂直间距。 */
    public static int messageGap() {
        return ChatBubbleConfig.MESSAGE_GAP.get();
    }

    /** 同组（同一发送者连续）消息间距：message_gap × 2/3，下限 2（D07）。 */
    public static int groupGap() {
        return ChatMessageRenderer.groupGap(messageGap());
    }

    /** 组间消息间距：message_gap × 2（D07）。 */
    public static int sectionGap() {
        return ChatMessageRenderer.sectionGap(messageGap());
    }

    /** 消息气泡头像尺寸（像素）。 */
    public static int avatarSize() {
        return ChatBubbleConfig.AVATAR_SIZE.get();
    }
}
