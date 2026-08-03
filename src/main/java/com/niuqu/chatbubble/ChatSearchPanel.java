package com.niuqu.chatbubble;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;

import java.util.List;

public class ChatSearchPanel {
    static final int PANEL_W = 180;
    static final int PANEL_H = 22;
    static final int INPUT_H = 14;
    static final int HIGHLIGHT = 0xFFFFFF55;

    boolean visible;

    // 弹层 x 夹在聊天面板内且不超屏幕左右（与表情面板同一模式）——6x 时
    // panelW 收缩到 ~166 < 180，固定居中会溢出屏幕左边
    static int clampX(int px, int pw, int panelX, int panelW) {
        int screenW = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth();
        int max = Math.min(panelX + panelW - pw - 2, screenW - pw - 2);
        return net.minecraft.util.math.MathHelper.clamp(px, Math.min(panelX + 2, max), max);
    }

    private static int searchX(int panelX, int panelW) {
        return clampX(panelX + panelW / 2 - PANEL_W / 2, PANEL_W, panelX, panelW);
    }

    public void render(DrawContext g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            TextFieldWidget searchInput,
            List<Integer> searchMatches, int searchMatchIdx) {
        if (!visible) return;
        int px = searchX(panelX, panelW);
        int py = barTop - PANEL_H - 4;

        g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, py, PANEL_W, PANEL_H, 0f, 0f, 16, 16, 16, 16);
        g.drawBorder(px, py, PANEL_W, PANEL_H, c.divider());

        int inputX = px + 4;
        int inputY = py + 4;
        int inputW = PANEL_W - 8;

        String counter = "";
        int counterW = 0;
        if (!searchInput.getText().isEmpty()) {
            if (searchMatches.isEmpty())
                counter = Text.translatable("e33chat.search.no_match").getString();
            else
                counter = (searchMatchIdx + 1) + "/" + searchMatches.size();
            counterW = font.getWidth(counter) + 6;
        }

        g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.INPUT_BG),
            inputX, inputY, inputW, INPUT_H, 0f, 0f, 16, 16, 16, 16);

        boolean hoverInput = mouseX >= inputX && mouseX <= inputX + inputW
            && mouseY >= inputY && mouseY <= inputY + INPUT_H;
        if (hoverInput || searchInput.isFocused())
            g.drawBorder(inputX, inputY, inputW, INPUT_H, c.textMuted());

        if (!counter.isEmpty()) {
            g.drawText(font, counter, inputX + inputW - counterW, inputY + 3,
                searchMatches.isEmpty() ? c.textMuted() : c.textSecondary(), false);
        }

        int editW = inputW - 4 - counterW;
        searchInput.setX(inputX + 2);
        searchInput.setWidth(editW - 4);
        searchInput.setY(inputY + 3);
        searchInput.setHeight(INPUT_H - 2);
        searchInput.setVisible(true);

        if (searchInput.getText().isEmpty()) {
            String ph = Text.translatable("e33chat.search.placeholder").getString();
            g.drawText(font, ph, inputX + 2, inputY + 3, c.textMuted(), false);
        }
    }

    public boolean isClickOnPanel(int mx, int my, int panelX, int panelW, int barTop) {
        if (!visible) return false;
        int sx = searchX(panelX, panelW);
        int sy = barTop - PANEL_H - 4;
        return mx >= sx && mx <= sx + PANEL_W && my >= sy && my <= sy + PANEL_H;
    }
}
