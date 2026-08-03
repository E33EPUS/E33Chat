package com.niuqu.chatbubble;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;

public class ChatQuickChatPanel {
    private static final int W = 140;
    private static final int ROW_H = 14;
    private static final int MAX_VISIBLE = 8;

    boolean visible;
    int scrollOffset;

    public void render(DrawContext g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            TextFieldWidget input) {
        if (!visible) return;
        var phrases = ChatBubbleClientSetup.config().quickChatPhrases();
        int visiblePhrases = Math.min(phrases.size(), MAX_VISIBLE);
        int listH = visiblePhrases * ROW_H;
        int separatorH = visiblePhrases > 0 ? 4 : 0;
        int panelH = 8 + listH + separatorH + 20;

        // 高 GUI 缩放（6x）时 panelW 收缩到 ~100 < 固定宽 140 → 居中会左溢出屏幕。
        // clamp 到面板内：min>max 时 Mth.clamp 返回下限（panelX+2），不会反转溢出
        int px = MathHelper.clamp(panelX + panelW / 2 - W / 2, panelX + 2, panelX + panelW - W - 2);
        int py = barTop - panelH - 4;

        g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, py, W, panelH, 0f, 0f, 16, 16, 16, 16);
        g.drawBorder(px, py, W, panelH, c.divider());

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
            String display = font.trimToWidth(phrase, textMaxW);
            boolean hover = mouseX >= px + 4 && mouseX <= hoverRight
                && mouseY >= rowY && mouseY <= rowY + ROW_H;
            if (hover) g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                px + 4, rowY, hoverRight - (px + 4), ROW_H, 0f, 0f, 16, 16, 16, 16);
            g.drawText(font, display, px + 6, rowY + 2, c.textPrimary(), false);
            int delX = hoverRight - 13;
            int delY = rowY + 1;
            boolean hoverDel = mouseX >= delX && mouseX <= delX + 12 && mouseY >= delY && mouseY <= delY + 12;
            g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(hoverDel
                    ? com.niuqu.chatbubble.texture.UiElement.CLOSE_HOVER
                    : com.niuqu.chatbubble.texture.UiElement.CLOSE_BG),
                delX, delY, 12, 12, 0f, 0f, 16, 16, 16, 16);
            g.drawText(font, "✕", delX + 6 - font.getWidth("✕") / 2, delY + 2, c.closeText(), false);
        }

        int inputY = py + 4 + listH + separatorH + 4;
        int inputX = px + 4;
        int inputW = W - 10;
        int inputH = 14;
        g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.INPUT_BG),
            inputX, inputY, inputW, inputH, 0f, 0f, 16, 16, 16, 16);
        boolean hoverInput = mouseX >= inputX && mouseX <= inputX + inputW
            && mouseY >= inputY && mouseY <= inputY + inputH;
        if (hoverInput || input.isFocused())
            g.drawBorder(inputX, inputY, inputW, inputH, c.textMuted());
        if (input.getText().isEmpty() && !input.isFocused())
            g.drawText(font, Text.translatable("e33chat.quick_chat.placeholder").getString(),
                inputX + 2, inputY + 3, c.textMuted(), false);

        input.setX(inputX + 2);
        input.setWidth(inputW - 4);
        input.setY(inputY + 3);
        input.setHeight(inputH - 2);
        input.setVisible(true);
    }

    // 输入框几何判定（与 render/handleClick 同款公式）：点击在输入框区域内直接聚焦，
    // 不依赖 widget 点击命中链路（yarn/1.21.1 TextFieldWidget 点击不自动聚焦）
    public static boolean isInsideInput(int mx, int my, int panelX, int panelW, int barTop, int totalPhrases) {
        int visiblePhrases = Math.min(totalPhrases, MAX_VISIBLE);
        int listH = visiblePhrases * ROW_H;
        int separatorH = visiblePhrases > 0 ? 4 : 0;
        int panelH = 8 + listH + separatorH + 20;
        int px = MathHelper.clamp(panelX + panelW / 2 - W / 2, panelX + 2, panelX + panelW - W - 2);
        int py = barTop - panelH - 4;
        int inputX = px + 4;
        int inputY = py + 4 + listH + separatorH + 4;
        return mx >= inputX && mx <= inputX + W - 10 && my >= inputY && my <= inputY + 14;
    }

    public int handleClick(int mx, int my,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            TextFieldWidget input) {
        if (!visible) return -1;
        var phrases = ChatBubbleClientSetup.config().quickChatPhrases();
        int visiblePhrases = Math.min(phrases.size(), MAX_VISIBLE);
        int listH = visiblePhrases * ROW_H;
        int separatorH = visiblePhrases > 0 ? 4 : 0;
        int panelH = 8 + listH + separatorH + 20;

        // 高 GUI 缩放（6x）时 panelW 收缩到 ~100 < 固定宽 140 → 居中会左溢出屏幕。
        // clamp 到面板内：min>max 时 Mth.clamp 返回下限（panelX+2），不会反转溢出
        int px = MathHelper.clamp(panelX + panelW / 2 - W / 2, panelX + 2, panelX + panelW - W - 2);
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
                var list = new ArrayList<>(phrases);
                list.remove(i);
                ChatBubbleClientSetup.saveConfig(ChatBubbleClientSetup.config().withQuickChatPhrases(list));
                scrollOffset = Math.min(scrollOffset, Math.max(0, list.size() - MAX_VISIBLE));
                return -1;
            }
            if (mx >= px + 4 && mx <= hoverRight
                && my >= rowY && my <= rowY + ROW_H) {
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
        var phrases = ChatBubbleClientSetup.config().quickChatPhrases();
        int maxScroll = Math.max(0, phrases.size() - MAX_VISIBLE);
        scrollOffset = MathHelper.clamp(scrollOffset - (int) scrollY, 0, maxScroll);
    }
}
