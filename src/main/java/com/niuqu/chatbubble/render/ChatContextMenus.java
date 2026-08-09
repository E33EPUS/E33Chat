package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.ChatBubbleTheme;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public final class ChatContextMenus {

    public static final int CTX_W = 80;
    public static final int CTX_ITEM_H = 18;

    private ChatContextMenus() {}

    public static int menuX(int contextX, int panelX, int panelW) {
        return Math.min(contextX, panelX + panelW - CTX_W - 2);
    }

    public static int menuY(int contextY, int menuH, int msgTop, boolean above) {
        int y = above ? contextY - menuH : contextY;
        if (y < msgTop) y = contextY + 4;
        return y;
    }

    public static boolean isOverItem(double mouseX, double mouseY,
                                     int menuX, int itemY, int itemH) {
        return mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= itemY && mouseY <= itemY + itemH;
    }

    public static void renderMessageMenu(GuiGraphics g, Font font, int mouseX, int mouseY,
                                          ChatBubbleTheme.Colors c, int panelX, int panelW,
                                          int msgTop, ResourceLocation copyIcon,
                                          ResourceLocation quoteIcon,
                                          int contextX, int contextY, float alpha) {
        int menuH = CTX_ITEM_H * 2 + 2;
        int mx = menuX(contextX, panelX, panelW);
        int my = menuY(contextY, menuH, msgTop, true);

        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.CONTEXT_MENU_BG), mx, my, CTX_W, menuH, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), mx, my, CTX_W, 1, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), mx, my + menuH - 1, CTX_W, 1, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), mx, my, 1, menuH, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), mx + CTX_W - 1, my, 1, menuH, alpha);

        boolean hoverCopy = isOverItem(mouseX, mouseY, mx, my, CTX_ITEM_H);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverCopy ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            mx + 1, my + 1, CTX_W - 2, CTX_ITEM_H - 1, alpha);
        drawIcon(g, copyIcon, mx + 5, my + 3, 12, alpha);
        g.drawString(font, Component.translatable("e33chat.context.copy"),
            mx + 22, my + 4, c.textPrimary(), false);

        g.fill(mx + 4, my + CTX_ITEM_H, mx + CTX_W - 4, my + CTX_ITEM_H + 1, c.closeHoverBg());

        boolean hoverQuote = isOverItem(mouseX, mouseY, mx, my + CTX_ITEM_H + 1, CTX_ITEM_H);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverQuote ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            mx + 1, my + CTX_ITEM_H + 1, CTX_W - 2, CTX_ITEM_H, alpha);
        drawIcon(g, quoteIcon, mx + 5, my + CTX_ITEM_H + 3, 12, alpha);
        g.drawString(font, Component.translatable("e33chat.context.quote"),
            mx + 22, my + CTX_ITEM_H + 5, c.textPrimary(), false);
    }

    public static void renderAvatarMenu(GuiGraphics g, Font font, int mouseX, int mouseY,
                                         ChatBubbleTheme.Colors c, int panelX, int panelW,
                                         int msgTop, ResourceLocation tpIcon,
                                         ResourceLocation whisperIcon,
                                         ResourceLocation blockIcon,
                                         boolean isBlocked,
                                         int contextAvatarX, int contextAvatarY,
                                         boolean useTpa, float alpha) {
        int menuH = CTX_ITEM_H * 3 + 4;
        int mx = menuX(contextAvatarX, panelX, panelW);
        int my = menuY(contextAvatarY, menuH, msgTop, true);

        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.CONTEXT_MENU_BG), mx, my, CTX_W, menuH, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), mx, my, CTX_W, 1, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), mx, my + menuH - 1, CTX_W, 1, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), mx, my, 1, menuH, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), mx + CTX_W - 1, my, 1, menuH, alpha);

        boolean hoverTp = isOverItem(mouseX, mouseY, mx, my, CTX_ITEM_H);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverTp ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            mx + 1, my + 1, CTX_W - 2, CTX_ITEM_H - 1, alpha);
        drawIcon(g, tpIcon, mx + 5, my + 3, 12, alpha);
        String tpKey = useTpa ? "e33chat.context.tpa" : "e33chat.context.tp";
        g.drawString(font, Component.translatable(tpKey), mx + 22, my + 4, c.textPrimary(), false);

        g.fill(mx + 4, my + CTX_ITEM_H + 1, mx + CTX_W - 4, my + CTX_ITEM_H + 2, c.closeHoverBg());

        boolean hoverWhisper = isOverItem(mouseX, mouseY, mx, my + CTX_ITEM_H + 2, CTX_ITEM_H);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverWhisper ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            mx + 1, my + CTX_ITEM_H + 2, CTX_W - 2, CTX_ITEM_H, alpha);
        drawIcon(g, whisperIcon, mx + 5, my + CTX_ITEM_H + 4, 12, alpha);
        g.drawString(font, Component.translatable("e33chat.context.whisper"),
            mx + 22, my + CTX_ITEM_H + 6, c.textPrimary(), false);

        g.fill(mx + 4, my + CTX_ITEM_H * 2 + 3, mx + CTX_W - 4, my + CTX_ITEM_H * 2 + 4, c.closeHoverBg());

        boolean hoverBlock = isOverItem(mouseX, mouseY, mx, my + CTX_ITEM_H * 2 + 4, CTX_ITEM_H);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverBlock ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            mx + 1, my + CTX_ITEM_H * 2 + 4, CTX_W - 2, CTX_ITEM_H, alpha);
        drawIcon(g, blockIcon, mx + 5, my + CTX_ITEM_H * 2 + 6, 12, alpha);
        g.drawString(font, Component.translatable(isBlocked ? "e33chat.context.unblock" : "e33chat.context.block"),
            mx + 22, my + CTX_ITEM_H * 2 + 8, c.textPrimary(), false);
    }

    private static void drawIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size, float alpha) {
        if (alpha <= 0.003f) return;
        if (size < 16) {
            // 同 ChatBars.drawIcon：采样内容区 14x14（偏移1,1）完整绘制，避免切掉图标右/下缘
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size,
                1f, 1f, 14, 14, 16, 16, alpha);
        } else {
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size,
                0f, 0f, size, size, size, size, alpha);
        }
    }
}
