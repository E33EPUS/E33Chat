package com.niuqu.chatbubble;

import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import net.minecraft.client.font.TextRenderer;
//#if MC >= 12109
import net.minecraft.client.gui.Click;
import net.minecraft.client.input.MouseInput;
//#endif
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
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

    public void render(Object g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            TextFieldWidget input) {
        if (!visible) return;
        var phrases = ChatBubbleClientSetup.config().quickChatPhrases();
        int visiblePhrases = Math.min(phrases.size(), MAX_VISIBLE);
        int listH = visiblePhrases * ROW_H;
        int separatorH = visiblePhrases > 0 ? 4 : 0;
        int panelH = 8 + listH + separatorH + 20;

        int px = panelX + panelW / 2 - W / 2;
        int py = barTop - panelH - 4;

        RenderHelper.drawTexture(g, UiTextureManager.rl(UiElement.CONTENT_BG),
            px, py, 0f, 0f, W, panelH, 1, 1);
        drawBorder(g, px, py, W, panelH, c.divider());

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
            RenderHelper.fill(g, trackX, trackTop, trackX + 3, trackBottom, (0x30 << 24) | trackRgb);
            int thumbH = Math.max(6, listH * MAX_VISIBLE / totalPhrases);
            int maxScrollOff = totalPhrases - MAX_VISIBLE;
            int travelRange = listH - thumbH;
            int thumbY = trackTop + (maxScrollOff > 0 ? scrollOffset * travelRange / maxScrollOff : 0);
            int thumbRgb = c.scrollbarHover() & 0x00FFFFFF;
            RenderHelper.fill(g, trackX, thumbY, trackX + 3, thumbY + thumbH, (0x70 << 24) | thumbRgb);
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
            if (hover) RenderHelper.fill(g, px + 4, rowY, hoverRight, rowY + ROW_H, c.iconHover());
            RenderHelper.drawText(g, font, display, px + 6, rowY + 2, c.textPrimary(), false);
            int delX = hoverRight - 13;
            int delY = rowY + 1;
            boolean hoverDel = mouseX >= delX && mouseX <= delX + 12 && mouseY >= delY && mouseY <= delY + 12;
            RenderHelper.fill(g, delX, delY, delX + 12, delY + 12, hoverDel ? c.closeHoverBg() : c.closeBg());
            RenderHelper.drawText(g, font, "\u2715", delX + 6 - font.getWidth("\u2715") / 2, delY + 2, c.closeText(), false);
        }

        int inputY = py + 4 + listH + separatorH + 4;
        int inputX = px + 4;
        int inputW = W - 10;
        int inputH = 14;
        RenderHelper.drawTexture(g, UiTextureManager.rl(UiElement.INPUT_BG),
            inputX, inputY, 0f, 0f, inputW, inputH, 1, 1);
        boolean hoverInput = mouseX >= inputX && mouseX <= inputX + inputW
            && mouseY >= inputY && mouseY <= inputY + inputH;
        if (hoverInput || input.isFocused())
            drawBorder(g, inputX, inputY, inputW, inputH, c.textMuted());
        if (input.getText().isEmpty() && !input.isFocused())
            RenderHelper.drawText(g, font, com.niuqu.chatbubble.Txt.translatable("e33chat.quick_chat.placeholder").getString(),
                inputX + 2, inputY + 3, c.textMuted(), false);

        input.setX(inputX + 2);
        input.setWidth(inputW - 4);
        GuiCompat.setWidgetY(input, inputY + 3);
        //#if MC >= 12004
        input.setHeight(inputH - 2);
        //#endif
        input.setVisible(true);
    }

    public static boolean isInsideInput(int mx, int my, int panelX, int panelW, int barTop, int totalPhrases) {
        int visiblePhrases = Math.min(totalPhrases, MAX_VISIBLE);
        int listH = visiblePhrases * ROW_H;
        int separatorH = visiblePhrases > 0 ? 4 : 0;
        int panelH = 8 + listH + separatorH + 20;
        int px = net.minecraft.util.math.MathHelper.clamp(panelX + panelW / 2 - W / 2, panelX + 2, panelX + panelW - W - 2);
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

        int px = panelX + panelW / 2 - W / 2;
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

        //#if MC >= 12109
        if (input.mouseClicked(new Click((double)mx, (double)my, new MouseInput(0, 0)), false))
        //#else
        //$$ if (input.mouseClicked(mx, my, 0))
        //#endif
            return -2;
        return -1;
    }

    public void handleScroll(double scrollY) {
        var phrases = ChatBubbleClientSetup.config().quickChatPhrases();
        int maxScroll = Math.max(0, phrases.size() - MAX_VISIBLE);
        scrollOffset = MathHelper.clamp(scrollOffset - (int) scrollY, 0, maxScroll);
    }

    private static void drawBorder(Object g, int x, int y, int w, int h, int color) {
        RenderHelper.fill(g, x, y, x + w, y + 1, color);
        RenderHelper.fill(g, x, y + h - 1, x + w, y + h, color);
        RenderHelper.fill(g, x, y + 1, x + 1, y + h - 1, color);
        RenderHelper.fill(g, x + w - 1, y + 1, x + w, y + h - 1, color);
    }
}
