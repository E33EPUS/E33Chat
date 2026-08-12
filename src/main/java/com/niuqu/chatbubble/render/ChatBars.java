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
                                       ResourceLocation menuIcon, float alpha, float contentAlpha) {
        int a255 = (int) (255 * alpha);
        // Content (icons/text) follows only the open/close animation; panelOpacity
        // must not tint it (2.3.7 regression: permanent 80% opacity made icons
        // lighter than their PNG colour on light themes).
        int c255 = (int) (255 * contentAlpha);
        int ty = 0;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.TITLE_BAR), panelX, ty, panelW, ChatLayout.TITLE_H, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), panelX, ty + ChatLayout.TITLE_H, panelW, 1, alpha);

        int menuX = panelX + 3;
        int menuY = ty + (ChatLayout.TITLE_H - ICON_S) / 2;
        boolean hoverMenu = mouseX >= menuX && mouseX <= menuX + ICON_S
            && mouseY >= menuY && mouseY <= menuY + ICON_S;
        if (hoverMenu) com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.HOVER_BG), menuX - 1, menuY - 1, ICON_S + 2, ICON_S + 2, alpha);
        drawIcon(g, menuIcon, menuX, menuY, ICON_S, contentAlpha);

        int titleW = font.width(title);
        int titleX = UiLayout.centerX(panelX, panelW, titleW);
        int titleTextY = ty + (ChatLayout.TITLE_H - font.lineHeight) / 2;
        g.drawString(font, Component.literal(title), titleX, titleTextY, ChatBubbleTheme.alphaBlend(c.textPrimary(), c255), false);

        int timeW = font.width(time);
        g.drawString(font, Component.literal(time),
            panelX + panelW - ChatLayout.PAD - 20 - timeW,
            ty + (ChatLayout.TITLE_H - font.lineHeight) / 2, ChatBubbleTheme.alphaBlend(c.timeColor(), c255), false);

        int closeX = panelX + panelW - 18;
        int closeY = ty + 6;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12
            && mouseY >= closeY && mouseY <= closeY + 12;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverClose ? UiElement.CLOSE_HOVER : UiElement.CLOSE_BG),
            closeX, closeY, 12, 12, alpha);
        g.drawString(font, Component.literal("✕"), closeX + 6 - font.width("✕") / 2,
            closeY + 2, ChatBubbleTheme.alphaBlend(c.closeText(), c255), false);
    }

    public static void renderBottomBar(GuiGraphics g, Font font, int mouseX, int mouseY,
                                        ChatBubbleTheme.Colors c,
                                        int panelX, int panelW, int barTop, int screenH,
                                        int inputX, int inputY, int inputW, boolean inputFocused,
                                        boolean emojiPanelVisible,
                                        ResourceLocation settingsIcon,
                                        ResourceLocation emojiIcon,
                                        ResourceLocation sendIcon, float alpha, float contentAlpha) {
        int a255 = (int) (255 * alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.BOTTOM_BAR), panelX, barTop, panelW, screenH - barTop, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), panelX, barTop, panelW, 1, alpha);

        int iconY = barTop + (ChatLayout.BAR_H - ICON_S) / 2;

        int ibX = inputX;
        int ibY = inputY;
        int ibH = INPUT_H;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), ibX - 1, ibY - 1, inputW + 1, 1, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.INPUT_BG), ibX - 1, ibY, inputW + 1, ibH, alpha);

        boolean hoverInput = mouseX >= ibX - 1 && mouseX <= ibX + inputW
            && mouseY >= ibY && mouseY <= ibY + ibH;
        if (hoverInput || inputFocused)
            g.renderOutline(ibX - 1, ibY, inputW + 1, ibH, ChatBubbleTheme.alphaBlend(c.textMuted(), a255));

        int gearX = panelX + 4;
        int sendX = panelX + panelW - ChatLayout.PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;

        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverGear) com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.HOVER_BG), gearX - 1, iconY - 1, ICON_S + 2, ICON_S + 2, alpha);
        drawIcon(g, settingsIcon, gearX, iconY, ICON_S, contentAlpha);

        boolean hoverEmoji = mouseX >= emojiX && mouseX <= emojiX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverEmoji || emojiPanelVisible)
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.HOVER_BG), emojiX - 1, iconY - 1, ICON_S + 2, ICON_S + 2, alpha);
        drawIcon(g, emojiIcon, emojiX, iconY, ICON_S, contentAlpha);

        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverSend) com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.HOVER_BG), sendX - 1, iconY - 1, ICON_S + 2, ICON_S + 2, alpha);
        drawIcon(g, sendIcon, sendX, iconY, ICON_S, contentAlpha);
    }

    private static void drawIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size, float alpha) {
        if (alpha <= 0.003f) return;
        if (size < 16) {
            // 图标纹理约定 16x16（内容居中，四周 1px 透明边）。采样内容区 14x14（偏移1,1）
            // 完整绘制——窗口取 size(12) 会切掉内容右/下 2px（copy 双页右页被切）。
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size,
                1f, 1f, 14, 14, 16, 16, alpha);
        } else {
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size,
                0f, 0f, size, size, size, size, alpha);
        }
    }
}
