package com.niuqu.chatbubble;

/**
 * 外观快照：从主题预设 + 配置覆盖生成一帧用的颜色集合。
 * 消费点（ChatBubbleScreen.c() 等）只读快照，不再散落魔法数字。
 */
public final class Appearance {
    private Appearance() {}

    /** 当前主题下、应用配置覆盖后的颜色快照。 */
    public static ChatBubbleTheme.Colors snapshot() {
        ChatBubbleTheme.Colors base = ChatBubbleConfig.THEME.get().colors();

        int panelBg = override(base.panelBg(), ChatBubbleConfig.PANEL_BG_COLOR.get());
        int accent = override(0, ChatBubbleConfig.ACCENT_COLOR.get());

        if (panelBg == base.panelBg() && accent == 0) {
            return base;
        }

        int notificationText = accent != 0 ? accent : base.notificationText();
        int bannerText = accent != 0 ? accent : base.bannerText();

        return new ChatBubbleTheme.Colors(
            panelBg, base.titleBg(), base.barBg(),
            base.sidebarBg(), base.sidebarItemHover(), base.sidebarItemSelected(), base.sidebarDivider(),
            base.divider(), base.inputBg(),
            base.textPrimary(), base.textSecondary(), base.textMuted(),
            base.nameColor(), base.timeColor(),
            base.popupBg(), base.popupHover(), base.popupText(),
            base.contextBg(), base.contextHover(), base.contextText(),
            base.iconHover(),
            notificationText, base.whisperBar(),
            base.toastBg(), base.toastText(),
            base.scrollbar(), base.scrollbarHover(),
            base.closeBg(), base.closeHoverBg(), base.closeText(),
            base.systemText(), base.quoteBar(), base.duplicateLabel(),
            base.redDot(), base.redDotMention(),
            base.configTitle(), base.configSection(), base.configLabel(), base.configBg(),
            base.bannerBg(), bannerText, base.bannerBar()
        );
    }

    /** UI 元素（右键菜单/弹层/toast 等）统一圆角半径。 */
    public static int cornerRadius() {
        return ChatBubbleConfig.UI_CORNER_RADIUS.get();
    }

    /** 消息之间的垂直间距。 */
    public static int messageGap() {
        return ChatBubbleConfig.MESSAGE_GAP.get();
    }

    /** 消息气泡头像尺寸（像素）。 */
    public static int avatarSize() {
        return ChatBubbleConfig.AVATAR_SIZE.get();
    }

    private static int override(int base, String hex) {
        if (hex == null || hex.trim().isEmpty()) return base;
        return ChatBubbleConfig.parseHexColor(hex, base);
    }
}
