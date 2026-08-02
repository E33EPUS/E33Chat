package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.ChatBubbleTheme;
import com.niuqu.chatbubble.UiLayout;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ChatBars {

    public static final int ICON_S = 14;
    private static final int INPUT_H = 14;

    private ChatBars() {}

    public static void renderTitleBar(GuiGraphics g, Font font, int mouseX, int mouseY,
                                       ChatBubbleTheme.Colors c,
                                       int panelX, int panelW,
                                       String title, String time,
                                       ResourceLocation menuIcon) {
        int ty = 0;
        g.blit(UiTextureManager.rl(UiElement.TITLE_BAR), panelX, ty, panelW, ChatLayout.TITLE_H, 0f, 0f, 1, 1, 1, 1);
        g.blit(UiTextureManager.rl(UiElement.DIVIDER), panelX, ty + ChatLayout.TITLE_H, panelW, 1, 0f, 0f, 1, 1, 1, 1);

        int menuX = panelX + 3;
        int menuY = ty + (ChatLayout.TITLE_H - ICON_S) / 2;
        boolean hoverMenu = mouseX >= menuX && mouseX <= menuX + ICON_S
            && mouseY >= menuY && mouseY <= menuY + ICON_S;
        if (hoverMenu) g.fill(menuX - 1, menuY - 1, menuX + ICON_S + 1, menuY + ICON_S + 1, c.iconHover());
        drawIcon(g, menuIcon, menuX, menuY, ICON_S);

        int titleW = font.width(title);
        int titleX = UiLayout.centerX(panelX, panelW, titleW);
        int titleTextY = ty + (ChatLayout.TITLE_H - font.lineHeight) / 2;
        g.drawString(font, Component.literal(title), titleX, titleTextY, c.textPrimary(), false);

        int timeW = font.width(time);
        g.drawString(font, Component.literal(time),
            panelX + panelW - ChatLayout.PAD - 20 - timeW,
            ty + (ChatLayout.TITLE_H - font.lineHeight) / 2, c.timeColor(), false);

        int closeX = panelX + panelW - 18;
        int closeY = ty + 6;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12
            && mouseY >= closeY && mouseY <= closeY + 12;
        int closeBg = hoverClose ? c.closeHoverBg() : c.closeBg();
        g.fill(closeX, closeY, closeX + 12, closeY + 12, closeBg);
        g.drawString(font, Component.literal("✕"), closeX + 6 - font.width("✕") / 2,
            closeY + 2, c.closeText(), false);
    }

    public static void renderBottomBar(GuiGraphics g, Font font, int mouseX, int mouseY,
                                        ChatBubbleTheme.Colors c,
                                        int panelX, int panelW, int barTop, int screenH,
                                        int inputX, int inputY, int inputW, boolean inputFocused,
                                        boolean emojiPanelVisible,
                                        ResourceLocation settingsIcon,
                                        ResourceLocation emojiIcon,
                                        ResourceLocation sendIcon) {
        g.blit(UiTextureManager.rl(UiElement.BOTTOM_BAR), panelX, barTop, panelW, screenH - barTop, 0f, 0f, 1, 1, 1, 1);
        g.blit(UiTextureManager.rl(UiElement.DIVIDER), panelX, barTop, panelW, 1, 0f, 0f, 1, 1, 1, 1);

        int iconY = barTop + (ChatLayout.BAR_H - ICON_S) / 2;

        int ibX = inputX;
        int ibY = inputY;
        int ibH = INPUT_H;
        g.blit(UiTextureManager.rl(UiElement.DIVIDER), ibX - 1, ibY - 1, inputW + 1, 1, 0f, 0f, 1, 1, 1, 1);
        g.blit(UiTextureManager.rl(UiElement.INPUT_BG), ibX - 1, ibY, inputW + 1, ibH, 0f, 0f, 1, 1, 1, 1);

        boolean hoverInput = mouseX >= ibX - 1 && mouseX <= ibX + inputW
            && mouseY >= ibY && mouseY <= ibY + ibH;
        if (hoverInput || inputFocused)
            g.renderOutline(ibX - 1, ibY, inputW + 1, ibH, c.textMuted());

        int gearX = panelX + 4;
        int sendX = panelX + panelW - ChatLayout.PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;

        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverGear) g.fill(gearX - 1, iconY - 1, gearX + ICON_S + 1, iconY + ICON_S + 1, c.iconHover());
        drawIcon(g, settingsIcon, gearX, iconY, ICON_S);

        boolean hoverEmoji = mouseX >= emojiX && mouseX <= emojiX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverEmoji || emojiPanelVisible)
            g.fill(emojiX - 1, iconY - 1, emojiX + ICON_S + 1, iconY + ICON_S + 1, c.iconHover());
        drawIcon(g, emojiIcon, emojiX, iconY, ICON_S);

        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverSend) g.fill(sendX - 1, iconY - 1, sendX + ICON_S + 1, iconY + ICON_S + 1, c.iconHover());
        drawIcon(g, sendIcon, sendX, iconY, ICON_S);
    }

    private static void drawIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, tex);
        com.mojang.blaze3d.systems.RenderSystem.setShader(
            net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        if (size < 16) {
            // 图标纹理约定 16x16（内容居中，四周 1px 透明边）。size<16 时采样内容区
            // (偏移1,1) 1:1 绘制——全幅采样压缩会在 4x 缩放下把边缘半透明像素
            // 线性放大成“切割”痕迹；1:1 整数放大则干净。
            g.blit(tex, x, y, 1f, 1f, size, size, 16, 16);
        } else {
            g.blit(tex, x, y, 0, 0, size, size, size, size);
        }
    }
}
