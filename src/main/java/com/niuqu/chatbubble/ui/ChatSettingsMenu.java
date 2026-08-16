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
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.render.UiTokens;

public class ChatSettingsMenu {
    private static final int W = 100;
    private static final int ROW_H = 18;
    private static final int COUNT = 4;

    public boolean visible;

    /** Screen 注入的关闭请求钩子（播放关闭动画）；null 时直接隐藏（D07-6）。 */
    public Runnable closeRequest;

    private void requestClose() {
        if (closeRequest != null) closeRequest.run();
        else visible = false;
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

        // SDF 圆角弹层背景（D1）：阴影 + 1px 描边 + 圆角，颜色取 token 表语义色
        RoundRectRenderer.fillPanel(g, px, py, W, menuH, UiTokens.RADIUS_MEDIUM, c.divider(), c.titleBg(), alpha);

        Identifier[] icons = {
            iconTex.apply("search"), iconTex.apply("quick_chat"),
            iconTex.apply("theme"), iconTex.apply("settings")
        };
        String[] labels = {
            Text.translatable("e33chat.menu.search").getString(),
            Text.translatable("e33chat.menu.quick_chat").getString(),
            Text.translatable("e33chat.menu.theme").getString(),
            Text.translatable("e33chat.menu.settings").getString()
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
            g.drawText(font, label, px + 20, ry + 4, com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
        }
    }

    public int handleClick(int mx, int my, int panelX, int panelW, int barTop, int iconS) {
        if (!visible) return -1;
        int gearX = panelX + 4;
        int iconY = barTop + (ChatBubbleScreen.BAR_H - iconS) / 2;
        if (mx >= gearX && mx <= gearX + iconS && my >= iconY && my <= iconY + iconS) {
            requestClose();
            return -1;
        }

        int menuH = COUNT * ROW_H + 4;
        int px = gearX;
        int py = barTop - menuH - 4;

        if (mx < px || mx > px + W || my < py || my > py + menuH) {
            requestClose();
            return -1;
        }

        int row = (my - py - 2) / ROW_H;
        if (row >= 0 && row < COUNT) {
            requestClose();
            return row; // 0=search, 1=quick_chat, 2=theme, 3=settings
        }
        return -1;
    }
}
