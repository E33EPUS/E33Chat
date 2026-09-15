package com.niuqu.chatbubble.ui;
import com.niuqu.chatbubble.texture.UiTextureManager;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.render.ChatBubbleTheme;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;
import com.niuqu.chatbubble.texture.UiElement;

public class ChatSearchPanel {
    static final int PANEL_W = 180;
    static final int PANEL_H = 22;
    static final int INPUT_H = 14;
    public static final int HIGHLIGHT = 0xFFFFFF55;

    public boolean visible;

    // 弹层 x 夹在聊天面板内且不超屏幕左右（与表情面板同一模式）——6x 时
    // panelW 收缩到 ~166 < 180，固定居中会溢出屏幕左边
    static int clampX(int px, int pw, int panelX, int panelW) {
        int screenW = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth();
        int max = Math.min(panelX + panelW - pw - 2, screenW - pw - 2);
        return net.minecraft.util.math.MathHelper.clamp(px, Math.min(panelX + 2, max), max);
    }

    // 宽度也随聊天面板收缩（仅 clamp 不收缩时，180 > 166 依然左溢出 16px）
    private static int fitW(int panelWidth) {
        return Math.max(100, Math.min(PANEL_W, panelWidth - 4));
    }

    public void render(DrawContext g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            TextFieldWidget searchInput,
            List<Integer> searchMatches, int searchMatchIdx, float alpha) {
        if (!visible) return;
        int a255 = (int) (255 * alpha);
        int w = fitW(panelW);
        int px = clampX(panelX + panelW / 2 - w / 2, w, panelX, panelW);
        int py = barTop - PANEL_H - 4;

        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.CONTENT_BG),
            px, py, w, PANEL_H, alpha);
        g.drawBorder(px, py, w, PANEL_H, ChatBubbleTheme.alphaBlend(c.divider(), a255));

        int inputX = px + 4;
        int inputY = py + 4;
        int inputW = w - 8;

        String counter = "";
        int counterW = 0;
        if (!searchInput.getText().isEmpty()) {
            if (searchMatches.isEmpty())
                counter = Text.translatable("e33chat.search.no_match").getString();
            else
                counter = (searchMatchIdx + 1) + "/" + searchMatches.size();
            counterW = font.getWidth(counter) + 6;
        }

        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.INPUT_BG),
            inputX, inputY, inputW, INPUT_H, alpha);

        boolean hoverInput = mouseX >= inputX && mouseX <= inputX + inputW
            && mouseY >= inputY && mouseY <= inputY + INPUT_H;
        if (hoverInput || searchInput.isFocused())
            g.drawBorder(inputX, inputY, inputW, INPUT_H, com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(c.textMuted(), a255));

        if (!counter.isEmpty()) {
            int cc = searchMatches.isEmpty() ? c.textMuted() : c.textSecondary();
            g.drawText(font, counter, inputX + inputW - counterW, inputY + 3,
                com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(cc, a255), false);
        }

        int editW = inputW - 4 - counterW;
        searchInput.setX(inputX + 2);
        searchInput.setWidth(editW - 4);
        searchInput.setY(inputY + 3);
        searchInput.setHeight(INPUT_H - 2);
        searchInput.setVisible(true);

        if (searchInput.getText().isEmpty()) {
            String ph = Text.translatable("e33chat.search.placeholder").getString();
            g.drawText(font, ph, inputX + 2, inputY + 3, com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(c.textMuted(), a255), false);
        }
    }

    public boolean isClickOnPanel(int mx, int my, int panelX, int panelW, int barTop) {
        if (!visible) return false;
        int w = fitW(panelW);
        int sx = clampX(panelX + panelW / 2 - w / 2, w, panelX, panelW);
        int sy = barTop - PANEL_H - 4;
        return mx >= sx && mx <= sx + w && my >= sy && my <= sy + PANEL_H;
    }
}
