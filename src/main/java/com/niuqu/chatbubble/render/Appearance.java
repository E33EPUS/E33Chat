package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.ChatBubbleScreen;
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

    /** 消息气泡头像尺寸（像素）。 */
    public static int avatarSize() {
        return ChatBubbleConfig.AVATAR_SIZE.get();
    }
}
