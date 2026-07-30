package com.niuqu.chatbubble;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.resources.ResourceLocation;

import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;
import java.util.ArrayList;
import java.util.List;

public class ChatBubbleHudOverlay {

    private static final int ICON_S = 16;
    // private_tip.png 经像素解码测得：红点为 4x4 实心块、bbox=x[6..9]y[6..9]、居中。
    // 故裁源只取这 4x4（SRC_*），nearest 整数倍放大到 TIP_DISP（4 的倍数才齐整，无台阶）。
    private static final int SRC_U = 6;
    private static final int SRC_V = 6;
    private static final int SRC_S = 4;
    private static final int TIP_DISP = 4;
    private static ChatBubbleTheme loadedTheme;

    private static ResourceLocation chatIconTex() {
        String theme = ChatBubbleConfig.THEME.get().name().toLowerCase();
        return ResourceLocation.fromNamespaceAndPath("e33chat", "textures/gui/" + theme + "/chat_icon");
    }

    private static void ensureIconLoaded() {
        var theme = ChatBubbleConfig.THEME.get();
        if (loadedTheme == theme) return;
        loadIconTexture();
        // HUD 未读角标复用聊天界面的 private_tip 纹理；没开过聊天界面时它尚未注册，
        // 这里随主题加载一并注册全套图标，避免角标画成 missing 纹理
        ChatBubbleScreen.loadIconTextures();
        loadedTheme = theme;
    }

    private static ChatBubbleTheme.Colors c() {
        return ChatBubbleConfig.THEME.get().colors();
    }

    public static void render(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) return;

        g.pose().pushPose();
        g.pose().translate(0, 0, 300);

        MentionNotificationBanner.INSTANCE.tick();
        if (mc.screen == null || mc.screen instanceof ChatBubbleScreen) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        }

        if (mc.screen == null) renderStrongHint(g);

        if (mc.screen != null) { g.pose().popPose(); return; }

        String keyName = mc.options.keyChat.getTranslatedKeyMessage().getString();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = 3;
        int iconY = screenH - ICON_S - 20;
        int textY = iconY + ICON_S + 1;

        // Message preview above icon: each line has its own lifetime (oldest fades first);
        // hidden while any screen is open, but the per-line countdown keeps ticking.
        List<ChatMessageStore.PreviewEntry> previews = ChatMessageStore.getPreviews();
        if (ChatBubbleConfig.PREVIEW_ENABLED.get() && !previews.isEmpty()) {
            int maxW = ChatBubbleConfig.PREVIEW_WIDTH.get();
            int lineH = mc.font.lineHeight;
            int gap = 2;

            List<FormattedText> displays = new ArrayList<>();
            int maxTextW = 0;
            int maxAlpha = 0;
            for (ChatMessageStore.PreviewEntry e : previews) {
                FormattedText trimmed;
                if (mc.font.width(e.text) > maxW - 4) {
                    var cut = mc.font.substrByWidth(e.text, maxW - 4 - mc.font.width("..."));
                    trimmed = FormattedText.composite(cut, FormattedText.of("..."));
                } else {
                    trimmed = e.text;
                }
                displays.add(trimmed);
                maxTextW = Math.max(maxTextW, mc.font.width(trimmed));
                int a = Animation.fadeIn(e.ticks, 10);
                if (a > maxAlpha) maxAlpha = a;
            }

            int px = x + ICON_S / 2 - maxTextW / 2;
            if (px < 2) px = 2;
            int bgX1 = px - 3;
            if (bgX1 < 0) bgX1 = 0;

            int bottomLineY = iconY - 5 - lineH;
            int topLineY = bottomLineY - (displays.size() - 1) * (lineH + gap);
            int bgAlpha = maxAlpha * 0xDD / 0xFF / 2;
            int bgColor = (bgAlpha << 24) | 0x000000;
            g.fill(bgX1, topLineY - 2, px + maxTextW + 3, bottomLineY + lineH + 2, bgColor);
            var lang = net.minecraft.locale.Language.getInstance();
            for (int i = displays.size() - 1; i >= 0; i--) {
                int lineY = bottomLineY - (displays.size() - 1 - i) * (lineH + gap);
                int lineAlpha = Animation.fadeIn(previews.get(i).ticks, 10);
                g.drawString(mc.font, lang.getVisualOrder(displays.get(i)), px, lineY, (lineAlpha << 24) | 0xFFFFFF, false);
            }
        }

        // Chat bubble icon (hidden if hide_chat_icon enabled)
        if (!ChatBubbleConfig.HIDE_CHAT_ICON.get()) {
            ensureIconLoaded();
            drawIcon(g, x, iconY);

            // 未读角标：裁出 4x4 红点 nearest 放大到 TIP_DISP，红点中心骑图标右上顶点 (x+ICON_S, iconY)，
            // 跳动沿 y 向下；不跳(wave=0)时红点中心精确在右上顶点。气泡横线在左/中，右上为框角空白，故不压文字。
            if (ChatBubbleConfig.RED_DOT_ENABLED.get() && ChatMessageStore.getUnreadCount() > 0) {
                double wave = Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 3;
                int tipX = x + ICON_S - TIP_DISP / 2;
                int tipY = iconY - TIP_DISP / 2 + (int) wave;
                drawScaledTip(g, tipX, tipY, TIP_DISP);
            }

            // Keybind text below icon
            String keyDisplay = "[" + keyName + "]";
            int keyW = mc.font.width(keyDisplay);
            int keyX = keyW > ICON_S ? x : x + (ICON_S - keyW) / 2;
            g.drawString(mc.font, keyDisplay, keyX, textY, 0xFFFFFFFF, false);
        }

        g.pose().popPose();
    }

    // On 1.21.1 screens draw over the HUD pass, so when a screen is open this is
    // invoked again from ScreenEvent.Render.Post to keep the hint visible on top
    public static void renderStrongHint(GuiGraphics g) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (mc.screen instanceof ChatBubbleScreen) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        }
        if (!ChatBubbleConfig.STRONG_HINT_ENABLED.get() && !ChatBubbleConfig.MENTION_BANNER_ENABLED.get()) return;
        Component hint = ChatMessageStore.getStrongHintText();
        if (hint == null) return;
        int ticks = ChatMessageStore.getStrongHintTicks();
        int screenW = mc.getWindow().getGuiScaledWidth();
        int hintW = mc.font.width(hint);
        int hintX = (screenW - hintW) / 2;
        int hintY = mc.getWindow().getGuiScaledHeight() - 22 - 30 - mc.font.lineHeight;
        int alpha = Animation.fadeInOut(ticks, 10, 40, 10);
        int bgAlpha = alpha / 2;
        int bgColor = (bgAlpha << 24) | 0x000000;
        g.fill(hintX - 6, hintY - 3, hintX + hintW + 6, hintY + mc.font.lineHeight + 3, bgColor);
        // Colors are baked into the hint Component (mention = yellow, system = its
        // own colors); pass white only as a fallback so embedded colors always win.
        g.drawString(mc.font, hint, hintX, hintY, (alpha << 24) | 0xFFFFFF, false);
    }

    public static boolean isMouseOverIcon(double mx, double my) {
        if (ChatBubbleConfig.HIDE_CHAT_ICON.get()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        int screenH = mc.getWindow().getGuiScaledHeight();
        int iconY = screenH - ICON_S - 20;
        return mx >= 3 && mx <= 3 + ICON_S && my >= iconY && my <= iconY + ICON_S + mc.font.lineHeight + 2;
    }

    private static void loadIconTexture() {
        try (java.io.InputStream in = ChatBubbleHudOverlay.class.getClassLoader()
                .getResourceAsStream("assets/e33chat/textures/gui/" + ChatBubbleConfig.THEME.get().name().toLowerCase() + "/chat_icon.png")) {
            if (in != null) {
                com.mojang.blaze3d.platform.NativeImage img = com.mojang.blaze3d.platform.NativeImage.read(in);
                net.minecraft.client.renderer.texture.DynamicTexture tex =
                    new net.minecraft.client.renderer.texture.DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(chatIconTex(), tex);
            }
        } catch (Exception e) { com.mojang.logging.LogUtils.getLogger().error("[e33chat] Failed to load HUD icon texture", e); }
    }

    private static void drawIcon(GuiGraphics g, int x, int y) {
        var mc = Minecraft.getInstance();
        AbstractTexture abstractTex;
        try {
            abstractTex = mc.getTextureManager().getTexture(chatIconTex());
        } catch (Exception e) {
            // Texture lost (F3+T resource reload), reload it
            loadIconTexture();
            abstractTex = mc.getTextureManager().getTexture(chatIconTex());
        }
        RenderSystem.setShaderTexture(0, abstractTex.getId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        g.blit(chatIconTex(), x, y, 0, 0, ICON_S, ICON_S, ICON_S, ICON_S);
    }

    // 裁源放大绘制红点：源取 SRC_U/SRC_V 起的 SRC_S×SRC_S（红点 bbox），nearest 拉伸到 disp×disp。
    // C 重载把“显示尺寸 disp”与“源尺寸 SRC_S”分开，UV=SRC_S/16 不越界，不会 wrap 碎裂。
    private static void drawScaledTip(GuiGraphics g, int x, int y, int disp) {
        ResourceLocation tex = ChatBubbleScreen.iconTex("private_tip");
        var tm = Minecraft.getInstance().getTextureManager();
        AbstractTexture abstractTex;
        try {
            abstractTex = tm.getTexture(tex);
        } catch (Exception e) {
            ChatBubbleScreen.loadIconTextures();
            abstractTex = tm.getTexture(tex);
        }
        RenderSystem.setShaderTexture(0, abstractTex.getId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        g.blit(tex, x, y, disp, disp, (float) SRC_U, (float) SRC_V, SRC_S, SRC_S, 16, 16);
    }
}
