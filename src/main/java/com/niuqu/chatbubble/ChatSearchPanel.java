package com.niuqu.chatbubble;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;

public class ChatSearchPanel {
    static final int PANEL_W = 180;
    static final int PANEL_H = 22;
    static final int INPUT_H = 14;
    public static final int HIGHLIGHT = 0xFFFFFF55;

    boolean visible;

    public void render(GuiGraphics g, int mouseX, int mouseY,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            net.minecraft.client.gui.components.EditBox searchInput,
            java.util.List<Integer> searchMatches, int searchMatchIdx) {
        if (!visible) return;
        int px = panelX + panelW / 2 - PANEL_W / 2;
        int py = barTop - PANEL_H - 4;

        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, py, PANEL_W, PANEL_H, 0f, 0f, 1, 1, 1, 1);
        g.renderOutline(px, py, PANEL_W, PANEL_H, c.divider());

        int inputX = px + 4;
        int inputY = py + 4;
        int inputW = PANEL_W - 8;

        String counter = "";
        int counterW = 0;
        if (!searchInput.getValue().isEmpty()) {
            if (searchMatches.isEmpty())
                counter = Component.translatable("e33chat.search.no_match").getString();
            else
                counter = (searchMatchIdx + 1) + "/" + searchMatches.size();
            counterW = font.width(counter) + 6;
        }

        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.INPUT_BG),
            inputX, inputY, inputW, INPUT_H, 0f, 0f, 1, 1, 1, 1);

        boolean hoverInput = mouseX >= inputX && mouseX <= inputX + inputW
            && mouseY >= inputY && mouseY <= inputY + INPUT_H;
        if (hoverInput || searchInput.isFocused())
            g.renderOutline(inputX, inputY, inputW, INPUT_H, c.textMuted());

        if (!counter.isEmpty()) {
            g.drawString(font, Component.literal(counter), inputX + inputW - counterW, inputY + 3,
                searchMatches.isEmpty() ? c.textMuted() : c.textSecondary(), false);
        }

        int editW = inputW - 4 - counterW;
        searchInput.setX(inputX + 2);
        searchInput.setWidth(editW - 4);
        searchInput.setY(inputY + 3);
        searchInput.setHeight(INPUT_H - 2);
        searchInput.setVisible(true);

        if (searchInput.getValue().isEmpty()) {
            String ph = Component.translatable("e33chat.search.placeholder").getString();
            g.drawString(font, Component.literal(ph), inputX + 2, inputY + 3, c.textMuted(), false);
        }
    }

    public boolean isClickOnPanel(int mx, int my, int panelX, int panelW, int barTop) {
        if (!visible) return false;
        int sx = panelX + panelW / 2 - PANEL_W / 2;
        int sy = barTop - PANEL_H - 4;
        return mx >= sx && mx <= sx + PANEL_W && my >= sy && my <= sy + PANEL_H;
    }
}
