package com.niuqu.chatbubble.chat.notification;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.RoundRectRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;

import java.util.*;

public class MentionNotificationBanner {
    public static final MentionNotificationBanner INSTANCE = new MentionNotificationBanner();

    public enum NotificationType { MENTION, QUOTE, WHISPER }

    private static final long SLIDE_MS = 250;
    private static final long VISIBLE_MS_PERIOD = 1000;
    private static final int AVATAR = 24;
    private static final int AVATAR_HAT = 26;
    private static final int AVATAR_X = 8;
    private static final int TEXT_X = AVATAR_X + AVATAR + 6;
    private static final int BANNER_H = 36;
    private static final int MAX_MSG_LINES = 2;
    private static final int SHADOW_OFF = 2;
    private static final UUID NIL_UUID = new UUID(0, 0);

    private final Deque<PendingBanner> queue = new ArrayDeque<>();
    private PendingBanner current;
    private BannerState state = BannerState.HIDDEN;
    private long stateStartMs;
    private long visibleDurationMs;

    private static final Map<UUID, ResourceLocation> skinCache = new HashMap<>();

    private MentionNotificationBanner() {}

    public void enqueue(UUID senderUUID, Component senderName, Component content,
                        int messageIndex, NotificationType type) {
        Minecraft mc = Minecraft.getInstance();

        String prefix = switch (type) {
            case MENTION -> Component.translatable("e33chat.banner.mention").getString();
            case QUOTE -> Component.translatable("e33chat.banner.quote").getString();
            case WHISPER -> Component.translatable("e33chat.banner.whisper").getString();
        };
        Component labeledName = Component.literal(prefix).append(senderName);

        int maxTextW = Math.min(mc.getWindow().getGuiScaledWidth() - TEXT_X - 12, 200);
        int dotsW = mc.font.width("...");

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

        List<FormattedCharSequence> msgLines = mc.font.split(content, maxTextW);
        if (msgLines.size() > MAX_MSG_LINES) {
            String fullText = content.getString();
            String plain = mc.font.plainSubstrByWidth(fullText, maxTextW * 2 - dotsW) + "...";
            msgLines = mc.font.split(Component.literal(plain), maxTextW);
            if (msgLines.size() > MAX_MSG_LINES)
                msgLines = msgLines.subList(0, MAX_MSG_LINES);
        }

        int textW = mc.font.width(nameSeq);
        for (var line : msgLines) textW = Math.max(textW, mc.font.width(line));
        int bannerW = AVATAR_X + AVATAR + 6 + textW + 12;

        queue.addLast(new PendingBanner(senderUUID, senderName, content, messageIndex,
            type, nameSeq, msgLines, textW, bannerW));
    }

    public int pendingCount() { return queue.size() + (current != null ? 1 : 0); }

    public void tick() {
        long now = System.currentTimeMillis();
        BannerState prev = state;
        switch (state) {
            case HIDDEN:
                if (!queue.isEmpty()) {
                    current = queue.pollFirst();
                    visibleDurationMs = (long) ChatBubbleConfig.MENTION_BANNER_DURATION.get() * VISIBLE_MS_PERIOD;
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
                        visibleDurationMs = (long) ChatBubbleConfig.MENTION_BANNER_DURATION.get() * VISIBLE_MS_PERIOD;
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

    public void render(GuiGraphics g, int screenW, int screenH) {
        if (current == null || state == BannerState.HIDDEN) return;
        if (!ChatBubbleConfig.MENTION_BANNER_ENABLED.get()) return;

        Minecraft mc = Minecraft.getInstance();
        long now = System.currentTimeMillis();

        float raw = state == BannerState.SLIDING_DOWN
            ? Math.min(1f, (float)(now - stateStartMs) / SLIDE_MS)
            : state == BannerState.SLIDING_UP
                ? Math.max(0f, 1f - (float)(now - stateStartMs) / SLIDE_MS)
                : 1f;

        float slide;
        if (state == BannerState.SLIDING_DOWN) {
            float c = 1.70158f;
            slide = 1f + c * (float)Math.pow(raw - 1, 3) + c * (float)Math.pow(raw - 1, 2);
        } else if (state == BannerState.SLIDING_UP) {
            slide = raw * raw;
        } else {
            slide = 1f;
        }

        float fadeRaw = Math.min(1f, raw / 0.6f);
        float alpha = state == BannerState.SLIDING_UP ? raw : fadeRaw;

        int y = (int)((-BANNER_H) + slide * BANNER_H);

        var theme = ChatBubbleConfig.THEME.get().colors();
        int bg = theme.bannerBg();
        int cornerRadius = ChatBubbleConfig.BANNER_CORNER_RADIUS.get();

        FormattedCharSequence nameSeq = current.nameSeq;
        List<FormattedCharSequence> msgLines = current.msgLines;
        int textW = current.textW;
        int bannerW = current.bannerW;
        int x = (screenW - bannerW) / 2;

        int shadowAlpha = (int)(0x30 * alpha);
        int shadowColor = (shadowAlpha << 24);
        RoundRectRenderer.fill(g, x + SHADOW_OFF, y + SHADOW_OFF,
            x + bannerW + SHADOW_OFF, y + BANNER_H + SHADOW_OFF, cornerRadius, shadowColor);

        int bgAlpha = (int)((bg >>> 24) * alpha);
        RoundRectRenderer.fill(g, x, y, x + bannerW, y + BANNER_H, cornerRadius,
            (bgAlpha << 24) | (bg & 0x00FFFFFF));

        int avatarY = y + (BANNER_H - AVATAR_HAT) / 2;
        ResourceLocation skin = getSkin(current.senderUUID, current.senderName.getString());
        RenderSystem.setShaderColor(1f, 1f, 1f, alpha);
        drawPlayerHead(g, skin, x + AVATAR_X, avatarY, AVATAR, AVATAR_HAT);
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);

        int textX = x + TEXT_X;
        int nameY = y + 6;
        int nameAlpha = (int)((theme.textPrimary() >>> 24) * alpha);
        int nameColor = (nameAlpha << 24) | (theme.textPrimary() & 0x00FFFFFF);
        g.drawString(mc.font, nameSeq, textX, nameY, nameColor, false);

        int msgAlpha = (int)((theme.textSecondary() >>> 24) * alpha);
        int msgColor = (msgAlpha << 24) | (theme.textSecondary() & 0x00FFFFFF);
        int msgY = nameY + mc.font.lineHeight + 2;
        for (int i = 0; i < msgLines.size(); i++)
            g.drawString(mc.font, msgLines.get(i), textX,
                msgY + i * mc.font.lineHeight, msgColor, false);
    }

    public int currentMessageIndex() {
        return current != null ? current.messageIndex : -1;
    }

    private ResourceLocation getSkin(UUID uuid, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            var info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getSkin().texture();
        }
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            ResourceLocation cached = skinCache.get(uuid);
            if (cached != null) return cached;
            var skin = mc.getSkinManager().getInsecureSkin(
                new GameProfile(uuid, name != null ? name : ""));
            if (skin != null) {
                ResourceLocation tex = skin.texture();
                skinCache.put(uuid, tex);
                return tex;
            }
        }
        return DefaultPlayerSkin.get(uuid != null ? uuid : NIL_UUID).texture();
    }

    private void drawPlayerHead(GuiGraphics g, ResourceLocation skin, int x, int y,
                                 int baseSize, int hatSize) {
        RenderSystem.enableBlend();
        g.blit(skin, x, y, baseSize, baseSize, 8.0F, 8.0F, 8, 8, 64, 64);
        int hatOff = (hatSize - baseSize) / 2;
        g.blit(skin, x - hatOff, y - hatOff, hatSize, hatSize, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private enum BannerState { HIDDEN, SLIDING_DOWN, VISIBLE, SLIDING_UP }

    private record PendingBanner(UUID senderUUID, Component senderName, Component content,
                                  int messageIndex, NotificationType type,
                                  FormattedCharSequence nameSeq,
                                  List<FormattedCharSequence> msgLines,
                                  int textW, int bannerW) {}
}
