package com.niuqu.chatbubble.chat.notification;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.niuqu.chatbubble.render.Animation;
import com.niuqu.chatbubble.render.AnimationStyle;
import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.render.UiTokens;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;

public class MentionNotificationBanner {
    public static final MentionNotificationBanner INSTANCE = new MentionNotificationBanner();

    public enum NotificationType { MENTION, QUOTE, WHISPER, SYSTEM }

    private static final long SLIDE_IN_MS = 250;
    private static final long PUSH_MS = 200;
    private static final long EXIT_MS = 150;
    private static final long VISIBLE_MS_PERIOD = 1000;
    private static final int AVATAR = 24;
    private static final int AVATAR_HAT = 26;
    private static final int AVATAR_X = 8;
    private static final int TEXT_X = AVATAR_X + AVATAR + 6;
    private static final int TEXT_X_PLAIN = 8;   // no-avatar banners (system) start text flush left
    private static final int MAX_TEXT_W = 170;   // fixed content-area width cap for every banner
    private static final int BANNER_H = 36;
    private static final int MAX_MSG_LINES = 2;
    private static final int SHADOW_OFF = UiTokens.SHADOW_OFFSET_PANEL;
    private static final float COMPACT_SCALE = 0.75f;
    // Mobile-style overlap: each newer banner covers the top half of the banner
    // below it, so older banners peek out from behind like a notification stack.
    private static final float STACK_OVERLAP = 0.5f;
    private static final UUID NIL_UUID = new UUID(0, 0);

    /** Newest first. */
    private final List<ActiveBanner> banners = new ArrayList<>();
    private final List<ExitingBanner> exiting = new ArrayList<>();

    private static final Map<UUID, ResourceLocation> skinCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, ResourceLocation> eldest) {
            return size() > 256;
        }
    };

    private MentionNotificationBanner() {}

    public void enqueue(UUID senderUUID, Component senderName, Component content,
                        int messageIndex, NotificationType type) {
        Minecraft mc = Minecraft.getInstance();

        String prefix = switch (type) {
            case MENTION -> Component.translatable("e33chat.banner.mention").getString();
            case QUOTE -> Component.translatable("e33chat.banner.quote").getString();
            case WHISPER -> Component.translatable("e33chat.banner.whisper").getString();
            case SYSTEM -> Component.translatable("e33chat.banner.system").getString();
        };
        Component labeledName = Component.literal(prefix).append(senderName);

        // System banners carry no sender — plain text, no avatar, flush text start.
        // [系统] 标签与第一行内容同行，内容宽度预算扣掉标签宽，避免同行溢出横幅
        boolean hasAvatar = type != NotificationType.SYSTEM;
        int textOriginX = hasAvatar ? TEXT_X : TEXT_X_PLAIN;
        int maxTextW = Math.min(MAX_TEXT_W, mc.getWindow().getGuiScaledWidth() - textOriginX - 12);
        int dotsW = mc.font.width("...");
        int contentMaxW = hasAvatar ? maxTextW : Math.max(40, maxTextW - mc.font.width(prefix));

        List<FormattedCharSequence> nameLines = mc.font.split(labeledName, maxTextW);
        FormattedCharSequence nameSeq;
        if (nameLines.isEmpty()) {
            nameSeq = FormattedCharSequence.EMPTY;
        } else if (nameLines.size() > 1) {
            String plainName = mc.font.plainSubstrByWidth(
                labeledName.getString(), maxTextW - dotsW) + "...";
            nameSeq = mc.font.split(Component.literal(plainName), maxTextW).get(0);
        } else {
            nameSeq = nameLines.get(0);
        }

        List<FormattedCharSequence> msgLines = mc.font.split(content, contentMaxW);
        if (msgLines.size() > MAX_MSG_LINES) {
            // Styled truncation: keeps per-run colors of multi-colored system lines
            msgLines = mc.font.split(truncateStyled(content, contentMaxW * 2 - dotsW, mc.font, "..."), contentMaxW);
            if (msgLines.size() > MAX_MSG_LINES)
                msgLines = msgLines.subList(0, MAX_MSG_LINES);
        }

        int textW = mc.font.width(nameSeq);
        for (var line : msgLines) textW = Math.max(textW, mc.font.width(line));
        if (!hasAvatar && !msgLines.isEmpty()) {
            // 纯文本：[系统] 与第一行内容并排，横幅宽度按合并行算
            textW = Math.max(textW, mc.font.width(nameSeq) + mc.font.width(msgLines.get(0)));
        }
        int bannerW = textOriginX + textW + 12;
        // 高度：头像横幅固定 36（装名字+内容+头像）；纯文本系统横幅按行数紧凑
        // （上下各 5px 边距），1 行 ~20 / 2 行 ~30，不再留大片空白
        int bannerH = hasAvatar ? BANNER_H
            : mc.font.lineHeight * msgLines.size() + 10;

        PendingBanner pb = new PendingBanner(senderUUID, senderName, content, messageIndex,
            type, hasAvatar, nameSeq, msgLines, textW, bannerW, bannerH);
        addBanner(pb);
    }

    public int pendingCount() { return banners.size() + exiting.size(); }

    public void tick() {
        long now = System.currentTimeMillis();

        // Natural expiry: snapshot every current position, remove expired banners,
        // then let the remaining ones push up to fill the gaps.
        List<ActiveBanner> expired = new ArrayList<>();
        for (ActiveBanner b : banners) {
            if (now >= b.totalVisibleMs) expired.add(b);
        }
        if (!expired.isEmpty()) {
            for (ActiveBanner b : banners) {
                b.fromY = currentY(b, now);
                b.fromScale = currentScale(b, now);
            }
            for (ActiveBanner b : expired) {
                float y = b.fromY;
                float scale = b.fromScale;
                banners.remove(b);
                exiting.add(new ExitingBanner(b.data, now, y, scale));
            }
            for (ActiveBanner b : banners) {
                b.enterStartMs = -1;
                b.pushStartMs = now;
            }
        }

        exiting.removeIf(e -> now - e.startMs >= EXIT_MS);

        if (!banners.isEmpty() || !exiting.isEmpty()) {
            ChatMessageStore.debugLog(() -> "[e33chat] Banner stack | visible=" + banners.size()
                + " | exiting=" + exiting.size());
        }
    }

    public void render(GuiGraphics g, int screenW, int screenH) {
        if (!ChatBubbleConfig.MENTION_BANNER_ENABLED.get()) return;
        if (banners.isEmpty() && exiting.isEmpty()) return;

        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        // Exiting banners render behind the active stack. Their exit follows the
        // selected banner animation style so natural expiry and eviction feel
        // consistent with the entrance style.
        AnimationStyle exitStyle = ChatBubbleConfig.BANNER_ANIM_STYLE.get();
        for (ExitingBanner e : exiting) {
            float t = Math.min(1f, (float) (now - e.startMs) / EXIT_MS);
            float y = e.y;
            float scale = e.scale;
            float alpha;
            if (exitStyle == AnimationStyle.NONE) {
                alpha = 0f;
            } else if (exitStyle == AnimationStyle.SLIDE) {
                float ease = t * t;
                y = e.y - (e.data.bannerH * e.scale) * ease;
                alpha = exitFade(1f - t);
            } else if (exitStyle == AnimationStyle.FADE) {
                alpha = exitFade(1f - t);
            } else { // ZOOM
                alpha = exitFade(1f - t);
                scale = e.scale * (1f - 0.5f * t);
            }
            renderBanner(g, e.data, screenW, y, scale, alpha);
        }

        // Draw oldest first so newer banners render on top and can overlap them.
        for (int i = banners.size() - 1; i >= 0; i--) {
            ActiveBanner b = banners.get(i);
            float y = currentY(b, now);
            float scale = currentScale(b, now);
            float alpha = currentAlpha(b, now);
            renderBanner(g, b.data, screenW, y, scale, alpha);
        }
    }

    public int currentMessageIndex() {
        return banners.isEmpty() ? -1 : banners.get(0).data.messageIndex;
    }

    private void addBanner(PendingBanner pb) {
        long now = System.currentTimeMillis();
        int maxStack = maxStack();

        // New messages always win: drop any banners that are already exiting.
        exiting.clear();

        // Snapshot current render state before mutating the list.
        for (ActiveBanner b : banners) {
            b.fromY = currentY(b, now);
            b.fromScale = currentScale(b, now);
        }

        // Full stack: evict the oldest (bottom) banner.
        if (banners.size() >= maxStack && !banners.isEmpty()) {
            ActiveBanner oldest = banners.get(banners.size() - 1);
            exiting.add(new ExitingBanner(oldest.data, now, oldest.fromY, oldest.fromScale));
            banners.remove(banners.size() - 1);
        }

        ActiveBanner nb = new ActiveBanner(pb, now, now + visibleDurationMs(), now);
        banners.add(0, nb);

        // Every previously visible banner is now pushed down / compacted.
        for (ActiveBanner b : banners) {
            if (b != nb) {
                b.enterStartMs = -1;
                b.pushStartMs = now;
            }
        }
    }

    private long visibleDurationMs() {
        return (long) ChatBubbleConfig.MENTION_BANNER_DURATION.get() * VISIBLE_MS_PERIOD;
    }

    private int maxStack() {
        return Math.max(1, Math.min(5, ChatBubbleConfig.BANNER_MAX_STACK.get()));
    }

    private float targetY(int index, ActiveBanner b) {
        float y = ChatBubbleConfig.BANNER_OFFSET_Y.get();
        for (int i = 0; i < index; i++) {
            ActiveBanner prev = banners.get(i);
            float prevScale = i == 0 ? 1f : COMPACT_SCALE;
            y += prev.data.bannerH * prevScale * STACK_OVERLAP;
        }
        return y;
    }

    private float targetScale(int index) {
        return index == 0 ? 1f : COMPACT_SCALE;
    }

    private float currentY(ActiveBanner b, long now) {
        int i = banners.indexOf(b);
        if (i < 0) return ChatBubbleConfig.BANNER_OFFSET_Y.get();
        float target = targetY(i, b);
        if (b.pushStartMs >= 0) {
            float t = Math.min(1f, (float) (now - b.pushStartMs) / PUSH_MS);
            float e = Animation.easeOutCubic(t);
            return b.fromY + (target - b.fromY) * e;
        }
        if (i == 0 && b.enterStartMs >= 0) {
            long elapsed = now - b.enterStartMs;
            if (elapsed < SLIDE_IN_MS) {
                float raw = Math.min(1f, (float) elapsed / SLIDE_IN_MS);
                AnimationStyle bstyle = ChatBubbleConfig.BANNER_ANIM_STYLE.get();
                if (bstyle == AnimationStyle.SLIDE) {
                    float c = 1.70158f;
                    float slide = 1f + c * (float) Math.pow(raw - 1, 3) + c * (float) Math.pow(raw - 1, 2);
                    return (-b.data.bannerH) + slide * b.data.bannerH + ChatBubbleConfig.BANNER_OFFSET_Y.get();
                }
            }
        }
        return target;
    }

    private float currentScale(ActiveBanner b, long now) {
        int i = banners.indexOf(b);
        if (i < 0) return 1f;
        float target = targetScale(i);
        if (b.pushStartMs >= 0) {
            float t = Math.min(1f, (float) (now - b.pushStartMs) / PUSH_MS);
            float e = Animation.easeOutCubic(t);
            return b.fromScale + (target - b.fromScale) * e;
        }
        if (i == 0 && b.enterStartMs >= 0) {
            long elapsed = now - b.enterStartMs;
            if (elapsed < SLIDE_IN_MS) {
                float raw = Math.min(1f, (float) elapsed / SLIDE_IN_MS);
                AnimationStyle bstyle = ChatBubbleConfig.BANNER_ANIM_STYLE.get();
                if (bstyle == AnimationStyle.ZOOM) {
                    return 0.8f + 0.2f * Animation.easeOutBack(raw);
                }
            }
        }
        return target;
    }

    private float currentAlpha(ActiveBanner b, long now) {
        if (b.pushStartMs >= 0) return 1f;
        int i = banners.indexOf(b);
        if (i == 0 && b.enterStartMs >= 0) {
            long elapsed = now - b.enterStartMs;
            if (elapsed < SLIDE_IN_MS) {
                float raw = Math.min(1f, (float) elapsed / SLIDE_IN_MS);
                AnimationStyle bstyle = ChatBubbleConfig.BANNER_ANIM_STYLE.get();
                if (bstyle == AnimationStyle.NONE) return 1f;
                if (bstyle == AnimationStyle.SLIDE) return Math.min(1f, raw / 0.6f);
                return Animation.easeOutQuad(raw);
            }
        }
        return 1f;
    }

    private void renderBanner(GuiGraphics g, PendingBanner b, int screenW,
                              float y, float scale, float alpha) {
        if (alpha <= 0.003f) return;
        Minecraft mc = Minecraft.getInstance();

        float bgAlphaMul = alpha * (ChatBubbleConfig.BANNER_OPACITY.get() / 100f);

        var theme = Appearance.snapshot();
        int bg = theme.bannerBg();
        int cornerRadius = ChatBubbleConfig.BANNER_CORNER_RADIUS.get();

        int bannerW = b.bannerW;
        int bannerH = b.bannerH;
        int x = (screenW - bannerW) / 2 + ChatBubbleConfig.BANNER_OFFSET_X.get();
        int iy = (int) y;

        if (scale != 1f) {
            g.pose().pushPose();
            g.pose().translate(x + bannerW / 2f, y + bannerH / 2f, 0);
            g.pose().scale(scale, scale, 1f);
            g.pose().translate(-(x + bannerW / 2f), -(y + bannerH / 2f), 0);
        }

        // Shadow
        int shadowAlpha = (int)(UiTokens.SHADOW_ALPHA_PANEL * bgAlphaMul);
        int shadowColor = (shadowAlpha << 24);
        RoundRectRenderer.fill(g, x + SHADOW_OFF, iy + SHADOW_OFF,
            x + bannerW + SHADOW_OFF, iy + bannerH + SHADOW_OFF, cornerRadius, shadowColor);

        // Background：SDF 圆角（与阴影同 shader，半径配置实时生效；不可被资源包覆盖）
        int bgAlpha = (int)((bg >>> 24) * bgAlphaMul);
        RoundRectRenderer.fill(g, x, iy, x + bannerW, iy + bannerH, cornerRadius,
            (bgAlpha << 24) | (bg & 0x00FFFFFF));

        int textX = x + (b.hasAvatar ? TEXT_X : TEXT_X_PLAIN);
        // Compact banners show only the first content line, matching the mobile
        // notification style: avatar + title + one-line preview.
        List<FormattedCharSequence> drawLines = scale < 1f && b.msgLines.size() > 1
            ? b.msgLines.subList(0, 1) : b.msgLines;
        int nameColor, msgColor;
        if (b.hasAvatar) {
            int avatarY = iy + (bannerH - AVATAR_HAT) / 2;
            ResourceLocation skin = getSkin(b.senderUUID, b.senderName.getString());
            RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
            drawPlayerHead(g, skin, x + AVATAR_X, avatarY, AVATAR, AVATAR_HAT, alpha);
            RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            // Name (prefix already baked into nameSeq in enqueue)
            int nameY = iy + 6;
            int nameAlpha = (int)((theme.textPrimary() >>> 24) * alpha);
            nameColor = (nameAlpha << 24) | (theme.textPrimary() & 0x00FFFFFF);
            g.drawString(mc.font, b.nameSeq, textX, nameY, nameColor, false);

            // Message lines
            int msgAlpha = (int)((theme.textSecondary() >>> 24) * alpha);
            msgColor = (msgAlpha << 24) | (theme.textSecondary() & 0x00FFFFFF);
            int msgY = nameY + mc.font.lineHeight + 2;
            for (int i = 0; i < drawLines.size(); i++)
                g.drawString(mc.font, drawLines.get(i), textX,
                    msgY + i * mc.font.lineHeight, msgColor, false);
        } else {
            // Plain-text banner: [系统] label sits on the same line as the first
            // content line, extra lines wrap below; block vertically centered
            int nameAlpha = (int)((theme.textPrimary() >>> 24) * alpha);
            nameColor = (nameAlpha << 24) | (theme.textPrimary() & 0x00FFFFFF);
            int msgAlpha = (int)((theme.textSecondary() >>> 24) * alpha);
            msgColor = (msgAlpha << 24) | (theme.textSecondary() & 0x00FFFFFF);
            int lineH = mc.font.lineHeight;
            int totalH = lineH * drawLines.size();
            int textY = iy + (bannerH - totalH) / 2;
            g.drawString(mc.font, b.nameSeq, textX, textY, nameColor, false);
            int contentX = textX + mc.font.width(b.nameSeq);
            g.drawString(mc.font, drawLines.get(0), contentX, textY, msgColor, false);
            for (int i = 1; i < drawLines.size(); i++)
                g.drawString(mc.font, drawLines.get(i), textX,
                    textY + i * lineH, msgColor, false);
        }

        if (scale != 1f) g.pose().popPose();
    }

    private ResourceLocation getSkin(UUID uuid, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            var info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getSkinLocation();
        }
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            ResourceLocation cached = skinCache.get(uuid);
            if (cached != null) return cached;
            var skin = mc.getSkinManager().getInsecureSkinLocation(
                new GameProfile(uuid, name != null ? name : ""));
            if (skin != null) {
                skinCache.put(uuid, skin);
                return skin;
            }
        }
        return DefaultPlayerSkin.getDefaultSkin(uuid != null ? uuid : NIL_UUID);
    }

    private void drawPlayerHead(GuiGraphics g, ResourceLocation skin, int x, int y,
                                 int baseSize, int hatSize, float alpha) {
        if (alpha <= 0.003f) return;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, skin, x, y, baseSize, baseSize,
            8.0F, 8.0F, 8, 8, 64, 64, alpha);
        int hatOff = (hatSize - baseSize) / 2;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, skin, x - hatOff, y - hatOff, hatSize, hatSize,
            40.0F, 8.0F, 8, 8, 64, 64, alpha);
    }

    /** 退出淡出曲线：ease-in（慢起快走），07 §1.4 退出规范（raw 从 1→0）。 */
    private static float exitFade(float raw) {
        return raw * (2f - raw);
    }

    // Width-limit a component run by run, keeping each run's style (colors of
    // multi-colored system lines survive truncation), then append the ellipsis.
    private static Component truncateStyled(Component src, int maxWidth,
                                            net.minecraft.client.gui.Font font, String suffix) {
        int budget = maxWidth - font.width(suffix);
        net.minecraft.network.chat.MutableComponent out = Component.empty();
        int[] used = {0};
        src.visit((style, text) -> {
            if (used[0] >= budget) return java.util.Optional.<Object>empty();
            int w = font.width(text);
            if (used[0] + w <= budget) {
                out.append(Component.literal(text).withStyle(style));
                used[0] += w;
            } else {
                String sub = font.plainSubstrByWidth(text, budget - used[0]);
                out.append(Component.literal(sub).withStyle(style));
                used[0] = budget;
            }
            return java.util.Optional.<Object>empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        return out.append(Component.literal(suffix));
    }

    private static final class ActiveBanner {
        final PendingBanner data;
        final long bornMs;
        final long totalVisibleMs;
        long enterStartMs;
        long pushStartMs = -1;
        float fromY;
        float fromScale;

        ActiveBanner(PendingBanner data, long bornMs, long totalVisibleMs, long enterStartMs) {
            this.data = data;
            this.bornMs = bornMs;
            this.totalVisibleMs = totalVisibleMs;
            this.enterStartMs = enterStartMs;
        }
    }

    private static final class ExitingBanner {
        final PendingBanner data;
        final long startMs;
        final float y;
        final float scale;

        ExitingBanner(PendingBanner data, long startMs, float y, float scale) {
            this.data = data;
            this.startMs = startMs;
            this.y = y;
            this.scale = scale;
        }
    }

    private record PendingBanner(UUID senderUUID, Component senderName, Component content,
                                  int messageIndex, NotificationType type, boolean hasAvatar,
                                  FormattedCharSequence nameSeq,
                                  List<FormattedCharSequence> msgLines,
                                  int textW, int bannerW, int bannerH) {}
}
