package com.niuqu.chatbubble;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

public class ChatSettingsMenu {
    private static final int W = 100;
    private static final int ROW_H = 18;
    private static final int COUNT = 4;

    boolean visible;

    public void render(GuiGraphics g, int mouseX, int mouseY,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            java.util.function.Function<String, ResourceLocation> iconTex) {
        if (!visible) return;
        int gearX = panelX + 4;
        int menuH = COUNT * ROW_H + 4;
        int px = gearX;
        int py = barTop - menuH - 4;

        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, py, W, menuH, 0f, 0f, 1, 1, 1, 1);
        g.renderOutline(px, py, W, menuH, c.divider());

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
            if (hover) g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                px + 1, ry, W - 2, ROW_H, 0f, 0f, 1, 1, 1, 1);
            ChatBubbleScreen.drawTextureIcon(g, icons[i], px + 3, ry + 2, 14);
            int maxTextW = W - 22;
            String label = font.plainSubstrByWidth(labels[i], maxTextW);
            g.drawString(font, Component.literal(label), px + 20, ry + 4, c.textPrimary(), false);
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
