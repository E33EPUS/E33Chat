package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatBubbleTheme;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.RoundRectRenderer;
import com.niuqu.chatbubble.UiLayout;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ChatMessageRenderer {

    public static final int AVATAR = 20;
    public static final int BUBBLE_PAD_X = 6;
    public static final int BUBBLE_PAD_Y = 4;
    static final int GAP = 6;
    static final int NAME_H = 10;
    static final int TIME_SEP_H = 14;

    private ChatMessageRenderer() {}

    // ---- Pure computation (testable) ----

    public static int msgHeight(ChatMessageStore.ChatMessage msg, Font font, int bubbleMaxW) {
        if (msg.isSystem()) {
            List<FormattedCharSequence> lines = wrapContent(msg.content(), font, 999);
            return lines.size() * font.lineHeight + 4;
        }
        List<FormattedCharSequence> lines = wrapContent(msg.content(), font, bubbleMaxW);
        int h = lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2 + NAME_H;
        if (msg.replyContent() != null) h += font.lineHeight + 7;
        return h;
    }

    public static String timeKey(LocalTime t, int interval) {
        if (interval <= 0) return "";
        if (interval == 1) return t.format(DateTimeFormatter.ofPattern("HH:mm"));
        int m = (t.getMinute() / interval) * interval;
        return String.format("%02d:%02d", t.getHour(), m);
    }

    public static int computeTotalH(List<ChatMessageStore.ChatMessage> messages,
                                     Font font, int bubbleMaxW, int interval) {
        int totalH = 0;
        String lastKey = null;
        for (var msg : messages) {
            totalH += msgHeight(msg, font, bubbleMaxW) + GAP;
            if (!msg.isSystem()) {
                String key = timeKey(msg.time(), interval);
                if (lastKey == null || !key.equals(lastKey)) { lastKey = key; }
            }
        }
        return totalH;
    }

    public static Style findClickStyle(Component c) {
        Style s = c.getStyle();
        if (s != null && s.getClickEvent() != null) return s;
        for (Component child : c.getSiblings()) {
            s = findClickStyle(child);
            if (s != null) return s;
        }
        return null;
    }

    public static int prefixWidth(FormattedCharSequence line, int count, Font font) {
        if (count <= 0) return 0;
        return font.width((FormattedCharSequence) sink -> {
            int[] left = {count};
            line.accept((i, st, cp) -> left[0]-- > 0 && sink.accept(i, st, cp));
            return true;
        });
    }

    public static List<FormattedCharSequence> wrapContent(Component c, Font font, int width) {
        List<Component> paras = new ArrayList<>();
        MutableComponent[] cur = {Component.empty()};
        c.visit((style, text) -> {
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    if (i > start) cur[0].append(Component.literal(text.substring(start, i)).withStyle(style));
                    paras.add(cur[0]);
                    cur[0] = Component.empty();
                    start = i + 1;
                }
            }
            if (start < text.length()) cur[0].append(Component.literal(text.substring(start)).withStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        paras.add(cur[0]);
        while (!paras.isEmpty() && paras.get(0).getString().isEmpty()) paras.remove(0);
        while (!paras.isEmpty() && paras.get(paras.size() - 1).getString().isEmpty())
            paras.remove(paras.size() - 1);
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component p : paras) out.addAll(font.split(p, width));
        if (out.isEmpty()) out.addAll(font.split(c, width));
        return out;
    }

    // ---- Rendering ----

    public record ClickableSpan(int x, int y, int w, int h, Style style) {}

    public static void renderLineWithClicks(GuiGraphics g, Font font, FormattedCharSequence line,
                                             int x, int y, int color,
                                             List<ClickableSpan> clickableSpans) {
        renderLineWithClicks(g, font, line, x, y, color, null, clickableSpans);
    }

    public static void renderLineWithClicks(GuiGraphics g, Font font, FormattedCharSequence line,
                                             int x, int y, int color,
                                             Style fallback,
                                             List<ClickableSpan> clickableSpans) {
        FormattedCharSequence decorated = sink -> line.accept((i, st, cp) ->
            sink.accept(i, st.getClickEvent() != null && !st.isUnderlined() ? st.withUnderlined(true) : st, cp));
        g.drawString(font, decorated, x, y, color, false);

        List<Style> styles = new ArrayList<>();
        line.accept((i, st, cp) -> { styles.add(st); return true; });

        int beforeCount = clickableSpans.size();
        int runStart = -1;
        Style runStyle = null;
        for (int idx = 0; idx <= styles.size(); idx++) {
            Style st = idx < styles.size() ? styles.get(idx) : null;
            boolean clickable = st != null && (st.getClickEvent() != null || st.getHoverEvent() != null);
            if (runStyle == null) {
                if (clickable) { runStart = idx; runStyle = st; }
            } else if (!clickable || !st.equals(runStyle)) {
                int x0 = prefixWidth(line, runStart, font);
                int x1 = prefixWidth(line, idx, font);
                clickableSpans.add(new ClickableSpan(x + x0, y, x1 - x0, font.lineHeight, runStyle));
                runStart = clickable ? idx : -1;
                runStyle = clickable ? st : null;
            }
        }
        if (clickableSpans.size() == beforeCount && fallback != null && fallback.getClickEvent() != null) {
            clickableSpans.add(new ClickableSpan(x, y, font.width(line), font.lineHeight,
                fallback.withUnderlined(true)));
        }
    }

    public static void renderTimeSeparator(GuiGraphics g, Font font, LocalTime time, int y,
                                            int panelX, int panelW,
                                            ChatBubbleTheme.Colors c) {
        String text = time.format(DateTimeFormatter.ofPattern("HH:mm"));
        int tw = font.width(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        g.fill(tx - 6, y + 2, tx + tw + 6, y + TIME_SEP_H - 2,
            ChatBubbleTheme.alphaBlend(c.toastBg(), 0x44));
        g.drawString(font, Component.literal(text), tx, y + 3, c.timeColor(), false);
    }

    public static void renderBubble(GuiGraphics g, Font font,
                                     ChatMessageStore.ChatMessage msg, int index,
                                     int baseY, int mouseX, int mouseY,
                                     int panelX, int panelW,
                                     int ownBubbleColor, int otherBubbleColor,
                                     int ownTextColor, int otherTextColor,
                                     boolean own, int cornerRadius,
                                     ChatBubbleTheme.Colors c,
                                     ResourceLocation skin,
                                     int searchHighlightIndex,
                                     int bubbleMaxW,
                                     List<int[]> bubbleRects,
                                     List<ClickableSpan> clickableSpans) {
        if (msg.isSystem()) {
            List<FormattedCharSequence> lines = wrapContent(msg.content(), font, panelW - ChatLayout.PAD * 2 - 20);
            int yy = baseY + 2;
            Style fb = findClickStyle(msg.content());
            int sysColor = c.textMuted();
            for (var line : lines) {
                int lw = font.width(line);
                renderLineWithClicks(g, font, line, panelX + (panelW - lw) / 2, yy, sysColor, fb, clickableSpans);
                yy += font.lineHeight;
            }
            return;
        }

        List<FormattedCharSequence> lines = wrapContent(msg.content(), font, bubbleMaxW);

        int textW = 0;
        for (var line : lines) textW = Math.max(textW, font.width(line));
        int bubbleW = textW + BUBBLE_PAD_X * 2;
        int bubbleH = lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2;

        int avatarX, bubbleX;
        if (own) {
            avatarX = panelX + panelW - ChatLayout.PAD - AVATAR;
            bubbleX = avatarX - 4 - bubbleW;
        } else {
            avatarX = panelX + ChatLayout.PAD;
            bubbleX = avatarX + AVATAR + 4;
        }

        int nameY = baseY;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - AVATAR - ChatLayout.PAD * 2 - 20;
            Component sn = msg.senderName();
            FormattedCharSequence nameSeq;
            if (font.width(sn) > maxNameW) {
                var cut = font.substrByWidth(sn, maxNameW - font.width("..."));
                nameSeq = net.minecraft.locale.Language.getInstance().getVisualOrder(
                    net.minecraft.network.chat.FormattedText.composite(cut,
                        net.minecraft.network.chat.FormattedText.of("...")));
            } else {
                nameSeq = sn.getVisualOrderText();
            }
            int nameW = font.width(nameSeq);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            g.drawString(font, nameSeq, startX, nameY, c.nameColor(), false);
        }

        int bubbleY = baseY + NAME_H;
        int avatarY = baseY;

        int bg = own ? ownBubbleColor : otherBubbleColor;
        int fg = own ? ownTextColor : otherTextColor;

        RoundRectRenderer.fill(g, bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH,
            cornerRadius, bg);

        Style fb = findClickStyle(msg.content());
        for (int li = 0; li < lines.size(); li++)
            renderLineWithClicks(g, font, lines.get(li), bubbleX + BUBBLE_PAD_X,
                bubbleY + BUBBLE_PAD_Y + li * font.lineHeight, fg, fb, clickableSpans);

        // Draw avatar
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(skin, avatarX, avatarY, 20, 20, 8.0F, 8.0F, 8, 8, 64, 64);
        int hatOff = 1;
        g.blit(skin, avatarX - hatOff, avatarY - hatOff, 22, 22, 40.0F, 8.0F, 8, 8, 64, 64);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = font.width(label);
            int labelX, labelY = bubbleY + (bubbleH - font.lineHeight) / 2;
            if (own) labelX = bubbleX - labelW - 3;
            else labelX = bubbleX + bubbleW + 3;
            g.drawString(font, Component.literal(label), labelX, labelY, c.duplicateLabel(), false);
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - ChatLayout.PAD * 2 - AVATAR - 24;
            String quoteText = "↳ " + msg.replySender() + ": " + msg.replyContent();
            String quoteDisplay = font.plainSubstrByWidth(quoteText, quoteMaxW - 10);
            if (!quoteDisplay.equals(quoteText)) quoteDisplay += "...";
            int quoteTextW = font.width(quoteDisplay);
            int quoteW = Math.min(quoteTextW + 8, quoteMaxW);
            int quoteH = font.lineHeight + 4;
            int quoteY = bubbleY + bubbleH + 3;
            int quoteX = own ? (bubbleX + bubbleW - quoteW) : bubbleX;
            if (quoteX < panelX + ChatLayout.PAD) quoteX = panelX + ChatLayout.PAD;
            if (quoteX + quoteW > panelX + panelW - ChatLayout.PAD)
                quoteW = panelX + panelW - ChatLayout.PAD - quoteX;
            RoundRectRenderer.fill(g, quoteX, quoteY, quoteX + quoteW, quoteY + quoteH, 3, c.contextHover());
            g.drawString(font, Component.literal(quoteDisplay), quoteX + 4, quoteY + 2, c.textSecondary(), false);
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});

        if (index == searchHighlightIndex)
            g.renderOutline(bubbleX - 1, bubbleY - 1, bubbleW + 2, bubbleH + 2,
                com.niuqu.chatbubble.ChatSearchPanel.HIGHLIGHT);
    }
}
