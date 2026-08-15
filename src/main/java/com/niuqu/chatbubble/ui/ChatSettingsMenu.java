package com.niuqu.chatbubble.ui;
import com.niuqu.chatbubble.texture.UiTextureManager;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.ChatBubbleTheme;
import com.niuqu.chatbubble.ChatBubbleScreen;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ChatSettingsMenu {
    private static final int W = 100;
    private static final int ROW_H = 18;
    private static final int COUNT = 4;

    public boolean visible;

    public void render(GuiGraphics g, int mouseX, int mouseY,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            java.util.function.Function<String, ResourceLocation> iconTex, float alpha) {
        if (!visible) return;
        int a255 = (int) (255 * alpha);
        int gearX = panelX + 4;
        int menuH = COUNT * ROW_H + 4;
        int px = gearX;
        int py = barTop - menuH - 4;

        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, py, W, menuH, alpha);
        g.renderOutline(px, py, W, menuH, ChatBubbleTheme.alphaBlend(c.divider(), a255));

        ResourceLocation[] icons = {
            iconTex.apply("search"), iconTex.apply("quick_chat"),
            iconTex.apply("theme"), iconTex.apply("settings")
        };
        String[] labels = {
            Component.translatable("e33chat.menu.search").getString(),
            Component.translatable("e33chat.menu.quick_chat").getString(),
            Component.translatable("e33chat.menu.theme").getString(),
            Component.translatable("e33chat.menu.settings").getString()
        };

        for (int i = 0; i < COUNT; i++) {
            int ry = py + 2 + i * ROW_H;
            boolean hover = mouseX >= px && mouseX <= px + W
                && mouseY >= ry && mouseY <= ry + ROW_H;
            if (hover) com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
                com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                px + 1, ry, W - 2, ROW_H, alpha);
            ChatBubbleScreen.drawTextureIconAlpha(g, icons[i], px + 3, ry + 2, 14, alpha);
            int maxTextW = W - 22;
            String label = font.plainSubstrByWidth(labels[i], maxTextW);
            g.drawString(font, Component.literal(label), px + 20, ry + 4, ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
        }
    }

    public int handleClick(int mx, int my, int panelX, int panelW, int barTop, int iconS) {
        if (!visible) return -1;
        int gearX = panelX + 4;
        int iconY = barTop + (ChatBubbleScreen.BAR_H - iconS) / 2;
        if (mx >= gearX && mx <= gearX + iconS && my >= iconY && my <= iconY + iconS) {
            visible = false;
            return -1;
        }

        int menuH = COUNT * ROW_H + 4;
        int px = gearX;
        int py = barTop - menuH - 4;

        if (mx < px || mx > px + W || my < py || my > py + menuH) {
            visible = false;
            return -1;
        }

        int row = (my - py - 2) / ROW_H;
        if (row >= 0 && row < COUNT) {
            visible = false;
            return row; // 0=search, 1=quick_chat, 2=theme, 3=settings
        }
        return -1;
    }
}
