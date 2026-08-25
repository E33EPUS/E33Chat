package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.ui.ChatSearchPanel;

import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.render.UiLayout;
import com.niuqu.chatbubble.image.BracketCodec;
import com.niuqu.chatbubble.image.ImageEntry;
import com.niuqu.chatbubble.image.ImageLoader;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.network.chat.ClickEvent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.HoverEvent;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraft.util.FormattedCharSequence;

import java.time.format.DateTimeFormatter;
import java.util.*;

public final class ChatMessageRenderer {

    public static final int BUBBLE_PAD_X = UiTokens.BUBBLE_PAD_X;
    public static final int BUBBLE_PAD_Y = UiTokens.BUBBLE_PAD_Y;
    static final int NAME_H = 10;
    static final int TIME_SEP_H = 14;

    // ---- Bubble size (px: target text height, 5-14, default 9 = vanilla) ----
    // Scales the bubble text and its frame; the sender-name row and the avatar
    // keep their size, so only the bubble content grows/shrinks.

    /** Current bubble text height in px from config, clamped to 5-14. */
    public static int bubbleSizePx() {
        return Math.max(5, Math.min(14, ChatBubbleConfig.BUBBLE_SIZE.get()));
    }

    /** Scale factor for a target text height vs the actual line height (pure, testable). */
    public static float scaleFor(int sizePx, int lineHeight) {
        return Math.max(5, Math.min(14, sizePx)) / (float) lineHeight;
    }

    /** Current bubble scale factor (target text height / vanilla-ish line height). */
    public static float bubbleScale(int lineHeight) {
        return scaleFor(bubbleSizePx(), lineHeight);
    }

    /** Wrap width in design units for a given scale (bigger bubbles wrap earlier; clamped to stay legible). */
    public static int scaledWrapWidth(int bubbleMaxW, float scale) {
        return Math.max(16, (int) (bubbleMaxW / scale));
    }

    /** Max bubble text width in design units for the current scale. */
    public static int bubbleWrapWidth(int bubbleMaxW, int lineHeight) {
        return scaledWrapWidth(bubbleMaxW, bubbleScale(lineHeight));
    }

    // Bubble-less image rendering: long-edge clamped to the panel width so
    // narrow windows/guiScale never push images off-screen; small images keep
    // their real size (never upscaled).
    public static final int EMOTE_MAX_SIZE = 32;

    private ChatMessageRenderer() {}

    // ---- Pure computation (testable) ----

    /** 组内时间窗：同一发送者间隔超过 5 分钟视为新组（07 §1.2 惯例）。 */
    static final long GROUP_TIME_MS = 5 * 60_000L;

    /** 两条消息是否同组：同一发送者（rawPlayerName 优先）+ 5 分钟窗口内 + 非系统消息。 */
    public static boolean isSameGroup(ChatMessageStore.ChatMessage prev, ChatMessageStore.ChatMessage msg) {
        if (prev == null || msg == null) return false;
        if (prev.isSystem() || msg.isSystem()) return false;
        String a = prev.rawPlayerName() != null && !prev.rawPlayerName().isEmpty()
            ? prev.rawPlayerName() : prev.senderName().getString();
        String b = msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty()
            ? msg.rawPlayerName() : msg.senderName().getString();
        if (!a.equals(b)) return false;
        return msg.time() - prev.time() <= GROUP_TIME_MS;
    }


    public static int msgHeight(ChatMessageStore.ChatMessage msg, Font font, int bubbleMaxW, int panelW) {
        if (msg.isSystem()) {
            // 与 renderBubble 系统分支同宽（panelW - PAD*2 - 20）：宽度不一致会让
            // 长系统消息高度算 1 行、实际画多行 → 下一条消息重叠
            List<FormattedCharSequence> lines = wrapContent(msg.content(), font, panelW - ChatLayout.PAD * 2 - 20);
            return lines.size() * font.lineHeight + 4;
        }
        BracketCodec.ParseResult parsed = parseImages(msg.content());
        if (!parsed.images().isEmpty()
                && parsed.images().stream().allMatch(BracketCodec.ImageRef::emote)
                && parsed.textWithoutImages().getString().isBlank()) {
            return NAME_H + font.lineHeight + 2 + EMOTE_MAX_SIZE + 2;
        }
        if (!parsed.images().isEmpty()) {
            List<FormattedCharSequence> imgLines = wrapContent(parsed.textWithoutImages(), font, bubbleMaxW);
            int textH = imgLines.size() * font.lineHeight;
            int imgH = 0;
            for (var ref : parsed.images()) imgH += imageEdgeHeight(ref.url(), panelW) + 2;
            int h = NAME_H + textH + imgH;
            if (msg.replyContent() != null) h += font.lineHeight + 7;
            return h;
        }
        float s = bubbleScale(font.lineHeight);
        List<FormattedCharSequence> lines = wrapContent(parsed.textWithoutImages(), font, bubbleWrapWidth(bubbleMaxW, font.lineHeight));
        double contentH = lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2;
        if (msg.replyContent() != null) contentH += font.lineHeight + 7;
        return NAME_H + (int) (contentH * s);
    }

    /** Bubble-side parse: bracket codes stripped (or placeholder when receiving is off). */
    public static BracketCodec.ParseResult parseImages(Component c) {
        if (!ChatBubbleConfig.RECEIVE_IMAGES.get()) {
            // Receiving disabled: bracket codes render as a plain-text
            // placeholder, never downloaded (the flood limiter stays untouched).
            return new BracketCodec.ParseResult(
                BracketCodec.toPlaceholderText(c), java.util.List.of());
        }
        return BracketCodec.parseOrExtract(c);
    }

    /** Height in px for one bubble-less image (state-dependent, panel-clamped, never upscaled). */
    public static int imageEdgeHeight(String url, int panelW) {
        int maxW = Math.max(80, panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 16);
        ImageEntry entry = ImageLoader.getOrLoad(url);
        if (entry != null && entry.state() == ImageEntry.State.LOADED
                && entry.width() > 0 && entry.height() > 0) {
            float ratio = Math.min((float) maxW / entry.width(),
                (float) maxW / entry.height());
            ratio = Math.min(1f, ratio);
            return Math.max(1, (int) (entry.height() * ratio));
        }
        return maxW;
    }

    /** Truncated sender-name sequence shared by bubble and bubble-less paths. */
    private static FormattedCharSequence nameSequence(Font font, Component sn, int maxNameW) {
        if (font.width(sn) <= maxNameW) return sn.getVisualOrderText();
        var cut = font.substrByWidth(sn, maxNameW - font.width("..."));
        return net.minecraft.locale.Language.getInstance().getVisualOrder(
            net.minecraft.network.chat.FormattedText.composite(cut,
                net.minecraft.network.chat.FormattedText.of("...")));
    }

    /** avatarSize head + hat (hat = size + 2), direction-independent (mirrors the bubble avatar). */
    private static void drawAvatar(GuiGraphics g, ResourceLocation skin, int avatarX, int avatarY, float alpha) {
        if (alpha > 0.003f) {
            int size = Appearance.avatarSize();
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, skin, avatarX, avatarY, size, size,
                8.0F, 8.0F, 8, 8, 64, 64, alpha);
            int hatSize = size + 2;
            int hatOff = (hatSize - size) / 2;
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, skin, avatarX - hatOff, avatarY - hatOff, hatSize, hatSize,
                40.0F, 8.0F, 8, 8, 64, 64, alpha);
        }
    }

    /** Bubble-less image message: name + avatar + optional text + images
     * (240px long-edge, aspect preserved, stacked vertically, direction-aligned). */
    public static void renderNoBubbleMessage(GuiGraphics g, Font font,
            ChatMessageStore.ChatMessage msg, int index, int baseY, boolean own, float alpha,
            BracketCodec.ParseResult parsed, List<FormattedCharSequence> lines,
            ChatBubbleTheme.Colors c, ResourceLocation skin, int panelX, int panelW,
            List<int[]> bubbleRects, List<ClickableSpan> clickableSpans,
            List<TextSpan> textSpans, ChatTextSelection selection, boolean showAvatar) {
        int avatarX = own ? panelX + panelW - ChatLayout.PAD - Appearance.avatarSize() : panelX + ChatLayout.PAD;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 20;
            FormattedCharSequence nameSeq = nameSequence(font, msg.senderName(), maxNameW);
            int nameW = font.width(nameSeq);
            int startX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - nameW) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
            renderLineWithClicks(g, font, nameSeq, startX, baseY,
                ChatBubbleTheme.alphaBlend(c.nameColor(), (int)(255 * alpha)), null,
                index, 0, TextSpan.KIND_NAME, 1f, c.panelBg(), clickableSpans, textSpans, selection);
        }

        // 头像顶与名字行顶对齐（2.3.16 曾改气泡顶对齐，实测回退老锚点）
        if (showAvatar) drawAvatar(g, skin, avatarX, baseY, alpha);

        int maxTextW = 0;
        for (var line : lines) maxTextW = Math.max(maxTextW, font.width(line));
        int textX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - maxTextW) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);

        int y = baseY + NAME_H;
        if (!lines.isEmpty()) {
            int fg = own
                ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR.get(), 0xFFFFFFFF)
                : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR.get(), c.textPrimary());
            Style fb = findRootClickStyle(msg.content());
            int fgA = ChatBubbleTheme.alphaBlend(fg, (int)(255 * alpha));
            for (int li = 0; li < lines.size(); li++)
                renderLineWithClicks(g, font, lines.get(li), textX, y + li * font.lineHeight, fgA, fb,
                    index, li, TextSpan.KIND_CONTENT, 1f, c.panelBg(), clickableSpans, textSpans, selection);
            y += lines.size() * font.lineHeight;
        }

        int maxImgW = Math.max(80, panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 16);
        for (var ref : parsed.images()) {
            int w = maxImgW, h = maxImgW;
            ImageEntry entry = ImageLoader.getOrLoad(ref.url());
            if (entry != null && entry.state() == ImageEntry.State.LOADED
                    && entry.width() > 0 && entry.height() > 0) {
                float ratio = Math.min((float) maxImgW / entry.width(),
                    (float) maxImgW / entry.height());
                ratio = Math.min(1f, ratio); // never upscale
                w = Math.max(1, (int) (entry.width() * ratio));
                h = Math.max(1, (int) (entry.height() * ratio));
            }
            int imgX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - w) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
            if (entry != null && entry.state() == ImageEntry.State.LOADED && entry.textureId() != null) {
                g.blit(entry.textureId(), imgX, y, w, h, 0, 0,
                    entry.width(), entry.height(), entry.width(), entry.height());
            } else {
                boolean limited = entry != null && entry.state() == ImageEntry.State.FAILED
                    && entry.failure() != null && entry.failure().contains("rate limited");
                String txt = limited
                    ? Component.translatable("e33chat.image.ratelimited").getString()
                    : entry != null && entry.state() == ImageEntry.State.FAILED
                        ? Component.translatable("e33chat.image.failed").getString()
                        : Component.translatable("e33chat.image.loading").getString();
                g.drawString(font, Component.literal(txt), imgX, y,
                    ChatBubbleTheme.alphaBlend(limited ? 0xFFFF5555 : c.textSecondary(), (int)(255 * alpha)), false);
            }
            // Open the URL in the system browser on click; hover shows the URL
            Style st = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ref.url()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(ref.url())));
            clickableSpans.add(new ClickableSpan(imgX, y, w, h, st));
            y += h + 2;
        }

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = font.width(label);
            int lx = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - labelW - 3) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP + 3);
            g.drawString(font, Component.literal(label), lx, baseY + NAME_H + 2,
                ChatBubbleTheme.alphaBlend(c.duplicateLabel(), (int)(255 * alpha)), false);
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - ChatLayout.PAD * 2 - Appearance.avatarSize() - 24;
            String quoteText = "↳ " + msg.replySender() + ": " + msg.replyContent();
            String quoteDisplay = font.plainSubstrByWidth(quoteText, quoteMaxW - 10);
            if (!quoteDisplay.equals(quoteText)) quoteDisplay += "...";
            int quoteW = Math.min(font.width(quoteDisplay) + 8, quoteMaxW);
            int quoteX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - quoteW) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
            if (quoteX < panelX + ChatLayout.PAD) quoteX = panelX + ChatLayout.PAD;
            if (quoteX + quoteW > panelX + panelW - ChatLayout.PAD)
                quoteW = panelX + panelW - ChatLayout.PAD - quoteX;
            RoundRectRenderer.fill(g, quoteX, y, quoteX + quoteW, y + font.lineHeight + 4, ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get(),
                ChatBubbleTheme.alphaBlend(c.contextHover(), (int)(255 * alpha)));
            renderLineWithClicks(g, font, Component.literal(quoteDisplay).getVisualOrderText(),
                quoteX + 4, y + 2, ChatBubbleTheme.alphaBlend(c.textSecondary(), (int)(255 * alpha)),
                null, index, 0, TextSpan.KIND_QUOTE, 1f, c.contextHover(), clickableSpans, textSpans, selection);
        }

        // Hit-test region for avatar clicks / context menus: the message span.
        bubbleRects.add(new int[]{own ? avatarX - 8 - maxTextW : avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP,
            baseY, Math.max(maxTextW, maxImgW), y - baseY, index});
    }

    /** QQ-style emote: bubble-less image, max 32px, aligned by direction. */
    public static void renderEmoteMessage(GuiGraphics g, Font font,
            ChatMessageStore.ChatMessage msg, int index, int baseY, boolean own, float alpha,
            ChatBubbleTheme.Colors c, ResourceLocation skin, int panelX, int panelW,
            List<int[]> bubbleRects, List<ClickableSpan> clickableSpans,
            List<TextSpan> textSpans, ChatTextSelection selection, boolean showAvatar) {
        BracketCodec.ParseResult parsed = parseImages(msg.content());
        if (parsed.images().isEmpty()) return;
        BracketCodec.ImageRef ref = parsed.images().get(0);

        int avatarX = own ? panelX + panelW - ChatLayout.PAD - Appearance.avatarSize() : panelX + ChatLayout.PAD;
        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 20;
            FormattedCharSequence nameSeq = nameSequence(font, msg.senderName(), maxNameW);
            int nameW = font.width(nameSeq);
            int startX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - nameW) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
            renderLineWithClicks(g, font, nameSeq, startX, baseY,
                ChatBubbleTheme.alphaBlend(c.nameColor(), (int)(255 * alpha)), null,
                index, 0, TextSpan.KIND_NAME, 1f, c.panelBg(), clickableSpans, textSpans, selection);
        }

        if (showAvatar) drawAvatar(g, skin, avatarX, baseY, alpha);

        int emoteY = baseY + NAME_H + 2;
        int maxE = Math.max(16, Math.min(EMOTE_MAX_SIZE, panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 16));
        int w = maxE, h = maxE;
        ImageEntry entry = ImageLoader.getOrLoad(ref.url());
        if (entry != null && entry.state() == ImageEntry.State.LOADED
                && entry.width() > 0 && entry.height() > 0) {
            float ratio = Math.min((float) maxE / entry.width(), (float) maxE / entry.height());
            ratio = Math.min(1f, ratio); // never upscale
            w = Math.max(1, (int) (entry.width() * ratio));
            h = Math.max(1, (int) (entry.height() * ratio));
        }
        int emoteX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - w) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
        if (entry != null && entry.state() == ImageEntry.State.LOADED && entry.textureId() != null) {
            g.blit(entry.textureId(), emoteX, emoteY, w, h, 0, 0,
                entry.width(), entry.height(), entry.width(), entry.height());
        } else {
            boolean limited = entry != null && entry.state() == ImageEntry.State.FAILED
                && entry.failure() != null && entry.failure().contains("rate limited");
            String txt = limited
                ? Component.translatable("e33chat.image.ratelimited").getString()
                : entry != null && entry.state() == ImageEntry.State.FAILED
                    ? Component.translatable("e33chat.image.failed").getString()
                    : Component.translatable("e33chat.image.loading").getString();
            g.drawString(font, Component.literal(txt), emoteX, emoteY,
                ChatBubbleTheme.alphaBlend(limited ? 0xFFFF5555 : c.textSecondary(), (int)(255 * alpha)), false);
        }
        Style st = Style.EMPTY
            .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ref.url()))
            .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Component.literal(ref.url())));
        clickableSpans.add(new ClickableSpan(emoteX, emoteY, w, h, st));
        bubbleRects.add(new int[]{emoteX, emoteY, w, h, index});
    }

    public static String timeKey(long timeMillis, int interval) {
        if (interval <= 0) return "";
        // Epoch-minute bucket: carries the date, so a message crossing midnight
        // gets a new key and its own separator automatically
        return String.valueOf(timeMillis / (interval * 60_000L));
    }

    // WeChat-style separator: same day "15:30", other day "07-31 15:30",
    // other year "2025-12-31 15:30"
    public static String formatTime(long timeMillis) {
        var dt = java.time.Instant.ofEpochMilli(timeMillis)
            .atZone(java.time.ZoneId.systemDefault()).toLocalDateTime();
        java.time.LocalDate today = java.time.LocalDate.now();
        if (dt.toLocalDate().equals(today)) return dt.format(DateTimeFormatter.ofPattern("HH:mm"));
        if (dt.getYear() == today.getYear()) return dt.format(DateTimeFormatter.ofPattern("MM-dd HH:mm"));
        return dt.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }

    public static Style findRootClickStyle(Component c) {
        // Only a click event on the root/wrapper style is a true "parent-level"
        // fallback. A click event buried in one sibling must NOT underline or
        // make clickable unrelated lines/segments; per-character styles already
        // carry inherited parent styles, so the recursive search is unnecessary
        // and caused whole-line underlines on system messages.
        Style s = c.getStyle();
        return s != null && s.getClickEvent() != null ? s : null;
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
        renderLineWithClicks(g, font, line, x, y, color, null, -1, -1,
            TextSpan.KIND_CONTENT, 1f, 0, clickableSpans, null, null);
    }

    public static void renderLineWithClicks(GuiGraphics g, Font font, FormattedCharSequence line,
                                             int x, int y, int color,
                                             Style fallback,
                                             List<ClickableSpan> clickableSpans) {
        renderLineWithClicks(g, font, line, x, y, color, fallback, -1, -1,
            TextSpan.KIND_CONTENT, 1f, 0, clickableSpans, null, null);
    }

    public static void renderLineWithClicks(GuiGraphics g, Font font, FormattedCharSequence line,
                                             int x, int y, int color, Style fallback,
                                             int messageIndex, int lineIndex, int kind, float scale,
                                             int backgroundRgb,
                                             List<ClickableSpan> clickableSpans,
                                             List<TextSpan> textSpans,
                                             ChatTextSelection selection) {
        List<Style> styles = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        line.accept((i, st, cp) -> {
            styles.add(st);
            textBuilder.appendCodePoint(cp);
            return true;
        });
        String text = textBuilder.toString();

        int[] range = null;
        int selBg = 0;
        int selFg = 0;
        if (textSpans != null && messageIndex >= 0) {
            int w = font.width(line);
            int cpCount = text.codePointCount(0, text.length());
            int[] prefixWidths = new int[cpCount + 1];
            int charOff = 0;
            for (int i = 0; i < cpCount; i++) {
                int cp = text.codePointAt(charOff);
                int charLen = Character.charCount(cp);
                prefixWidths[i + 1] = prefixWidths[i] + font.width(text.substring(charOff, charOff + charLen));
                charOff += charLen;
            }
            selBg = ChatTextSelection.selectionBgFor(backgroundRgb);
            selFg = ChatTextSelection.selectionFgFor(backgroundRgb);
            textSpans.add(new TextSpan(messageIndex, lineIndex, kind,
                x, y, w, font.lineHeight, text, scale, line, prefixWidths, selBg, selFg));
            if (selection != null) {
                range = selection.rangeFor(textSpans.get(textSpans.size() - 1));
                if (range != null) {
                    int hx = x + prefixWidths[range[0]];
                    int hw = Math.max(1, prefixWidths[range[1]] - prefixWidths[range[0]]);
                    g.fill(hx, y, hx + hw, y + font.lineHeight, selBg);
                }
            }
        }

        int beforeCount = clickableSpans.size();
        int runStart = -1;
        Style runStyle = null;
        List<int[]> clickableCharRanges = new ArrayList<>();
        for (int idx = 0; idx <= styles.size(); idx++) {
            Style st = idx < styles.size() ? styles.get(idx) : null;
            boolean clickable = st != null && (st.getClickEvent() != null || st.getHoverEvent() != null);
            if (runStyle == null) {
                if (clickable) { runStart = idx; runStyle = st; }
            } else if (!clickable || !st.equals(runStyle)) {
                int x0 = prefixWidth(line, runStart, font);
                int x1 = prefixWidth(line, idx, font);
                clickableSpans.add(new ClickableSpan(x + x0, y, x1 - x0, font.lineHeight, runStyle));
                clickableCharRanges.add(new int[]{runStart, idx});
                runStart = clickable ? idx : -1;
                runStyle = clickable ? st : null;
            }
        }

        // 父级 ClickEvent 合并：整行无可点击 span 时全行继承；已有 span 时，把缺
        // ClickEvent 的（仅 hover）span 补上父级 ClickEvent（issue #9，Mod 消息兼容）
        if (fallback != null && fallback.getClickEvent() != null) {
            if (clickableSpans.size() == beforeCount) {
                clickableSpans.add(new ClickableSpan(x, y, font.width(line), font.lineHeight,
                    fallback.withUnderlined(true)));
                clickableCharRanges.add(new int[]{0, styles.size()});
            } else {
                for (int i = beforeCount; i < clickableSpans.size(); i++) {
                    ClickableSpan s = clickableSpans.get(i);
                    if (s.style().getClickEvent() == null) {
                        clickableSpans.set(i, new ClickableSpan(s.x(), s.y(), s.w(), s.h(),
                            s.style().withClickEvent(fallback.getClickEvent())));
                    }
                }
            }
        }

        // 下划线按“字符级是否有 ClickEvent”逐字标注。line.accept 的 i 是每个
        // 样式段内部的字符下标（会重复从 0 开始），不能直接当 styles 下标；
        // 用运行序号 idx 映射到 hasClickEvent，避免错位/整行下划线。
        int styleLen = styles.size();
        boolean[] hasClickEvent = new boolean[styleLen];
        for (int ri = 0; ri < clickableCharRanges.size(); ri++) {
            int spanIdx = beforeCount + ri;
            if (spanIdx < clickableSpans.size()
                && clickableSpans.get(spanIdx).style().getClickEvent() != null) {
                int[] r = clickableCharRanges.get(ri);
                for (int i = r[0]; i < r[1]; i++) hasClickEvent[i] = true;
            }
        }

        int[] idx = {0};
        int[] selectionRange = range;
        int selectionFg = selFg;
        FormattedCharSequence decorated = sink -> line.accept((i, st, cp) -> {
            int pos = Math.min(idx[0]++, styleLen);
            boolean underline = pos < styleLen ? hasClickEvent[pos] : st.getClickEvent() != null;
            Style out = underline && !st.isUnderlined() ? st.withUnderlined(true) : st;
            if (selectionRange != null && pos >= selectionRange[0] && pos < selectionRange[1]) {
                out = out.withColor(net.minecraft.network.chat.TextColor.fromRgb(selectionFg));
            }
            return sink.accept(i, out, cp);
        });
        g.drawString(font, decorated, x, y, color, false);
    }

    public static void renderTimeSeparator(GuiGraphics g, Font font, long timeMillis, int y,
                                            int panelX, int panelW,
                                            ChatBubbleTheme.Colors c) {
        String text = formatTime(timeMillis);
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
                                     List<ClickableSpan> clickableSpans,
                                     List<TextSpan> textSpans,
                                     ChatTextSelection selection,
                                     float alpha, boolean showAvatar) {
        if (msg.isSystem()) {
            List<FormattedCharSequence> lines = wrapContent(msg.content(), font, panelW - ChatLayout.PAD * 2 - 20);
            int yy = baseY + 2;
            Style fb = findRootClickStyle(msg.content());
            int sysColor = ChatBubbleTheme.alphaBlend(c.textMuted(), (int)(255 * alpha));
            int beforeSys = clickableSpans.size();
            for (int li = 0; li < lines.size(); li++) {
                FormattedCharSequence line = lines.get(li);
                int lw = font.width(line);
                renderLineWithClicks(g, font, line, panelX + (panelW - lw) / 2, yy, sysColor, fb,
                    index, li, TextSpan.KIND_CONTENT, 1f, c.panelBg(), clickableSpans, textSpans, selection);
                yy += font.lineHeight;
            }
            for (int i = beforeSys; i < clickableSpans.size(); i++) {
                net.minecraft.network.chat.HoverEvent h = clickableSpans.get(i).style().getHoverEvent();
                if (h != null && h.getAction() == net.minecraft.network.chat.HoverEvent.Action.SHOW_ITEM) {
                    try {
                        net.minecraft.network.chat.HoverEvent.ItemStackInfo info = h.getValue(net.minecraft.network.chat.HoverEvent.Action.SHOW_ITEM);
                        if (info == null) continue;
                        ItemStack stack = info.getItemStack();
                        if (!stack.isEmpty()) {
                            float iconX = clickableSpans.get(i).x();
                            float iconY = clickableSpans.get(i).y();
                            g.pose().pushPose();
                            g.pose().translate(iconX, iconY, 0);
                            g.pose().scale(0.5f, 0.5f, 0.5f);
                            g.renderItem(stack, 0, 0);
                            g.pose().popPose();
                        }
                    } catch (Exception ignored) {}
                }
            }
            return;
        }

        BracketCodec.ParseResult parsed = parseImages(msg.content());
        List<FormattedCharSequence> lines = wrapContent(parsed.textWithoutImages(), font, bubbleMaxW);

        // E33Emote-only messages render bubble-less: max 32px, aligned by direction.
        if (!parsed.images().isEmpty()
                && parsed.images().stream().allMatch(BracketCodec.ImageRef::emote)
                && parsed.textWithoutImages().getString().isBlank()) {
            renderEmoteMessage(g, font, msg, index, baseY, own, alpha, c, skin,
                panelX, panelW, bubbleRects, clickableSpans, textSpans, selection, showAvatar);
            return;
        }
        // Any message carrying images renders bubble-less too (240px long-edge,
        // aspect preserved, stacked vertically, direction-aligned).
        if (!parsed.images().isEmpty()) {
            renderNoBubbleMessage(g, font, msg, index, baseY, own, alpha, parsed, lines, c, skin,
                panelX, panelW, bubbleRects, clickableSpans, textSpans, selection, showAvatar);
            return;
        }

        // Bubble path only: re-wrap at the scaled width so bigger bubbles fit fewer
        // characters per line (bubble-less emote/image paths above keep the unscaled lines).
        float s = bubbleScale(font.lineHeight);
        lines = wrapContent(parsed.textWithoutImages(), font, bubbleWrapWidth(bubbleMaxW, font.lineHeight));
        int textW = 0;
        for (var line : lines) textW = Math.max(textW, font.width(line));
        int bubbleW = (int) ((textW + BUBBLE_PAD_X * 2) * s);
        int bubbleH = (int) ((lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2) * s);

        int avatarX, bubbleX;
        if (own) {
            avatarX = panelX + panelW - ChatLayout.PAD - Appearance.avatarSize();
            bubbleX = avatarX - UiTokens.AVATAR_GAP - bubbleW;
        } else {
            avatarX = panelX + ChatLayout.PAD;
            bubbleX = avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP;
        }

        int nameY = baseY;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 20;
            FormattedCharSequence nameSeq = nameSequence(font, msg.senderName(), maxNameW);
            int nameW = font.width(nameSeq);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            renderLineWithClicks(g, font, nameSeq, startX, nameY,
                ChatBubbleTheme.alphaBlend(c.nameColor(), (int)(255 * alpha)), null,
                index, 0, TextSpan.KIND_NAME, 1f, c.panelBg(), clickableSpans, textSpans, selection);
        }

        int bubbleY = baseY + NAME_H;
        int avatarY = baseY;

        int bg = own ? ownBubbleColor : otherBubbleColor;
        int fg = own ? ownTextColor : otherTextColor;

        // 气泡背景：SDF 圆角（shader 数学，任何半径平滑；配置实时生效，不可被资源包覆盖）
        // 坐标已含 bubble_size 缩放，圆角半径同样按比例缩放，否则放大后圆角相对变小
        RoundRectRenderer.fill(g, bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH,
            cornerRadius * s, ChatBubbleTheme.alphaBlend(bg, (int)(255 * alpha)));

        Style fb = findRootClickStyle(msg.content());
        int fgA = ChatBubbleTheme.alphaBlend(fg, (int)(255 * alpha));
        for (int li = 0; li < lines.size(); li++) {
            // Bubble text is drawn at the pose origin; the translate must be unconditional
            // (s == 1 still needs the offset, only the scale is skipped), and clickable spans
            // are recorded in origin space then transformed back to screen space so
            // hit-testing and the visual position stay in sync at every bubble size.
            int textSX = bubbleX + (int)(BUBBLE_PAD_X * s);
            int textSY = bubbleY + (int)(BUBBLE_PAD_Y * s) + (int)(li * font.lineHeight * s);
            int beforeLine = clickableSpans.size();
            int beforeText = textSpans.size();
            g.pose().pushPose();
            g.pose().translate(textSX, textSY, 0);
            if (s != 1f) g.pose().scale(s, s, 1f);
            renderLineWithClicks(g, font, lines.get(li), 0, 0, fgA, fb,
                index, li, TextSpan.KIND_CONTENT, s, bg, clickableSpans, textSpans, selection);
            g.pose().popPose();
            for (int i = beforeLine; i < clickableSpans.size(); i++) {
                ClickableSpan sp = clickableSpans.get(i);
                clickableSpans.set(i, new ClickableSpan(
                    textSX + (int)(sp.x() * s),
                    textSY + (int)(sp.y() * s),
                    Math.max(1, (int)(sp.w() * s)),
                    Math.max(1, (int)(sp.h() * s)),
                    sp.style()));
            }
            for (int i = beforeText; i < textSpans.size(); i++) {
                TextSpan sp = textSpans.get(i);
                textSpans.set(i, sp.withPosition(
                    textSX + (int)(sp.x() * s),
                    textSY + (int)(sp.y() * s),
                    Math.max(1, (int)(sp.w() * s)),
                    Math.max(1, (int)(sp.h() * s))));
            }
        }

        // Draw avatar (per-element alpha: vanilla blit ignores setShaderColor)
        if (showAvatar) drawAvatar(g, skin, avatarX, avatarY, alpha);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = (int)(font.width(label) * s);
            int labelX, labelY = bubbleY + (bubbleH - (int)(font.lineHeight * s)) / 2;
            if (own) labelX = bubbleX - labelW - 3;
            else labelX = bubbleX + bubbleW + 3;
            g.pose().pushPose();
            g.pose().translate(labelX, labelY, 0);
            if (s != 1f) g.pose().scale(s, s, 1f);
            g.drawString(font, Component.literal(label), 0, 0, ChatBubbleTheme.alphaBlend(c.duplicateLabel(), (int)(255 * alpha)), false);
            g.pose().popPose();
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - ChatLayout.PAD * 2 - Appearance.avatarSize() - 24;
            String quoteText = "↳ " + msg.replySender() + ": " + msg.replyContent();
            String quoteDisplay = font.plainSubstrByWidth(quoteText, Math.max(8, (int)((quoteMaxW - 10) / s)));
            if (!quoteDisplay.equals(quoteText)) quoteDisplay += "...";
            int quoteTextW = (int)(font.width(quoteDisplay) * s);
            int quoteW = Math.min(quoteTextW + (int)(8 * s), quoteMaxW);
            int quoteH = Math.max(1, (int)((font.lineHeight + 4) * s));
            int quoteY = bubbleY + bubbleH + 3;
            int quoteX = own ? (bubbleX + bubbleW - quoteW) : bubbleX;
            if (quoteX < panelX + ChatLayout.PAD) quoteX = panelX + ChatLayout.PAD;
            if (quoteX + quoteW > panelX + panelW - ChatLayout.PAD)
                quoteW = panelX + panelW - ChatLayout.PAD - quoteX;
            // 引用块：SDF 圆角（随 bubble_size 缩放）
            RoundRectRenderer.fill(g, quoteX, quoteY, quoteX + quoteW, quoteY + quoteH, cornerRadius * s, ChatBubbleTheme.alphaBlend(c.contextHover(), (int)(255 * alpha)));
            int beforeText = textSpans.size();
            g.pose().pushPose();
            g.pose().translate(quoteX + (int)(4 * s), quoteY + (int)(2 * s), 0);
            if (s != 1f) g.pose().scale(s, s, 1f);
            renderLineWithClicks(g, font, Component.literal(quoteDisplay).getVisualOrderText(),
                0, 0, ChatBubbleTheme.alphaBlend(c.textSecondary(), (int)(255 * alpha)), null,
                index, 0, TextSpan.KIND_QUOTE, s, c.contextHover(), clickableSpans, textSpans, selection);
            g.pose().popPose();
            for (int i = beforeText; i < textSpans.size(); i++) {
                TextSpan sp = textSpans.get(i);
                textSpans.set(i, sp.withPosition(
                    quoteX + (int)(4 * s) + (int)(sp.x() * s),
                    quoteY + (int)(2 * s) + (int)(sp.y() * s),
                    Math.max(1, (int)(sp.w() * s)),
                    Math.max(1, (int)(sp.h() * s))));
            }
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});

        if (index == searchHighlightIndex)
            g.renderOutline(bubbleX - 1, bubbleY - 1, bubbleW + 2, bubbleH + 2,
                ChatSearchPanel.HIGHLIGHT);
    }
}
