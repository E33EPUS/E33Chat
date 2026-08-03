package com.niuqu.chatbubble;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;

import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;

public class ChatBubbleHudOverlay {

    private static final int ICON_S = 16;
    // private_tip.png 经像素解码测得：红点为 4x4 实心块、bbox=x[6..9]y[6..9]、居中。
    // 故裁源只取这 4x4（SRC_*），nearest 整数倍放大到 TIP_DISP（4 的倍数才齐整，无台阶）。
    private static final int SRC_U = 6;
    private static final int SRC_V = 6;
    private static final int SRC_S = 4;
    private static final int TIP_DISP = 4;

    private static ResourceLocation chatIconTex() {
        String theme = ChatBubbleConfig.THEME.get().name().toLowerCase();
        return ResourceLocation.fromNamespaceAndPath(ChatBubbleMod.MODID, "textures/gui/" + theme + "/chat_icon.png");
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
        if (mc.screen == null) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getGuiScaledWidth(),
                mc.getWindow().getGuiScaledHeight());
        }

        if (mc.screen != null) { g.pose().popPose(); return; }

        String keyName = mc.options.keyChat.getTranslatedKeyMessage().getString();
        int screenH = mc.getWindow().getGuiScaledHeight();
        int x = 3;
        int iconY = screenH - ICON_S - 20;
        int textY = iconY + ICON_S + 1;

        // Chat bubble icon (hidden if hide_chat_icon enabled)
        if (!ChatBubbleConfig.HIDE_CHAT_ICON.get()) {
            drawIcon(g, x, iconY);

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
            g.drawString(mc.font, keyDisplay, keyX, textY, c().textPrimary(), false);
        }

        g.pose().popPose();
    }

    public static boolean isMouseOverIcon(double mx, double my) {
        if (ChatBubbleConfig.HIDE_CHAT_ICON.get()) return false;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return false;
        int screenH = mc.getWindow().getGuiScaledHeight();
        int iconY = screenH - ICON_S - 20;
        return mx >= 3 && mx <= 3 + ICON_S && my >= iconY && my <= iconY + ICON_S + mc.font.lineHeight + 2;
    }

    private static void drawIcon(GuiGraphics g, int x, int y) {
        // getTexture 无缓存时自动 new SimpleTexture 懒加载（资源包可覆盖，F3+T 即时生效）
        RenderSystem.setShaderTexture(0, chatIconTex());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        g.blit(chatIconTex(), x, y, 0, 0, ICON_S, ICON_S, ICON_S, ICON_S);
        RenderSystem.disableBlend();
    }

    // 裁源放大绘制红点：源取 SRC_U/SRC_V 起的 SRC_S×SRC_S（红点 bbox），nearest 拉伸到 disp×disp。
    // C 重载把“显示尺寸 disp”与“源尺寸 SRC_S”分开，UV=SRC_S/16 不越界，不会 wrap 碎裂。
    private static void drawScaledTip(GuiGraphics g, int x, int y, int disp) {
        ResourceLocation tex = ChatBubbleScreen.iconTex("private_tip");
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        g.blit(tex, x, y, disp, disp, (float) SRC_U, (float) SRC_V, SRC_S, SRC_S, 16, 16);
        RenderSystem.disableBlend();
    }
}
