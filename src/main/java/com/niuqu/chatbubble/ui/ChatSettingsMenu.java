package com.niuqu.chatbubble.ui;
import com.niuqu.chatbubble.texture.UiTextureManager;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.ChatBubbleScreen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.function.Function;

public class ChatSettingsMenu {
    private static final int W = 100;
    private static final int ROW_H = 18;
    private static final int COUNT = 5;
    /** Row index of the clear-history item. */
    private static final int CLEAR_ROW = 4;
    /** handleClick return: clear-history confirmed (second click). */
    public static final int ACTION_CLEAR = 4;
    /** handleClick return: clear-history first click on an empty history. */
    public static final int ACTION_CLEAR_EMPTY = -2;
    private static final int CLEAR_RED = 0xFFFF5555;
    /** Window in ms between the first click (arm) and the confirming second click. */
    private static final long ARM_MS = 1000;

    public boolean visible;

    /** Screen 注入的关闭请求钩子（播放关闭动画）；null 时直接隐藏（D07-6）。 */
    public Runnable closeRequest;

    /** Screen 注入：判断当前是否有可清空的历史；null 时视为有（跳过空态分支）。 */
    public java.util.function.BooleanSupplier hasHistory;

    // Two-click confirm state: first click on the clear row arms it and the label
    // turns red; a second click within ARM_MS executes, anything else cancels.
    private boolean clearArmed;
    private long clearArmedAt;

    private void requestClose() {
        if (closeRequest != null) closeRequest.run();
        else visible = false;
    }

    public void resetClearArmed() {
        clearArmed = false;
    }

    /** Expires the armed state after ARM_MS without a confirming click. */
    public void maybeExpire(long now) {
        if (clearArmed && now - clearArmedAt >= ARM_MS) clearArmed = false;
    }

    public void render(DrawContext g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop,
            Function<String, Identifier> iconTex, float alpha) {
        if (!visible) return;
        int a255 = (int) (255 * alpha);
        int gearX = panelX + 4;
        int menuH = COUNT * ROW_H + 4;
        int px = gearX;
        int py = barTop - menuH - 4;

        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, py, W, menuH, alpha);
        g.drawBorder(px, py, W, menuH, ChatBubbleTheme.alphaBlend(c.divider(), a255));

        Identifier[] icons = {
            iconTex.apply("search"), iconTex.apply("quick_chat"),
            iconTex.apply("theme"), iconTex.apply("settings"), iconTex.apply("trash")
        };
        String[] labels = {
            Text.translatable("e33chat.menu.search").getString(),
            Text.translatable("e33chat.menu.quick_chat").getString(),
            Text.translatable("e33chat.menu.theme").getString(),
            Text.translatable("e33chat.menu.settings").getString(),
            Text.translatable(clearArmed
                ? "e33chat.menu.clear_confirm" : "e33chat.menu.clear_history").getString()
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
            String label = font.trimToWidth(labels[i], maxTextW);
            int color = clearArmed && i == CLEAR_ROW
                ? ChatBubbleTheme.alphaBlend(CLEAR_RED, a255)
                : ChatBubbleTheme.alphaBlend(c.textPrimary(), a255);
            g.drawText(font, label, px + 20, ry + 4, color, false);
        }
    }

    public int handleClick(int mx, int my, int panelX, int panelW, int barTop, int iconS) {
        if (!visible) return -1;
        int gearX = panelX + 4;
        int iconY = barTop + (ChatBubbleScreen.BAR_H - iconS) / 2;
        if (mx >= gearX && mx <= gearX + iconS && my >= iconY && my <= iconY + iconS) {
            resetClearArmed();
            requestClose();
            return -1;
        }

        int menuH = COUNT * ROW_H + 4;
        int px = gearX;
        int py = barTop - menuH - 4;

        if (mx < px || mx > px + W || my < py || my > py + menuH) {
            resetClearArmed();
            requestClose();
            return -1;
        }

        int row = (my - py - 2) / ROW_H;
        if (row >= 0 && row < COUNT) {
            if (row == CLEAR_ROW) {
                if (clearArmed) {
                    // Confirming second click — close the menu and execute.
                    resetClearArmed();
                    requestClose();
                    return ACTION_CLEAR;
                }
                if (hasHistory != null && !hasHistory.getAsBoolean()) {
                    // Nothing to clear: keep the menu open, tell the screen to toast.
                    return ACTION_CLEAR_EMPTY;
                }
                // Arm the two-click confirm; the menu stays open.
                clearArmed = true;
                clearArmedAt = System.currentTimeMillis();
                return -1;
            }
            resetClearArmed();
            requestClose();
            return row; // 0=search, 1=quick_chat, 2=theme, 3=settings
        }
        return -1;
    }
}
