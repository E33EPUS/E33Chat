package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.ChatBubbleTheme;
import java.util.function.Function;
import net.minecraft.util.Identifier;

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
    TOAST_BG("toast_bg", c -> c.toastBg()),
    WHISPER_BAR("whisper_bar", c -> c.whisperBar()),
    CONFIG_BG("config_bg", c -> c.configBg()),
    CONTENT_BG("content_bg", c -> c.barBg());

    private final String path;
    private final Function<ChatBubbleTheme.Colors, Integer> themeColor;

    UiElement(String path, Function<ChatBubbleTheme.Colors, Integer> themeColor) {
        this.path = path;
        this.themeColor = themeColor;
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
}
