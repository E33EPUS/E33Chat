package com.niuqu.chatbubble.texture;
import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleTheme;
import java.io.InputStream;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
public final class UiTextureManager {
    private UiTextureManager() {}
    public static Identifier rl(UiElement el) {
        return el.rl(currentTheme());
    }
    public static Identifier rl(UiElement el, ChatBubbleTheme theme) {
        return el.rl(theme);
    }
    public static ChatBubbleTheme currentTheme() {
        String theme = ChatBubbleClientSetup.config().theme();
        return "light".equalsIgnoreCase(theme) ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
    }
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
                    //#if MC >= 12105
                    mc.getTextureManager().registerTexture(id,
                        new NativeImageBackedTexture(() -> "ui_texture", NativeImage.read(in)));
                    //#else
                    //$$ mc.getTextureManager().registerTexture(id,
                    //$$     new NativeImageBackedTexture(NativeImage.read(in)));
                    //#endif
                    return;
                }
            }
        } catch (Exception e) {
            LogUtils.getLogger().warn("[e33chat] resource pack texture {} failed to load, using generated default",
                el.rl(theme), e);
        }
        int argb = el.themeColor(theme);
        NativeImage img = new NativeImage(1, 1, false);
        //#if MC >= 12102
        img.setColorArgb(0, 0, argb);
        //#else
        //$$ img.setColor(0, 0, TextureGenerators.argbToAbgr(argb));
        //#endif
        //#if MC >= 12105
        mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(() -> "ui_texture", img));
        //#else
        //$$ mc.getTextureManager().registerTexture(id, new NativeImageBackedTexture(img));
        //#endif
    }
}