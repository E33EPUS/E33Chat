package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import net.minecraft.util.Identifier;

/**
 * UI 纹理管理：仅负责按当前主题拼纹理 ID。
 * 渲染处 drawTexture 直接引用 Identifier，TextureManager.getTexture 无缓存时自动 new ResourceTexture
 * 从资源栈（用户资源包 > mod jar 内置 16×16 PNG）懒加载；F3+T 重载后自动重读新 PNG。
 */
public final class UiTextureManager {

    private UiTextureManager() {}

    /** 当前配置主题下元素的纹理 ID。 */
    public static Identifier rl(UiElement el) {
        return el.rl(currentTheme());
    }

    /** 指定主题下元素的纹理 ID（配置界面等固定主题场景使用）。*/
    public static Identifier rl(UiElement el, ChatBubbleTheme theme) {
        return el.rl(theme);
    }

    public static ChatBubbleTheme currentTheme() {
        return ChatBubbleClientSetup.config().theme().equalsIgnoreCase("light")
            ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
    }
}
