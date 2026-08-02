package com.niuqu.chatbubble;

import com.mojang.blaze3d.systems.RenderSystem;
import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import java.util.List;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ChatBubbleHudOverlay {

    private static final int ICON_S = 16;
    private static final int SRC_U = 6;
    private static final int SRC_V = 6;
    private static final int SRC_S = 4;
    private static final int TIP_DISP = 4;

    private static ChatBubbleConfig cfg() { return ChatBubbleClientSetup.config(); }

    private static Identifier chatIconTex() {
        String theme = cfg().theme().toLowerCase();
        return Identifier.of("e33chat", "textures/gui/" + theme + "/chat_icon.png");
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

        MentionNotificationBanner.INSTANCE.tick();
        if (mc.currentScreen == null) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight());
        }

        if (mc.currentScreen == null) renderStrongHint(g);

        if (mc.currentScreen != null) { g.getMatrices().pop(); return; }

        String keyName = mc.options.chatKey.getBoundKeyLocalizedText().getString();
        int screenH = mc.getWindow().getScaledHeight();
        int x = 3;
        int iconY = screenH - ICON_S - 20;
        int textY = iconY + ICON_S + 1;

        if (!cfg().hideChatIcon()) {
            drawIcon(g, x, iconY);

            if (cfg().redDotEnabled() && ChatMessageStore.getUnreadCount() > 0) {
                double wave = Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 3;
                int tipX = x + ICON_S - TIP_DISP / 2;
                int tipY = iconY - TIP_DISP / 2 + (int) wave;
                drawScaledTip(g, tipX, tipY, TIP_DISP);
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
        if (mc.currentScreen instanceof ChatBubbleScreen) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight());
        }
        if (!cfg().strongHintEnabled() && !cfg().mentionBannerEnabled()) return;
        Text hint = ChatMessageStore.getStrongHintText();
        if (hint == null) return;
        int ticks = ChatMessageStore.getStrongHintTicks();
        int screenW = mc.getWindow().getScaledWidth();
        int hintW = mc.textRenderer.getWidth(hint);
        int hintX = (screenW - hintW) / 2;
        int hintY = mc.getWindow().getScaledHeight() - 22 - 30 - mc.textRenderer.fontHeight;
        int alpha = Animation.fadeInOut(ticks, 10, 40, 10);
        // 纹理 × 动态 alpha（半透明黑），资源包可覆盖提示条底色
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.STRONG_HINT_BG),
            hintX - 6, hintY - 3, hintW + 12, mc.textRenderer.fontHeight + 6, (alpha / 2) / 255f);
        g.drawText(mc.textRenderer, hint, hintX, hintY, (alpha << 24) | 0xFFFFFF, false);
    }

    public static boolean isMouseOverIcon(double mx, double my) {
        if (cfg().hideChatIcon()) return false;
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.currentScreen != null) return false;
        int screenH = mc.getWindow().getScaledHeight();
        int iconY = screenH - ICON_S - 20;
        return mx >= 3 && mx <= 3 + ICON_S && my >= iconY && my <= iconY + ICON_S + mc.textRenderer.fontHeight + 2;
    }


    private static void drawIcon(DrawContext g, int x, int y) {
        // getTexture 无缓存时自动 new ResourceTexture 懒加载（资源包可覆盖，F3+T 即时生效）
        g.drawTexture(chatIconTex(), x, y, 0.0F, 0.0F, ICON_S, ICON_S, ICON_S, ICON_S);
    }

    private static void drawScaledTip(DrawContext g, int x, int y, int disp) {
        Identifier tex = ChatBubbleScreen.iconTex("private_tip");
        g.drawTexture(tex, x, y, disp, disp, (float) SRC_U, (float) SRC_V, SRC_S, SRC_S, 16, 16);
    }
}
