package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.ChatBubbleTheme;
import java.util.function.Function;
import net.minecraft.resources.ResourceLocation;

/** UI 纹理元素：资源包路径 + 默认主题色。路径约定 assets/e33chat/textures/gui/{theme}/{path}.png */
public enum UiElement {
    PANEL_BG("panel_bg", c -> ChatBubbleTheme.alphaBlend(c.panelBg(), 255)),
    TITLE_BAR("title_bar", c -> c.titleBg()),
    BOTTOM_BAR("bottom_bar", c -> c.barBg()),
    SIDEBAR_BG("sidebar_bg", c -> c.sidebarBg()),
    DIVIDER("divider", c -> c.divider()),
    INPUT_BG("input_bg", c -> c.inputBg()),
    SCROLLBAR_TRACK("scrollbar_track", c -> ChatBubbleTheme.alphaBlend(c.scrollbar(), 255)),
    SCROLLBAR_THUMB("scrollbar_thumb", c -> ChatBubbleTheme.alphaBlend(c.scrollbar(), 255)),
    CONTEXT_MENU_BG("context_menu_bg", c -> c.contextBg()),
    POPUP_BG("popup_bg", c -> c.popupBg()),
    TOAST_BG("toast_bg", c -> ChatBubbleTheme.alphaBlend(c.toastBg(), 255)),
    WHISPER_BAR("whisper_bar", c -> c.whisperBar()),
    CONFIG_BG("config_bg", c -> c.configBg()),
    CONTENT_BG("content_bg", c -> c.barBg()),
    TIME_SEP_BG("time_sep_bg", c -> ChatBubbleTheme.alphaBlend(c.toastBg(), 255)),
    STRONG_HINT_BG("strong_hint_bg", c -> 0xFF000000),
    // 常用语面板滚动条：白色纹理 × tint 动态着色（主题色 + hover 态），颜色变亮的行为由 tint 控制
    QUICK_SCROLLBAR_TRACK("quick_scrollbar_track", c -> 0xFFFFFFFF),
    QUICK_SCROLLBAR_THUMB("quick_scrollbar_thumb", c -> 0xFFFFFFFF);

    private final String path;
    private final Function<ChatBubbleTheme.Colors, Integer> themeColor;

    UiElement(String path, Function<ChatBubbleTheme.Colors, Integer> themeColor) {
        this.path = path;
        this.themeColor = themeColor;
    }

    /** 渲染/注册用的纹理 ID（不带 .png）。 */
    public ResourceLocation rl(ChatBubbleTheme theme) {
        return ResourceLocation.fromNamespaceAndPath(ChatBubbleMod.MODID,
            "textures/gui/" + theme.name().toLowerCase() + "/" + path);
    }

    /** 资源包文件路径（带 .png），供 getResource 查询。 */
    public ResourceLocation png(ChatBubbleTheme theme) {
        return ResourceLocation.fromNamespaceAndPath(rl(theme).getNamespace(), rl(theme).getPath() + ".png");
    }

    /** 默认纹理烘焙的主题色（完整 ARGB）。 */
    public int themeColor(ChatBubbleTheme theme) {
        return themeColor.apply(theme.colors());
    }
}
