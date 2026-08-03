package com.niuqu.chatbubble;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

public class ChatEmojiPanel {
    private static final int PANEL_H = 132;
    private static final int TAB_H = 18;
    private static final int COLS = 9;
    private static final int SLOT = 18;
    private static final int KAO_ITEM_H = 13;
    private static final int KAO_COLS = 2;
    private static final int KAO_COL_W = 90;

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
        int screenW = net.minecraft.client.Minecraft.getInstance().getWindow().getGuiScaledWidth();
        int max = Math.min(panelX + panelW - pw - 2, screenW - pw - 2);
        return Mth.clamp(px, Math.min(panelX + 2, max), max);
    }

    private static final String[] EMOTES = {
        "😀","😃","😄","😁","😆","😅","🤣","😂",
        "🙂","😉","😊","😇","🥰","😍","🤩","😘",
        "😋","😛","😜","🤪","😎","🤗","🤔","😐",
        "😢","😭","😤","😡","🥺","😴","😷","🤒",
        "🐱","🐶","🐼","🐨","🐰","🦊","🐸","🐵",
        "🐭","🐹","🐮","🦁","🐯","🐻","🐧","🐤",
        "🐴","🦄","🐝","🐞","🦋","🐙","🦀","🐠",
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

    boolean visible;
    int scroll;
    int tab;

    public void render(GuiGraphics g, int mouseX, int mouseY,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop, int iconS, int pad) {
        if (!visible) return;
        int sendX = panelX + panelW - pad - iconS + 2;

        boolean isKaomoji = tab == 1;
        int pw = fitWidth(isKaomoji ? KAO_COLS * KAO_COL_W + 8 : COLS * SLOT + 8, panelW);
        int px = clampX(sendX + iconS / 2 - pw / 2, pw, panelX, panelW);
        int py = Math.max(2, barTop - PANEL_H - 4);

        // Tab bar
        String[] tabLabels = {
            Component.translatable("e33chat.emoji.tab_emoji").getString(),
            Component.translatable("e33chat.emoji.tab_kaomoji").getString()
        };
        int tabW = pw / tabLabels.length;
        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.TITLE_BAR),
            px, py, pw, TAB_H + 1, 0f, 0f, 1, 1, 1, 1);
        for (int t = 0; t < tabLabels.length; t++) {
            int tx = px + t * tabW;
            if (t == tab)
                g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.INPUT_BG),
                    tx, py, tabW, TAB_H, 0f, 0f, 1, 1, 1, 1);
            String label = tabLabels[t];
            g.drawString(font, Component.literal(label),
                tx + tabW / 2 - font.width(label) / 2, py + (TAB_H - font.lineHeight) / 2, c.textPrimary(), false);
        }
        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER),
            px, py + TAB_H, pw, 1, 0f, 0f, 1, 1, 1, 1);

        // Content area
        int cy = py + TAB_H + 1;
        int ch = PANEL_H - TAB_H - 1;
        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONTENT_BG),
            px, cy, pw, py + PANEL_H - cy, 0f, 0f, 1, 1, 1, 1);
        g.renderOutline(px, py, pw, PANEL_H, c.divider());

        if (isKaomoji) {
            renderKaomojiList(g, mouseX, mouseY, font, c, px, cy, pw, ch);
        } else {
            renderEmojiGrid(g, mouseX, mouseY, font, c, px, cy, pw, ch, gridCols(pw));
        }
    }

    private void renderEmojiGrid(GuiGraphics g, int mouseX, int mouseY,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int px, int cy, int pw, int ch, int cols) {
        int rows = (EMOTES.length + cols - 1) / cols;
        int totalH = rows * SLOT + 4;
        int maxScroll = Math.max(0, totalH - ch + 4);
        scroll = Mth.clamp(scroll, 0, maxScroll);

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
                g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                    ex, ey, SLOT - 1, SLOT - 1, 0f, 0f, 1, 1, 1, 1);
            String emoji = EMOTES[i];
            g.drawString(font, Component.literal(emoji),
                ex + SLOT / 2 - font.width(emoji) / 2,
                ey + (SLOT - font.lineHeight) / 2, c.textPrimary(), false);
        }
        g.disableScissor();
    }

    private void renderKaomojiList(GuiGraphics g, int mouseX, int mouseY,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int px, int cy, int pw, int ch) {
        int kCols = 2;
        int kColW = (pw - 8) / kCols;
        int totalH = ((KAO.length + kCols - 1) / kCols) * KAO_ITEM_H + 4;
        int maxScroll = Math.max(0, totalH - ch + 4);
        scroll = Mth.clamp(scroll, 0, maxScroll);

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
                g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG),
                    ex, ey, kColW - 1, KAO_ITEM_H - 1, 0f, 0f, 1, 1, 1, 1);
            g.drawString(font, Component.literal(KAO[i]),
                ex + 2, ey + (KAO_ITEM_H - font.lineHeight) / 2, c.textPrimary(), false);
        }
        g.disableScissor();
    }

    public String handleClick(int mx, int my,
            net.minecraft.client.gui.Font font, ChatBubbleTheme.Colors c,
            int panelX, int panelW, int barTop, int iconS, int pad) {
        if (!visible) return null;
        int sendX = panelX + panelW - pad - iconS + 2;

        int iconY = barTop + (ChatBubbleScreen.BAR_H - iconS) / 2;
        int emojiIconX = sendX - iconS - 6;
        if (mx >= emojiIconX && mx <= emojiIconX + iconS && my >= iconY && my <= iconY + iconS) {
            visible = false;
            return "";
        }

        boolean isKaomoji = tab == 1;
        int pw = fitWidth(isKaomoji ? KAO_COLS * KAO_COL_W + 8 : COLS * SLOT + 8, panelW);
        int px = clampX(sendX + iconS / 2 - pw / 2, pw, panelX, panelW);
        int py = Math.max(2, barTop - PANEL_H - 4);

        if (mx < px || mx > px + pw || my < py || my > py + PANEL_H) {
            visible = false;
            return null;
        }

        if (my < py + TAB_H) {
            int tabW = pw / 2;
            int t = (mx - px) / tabW;
            if (t >= 0 && t <= 1) { tab = t; scroll = 0; }
            return "";
        }

        int cy = py + TAB_H + 1;
        if (isKaomoji) {
            int cw = (pw - 8) / KAO_COLS;
            int col = (mx - px - 4) / cw;
            int row = (my - cy - 2 + scroll) / KAO_ITEM_H;
            int idx = row * KAO_COLS + col;
            if (idx >= 0 && idx < KAO.length) return KAO[idx];
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
        int totalH;
        if (isKaomoji) {
            totalH = ((KAO.length + KAO_COLS - 1) / KAO_COLS) * KAO_ITEM_H + 4;
        } else {
            int rows = (EMOTES.length + COLS - 1) / COLS;
            totalH = rows * SLOT + 4;
        }
        int ch = PANEL_H - TAB_H - 1;
        int maxScroll = Math.max(0, totalH - ch + 4);
        scroll = Mth.clamp(scroll - (int) scrollY * 20, 0, maxScroll);
    }
}
