package com.niuqu.chatbubble.ui;
import com.niuqu.chatbubble.texture.UiTextureManager;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.ChatBubbleScreen;

import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

public class ChatEmojiPanel {
    private static final int PANEL_H = 132;
    private static final int TAB_H = 18;
    private static final int COLS = 9;
    private static final int SLOT = 18;
    private static final int KAO_ITEM_H = 13;
    private static final int KAO_COLS = 2;
    private static final int KAO_COL_W = 90;
    private static final int EMOTE_SLOT = 26;
    private static final int EMOTE_COLS = 5;

    // 面板宽度自适应：聊天面板按固定物理宽设计（6x 时 panelW 收缩到 ~166），
    // 表情面板若固定 170 逻辑宽会反超面板 → clamp 边界反转 → 溢出屏幕左边。
    // 收缩到 panelW-4（保证 clamp 右界 ≥ 左界），最小 100。
    private static int fitWidth(int natural, int panelW) {
        return Math.max(100, Math.min(natural, panelW - 4));
    }

    // 表情列数随实际宽度收缩（SLOT 不变）
    private static int gridCols(int pw) {
        return Math.max(1, (pw - 8) / SLOT);
    }

    // 弹层 x 夹在聊天面板内且不超屏幕左右（表情/快捷/搜索共用模式）
    private static int clampX(int px, int pw, int panelX, int panelW) {
        int screenW = net.minecraft.client.MinecraftClient.getInstance().getWindow().getScaledWidth();
        int max = Math.min(panelX + panelW - pw - 2, screenW - pw - 2);
        return MathHelper.clamp(px, Math.min(panelX + 2, max), max);
    }

    private static final String[] EMOTES = {
        "😀","😃","😄","😁","😆","😅","🤣","😂",
        "🙂","😉","😊","😇","🥰","😍","🤩","😘",
        "😋","😛","😜","🤪","😎","🤗","🤔","😐",
        "😢","😭","😤","😡","🥺","😴","😷","🤒",
        "🐱","🐶","🐼","🐨","🐰","🦊","🐸","🐵",
        "🐭","🐹","🐮","🦁","🐯","🐻","🐧","🐤",
        "🐴","🦄","🐝","🐞","🦋","🐙","🦀","🐠",
        "🐷","🐖",
        "❤️","🧡","💛","💚","💙","💜","🖤","💔",
        "💕","💖","💗","💘","💝","💟","❣️","💌",
        "👍","👎","👏","🙌","💪","🤝","👋","✌️",
        "🎮","🎯","🎨","🎵","🎶","🎤","🎧","🎼",
        "⭐","🌟","🔥","💧","🌈","❄️","🎉","🎊",
        "🍕","🍔","🌮","🍩","🍪","🎂","☕","🍺",
        "⬆️","⬇️","✅","❌","❓","❗","💤","💡",
        "💀","🗿","🤡","👀","💯","💢","💬","💭",
    };

    private static final String[] KAO = {
        "(｡•̀ᴗ-)✧","(๑˃̵ᴗ˂̵)و","(๑•̀ㅂ•́)و✧","(◍•ᴗ•◍)",
        "╰(*°▽°*)╯","(≧∇≦)ﾉ","(＾▽＾)","✧٩(ˊωˋ*)و✧",
        "ฅ^•ﻌ•^ฅ","(•ω•)","(￣▽￣*)","(⌒▽⌒)☆",
        "(o゜▽゜)o☆","＼(￣▽￣)／","(◔◡◔)","／(=✪ x ✪=)＼",
        "¯\\_(ツ)_/¯","(ー_ー゛)","(￢_￢)","(¬_¬)",
        "(⇀‸↼‶)","(｡ŏ_ŏ)","(・∀・)","_(:з」∠)_",
        "(╯°□°）╯︵ ┻━┻","(´;ω;｀)","Σ(°△°|||)","(◎ロ◎)",
        "(∪.∪ )...zzz",
    };

    public boolean visible;
    public int scroll;
    public int tab;

    /** Screen 注入的关闭请求钩子（播放关闭动画）；null 时直接隐藏（D07-6）。 */
    public Runnable closeRequest;

    private void requestClose() {
        if (closeRequest != null) closeRequest.run();
        else visible = false;
    }

    public void render(DrawContext g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop, int iconS, int pad, float alpha) {
        if (!visible) return;
        int a255 = (int) (255 * alpha);
        int sendX = panelX + panelW - pad - iconS + 2;

        boolean isKaomoji = tab == 1;
        boolean isCustom = tab == 2;
        int natural = isKaomoji ? KAO_COLS * KAO_COL_W + 8
            : isCustom ? EMOTE_COLS * EMOTE_SLOT + 8 : COLS * SLOT + 8;
        int pw = fitWidth(natural, panelW);
        int px = clampX(sendX + iconS / 2 - pw / 2, pw, panelX, panelW);
        int py = Math.max(2, barTop - PANEL_H - 4);

        String[] tabLabels = {
            Text.translatable("e33chat.emoji.tab_emoji").getString(),
            Text.translatable("e33chat.emoji.tab_kaomoji").getString(),
            Text.translatable("e33chat.emoji.tab_custom").getString()
        };
        int tabW = pw / tabLabels.length;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.TITLE_BAR),
            px, py, pw, TAB_H + 1, alpha);
        for (int t = 0; t < tabLabels.length; t++) {
            int tx = px + t * tabW;
            if (t == tab)
                com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
                    com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.INPUT_BG),
                    tx, py, tabW, TAB_H, alpha);
            String label = tabLabels[t];
            g.drawText(font, label,
                tx + tabW / 2 - font.getWidth(label) / 2, py + (TAB_H - font.fontHeight) / 2,
                com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
        }
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER),
            px, py + TAB_H, pw, 1, alpha);

        int cy = py + TAB_H + 1;
        int ch = PANEL_H - TAB_H - 1;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, cy, pw, py + PANEL_H - cy, alpha);
        g.drawBorder(px, py, pw, PANEL_H, ChatBubbleTheme.alphaBlend(c.divider(), a255));

        if (isKaomoji) {
            renderKaomojiList(g, mouseX, mouseY, font, c, px, cy, pw, ch, alpha);
        } else if (isCustom) {
            renderEmoteGrid(g, mouseX, mouseY, font, c, px, cy, pw, ch, alpha);
        } else {
            renderEmojiGrid(g, mouseX, mouseY, font, c, px, cy, pw, ch, gridCols(pw), alpha);
        }
    }

    private void renderEmoteGrid(DrawContext g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int px, int cy, int pw, int ch, float alpha) {
        int a255 = (int) (255 * alpha);
        java.util.List<java.io.File> emotes = EmoteStore.list();
        int cols = Math.max(1, (pw - 8) / EMOTE_SLOT);
        int n = emotes.size() + 1; // +1 add slot
        int rows = (n + cols - 1) / cols;
        int totalH = rows * EMOTE_SLOT + 4;
        int maxScroll = Math.max(0, totalH - ch + 4);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);

        g.enableScissor(px + 1, cy + 1, px + pw - 1, cy + ch - 1);
        int sy = cy + 2 - scroll;
        for (int i = 0; i < n; i++) {
            int col = i % cols;
            int row = i / cols;
            int ex = px + 4 + col * EMOTE_SLOT;
            int ey = sy + row * EMOTE_SLOT;
            if (ey + EMOTE_SLOT <= cy || ey >= cy + ch) continue;
            boolean hover = mouseX >= ex && mouseX <= ex + EMOTE_SLOT - 1
                && mouseY >= ey && mouseY <= ey + EMOTE_SLOT - 1;
            if (hover)
                com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
                    com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                    ex, ey, EMOTE_SLOT - 1, EMOTE_SLOT - 1, alpha);
            if (i < emotes.size()) {
                java.io.File f = emotes.get(i);
                net.minecraft.util.Identifier tex = EmoteStore.texture(f);
                if (tex != null)
                    com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex,
                        ex + 4, ey + 4, EMOTE_SLOT - 8, EMOTE_SLOT - 8, alpha);
                else
                    g.drawText(font, "?", ex + EMOTE_SLOT / 2 - 3,
                        ey + (EMOTE_SLOT - font.fontHeight) / 2,
                        com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(c.textMuted(), a255), false);
                if (hover) {
                    com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
                        com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CLOSE_BG),
                        ex + EMOTE_SLOT - 10, ey, 10, 10, alpha);
                    g.drawText(font, "✕", ex + EMOTE_SLOT - 8, ey + 1, c.closeText(), false);
                }
            } else {
                g.drawText(font, "+", ex + EMOTE_SLOT / 2 - 3,
                    ey + (EMOTE_SLOT - font.fontHeight) / 2,
                    com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
            }
        }
        g.disableScissor();
    }

    private void renderEmojiGrid(DrawContext g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int px, int cy, int pw, int ch, int cols, float alpha) {
        int a255 = (int) (255 * alpha);
        int rows = (EMOTES.length + cols - 1) / cols;
        int totalH = rows * SLOT + 4;
        int maxScroll = Math.max(0, totalH - ch + 4);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);

        g.enableScissor(px + 1, cy + 1, px + pw - 1, cy + ch - 1);
        int sy = cy + 2 - scroll;
        for (int i = 0; i < EMOTES.length; i++) {
            int col = i % cols;
            int row = i / cols;
            int ex = px + 4 + col * SLOT;
            int ey = sy + row * SLOT;
            if (ey + SLOT <= cy || ey >= cy + ch) continue;
            if (mouseX >= ex && mouseX <= ex + SLOT - 1
                && mouseY >= ey && mouseY <= ey + SLOT - 1)
                com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
                    com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                    ex, ey, SLOT - 1, SLOT - 1, alpha);
            String emoji = EMOTES[i];
            g.drawText(font, emoji,
                ex + SLOT / 2 - font.getWidth(emoji) / 2,
                ey + (SLOT - font.fontHeight) / 2, com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
        }
        g.disableScissor();
    }

    private void renderKaomojiList(DrawContext g, int mouseX, int mouseY,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int px, int cy, int pw, int ch, float alpha) {
        int a255 = (int) (255 * alpha);
        int kCols = KAO_COLS;
        int kColW = (pw - 8) / kCols;
        int totalH = ((KAO.length + kCols - 1) / kCols) * KAO_ITEM_H + 4;
        int maxScroll = Math.max(0, totalH - ch + 4);
        scroll = MathHelper.clamp(scroll, 0, maxScroll);

        g.enableScissor(px + 1, cy + 1, px + pw - 1, cy + ch - 1);
        int sy = cy + 2 - scroll;
        for (int i = 0; i < KAO.length; i++) {
            int col = i % kCols;
            int row = i / kCols;
            int ex = px + 4 + col * kColW;
            int ey = sy + row * KAO_ITEM_H;
            if (ey + KAO_ITEM_H <= cy || ey >= cy + ch) continue;
            if (mouseX >= ex && mouseX <= ex + kColW - 1
                && mouseY >= ey && mouseY <= ey + KAO_ITEM_H - 1)
                com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
                    com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                    ex, ey, kColW - 1, KAO_ITEM_H - 1, alpha);
            g.drawText(font, KAO[i],
                ex + 2, ey + (KAO_ITEM_H - font.fontHeight) / 2, com.niuqu.chatbubble.render.ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
        }
        g.disableScissor();
    }

    public String handleClick(int mx, int my,
            TextRenderer font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop, int iconS, int pad) {
        if (!visible) return null;
        int sendX = panelX + panelW - pad - iconS + 2;

        int iconY = barTop + (ChatBubbleScreen.BAR_H - iconS) / 2;
        int emojiIconX = sendX - iconS - 6;
        if (mx >= emojiIconX && mx <= emojiIconX + iconS && my >= iconY && my <= iconY + iconS) {
            requestClose();
            return "";
        }

        boolean isKaomoji = tab == 1;
        boolean isCustom = tab == 2;
        int natural = isKaomoji ? KAO_COLS * KAO_COL_W + 8
            : isCustom ? EMOTE_COLS * EMOTE_SLOT + 8 : COLS * SLOT + 8;
        int pw = fitWidth(natural, panelW);
        int px = clampX(sendX + iconS / 2 - pw / 2, pw, panelX, panelW);
        int py = Math.max(2, barTop - PANEL_H - 4);

        if (mx < px || mx > px + pw || my < py || my > py + PANEL_H) {
            requestClose();
            return null;
        }

        if (my < py + TAB_H) {
            int tabW = pw / 3;
            int t = (mx - px) / tabW;
            if (t >= 0 && t <= 2) { tab = t; scroll = 0; }
            return "";
        }

        int cy = py + TAB_H + 1;
        if (isKaomoji) {
            int cw = (pw - 8) / KAO_COLS;
            int col = (mx - px - 4) / cw;
            int row = (my - cy - 2 + scroll) / KAO_ITEM_H;
            int idx = row * KAO_COLS + col;
            if (idx >= 0 && idx < KAO.length) return KAO[idx];
        } else if (isCustom) {
            int cols = Math.max(1, (pw - 8) / EMOTE_SLOT);
            java.util.List<java.io.File> emotes = EmoteStore.list();
            int col = (mx - px - 4) / EMOTE_SLOT;
            int row = (my - cy - 2 + scroll) / EMOTE_SLOT;
            int idx = row * cols + col;
            if (idx < 0) return null;
            if (idx < emotes.size()) {
                java.io.File f = emotes.get(idx);
                int ex = px + 4 + col * EMOTE_SLOT;
                int ey = cy + 2 - scroll + row * EMOTE_SLOT;
                if (mx >= ex + EMOTE_SLOT - 10 && mx <= ex + EMOTE_SLOT
                    && my >= ey && my <= ey + 10)
                    return "@EMOTE_DEL:" + f.getAbsolutePath();
                return "@EMOTE:" + f.getAbsolutePath();
            }
            if (idx == emotes.size()) return "@EMOTE_ADD";
            return null;
        } else {
            int cols = gridCols(pw);
            int col = (mx - px - 4) / SLOT;
            int row = (my - cy - 2 + scroll) / SLOT;
            int idx = row * cols + col;
            if (idx >= 0 && idx < EMOTES.length) return EMOTES[idx];
        }
        return null;
    }

    public void handleScroll(double scrollY) {
        boolean isKaomoji = tab == 1;
        boolean isCustom = tab == 2;
        int totalH;
        if (isKaomoji) {
            totalH = ((KAO.length + KAO_COLS - 1) / KAO_COLS) * KAO_ITEM_H + 4;
        } else if (isCustom) {
            int cols = Math.max(1, (EMOTE_COLS * EMOTE_SLOT + 8 - 8) / EMOTE_SLOT);
            int n = EmoteStore.list().size() + 1;
            totalH = ((n + cols - 1) / cols) * EMOTE_SLOT + 4;
        } else {
            int rows = (EMOTES.length + COLS - 1) / COLS;
            totalH = rows * SLOT + 4;
        }
        int ch = PANEL_H - TAB_H - 1;
        int maxScroll = Math.max(0, totalH - ch + 4);
        scroll = MathHelper.clamp(scroll - (int) scrollY * 20, 0, maxScroll);
    }
}
