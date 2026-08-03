package com.niuqu.chatbubble;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.List;

public class ChatQuickChatPanel {
    private static final int W = 140;
    private static final int ROW_H = 14;
    private static final int MAX_VISIBLE = 8;

    boolean visible;
    int scrollOffset;

    public void render(GuiGraphics g, int mouseX, int mouseY,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            net.minecraft.client.gui.components.EditBox input) {
        if (!visible) return;
        var phrases = ChatBubbleConfig.QUICK_CHAT_PHRASES.get();
        int visiblePhrases = Math.min(phrases.size(), MAX_VISIBLE);
        int listH = visiblePhrases * ROW_H;
        int separatorH = visiblePhrases > 0 ? 4 : 0;
        int panelH = 8 + listH + separatorH + 20;

        // 高 GUI 缩放（6x）时 panelW 收缩到 ~100 < 固定宽 140 → 居中会左溢出屏幕。
        // clamp 到面板内：min>max 时 Mth.clamp 返回下限（panelX+2），不会反转溢出
        int px = Mth.clamp(panelX + panelW / 2 - W / 2, panelX + 2, panelX + panelW - W - 2);
        int py = barTop - panelH - 4;

        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, py, W, panelH, 0f, 0f, 1, 1, 1, 1);
        g.renderOutline(px, py, W, panelH, c.divider());

        // Scrollbar
        int totalPhrases = phrases.size();
        int phraseAreaRight = px + W - 4;
        boolean hasScrollbar = totalPhrases > MAX_VISIBLE;
        int textMaxW = (hasScrollbar ? W - 20 : W - 16) - 14;
        int hoverRight = hasScrollbar ? px + W - 8 : px + W - 4;
        if (hasScrollbar) {
            int trackX = phraseAreaRight;
            int trackTop = py + 4;
            int trackBottom = py + 4 + listH;
            int trackRgb = c.scrollbar() & 0x00FFFFFF;
            // 白色纹理 × tint 动态着色：颜色（主题色 + 透明度）由 tint 控制，纹理可覆盖
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawTinted(g,
                com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.QUICK_SCROLLBAR_TRACK),
                trackX, trackTop, 3, trackBottom - trackTop, (0x30 << 24) | trackRgb);
            int thumbH = Math.max(6, listH * MAX_VISIBLE / totalPhrases);
            int maxScrollOff = totalPhrases - MAX_VISIBLE;
            int travelRange = listH - thumbH;
            int thumbY = trackTop + (maxScrollOff > 0 ? scrollOffset * travelRange / maxScrollOff : 0);
            int thumbRgb = c.scrollbarHover() & 0x00FFFFFF;
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawTinted(g,
                com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.QUICK_SCROLLBAR_THUMB),
                trackX, thumbY, 3, thumbH, (0x70 << 24) | thumbRgb);
        }

        int listY = py + 4;
        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + MAX_VISIBLE, phrases.size());
        for (int i = startIdx; i < endIdx; i++) {
            String phrase = phrases.get(i);
            int rowY = listY + (i - startIdx) * ROW_H;
            String display = font.plainSubstrByWidth(phrase, textMaxW);
            boolean hover = mouseX >= px + 4 && mouseX <= hoverRight
                && mouseY >= rowY && mouseY <= rowY + ROW_H;
            if (hover) g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                px + 4, rowY, hoverRight - (px + 4), ROW_H, 0f, 0f, 1, 1, 1, 1);
            g.drawString(font, Component.literal(display), px + 6, rowY + 2, c.textPrimary(), false);
            int delX = hoverRight - 13;
            int delY = rowY + 1;
            boolean hoverDel = mouseX >= delX && mouseX <= delX + 12 && mouseY >= delY && mouseY <= delY + 12;
            g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(hoverDel
                    ? com.niuqu.chatbubble.texture.UiElement.CLOSE_HOVER
                    : com.niuqu.chatbubble.texture.UiElement.CLOSE_BG),
                delX, delY, 12, 12, 0f, 0f, 1, 1, 1, 1);
            g.drawString(font, Component.literal("✕"), delX + 6 - font.width("✕") / 2, delY + 2, c.closeText(), false);
        }

        // Input box
        int inputY = py + 4 + listH + separatorH + 4;
        int inputX = px + 4;
        int inputW = W - 10;
        int inputH = 14;
        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.INPUT_BG),
            inputX, inputY, inputW, inputH, 0f, 0f, 1, 1, 1, 1);
        boolean hoverInput = mouseX >= inputX && mouseX <= inputX + inputW
            && mouseY >= inputY && mouseY <= inputY + inputH;
        if (hoverInput || input.isFocused())
            g.renderOutline(inputX, inputY, inputW, inputH, c.textMuted());
        if (input.getValue().isEmpty() && !input.isFocused())
            g.drawString(font, Component.translatable("e33chat.quick_chat.placeholder"),
                inputX + 2, inputY + 3, c.textMuted(), false);

        input.setX(inputX + 2);
        input.setWidth(inputW - 4);
        input.setY(inputY + 3);
        input.setHeight(inputH - 2);
        input.setVisible(true);
    }

    public int handleClick(int mx, int my,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            net.minecraft.client.gui.components.EditBox input) {
        if (!visible) return -1;
        var phrases = ChatBubbleConfig.QUICK_CHAT_PHRASES.get();
        int visiblePhrases = Math.min(phrases.size(), MAX_VISIBLE);
        int listH = visiblePhrases * ROW_H;
        int separatorH = visiblePhrases > 0 ? 4 : 0;
        int panelH = 8 + listH + separatorH + 20;

        // 高 GUI 缩放（6x）时 panelW 收缩到 ~100 < 固定宽 140 → 居中会左溢出屏幕。
        // clamp 到面板内：min>max 时 Mth.clamp 返回下限（panelX+2），不会反转溢出
        int px = Mth.clamp(panelX + panelW / 2 - W / 2, panelX + 2, panelX + panelW - W - 2);
        int py = barTop - panelH - 4;

        if (mx < px || mx > px + W || my < py || my > py + panelH) {
            visible = false;
            input.setVisible(false);
            return -1;
        }

        int hoverRight = phrases.size() > MAX_VISIBLE ? px + W - 8 : px + W - 4;
        int listY = py + 4;
        int startIdx = scrollOffset;
        int endIdx = Math.min(startIdx + MAX_VISIBLE, phrases.size());
        for (int i = startIdx; i < endIdx; i++) {
            int rowY = listY + (i - startIdx) * ROW_H;
            int delX = hoverRight - 13;
            int delY = rowY + 1;
            if (mx >= delX && mx <= delX + 12 && my >= delY && my <= delY + 12) {
                var list = new java.util.ArrayList<>(phrases);
                list.remove(i);
                ChatBubbleConfig.QUICK_CHAT_PHRASES.set(list);
                scrollOffset = Math.min(scrollOffset, Math.max(0, list.size() - MAX_VISIBLE));
                return -1;
            }
            if (mx >= px + 4 && mx <= hoverRight
                && my >= rowY && my <= rowY + ROW_H) {
                String phrase = phrases.get(i);
                visible = false;
                input.setVisible(false);
                return i;
            }
        }

        if (input.mouseClicked(mx, my, 0))
            return -2;
        return -1;
    }

    public void handleScroll(double scrollY) {
        var phrases = ChatBubbleConfig.QUICK_CHAT_PHRASES.get();
        int maxScroll = Math.max(0, phrases.size() - MAX_VISIBLE);
        scrollOffset = Mth.clamp(scrollOffset - (int) scrollY, 0, maxScroll);
    }
}
