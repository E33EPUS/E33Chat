package com.niuqu.chatbubble.texture;

import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleTheme;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Map;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

/**
 * UI 纹理管理：资源包优先 → 代码生成 fallback。
 * 启动时把 dark/light 两套主题的全部元素注册进 TextureManager；
 * 渲染时按当前主题动态取 RL，主题切换即时生效（纹理都已注册）。
 * 同时记录每个纹理的尺寸（texSizes），NineSliceRenderer 据此推导 9-slice border
 * ——border = 短边/4，保证采样与贴图严格 1:1，任意尺寸贴图都不会放大失配。
 */
public final class UiTextureManager {

    private UiTextureManager() {}

    /** 已注册纹理的尺寸（短边像素）。 */
    private static final Map<Identifier, Integer> TEX_SIZES = new HashMap<>();

    /** 当前配置主题下元素的纹理 ID。 */
    public static Identifier rl(UiElement el) {
        return el.rl(currentTheme());
    }

    /** 指定主题下元素的纹理 ID（配置界面等固定主题场景使用）。*/
    public static Identifier rl(UiElement el, ChatBubbleTheme theme) {
        return el.rl(theme);
    }

    public static ChatBubbleTheme currentTheme() {
        String theme = ChatBubbleClientSetup.config().theme();
        return "light".equalsIgnoreCase(theme) ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
    }

    /** 纹理的 9-slice border：四角保留区 = 短边/4（1×1 纯色 → 0 → 纯拉伸）。 */
    public static int borderFor(Identifier tex) {
        Integer size = TEX_SIZES.get(tex);
        if (size == null || size <= 1) return 0;
        return size / 4;
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
        MinecraftClient mc = MinecraftClient.getInstance();
        Identifier id = el.rl(theme);
        try {
            var res = mc.getResourceManager().getResource(el.png(theme));
            if (res.isPresent()) {
                try (InputStream in = res.get().getInputStream()) {
                    NativeImage img = NativeImage.read(in);
                    TEX_SIZES.put(id, Math.min(img.getWidth(), img.getHeight()));
                    mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
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
                img.setColor(i % texSize, i / texSize, TextureGenerators.argbToAbgr(px[i]));
            }
            TEX_SIZES.put(id, texSize);
            mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
            return;
        }
        NativeImage img = new NativeImage(1, 1, false);
        img.setColor(0, 0, TextureGenerators.argbToAbgr(argb));
        TEX_SIZES.put(id, 1);
        mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
    }

    /** 圆角元素的生成半径：跟随用户配置（0 = 直角），无配置项的元素用固定值。 */
    private static int radiusFor(UiElement el) {
        var cfg = ChatBubbleClientSetup.config();
        switch (el) {
            case BUBBLE_BG: return cfg.bubbleCornerRadius();
            case BANNER_BG: return cfg.bannerCornerRadius();
            default: return el.kind().radius; // QUOTE_BG 等无配置项元素
        }
    }
}
