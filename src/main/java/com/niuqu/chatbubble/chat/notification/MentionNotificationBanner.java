package com.niuqu.chatbubble.chat.notification;

import com.mojang.authlib.GameProfile;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.RoundRectRenderer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.PlayerSkinDrawer;
import net.minecraft.client.util.DefaultSkinHelper;
//#if MC >= 12109
import net.minecraft.entity.player.SkinTextures;
//#else
//$$ import net.minecraft.client.util.SkinTextures;
//#endif
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;

import java.util.*;

public class MentionNotificationBanner {
    public static final MentionNotificationBanner INSTANCE = new MentionNotificationBanner();

    public enum NotificationType { MENTION, QUOTE, WHISPER
        //#if MC >= 12111
        , SYSTEM
        //#endif
    }

    private static final long SLIDE_MS = 250;
    private static final long VISIBLE_MS_PERIOD = 1000;
    private static final int AVATAR = 24;
    private static final int AVATAR_HAT = 26;
    private static final int AVATAR_X = 8;
    private static final int TEXT_X = AVATAR_X + AVATAR + 6;
    private static final int TEXT_X_PLAIN = 8;
    private static final int BANNER_H = 36;
    private static final int MAX_MSG_LINES = 2;
    private static final int SHADOW_OFF = 2;
    private static final int MAX_TEXT_W = 200;
    private static final UUID NIL_UUID = new UUID(0, 0);

    private final Deque<PendingBanner> queue = new ArrayDeque<>();
    private PendingBanner current;
    private BannerState state = BannerState.HIDDEN;
    private long stateStartMs;
    private long visibleDurationMs;

    private static final Map<UUID, SkinTextures> skinCache = new HashMap<>();

    private MentionNotificationBanner() {}

    public void enqueue(UUID senderUUID, Text senderName, Text content,
                        int messageIndex, NotificationType type) {
        MinecraftClient mc = MinecraftClient.getInstance();

        String prefix = switch (type) {
            case MENTION -> Text.translatable("e33chat.banner.mention").getString();
            case QUOTE -> Text.translatable("e33chat.banner.quote").getString();
            case WHISPER -> Text.translatable("e33chat.banner.whisper").getString();
            //#if MC >= 12111
            case SYSTEM -> Text.translatable("e33chat.banner.system").getString();
            //#endif
        };
        Text labeledName = Text.literal(prefix).append(senderName);

        // System banners carry no sender — plain text, no avatar, flush text start.
        // [系统] 标签与第一行内容同行，内容宽度预算扣掉标签宽，避免同行溢出横幅
        //#if MC >= 12111
        boolean hasAvatar = type != NotificationType.SYSTEM;
        //#else
        //$$ boolean hasAvatar = true;
        //#endif
        int textOriginX = hasAvatar ? TEXT_X : TEXT_X_PLAIN;
        int maxTextW = Math.min(MAX_TEXT_W, mc.getWindow().getScaledWidth() - textOriginX - 12);
        int dotsW = mc.textRenderer.getWidth("...");
        int contentMaxW = hasAvatar ? maxTextW : Math.max(40, maxTextW - mc.textRenderer.getWidth(prefix));

        List<OrderedText> nameLines = mc.textRenderer.wrapLines(labeledName, maxTextW);
        OrderedText nameSeq;
        if (nameLines.isEmpty()) {
            nameSeq = OrderedText.EMPTY;
        } else if (nameLines.size() > 1) {
            String plainName = mc.textRenderer.trimToWidth(
                labeledName.getString(), maxTextW - dotsW) + "...";
            nameSeq = mc.textRenderer.wrapLines(Text.literal(plainName), maxTextW).get(0);
        } else {
            nameSeq = nameLines.get(0);
        }

        List<OrderedText> msgLines = mc.textRenderer.wrapLines(content, contentMaxW);
        if (msgLines.size() > MAX_MSG_LINES) {
            // Styled truncation: keeps per-run colors of multi-colored system lines
            msgLines = mc.textRenderer.wrapLines(truncateStyled(content, contentMaxW * 2 - dotsW, mc.textRenderer, "..."), contentMaxW);
            if (msgLines.size() > MAX_MSG_LINES)
                msgLines = msgLines.subList(0, MAX_MSG_LINES);
        }

        int textW = mc.textRenderer.getWidth(nameSeq);
        for (var line : msgLines) textW = Math.max(textW, mc.textRenderer.getWidth(line));
        if (!hasAvatar && !msgLines.isEmpty()) {
            // 纯文本：[系统] 与第一行内容并排，横幅宽度按合并行算
            textW = Math.max(textW, mc.textRenderer.getWidth(nameSeq) + mc.textRenderer.getWidth(msgLines.get(0)));
        }
        int bannerW = textOriginX + textW + 12;
        // 高度：头像横幅固定 36（装名字+内容+头像）；纯文本系统横幅按行数紧凑
        // （上下各 5px 边距），1 行 ~20 / 2 行 ~30，不再留大片空白
        int bannerH = hasAvatar ? BANNER_H
            : mc.textRenderer.fontHeight * msgLines.size() + 10;

        queue.addLast(new PendingBanner(senderUUID, senderName, content, messageIndex,
            type, hasAvatar, nameSeq, msgLines, textW, bannerW, bannerH));
    }

    public int pendingCount() { return queue.size() + (current != null ? 1 : 0); }

    public void tick() {
        long now = System.currentTimeMillis();
        BannerState prev = state;
        switch (state) {
            case HIDDEN:
                if (!queue.isEmpty()) {
                    current = queue.pollFirst();
                    visibleDurationMs = (long) ChatBubbleClientSetup.config().mentionBannerDuration() * VISIBLE_MS_PERIOD;
                    state = BannerState.SLIDING_DOWN;
                    stateStartMs = now;
                }
                break;
            case SLIDING_DOWN:
                if (now - stateStartMs >= SLIDE_MS) {
                    state = BannerState.VISIBLE;
                    stateStartMs = now;
                }
                break;
            case VISIBLE:
                if (now - stateStartMs >= visibleDurationMs) {
                    state = BannerState.SLIDING_UP;
                    stateStartMs = now;
                }
                break;
            case SLIDING_UP:
                if (now - stateStartMs >= SLIDE_MS) {
                    current = null;
                    if (!queue.isEmpty()) {
                        current = queue.pollFirst();
                        visibleDurationMs = (long) ChatBubbleClientSetup.config().mentionBannerDuration() * VISIBLE_MS_PERIOD;
                        state = BannerState.SLIDING_DOWN;
                    } else {
                        state = BannerState.HIDDEN;
                    }
                    stateStartMs = now;
                }
                break;
        }
        if (state != prev) {
            String sender = current != null ? current.senderName.getString() : "?";
            ChatMessageStore.debugLog(() -> "[e33chat] Banner " + prev + " -> "
                + state + " | queue=" + queue.size() + " | sender=" + sender);
        }
    }

    public void render(DrawContext g, int screenW, int screenH) {
        if (current == null || state == BannerState.HIDDEN) return;
        if (!ChatBubbleClientSetup.config().mentionBannerEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();

        float raw = state == BannerState.SLIDING_DOWN
            ? Math.min(1f, (float)(now - stateStartMs) / SLIDE_MS)
            : state == BannerState.SLIDING_UP
                ? Math.max(0f, 1f - (float)(now - stateStartMs) / SLIDE_MS)
                : 1f;

        float slide;
        if (state == BannerState.SLIDING_DOWN) {
            float c = 1.70158f;
            slide = 1f + c * (float) Math.pow(raw - 1, 3) + c * (float) Math.pow(raw - 1, 2);
        } else if (state == BannerState.SLIDING_UP) {
            slide = raw * raw;
        } else {
            slide = 1f;
        }

        float fadeRaw = Math.min(1f, raw / 0.6f);
        float alpha = state == BannerState.SLIDING_UP ? raw : fadeRaw;

        var theme = com.niuqu.chatbubble.ChatBubbleTheme.valueOf(
            ChatBubbleClientSetup.config().theme().toUpperCase()).colors();
        int bg = theme.bannerBg();
        int cornerRadius = ChatBubbleClientSetup.config().bannerCornerRadius();

        OrderedText nameSeq = current.nameSeq;
        List<OrderedText> msgLines = current.msgLines;
        int textW = current.textW;
        int bannerW = current.bannerW;
        int bannerH = current.bannerH;
        int x = (screenW - bannerW) / 2;
        int y = (int) ((-bannerH) + slide * bannerH);

        int shadowAlpha = (int) (0x30 * alpha);
        int shadowColor = (shadowAlpha << 24);
        RoundRectRenderer.fill(g, x + SHADOW_OFF, y + SHADOW_OFF,
            x + bannerW + SHADOW_OFF, y + bannerH + SHADOW_OFF, cornerRadius, shadowColor);

        int bgAlpha = (int) ((bg >>> 24) * alpha);
        RoundRectRenderer.fill(g, x, y, x + bannerW, y + bannerH, cornerRadius,
            (bgAlpha << 24) | (bg & 0x00FFFFFF));

        int textX = x + (current.hasAvatar ? TEXT_X : TEXT_X_PLAIN);
        int nameColor, msgColor;
        if (current.hasAvatar) {
            int avatarY = y + (bannerH - AVATAR_HAT) / 2;
            SkinTextures skin = getSkin(current.senderUUID, current.senderName.getString());
            drawPlayerHead(g, skin, x + AVATAR_X, avatarY, AVATAR, AVATAR_HAT);

            int nameAlpha = (int) ((theme.textPrimary() >>> 24) * alpha);
            nameColor = (nameAlpha << 24) | (theme.textPrimary() & 0x00FFFFFF);
            g.drawText(mc.textRenderer, nameSeq, textX, y + 6, nameColor, false);

            int msgAlpha = (int) ((theme.textSecondary() >>> 24) * alpha);
            msgColor = (msgAlpha << 24) | (theme.textSecondary() & 0x00FFFFFF);
            int msgY = y + 6 + mc.textRenderer.fontHeight + 2;
            for (int i = 0; i < msgLines.size(); i++)
                g.drawText(mc.textRenderer, msgLines.get(i), textX,
                    msgY + i * mc.textRenderer.fontHeight, msgColor, false);
        } else {
            // Plain-text banner: [系统] label sits on the same line as the first
            // content line, extra lines wrap below; block vertically centered
            int nameAlpha = (int) ((theme.textPrimary() >>> 24) * alpha);
            nameColor = (nameAlpha << 24) | (theme.textPrimary() & 0x00FFFFFF);
            int msgAlpha = (int) ((theme.textSecondary() >>> 24) * alpha);
            msgColor = (msgAlpha << 24) | (theme.textSecondary() & 0x00FFFFFF);
            int lineH = mc.textRenderer.fontHeight;
            int totalH = lineH * msgLines.size();
            int textY = y + (bannerH - totalH) / 2;
            g.drawText(mc.textRenderer, nameSeq, textX, textY, nameColor, false);
            int contentX = textX + mc.textRenderer.getWidth(nameSeq);
            g.drawText(mc.textRenderer, msgLines.get(0), contentX, textY, msgColor, false);
            for (int i = 1; i < msgLines.size(); i++)
                g.drawText(mc.textRenderer, msgLines.get(i), textX,
                    textY + i * lineH, msgColor, false);
        }
    }

    public int currentMessageIndex() {
        return current != null ? current.messageIndex : -1;
    }

    private SkinTextures getSkin(UUID uuid, String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            var info = mc.getNetworkHandler().getPlayerListEntry(uuid);
            if (info != null) {
                SkinTextures textures = info.getSkinTextures();
                if (textures != null) return textures;
            }
        }
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            SkinTextures cached = skinCache.get(uuid);
            if (cached != null) return cached;
            //#if MC >= 12109
            SkinTextures skin = mc.getSkinProvider().supplySkinTextures(
                new GameProfile(uuid, name != null ? name : ""), false).get();
            //#else
            //$$ SkinTextures skin = mc.getSkinProvider().getSkinTextures(
            //$$     new GameProfile(uuid, name != null ? name : ""));
            //#endif
            if (skin != null) {
                skinCache.put(uuid, skin);
                return skin;
            }
        }
        //#if MC >= 12109
        return DefaultSkinHelper.getSkinTextures(
            new GameProfile(uuid != null ? uuid : NIL_UUID, name != null ? name : ""));
        //#else
        //$$ return DefaultSkinHelper.getSkinTextures(uuid != null ? uuid : NIL_UUID);
        //#endif
    }

    private void drawPlayerHead(DrawContext g, SkinTextures skin, int x, int y,
                             int baseSize, int hatSize) {
        if (skin == null) return;
        PlayerSkinDrawer.draw(g, skin, x, y, baseSize);
    }

    // Width-limit a component run by run, keeping each run's style (colors of
    // multi-colored system lines survive truncation), then append the ellipsis.
    private static Text truncateStyled(Text src, int maxWidth,
                                        net.minecraft.client.font.TextRenderer font, String suffix) {
        int budget = maxWidth - font.getWidth(suffix);
        net.minecraft.text.MutableText out = Text.empty();
        int[] used = {0};
        src.visit((style, text) -> {
            if (used[0] >= budget) return java.util.Optional.<Object>empty();
            int w = font.getWidth(text);
            if (used[0] + w <= budget) {
                //#if MC >= 12109
                out.append(Text.literal(text).fillStyle(style));
                //#else
                //$$ out.append(Text.literal(text).setStyle(style));
                //#endif
                used[0] += w;
            } else {
                String sub = font.trimToWidth(text, budget - used[0]);
                //#if MC >= 12111
                out.append(Text.literal(sub).fillStyle(style));
                //#else
                //$$ out.append(Text.literal(sub).setStyle(style));
                //#endif
                used[0] = budget;
            }
            return java.util.Optional.<Object>empty();
        }, net.minecraft.text.Style.EMPTY);
        return out.append(Text.literal(suffix));
    }

    private enum BannerState { HIDDEN, SLIDING_DOWN, VISIBLE, SLIDING_UP }

    private record PendingBanner(UUID senderUUID, Text senderName, Text content,
                                  int messageIndex, NotificationType type, boolean hasAvatar,
                                  OrderedText nameSeq, List<OrderedText> msgLines,
                                  int textW, int bannerW, int bannerH) {}
}