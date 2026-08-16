package com.niuqu.chatbubble.render;

/**
 * 视觉 token 最小表（00e D2，2.3.16）：圆角 2 档 + 阴影 2 档 + 1px 描边 + 基础间距。
 * 颜色仍走 ChatBubbleTheme.Colors（DARK/LIGHT + 资源包纹理），本表只收敛
 * "形状/阴影/间距"结构值；新 UI 元素一律从本表取值，禁止散落魔法数字。
 * 组内/组间消息间距语义分离（D07）也在此定义。
 */
public final class UiTokens {
    private UiTokens() {}

    // ---- 圆角（2 档，00e 拍板：中 8-12 / 大 16） ----
    /** 中档：气泡/引用块/弹层/上下文菜单/mention 弹层 */
    public static final int RADIUS_MEDIUM = 8;
    /** 大档：横幅等大浮层 */
    public static final int RADIUS_LARGE = 16;

    // ---- 阴影（2 档：面板/弹层，硬阴影 offset + 纯黑 alpha） ----
    public static final int SHADOW_OFFSET_PANEL = 2;
    public static final int SHADOW_ALPHA_PANEL = 0x30;
    public static final int SHADOW_OFFSET_POPUP = 4;
    public static final int SHADOW_ALPHA_POPUP = 0x40;

    // ---- 描边（1px 语言） ----
    public static final int BORDER_W = 1;

    // ---- 间距 ----
    /** 面板内容边距 */
    public static final int PAD = 8;
    /** 气泡内边距 */
    public static final int BUBBLE_PAD_X = 6;
    public static final int BUBBLE_PAD_Y = 4;
    /** 头像与气泡/名字的间距（气泡 4 / 名字 8 沿用既有手感） */
    public static final int AVATAR_GAP = 4;
    public static final int AVATAR_NAME_GAP = 8;
    /** 同组（同一发送者连续消息）间距 = message_gap 的 2/3，下限 2 */
    public static final int GROUP_GAP_FACTOR_NUM = 2;
    public static final int GROUP_GAP_FACTOR_DEN = 3;
    public static final int GROUP_GAP_MIN = 2;
    /** 组间（换人/超时/系统消息前后）间距 = message_gap × 2 */
    public static final int SECTION_GAP_FACTOR = 2;
}
