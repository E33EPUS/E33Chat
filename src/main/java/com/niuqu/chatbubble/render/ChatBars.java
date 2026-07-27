package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.ChatBubbleTheme;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;

public final class ChatBars {

    public static final int ICON_S = 14;
    private static final int INPUT_H = 14;

    private ChatBars() {}

    public static void renderBottomBar(GuiGraphics g, Font font, int mouseX, int mouseY,
                                        ChatBubbleTheme.Colors c,
                                        int panelX, int panelW, int barTop, int screenH,
                                        int inputX, int inputY, int inputW, boolean inputFocused,
                                        boolean emojiPanelVisible,
                                        ResourceLocation settingsIcon,
                                        ResourceLocation emojiIcon,
                                        ResourceLocation sendIcon) {
        g.fill(panelX, barTop, panelX + panelW, screenH, c.barBg());
        g.fill(panelX, barTop, panelX + panelW, barTop + 1, c.divider());

        int iconY = barTop + (ChatLayout.BAR_H - ICON_S) / 2;

        int ibX = inputX;
        int ibY = inputY;
        int ibH = INPUT_H;
        g.fill(ibX - 1, ibY - 1, ibX + inputW, ibY, c.divider());
        g.fill(ibX - 1, ibY, ibX + inputW, ibY + ibH, c.inputBg());

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
        g.blit(tex, x, y, 0, 0, size, size, size, size);
    }
}
