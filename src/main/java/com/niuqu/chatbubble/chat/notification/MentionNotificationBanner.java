package com.niuqu.chatbubble.chat.notification;

import com.mojang.authlib.GameProfile;
import com.niuqu.chatbubble.Animation;
import com.niuqu.chatbubble.AnimationStyle;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.RenderHelper;
import com.niuqu.chatbubble.RoundRectRenderer;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import net.minecraft.client.MinecraftClient;
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
import net.minecraft.client.util.DefaultSkinHelper;
//#if MC >= 12004
//#if MC >= 12109
import net.minecraft.entity.player.SkinTextures;
//#else
import net.minecraft.client.util.SkinTextures;
//#endif
//#endif
import net.minecraft.text.MutableText;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

import java.util.*;

public class MentionNotificationBanner {
    public static final MentionNotificationBanner INSTANCE = new MentionNotificationBanner();

    public enum NotificationType { MENTION, QUOTE, WHISPER, SYSTEM }

    private static final long SLIDE_MS = 250;
    private static final long VISIBLE_MS_PERIOD = 1000;
    private static final int AVATAR = 24;
    private static final int AVATAR_HAT = 26;
    private static final int AVATAR_X = 8;
    private static final int TEXT_X = AVATAR_X + AVATAR + 6;
    private static final int TEXT_X_PLAIN = 8;   // no-avatar banners (system) start text flush left
    private static final int MAX_TEXT_W = 170;   // fixed content-area width cap for every banner
    private static final int BANNER_H = 36;
    private static final int MAX_MSG_LINES = 2;
    private static final int SHADOW_OFF = 2;
    private static final UUID NIL_UUID = new UUID(0, 0);

    private final Deque<PendingBanner> queue = new ArrayDeque<>();
    private PendingBanner current;
    private BannerState state = BannerState.HIDDEN;
    private long stateStartMs;
    private long visibleDurationMs;

    private static final Map<UUID, Identifier> skinCache = new HashMap<>();

    private MentionNotificationBanner() {}

    public void enqueue(UUID senderUUID, Text senderName, Text content,
                        int messageIndex, NotificationType type) {
        MinecraftClient mc = MinecraftClient.getInstance();

        String prefix = switch (type) {
            case MENTION -> com.niuqu.chatbubble.Txt.translatable("e33chat.banner.mention").getString();
            case QUOTE -> com.niuqu.chatbubble.Txt.translatable("e33chat.banner.quote").getString();
            case WHISPER -> com.niuqu.chatbubble.Txt.translatable("e33chat.banner.whisper").getString();
            case SYSTEM -> com.niuqu.chatbubble.Txt.translatable("e33chat.banner.system").getString();
        };
        Text labeledName = com.niuqu.chatbubble.Txt.literal(prefix).append(senderName);

        // System banners carry no sender — plain text, no avatar, flush text start.
        // [系统] 标签与第一行内容同行，内容宽度预算扣掉标签宽，避免同行溢出横幅
        boolean hasAvatar = type != NotificationType.SYSTEM;
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
            nameSeq = mc.textRenderer.wrapLines(com.niuqu.chatbubble.Txt.literal(plainName), maxTextW).get(0);
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

    public void render(Object g, int screenW, int screenH) {
        if (current == null || state == BannerState.HIDDEN) return;
        if (!ChatBubbleClientSetup.config().mentionBannerEnabled()) return;

        MinecraftClient mc = MinecraftClient.getInstance();
        long now = System.currentTimeMillis();

        float raw = state == BannerState.SLIDING_DOWN
            ? Math.min(1f, (float)(now - stateStartMs) / SLIDE_MS)
            : state == BannerState.SLIDING_UP
                ? Math.max(0f, 1f - (float)(now - stateStartMs) / SLIDE_MS)
                : 1f;

        AnimationStyle bstyle = AnimationStyle.parse(ChatBubbleClientSetup.config().bannerAnimStyle());
        float slide;
        float alpha;
        float bscale = 1f;
        if (bstyle == AnimationStyle.NONE) {
            slide = 1f;
            alpha = 1f;
        } else if (bstyle == AnimationStyle.FADE) {
            slide = 1f;
            alpha = state == BannerState.SLIDING_UP ? raw : Animation.easeOutQuad(raw);
        } else if (bstyle == AnimationStyle.ZOOM) {
            slide = 1f;
            alpha = state == BannerState.SLIDING_UP ? raw : Animation.easeOutQuad(raw);
            if (state == BannerState.SLIDING_DOWN) bscale = 0.8f + 0.2f * Animation.easeOutBack(raw);
            else if (state == BannerState.SLIDING_UP) bscale = 0.8f + 0.2f * raw;
        } else {
            // SLIDE (default): slide from the top with overshoot, fade in early
            if (state == BannerState.SLIDING_DOWN) {
                float c = 1.70158f;
                slide = 1f + c * (float) Math.pow(raw - 1, 3) + c * (float) Math.pow(raw - 1, 2);
            } else if (state == BannerState.SLIDING_UP) {
                slide = raw * raw;
            } else {
                slide = 1f;
            }
            float fadeRaw = Math.min(1f, raw / 0.6f);
            alpha = state == BannerState.SLIDING_UP ? raw : fadeRaw;
        }

        // Avatar (only for real senders; system banners stay plain text)
        OrderedText nameSeq = current.nameSeq;
        List<OrderedText> msgLines = current.msgLines;
        int textW = current.textW;
        int bannerW = current.bannerW;
        int bannerH = current.bannerH;
        int x = (screenW - bannerW) / 2 + ChatBubbleClientSetup.config().bannerOffsetX();
        int y = (int) ((-bannerH) + slide * bannerH) + ChatBubbleClientSetup.config().bannerOffsetY();

        if (bscale != 1f) {
            RenderHelper.pushMatrix(g);
            RenderHelper.translate(g, x + bannerW / 2f, y + bannerH / 2f, 0);
            RenderHelper.scale(g, bscale, bscale, 1f);
            RenderHelper.translate(g, -(x + bannerW / 2f), -(y + bannerH / 2f), 0);
        }

        var theme = com.niuqu.chatbubble.ChatBubbleTheme.valueOf(
            ChatBubbleClientSetup.config().theme().toUpperCase()).colors();
        int bg = theme.bannerBg();
        int cornerRadius = ChatBubbleClientSetup.config().bannerCornerRadius();

        int shadowAlpha = (int) (0x30 * alpha);
        int shadowColor = (shadowAlpha << 24);
        RoundRectRenderer.fill(g, x + SHADOW_OFF, y + SHADOW_OFF,
            x + bannerW + SHADOW_OFF, y + bannerH + SHADOW_OFF, cornerRadius, shadowColor);

        // Background：SDF 圆角（与阴影同 shader，半径配置实时生效；不可被资源包覆盖）
        int bgAlpha = (int) ((bg >>> 24) * alpha);
        RoundRectRenderer.fill(g, x, y, x + bannerW, y + bannerH, cornerRadius,
            (bgAlpha << 24) | (bg & 0x00FFFFFF));

        int textX = x + (current.hasAvatar ? TEXT_X : TEXT_X_PLAIN);
        int nameColor, msgColor;
        if (current.hasAvatar) {
            int avatarY = y + (bannerH - AVATAR_HAT) / 2;
            Identifier skin = getSkin(current.senderUUID, current.senderName.getString());
            drawPlayerHead(g, skin, x + AVATAR_X, avatarY, AVATAR, AVATAR_HAT, alpha);

            // Name (prefix already baked into nameSeq in enqueue)
            int nameY = y + 6;
            int nameAlpha = (int) ((theme.textPrimary() >>> 24) * alpha);
            nameColor = (nameAlpha << 24) | (theme.textPrimary() & 0x00FFFFFF);
            RenderHelper.drawText(g, mc.textRenderer, nameSeq, textX, nameY, nameColor, false);

            // Message lines
            int msgAlpha = (int) ((theme.textSecondary() >>> 24) * alpha);
            msgColor = (msgAlpha << 24) | (theme.textSecondary() & 0x00FFFFFF);
            int msgY = nameY + mc.textRenderer.fontHeight + 2;
            for (int i = 0; i < msgLines.size(); i++)
                RenderHelper.drawText(g, mc.textRenderer, msgLines.get(i), textX,
                    msgY + i * mc.textRenderer.fontHeight, msgColor, false);
        } else {
            // Plain-text banner: [系统] label + content vertically centered, single row
            int nameAlpha = (int) ((theme.textPrimary() >>> 24) * alpha);
            nameColor = (nameAlpha << 24) | (theme.textPrimary() & 0x00FFFFFF);
            int msgAlpha = (int) ((theme.textSecondary() >>> 24) * alpha);
            msgColor = (msgAlpha << 24) | (theme.textSecondary() & 0x00FFFFFF);
            int lineH = mc.textRenderer.fontHeight;
            int totalH = lineH * msgLines.size();
            int textY = y + (bannerH - totalH) / 2;
            RenderHelper.drawText(g, mc.textRenderer, nameSeq, textX, textY, nameColor, false);
            int contentX = textX + mc.textRenderer.getWidth(nameSeq);
            RenderHelper.drawText(g, mc.textRenderer, msgLines.get(0), contentX, textY, msgColor, false);
            for (int i = 1; i < msgLines.size(); i++)
                RenderHelper.drawText(g, mc.textRenderer, msgLines.get(i), textX,
                    textY + i * lineH, msgColor, false);
        }

        if (bscale != 1f) RenderHelper.popMatrix(g);
    }

    public int currentMessageIndex() {
        return current != null ? current.messageIndex : -1;
    }

    private Identifier getSkin(UUID uuid, String name) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.getNetworkHandler() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            var info = mc.getNetworkHandler().getPlayerListEntry(uuid);
            if (info != null) {
                //#if MC >= 12004
                //#if MC >= 12109
                return info.getSkinTextures().body().texturePath();
                //#else
                return info.getSkinTextures().texture();
                //#endif
                //#else
                //$$ return info.getSkinTexture();
                //#endif
            }
        }
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            Identifier cached = skinCache.get(uuid);
            if (cached != null) return cached;
            //#if MC >= 12004
            //#if MC >= 12109
            SkinTextures skin = mc.getSkinProvider().supplySkinTextures(
                new GameProfile(uuid, name != null ? name : ""), false).get();
            if (skin != null) {
                Identifier tex = skin.body().texturePath();
                if (tex != null) {
                    skinCache.put(uuid, tex);
                    return tex;
                }
            }
            //#else
            SkinTextures skin = mc.getSkinProvider().getSkinTextures(
                new GameProfile(uuid, name != null ? name : ""));
            if (skin != null && skin.texture() != null) {
                skinCache.put(uuid, skin.texture());
                return skin.texture();
            }
            //#endif
            //#endif
        }
        //#if MC >= 12004
        //#if MC >= 12109
        return DefaultSkinHelper.getSkinTextures(
            new GameProfile(uuid != null ? uuid : NIL_UUID, name != null ? name : "")).body().texturePath();
        //#else
        return DefaultSkinHelper.getSkinTextures(uuid != null ? uuid : NIL_UUID).texture();
        //#endif
        //#else
        //$$ return DefaultSkinHelper.getTexture();
        //#endif
    }

    private void drawPlayerHead(Object g, Identifier skin, int x, int y,
                                 int baseSize, int hatSize, float alpha) {
        if (alpha <= 0.003f) return;
        ColoredTextureRenderer.drawWithAlpha(g, skin, x, y, baseSize, baseSize, 8.0F, 8.0F, 8, 8, 64, 64, alpha);
        int hatOff = (hatSize - baseSize) / 2;
        ColoredTextureRenderer.drawWithAlpha(g, skin, x - hatOff, y - hatOff, hatSize, hatSize, 40.0F, 8.0F, 8, 8, 64, 64, alpha);
    }

    // Width-limit a text run by run, keeping each run's style (colors of
    // multi-colored system lines survive truncation), then append the ellipsis.
    private static Text truncateStyled(Text src, int maxWidth,
                                       net.minecraft.client.font.TextRenderer font, String suffix) {
        int budget = maxWidth - font.getWidth(suffix);
        MutableText out = com.niuqu.chatbubble.Txt.empty();
        int[] used = {0};
        src.visit((style, text) -> {
            if (used[0] >= budget) return java.util.Optional.<Object>empty();
            int w = font.getWidth(text);
            if (used[0] + w <= budget) {
                out.append(com.niuqu.chatbubble.Txt.literal(text).fillStyle(style));
                used[0] += w;
            } else {
                String sub = font.trimToWidth(text, budget - used[0]);
                out.append(com.niuqu.chatbubble.Txt.literal(sub).fillStyle(style));
                used[0] = budget;
            }
            return java.util.Optional.<Object>empty();
        }, net.minecraft.text.Style.EMPTY);
        return out.append(com.niuqu.chatbubble.Txt.literal(suffix));
    }

    private enum BannerState { HIDDEN, SLIDING_DOWN, VISIBLE, SLIDING_UP }

    private record PendingBanner(UUID senderUUID, Text senderName, Text content,
                                  int messageIndex, NotificationType type, boolean hasAvatar,
                                  OrderedText nameSeq, List<OrderedText> msgLines,
                                  int textW, int bannerW, int bannerH) {}
}
