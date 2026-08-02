package com.niuqu.chatbubble.texture;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatBubbleTheme;
import java.io.InputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;

/**
 * UI 纹理管理：资源包优先 → 代码生成 fallback。
 * 启动时把 dark/light 两套主题的全部元素注册进 TextureManager；
 * 渲染时按当前主题动态取 RL，主题切换即时生效（纹理都已注册）。
 */
public final class UiTextureManager {

    private UiTextureManager() {}

    /** 当前配置主题下元素的纹理 ID。 */
    public static ResourceLocation rl(UiElement el) {
        return el.rl(currentTheme());
    }

    /** 指定主题下元素的纹理 ID（配置界面等固定主题场景使用）。*/
    public static ResourceLocation rl(UiElement el, ChatBubbleTheme theme) {
        return el.rl(theme);
    }

    public static ChatBubbleTheme currentTheme() {
        return ChatBubbleConfig.THEME.get();
    }

    /** 启动注册：所有主题 × 所有元素。 */
    public static void preloadAll() {
        for (ChatBubbleTheme theme : ChatBubbleTheme.values()) {
            for (UiElement el : UiElement.values()) {
                loadOrGenerate(theme, el);
            }
        }
    }

    private static void loadOrGenerate(ChatBubbleTheme theme, UiElement el) {
        Minecraft mc = Minecraft.getInstance();
        ResourceLocation id = el.rl(theme);
        try {
            var res = mc.getResourceManager().getResource(el.png(theme));
            if (res.isPresent()) {
                try (InputStream in = res.get().open()) {
                    mc.getTextureManager().register(id,
                        new DynamicTexture(NativeImage.read(in)));
                    return;
                }
            }
        } catch (Exception e) {
            LogUtils.getLogger().warn("[e33chat] resource pack texture {} failed to load, using generated default",
                el.png(theme), e);
        }
        int argb = el.themeColor(theme);
        if (el.kind().rounded()) {
            // 圆角纹理：尺寸 = 半径×4（9-slice 四角区=半径，中心 2×半径 双向拉伸）。
            // 半径跟随用户配置（气泡/横幅圆角配置项 0-10），配置变更后重注册即时生效。
            // 16 下限保证最小圆角（4px）也有 16×16 纹理；radius=0 退化为纯色（半径 0 圆角=直角）
            int radius = radiusFor(el);
            int texSize = Math.max(UiElement.DEFAULT_TEX_SIZE, radius * 4);
            int[] px = TextureGenerators.roundedRect(texSize, texSize, radius, argb, 0, 0);
            NativeImage img = new NativeImage(texSize, texSize, false);
            for (int i = 0; i < px.length; i++) {
                img.setPixelRGBA(i % texSize, i / texSize, TextureGenerators.argbToAbgr(px[i]));
            }
            mc.getTextureManager().register(id, new DynamicTexture(img));
            return;
        }
        NativeImage img = new NativeImage(1, 1, false);
        img.setPixelRGBA(0, 0, TextureGenerators.argbToAbgr(argb));
        mc.getTextureManager().register(id, new DynamicTexture(img));
    }

    /** 圆角元素的生成半径：跟随用户配置（0 = 直角），无配置项的元素用固定值。 */
    private static int radiusFor(UiElement el) {
        switch (el) {
            case BUBBLE_BG: return com.niuqu.chatbubble.ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get();
            case BANNER_BG: return com.niuqu.chatbubble.ChatBubbleConfig.BANNER_CORNER_RADIUS.get();
            default: return el.kind().radius; // QUOTE_BG 等无配置项元素
        }
    }
}
