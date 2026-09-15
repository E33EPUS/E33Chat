package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import net.minecraft.resources.ResourceLocation;

/** UI 纹理元素：资源包路径。路径约定 assets/e33chat/textures/gui/{theme}/{path}.png，默认纹理为 jar 内置 16×16 PNG。 */
public enum UiElement {
    PANEL_BG("panel_bg"),
    TITLE_BAR("title_bar"),
    BOTTOM_BAR("bottom_bar"),
    SIDEBAR_BG("sidebar_bg"),
    DIVIDER("divider"),
    INPUT_BG("input_bg"),
    SCROLLBAR_TRACK("scrollbar_track"),
    SCROLLBAR_THUMB("scrollbar_thumb"),
    CONTEXT_MENU_BG("context_menu_bg"),
    POPUP_BG("popup_bg"),
    TOAST_BG("toast_bg"),
    WHISPER_BAR("whisper_bar"),
    CONFIG_BG("config_bg"),
    CONTENT_BG("content_bg"),
    // 常用语面板滚动条：白色纹理 × tint 动态着色（主题色 + hover 态），颜色变亮的行为由 tint 控制
    QUICK_SCROLLBAR_TRACK("quick_scrollbar_track"),
    QUICK_SCROLLBAR_THUMB("quick_scrollbar_thumb"),
    // 状态高亮
    HOVER_BG("hover_bg"),
    SIDEBAR_SELECTED("sidebar_selected"),
    SIDEBAR_HOVER("sidebar_hover"),
    CONTEXT_HOVER("context_hover"),
    CLOSE_BG("close_bg"),
    CLOSE_HOVER("close_hover");

    private final String path;

    UiElement(String path) {
        this.path = path;
    }

    /** 渲染/注册用的纹理 ID（带 .png——SimpleTexture 原样查资源，不自动补后缀）。 */
    public ResourceLocation rl(ChatBubbleTheme theme) {
        return new ResourceLocation(ChatBubbleMod.MODID,
            "textures/gui/" + theme.name().toLowerCase() + "/" + path + ".png");
    }
}
