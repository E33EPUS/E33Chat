package com.niuqu.chatbubble;

import com.mojang.blaze3d.systems.RenderSystem;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.DynamicTexture;
import net.minecraft.text.Text;
import net.minecraft.text.StringVisitable;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;

import java.util.ArrayList;
import java.util.List;

public class ChatBubbleHudOverlay {

    private static final int ICON_S = 16;
    private static final int RED_DOT_R = 4;
    private static ChatBubbleTheme loadedTheme;

    private static ChatBubbleConfig cfg() { return ChatBubbleClientSetup.config(); }

    private static Identifier chatIconTex() {
        String theme = cfg().theme().toLowerCase();
        return Identifier.of("e33chat", "textures/gui/" + theme + "/chat_icon");
    }

    private static void ensureIconLoaded() {
        String currentTheme = cfg().theme();
        ChatBubbleTheme theme = "light".equalsIgnoreCase(currentTheme) ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
        if (loadedTheme == theme) return;
        loadIconTexture();
        loadedTheme = theme;
    }

    private static ChatBubbleTheme theme() {
        return "light".equalsIgnoreCase(cfg().theme()) ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
    }

    private static ChatBubbleTheme.Colors c() { return theme().colors(); }

    public static void render(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;

        g.getMatrices().push();
        g.getMatrices().translate(0, 0, 300);

        if (mc.currentScreen == null) renderStrongHint(g);

        if (mc.currentScreen != null) { g.getMatrices().pop(); return; }

        String keyName = mc.options.chatKey.getBoundKeyLocalizedText().getString();
        int screenH = mc.getWindow().getScaledHeight();
        int x = 3;
        int iconY = screenH - ICON_S - 20;
        int textY = iconY + ICON_S + 1;

        if (cfg().previewEnabled()) {
            List<ChatMessageStore.PreviewEntry> previews = ChatMessageStore.getPreviews();
            if (previews != null && !previews.isEmpty()) {
                int maxW = cfg().previewWidth();
                int lineH = mc.textRenderer.fontHeight;
                int gap = 2;

                List<StringVisitable> displays = new ArrayList<>();
                int maxTextW = 0;
                for (var e : previews) {
                    StringVisitable trimmed;
                    if (mc.textRenderer.getWidth(e.text) > maxW - 4) {
                        var cut = mc.textRenderer.trimToWidth(e.text, maxW - 4 - mc.textRenderer.getWidth("..."));
                        trimmed = StringVisitable.concat(cut, StringVisitable.plain("..."));
                    } else {
                        trimmed = e.text;
                    }
                    displays.add(trimmed);
                    maxTextW = Math.max(maxTextW, mc.textRenderer.getWidth(trimmed));
                }

                int px = x + ICON_S / 2 - maxTextW / 2;
                if (px < 2) px = 2;
                int bgX1 = px - 3;
                if (bgX1 < 0) bgX1 = 0;

                int bottomLineY = iconY - 5 - lineH;
                int topLineY = bottomLineY - (displays.size() - 1) * (lineH + gap);
                int maxAlpha = 0;
                for (var e : previews) {
                    int a = Animation.fadeIn(e.ticks, 10) * 0xDD / 0xFF;
                    if (a > maxAlpha) maxAlpha = a;
                }
                int bgAlpha = maxAlpha / 2;
                int bgColor = (bgAlpha << 24) | 0x000000;
                g.fill(bgX1, topLineY - 2, px + maxTextW + 3, bottomLineY + lineH + 2, bgColor);
                var lang = Language.getInstance();
                for (int i = displays.size() - 1; i >= 0; i--) {
                    int lineY = bottomLineY - (displays.size() - 1 - i) * (lineH + gap);
                    int lineAlpha = Animation.fadeIn(previews.get(i).ticks, 10);
                    g.drawText(mc.textRenderer, lang.reorder(displays.get(i)), px, lineY, (lineAlpha << 24) | 0xFFFFFF, false);
                }
            }
        }

        if (!cfg().hideChatIcon()) {
            ensureIconLoaded();
            drawIcon(g, x, iconY);

            if (cfg().redDotEnabled() && ChatMessageStore.getUnreadCount() > 0) {
                int dotX = x + ICON_S - RED_DOT_R;
                int dotY = iconY + RED_DOT_R;
                int dotColor = ChatMessageStore.hasUnreadMention(mc.player.getName().getString())
                    ? c().redDotMention() : c().redDot();
                g.fill(dotX - RED_DOT_R, dotY - RED_DOT_R, dotX + RED_DOT_R, dotY + RED_DOT_R, dotColor);
            }

            String keyDisplay = "[" + keyName + "]";
            int keyW = mc.textRenderer.getWidth(keyDisplay);
            int keyX = keyW > ICON_S ? x : x + (ICON_S - keyW) / 2;
            g.drawText(mc.textRenderer, keyDisplay, keyX, textY, 0xFFFFFFFF, false);
        }

        g.getMatrices().pop();
    }

    public static void renderStrongHint(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (!cfg().strongHintEnabled() && !cfg().mentionStrongHintEnabled()) return;
        Text hint = ChatMessageStore.getStrongHintText();
        if (hint == null) return;
        int ticks = ChatMessageStore.getStrongHintTicks();
        int screenW = mc.getWindow().getScaledWidth();
        int hintW = mc.textRenderer.getWidth(hint);
        int hintX = (screenW - hintW) / 2;
        int hintY = mc.getWindow().getScaledHeight() - 22 - 30 - mc.textRenderer.fontHeight;
        int alpha = Animation.fadeInOut(ticks, 10, 40, 10);
        int bgAlpha = alpha / 2;
        int bgColor = (bgAlpha << 24) | 0x000000;
        int textColor = (alpha << 24) | 0xFFFFFF;
        g.fill(hintX - 6, hintY - 3, hintX + hintW + 6, hintY + mc.textRenderer.fontHeight + 3, bgColor);
        g.drawText(mc.textRenderer, hint, hintX, hintY, textColor, false);
    }

    public static boolean isMouseOverIcon(double mx, double my) {
        if (cfg().hideChatIcon()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return false;
        int screenH = mc.getWindow().getScaledHeight();
        int iconY = screenH - ICON_S - 20;
        return mx >= 3 && mx <= 3 + ICON_S && my >= iconY && my <= iconY + ICON_S + mc.textRenderer.fontHeight + 2;
    }

    private static void loadIconTexture() {
        try (java.io.InputStream in = ChatBubbleHudOverlay.class.getClassLoader()
                .getResourceAsStream("assets/e33chat/textures/gui/" + cfg().theme().toLowerCase() + "/chat_icon.png")) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                net.minecraft.client.texture.NativeImageBackedTexture tex = new net.minecraft.client.texture.NativeImageBackedTexture(img);
                MinecraftClient.getInstance().getTextureManager().registerTexture(chatIconTex(), tex);
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("[e33chat] Failed to load HUD icon texture", e);
        }
    }

    private static void drawIcon(DrawContext g, int x, int y) {
        var mc = MinecraftClient.getInstance();
        AbstractTexture abstractTex;
        try {
            abstractTex = mc.getTextureManager().getTexture(chatIconTex());
        } catch (Exception e) {
            loadIconTexture();
            abstractTex = mc.getTextureManager().getTexture(chatIconTex());
        }
        // Flush any batched geometry (preview, hint) before switching shaders so it
        // renders with its own state, then flush the icon before the red dot fill so
        // the icon shader doesn't leak into the fill.
        g.draw();
        RenderSystem.setShaderTexture(0, abstractTex.getGlId());
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.enableBlend();
        g.drawTexture(chatIconTex(), x, y, 0, 0, ICON_S, ICON_S, ICON_S, ICON_S);
        g.draw();
    }
}
