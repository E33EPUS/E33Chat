package com.niuqu.chatbubble;

import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.DrawHelper;
import java.util.List;
import net.minecraft.client.MinecraftClient;
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

public class ChatBubbleHudOverlay {

    private static final int ICON_S = 16;
    private static final int SRC_U = 6;
    private static final int SRC_V = 6;
    private static final int SRC_S = 4;
    private static final int TIP_DISP = 4;
    private static ChatBubbleTheme loadedTheme;

    private static ChatBubbleConfig cfg() { return ChatBubbleClientSetup.config(); }

    private static Identifier chatIconTex() {
        String theme = cfg().theme().toLowerCase();
        return GuiCompat.id("e33chat", "textures/gui/" + theme + "/chat_icon");
    }

    private static void ensureIconLoaded() {
        String currentTheme = cfg().theme();
        ChatBubbleTheme theme = "light".equalsIgnoreCase(currentTheme) ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
        if (loadedTheme == theme) return;
        loadIconTexture();
        ChatBubbleScreen.loadIconTextures();
        loadedTheme = theme;
    }

    private static ChatBubbleTheme theme() {
        return "light".equalsIgnoreCase(cfg().theme()) ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
    }

    private static ChatBubbleTheme.Colors c() { return theme().colors(); }

    public static void render(Object g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;

        //#if MC >= 12106
        RenderHelper.pushMatrix(g);
        //#else
        //$$ RenderHelper.pushMatrix(g);
        //#endif
        //#if MC >= 12106
        RenderHelper.translate(g, 0, 0);
        //#else
        //$$ RenderHelper.translate(g, 0, 0, 300);
        //#endif

        MentionNotificationBanner.INSTANCE.tick();
        if (mc.currentScreen == null) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight());
        }

        //#if MC >= 12106
        if (mc.currentScreen != null) { RenderHelper.popMatrix(g); return; }
        //#else
        //$$ if (mc.currentScreen != null) { RenderHelper.popMatrix(g); return; }
        //#endif

        String keyName = GuiCompat.chatKeyText(mc).getString();
        int screenH = mc.getWindow().getScaledHeight();
        int x = 3;
        int iconY = screenH - ICON_S - 20;
        int textY = iconY + ICON_S + 1;

        if (!cfg().hideChatIcon()) {
            ensureIconLoaded();
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
            RenderHelper.drawText(g, mc.textRenderer, keyDisplay, keyX, textY, 0xFFFFFFFF, false);
        }

        //#if MC >= 12106
        RenderHelper.popMatrix(g);
        //#else
        //$$ RenderHelper.popMatrix(g);
        //#endif
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
        String classpath = "assets/e33chat/textures/gui/" + cfg().theme().toLowerCase() + "/chat_icon.png";
        try (java.io.InputStream in = ChatBubbleHudOverlay.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                //#if MC >= 12105
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "chat_icon", img);
                //#else
                //$$ NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                //#endif
                MinecraftClient.getInstance().getTextureManager().registerTexture(chatIconTex(), tex);
            } else {
                ChatBubbleScreen.loadIconTexture(chatIconTex(), classpath, "chat_icon");
            }
        } catch (Exception e) {
            E33Log.error("[e33chat] Failed to load HUD icon texture", e);
            ChatBubbleScreen.loadIconTexture(chatIconTex(), classpath, "chat_icon");
        }
    }

    private static void drawIcon(Object g, int x, int y) {
        var mc = MinecraftClient.getInstance();
        try {
            mc.getTextureManager().getTexture(chatIconTex());
        } catch (Exception e) {
            loadIconTexture();
        }
        RenderHelper.drawTexture(g, chatIconTex(), x, y, 0.0F, 0.0F, ICON_S, ICON_S, ICON_S, ICON_S);
    }

    //#if MC >= 12111
    public static void renderStrongHint(Object g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        Text hint = ChatMessageStore.getStrongHintText();
        if (hint == null) return;
        int screenW = mc.getWindow().getScaledWidth();
        int textW = mc.textRenderer.getWidth(hint);
        int x = (screenW - textW) / 2;
        int y = 4;
        RenderHelper.drawText(g, mc.textRenderer, hint, x, y, 0xFFFFFFFF, true);
    }
    //#endif

    private static void drawScaledTip(Object g, int x, int y, int disp) {
        Identifier tex = ChatBubbleScreen.iconTex("private_tip");
        var tm = MinecraftClient.getInstance().getTextureManager();
        try {
            tm.getTexture(tex);
        } catch (Exception e) {
            ChatBubbleScreen.loadIconTextures();
        }
        RenderHelper.drawTexture(g, tex, x, y, disp, disp, (float) SRC_U, (float) SRC_V, SRC_S, SRC_S, 16, 16);
    }
}
