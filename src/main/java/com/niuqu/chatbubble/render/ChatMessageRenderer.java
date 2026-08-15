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

    public static final int BUBBLE_PAD_X = 6;
    public static final int BUBBLE_PAD_Y = 4;
    static final int NAME_H = 10;
    static final int TIME_SEP_H = 14;

    // Bubble-less image rendering: long-edge clamped to the panel width so
    // narrow windows/guiScale never push images off-screen; small images keep
    // their real size (never upscaled).
    public static final int EMOTE_MAX_SIZE = 32;

    private ChatMessageRenderer() {}

    // ---- Pure computation (testable) ----

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
        List<FormattedCharSequence> lines = wrapContent(parsed.textWithoutImages(), font, bubbleMaxW);
        int h = lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2 + NAME_H;
        if (msg.replyContent() != null) h += font.lineHeight + 7;
        return h;
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
            List<int[]> bubbleRects, List<ClickableSpan> clickableSpans) {
        int avatarX = own ? panelX + panelW - ChatLayout.PAD - Appearance.avatarSize() : panelX + ChatLayout.PAD;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 20;
            FormattedCharSequence nameSeq = nameSequence(font, msg.senderName(), maxNameW);
            int nameW = font.width(nameSeq);
            int startX = own ? (avatarX - 8 - nameW) : (avatarX + Appearance.avatarSize() + 4);
            g.drawString(font, nameSeq, startX, baseY, ChatBubbleTheme.alphaBlend(c.nameColor(), (int)(255 * alpha)), false);
        }

        drawAvatar(g, skin, avatarX, baseY, alpha);

        int maxTextW = 0;
        for (var line : lines) maxTextW = Math.max(maxTextW, font.width(line));
        int textX = own ? (avatarX - 8 - maxTextW) : (avatarX + Appearance.avatarSize() + 4);

        int y = baseY + NAME_H;
        if (!lines.isEmpty()) {
            int fg = own
                ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR.get(), 0xFFFFFFFF)
                : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR.get(), c.textPrimary());
            Style fb = findClickStyle(msg.content());
            int fgA = ChatBubbleTheme.alphaBlend(fg, (int)(255 * alpha));
            for (int li = 0; li < lines.size(); li++)
                renderLineWithClicks(g, font, lines.get(li), textX, y + li * font.lineHeight, fgA, fb, clickableSpans);
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
            int imgX = own ? (avatarX - 8 - w) : (avatarX + Appearance.avatarSize() + 4);
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
            int lx = own ? (avatarX - 8 - labelW - 3) : (avatarX + Appearance.avatarSize() + 4 + 3);
            g.drawString(font, Component.literal(label), lx, baseY + NAME_H + 2,
                ChatBubbleTheme.alphaBlend(c.duplicateLabel(), (int)(255 * alpha)), false);
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - ChatLayout.PAD * 2 - Appearance.avatarSize() - 24;
            String quoteText = "↳ " + msg.replySender() + ": " + msg.replyContent();
            String quoteDisplay = font.plainSubstrByWidth(quoteText, quoteMaxW - 10);
            if (!quoteDisplay.equals(quoteText)) quoteDisplay += "...";
            int quoteW = Math.min(font.width(quoteDisplay) + 8, quoteMaxW);
            int quoteX = own ? (avatarX - 8 - quoteW) : (avatarX + Appearance.avatarSize() + 4);
            if (quoteX < panelX + ChatLayout.PAD) quoteX = panelX + ChatLayout.PAD;
            if (quoteX + quoteW > panelX + panelW - ChatLayout.PAD)
                quoteW = panelX + panelW - ChatLayout.PAD - quoteX;
            RoundRectRenderer.fill(g, quoteX, y, quoteX + quoteW, y + font.lineHeight + 4, 3,
                ChatBubbleTheme.alphaBlend(c.contextHover(), (int)(255 * alpha)));
            g.drawString(font, Component.literal(quoteDisplay), quoteX + 4, y + 2,
                ChatBubbleTheme.alphaBlend(c.textSecondary(), (int)(255 * alpha)), false);
        }

        // Hit-test region for avatar clicks / context menus: the message span.
        bubbleRects.add(new int[]{own ? avatarX - 8 - maxTextW : avatarX + Appearance.avatarSize() + 4,
            baseY, Math.max(maxTextW, maxImgW), y - baseY, index});
    }

    /** QQ-style emote: bubble-less image, max 32px, aligned by direction. */
    public static void renderEmoteMessage(GuiGraphics g, Font font,
            ChatMessageStore.ChatMessage msg, int index, int baseY, boolean own, float alpha,
            ChatBubbleTheme.Colors c, ResourceLocation skin, int panelX, int panelW,
            List<int[]> bubbleRects, List<ClickableSpan> clickableSpans) {
        BracketCodec.ParseResult parsed = parseImages(msg.content());
        if (parsed.images().isEmpty()) return;
        BracketCodec.ImageRef ref = parsed.images().get(0);

        int avatarX = own ? panelX + panelW - ChatLayout.PAD - Appearance.avatarSize() : panelX + ChatLayout.PAD;
        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 20;
            FormattedCharSequence nameSeq = nameSequence(font, msg.senderName(), maxNameW);
            int nameW = font.width(nameSeq);
            int startX = own ? (avatarX - 8 - nameW) : (avatarX + Appearance.avatarSize() + 4);
            g.drawString(font, nameSeq, startX, baseY, ChatBubbleTheme.alphaBlend(c.nameColor(), (int)(255 * alpha)), false);
        }

        drawAvatar(g, skin, avatarX, baseY, alpha);

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
        int emoteX = own ? (avatarX - 8 - w) : (avatarX + Appearance.avatarSize() + 4);
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
        List<Style> styles = new ArrayList<>();
        line.accept((i, st, cp) -> { styles.add(st); return true; });

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

        // 下划线按“字符级是否有 ClickEvent”逐字标注；i 越界（ModernUI 类文本引擎
        // 会访问超出样式列表的字符索引）时退回按样式自身判断，避免数组越界
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

        FormattedCharSequence decorated = sink -> line.accept((i, st, cp) ->
            sink.accept(i, (i < styleLen ? hasClickEvent[i] : st.getClickEvent() != null)
                && !st.isUnderlined() ? st.withUnderlined(true) : st, cp));
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
                                     float alpha) {
        if (msg.isSystem()) {
            List<FormattedCharSequence> lines = wrapContent(msg.content(), font, panelW - ChatLayout.PAD * 2 - 20);
            int yy = baseY + 2;
            Style fb = findClickStyle(msg.content());
            int sysColor = ChatBubbleTheme.alphaBlend(c.textMuted(), (int)(255 * alpha));
            int beforeSys = clickableSpans.size();
            for (var line : lines) {
                int lw = font.width(line);
                renderLineWithClicks(g, font, line, panelX + (panelW - lw) / 2, yy, sysColor, fb, clickableSpans);
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
                panelX, panelW, bubbleRects, clickableSpans);
            return;
        }
        // Any message carrying images renders bubble-less too (240px long-edge,
        // aspect preserved, stacked vertically, direction-aligned).
        if (!parsed.images().isEmpty()) {
            renderNoBubbleMessage(g, font, msg, index, baseY, own, alpha, parsed, lines, c, skin,
                panelX, panelW, bubbleRects, clickableSpans);
            return;
        }

        int textW = 0;
        for (var line : lines) textW = Math.max(textW, font.width(line));
        int bubbleW = textW + BUBBLE_PAD_X * 2;
        int bubbleH = lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2;

        int avatarX, bubbleX;
        if (own) {
            avatarX = panelX + panelW - ChatLayout.PAD - Appearance.avatarSize();
            bubbleX = avatarX - 4 - bubbleW;
        } else {
            avatarX = panelX + ChatLayout.PAD;
            bubbleX = avatarX + Appearance.avatarSize() + 4;
        }

        int nameY = baseY;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - 20;
            FormattedCharSequence nameSeq = nameSequence(font, msg.senderName(), maxNameW);
            int nameW = font.width(nameSeq);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            g.drawString(font, nameSeq, startX, nameY, ChatBubbleTheme.alphaBlend(c.nameColor(), (int)(255 * alpha)), false);
        }

        int bubbleY = baseY + NAME_H;
        int avatarY = baseY;

        int bg = own ? ownBubbleColor : otherBubbleColor;
        int fg = own ? ownTextColor : otherTextColor;

        // 气泡背景：SDF 圆角（shader 数学，任何半径平滑；配置实时生效，不可被资源包覆盖）
        RoundRectRenderer.fill(g, bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH,
            cornerRadius, ChatBubbleTheme.alphaBlend(bg, (int)(255 * alpha)));

        Style fb = findClickStyle(msg.content());
        int fgA = ChatBubbleTheme.alphaBlend(fg, (int)(255 * alpha));
        for (int li = 0; li < lines.size(); li++)
            renderLineWithClicks(g, font, lines.get(li), bubbleX + BUBBLE_PAD_X,
                bubbleY + BUBBLE_PAD_Y + li * font.lineHeight, fgA, fb, clickableSpans);

        // Draw avatar (per-element alpha: vanilla blit ignores setShaderColor)
        drawAvatar(g, skin, avatarX, avatarY, alpha);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = font.width(label);
            int labelX, labelY = bubbleY + (bubbleH - font.lineHeight) / 2;
            if (own) labelX = bubbleX - labelW - 3;
            else labelX = bubbleX + bubbleW + 3;
            g.drawString(font, Component.literal(label), labelX, labelY, ChatBubbleTheme.alphaBlend(c.duplicateLabel(), (int)(255 * alpha)), false);
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - ChatLayout.PAD * 2 - Appearance.avatarSize() - 24;
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
            // 引用块：SDF 圆角
            RoundRectRenderer.fill(g, quoteX, quoteY, quoteX + quoteW, quoteY + quoteH, 3, ChatBubbleTheme.alphaBlend(c.contextHover(), (int)(255 * alpha)));
            g.drawString(font, Component.literal(quoteDisplay), quoteX + 4, quoteY + 2, ChatBubbleTheme.alphaBlend(c.textSecondary(), (int)(255 * alpha)), false);
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});

        if (index == searchHighlightIndex)
            g.renderOutline(bubbleX - 1, bubbleY - 1, bubbleW + 2, bubbleH + 2,
                ChatSearchPanel.HIGHLIGHT);
    }
}
