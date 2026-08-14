package com.niuqu.chatbubble;

/**
 * 外观快照：从主题预设 + 配置覆盖生成一帧用的颜色集合。
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
