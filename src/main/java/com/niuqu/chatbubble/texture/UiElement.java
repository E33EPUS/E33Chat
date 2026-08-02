package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.ChatBubbleTheme;
import java.util.function.Function;
import net.minecraft.util.Identifier;

/** UI 纹理元素：资源包路径 + 默认主题色 + 默认纹理生成方式。路径约定 assets/e33chat/textures/gui/{theme}/{path}.png */
public enum UiElement {
    PANEL_BG("panel_bg", c -> ChatBubbleTheme.alphaBlend(c.panelBg(), 255), Kind.SOLID),
    TITLE_BAR("title_bar", c -> c.titleBg(), Kind.SOLID),
    BOTTOM_BAR("bottom_bar", c -> c.barBg(), Kind.SOLID),
    SIDEBAR_BG("sidebar_bg", c -> c.sidebarBg(), Kind.SOLID),
    DIVIDER("divider", c -> c.divider(), Kind.SOLID),
    INPUT_BG("input_bg", c -> c.inputBg(), Kind.SOLID),
    SCROLLBAR_TRACK("scrollbar_track", c -> ChatBubbleTheme.alphaBlend(c.scrollbar(), 255), Kind.SOLID),
    SCROLLBAR_THUMB("scrollbar_thumb", c -> ChatBubbleTheme.alphaBlend(c.scrollbar(), 255), Kind.SOLID),
    CONTEXT_MENU_BG("context_menu_bg", c -> c.contextBg(), Kind.SOLID),
    POPUP_BG("popup_bg", c -> c.popupBg(), Kind.SOLID),
    TOAST_BG("toast_bg", c -> ChatBubbleTheme.alphaBlend(c.toastBg(), 255), Kind.SOLID),
    WHISPER_BAR("whisper_bar", c -> c.whisperBar(), Kind.SOLID),
    CONFIG_BG("config_bg", c -> c.configBg(), Kind.SOLID),
    CONTENT_BG("content_bg", c -> c.barBg(), Kind.SOLID),
    // 2.2.8 新增：动态尺寸组件。ROUNDED 默认生成 16×16 圆角纹理走 9-slice 渲染——
    // 圆角在纹理里（四角恒定），资源包可覆盖圆角形状/边框/图案；白色默认色配合渲染时 tint 动态着色。
    BUBBLE_BG("bubble_bg", c -> 0xFFFFFFFF, Kind.ROUNDED_4),
    QUOTE_BG("quote_bg", c -> 0xFFFFFFFF, Kind.ROUNDED_2),
    BANNER_BG("banner_bg", c -> 0xFFFFFFFF, Kind.ROUNDED_6),
    TIME_SEP_BG("time_sep_bg", c -> ChatBubbleTheme.alphaBlend(c.toastBg(), 255), Kind.SOLID),
    STRONG_HINT_BG("strong_hint_bg", c -> 0xFF000000, Kind.SOLID),
    // 常用语面板滚动条：白色纹理 × tint 动态着色（主题色 + hover 态），颜色变亮的行为由 tint 控制
    QUICK_SCROLLBAR_TRACK("quick_scrollbar_track", c -> 0xFFFFFFFF, Kind.SOLID),
    QUICK_SCROLLBAR_THUMB("quick_scrollbar_thumb", c -> 0xFFFFFFFF, Kind.SOLID);

    /** 默认纹理生成方式：SOLID=1×1 纯色拉伸；ROUNDED_n=n 像素半径圆角纹理（9-slice 四角恒定圆角）。 */
    public enum Kind {
        SOLID(0),
        ROUNDED_2(2),
        ROUNDED_4(4),
        ROUNDED_6(6);

        public final int radius;

        Kind(int radius) {
            this.radius = radius;
        }

        public boolean rounded() {
            return radius > 0;
        }
    }

    private final String path;
    private final Function<ChatBubbleTheme.Colors, Integer> themeColor;
    private final Kind kind;

    UiElement(String path, Function<ChatBubbleTheme.Colors, Integer> themeColor, Kind kind) {
        this.path = path;
        this.themeColor = themeColor;
        this.kind = kind;
    }

    /** 渲染/注册用的纹理 ID（不带 .png）。 */
    public Identifier rl(ChatBubbleTheme theme) {
        return Identifier.of("e33chat",
            "textures/gui/" + theme.name().toLowerCase() + "/" + path);
    }

    /** 资源包文件路径（带 .png），供 getResource 查询。 */
    public Identifier png(ChatBubbleTheme theme) {
        return Identifier.of(rl(theme).getNamespace(), rl(theme).getPath() + ".png");
    }

    /** 默认纹理烘焙的主题色（完整 ARGB）。 */
    public int themeColor(ChatBubbleTheme theme) {
        return themeColor.apply(theme.colors());
    }

    /** 默认纹理生成方式。 */
    public Kind kind() {
        return kind;
    }

    /** 默认纹理基准尺寸（9-slice border 按此等比缩放）。 */
    public static final int DEFAULT_TEX_SIZE = 16;
}
