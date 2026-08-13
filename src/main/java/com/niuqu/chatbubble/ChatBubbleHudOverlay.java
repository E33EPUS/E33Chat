package com.niuqu.chatbubble;

import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import java.util.List;
import net.minecraft.client.MinecraftClient;
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#endif
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
        //#if MC >= 12005
        return Identifier.of("e33chat", "textures/gui/" + theme + "/chat_icon.png");
        //#else
        //$$ return new Identifier("e33chat", "textures/gui/" + theme + "/chat_icon.png");
        //#endif
    }

    private static ChatBubbleTheme theme() {
        return "light".equalsIgnoreCase(cfg().theme()) ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
    }

    private static ChatBubbleTheme.Colors c() { return theme().colors(); }

    public static void render(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;

        //#if MC >= 12106
        g.getMatrices().pushMatrix();
        //#else
        g.getMatrices().push();
        //#endif
        //#if MC >= 12106
        g.getMatrices().translate(0, 0);
        //#else
        g.getMatrices().translate(0, 0, 300);
        //#endif

        MentionNotificationBanner.INSTANCE.tick();
        if (mc.currentScreen == null) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight());
        }

        //#if MC >= 12106
        if (mc.currentScreen != null) { g.getMatrices().popMatrix(); return; }
        //#else
        if (mc.currentScreen != null) { g.getMatrices().pop(); return; }
        //#endif

        //#if MC >= 11700
        String keyName = mc.options.chatKey.getBoundKeyLocalizedText().getString();
        //#else
        //$$ String keyName = mc.options.keyChat.getBoundKeyLocalizedText().getString();
        //#endif
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

        //#if MC >= 12106
        g.getMatrices().popMatrix();
        //#else
        g.getMatrices().pop();
        //#endif
    }

    // Fabric's HUD layer draws behind the screen batch; screens that render over
    // it re-invoke this so the banner stays visible on top
    public static void renderBannerForScreen(DrawContext g) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null || mc.options == null) return;
        if (mc.currentScreen instanceof ChatBubbleScreen) {
            MentionNotificationBanner.INSTANCE.render(g,
                mc.getWindow().getScaledWidth(),
                mc.getWindow().getScaledHeight());
        }
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
        //#if MC >= 12106
        g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, chatIconTex(), x, y, 0.0F, 0.0F, ICON_S, ICON_S, ICON_S, ICON_S);
        //#else
        //#if MC >= 12102
        //$$ g.drawTexture(id -> net.minecraft.client.render.RenderLayer.getGuiTextured(id), chatIconTex(), x, y, (int)0.0F, (int)0.0F, ICON_S, ICON_S, ICON_S, ICON_S);
        //#else
        g.drawTexture(chatIconTex(), x, y, 0.0F, 0.0F, ICON_S, ICON_S, ICON_S, ICON_S);
        //#endif
        //#endif
    }

    private static void drawScaledTip(DrawContext g, int x, int y, int disp) {
        Identifier tex = ChatBubbleScreen.iconTex("private_tip");
        //#if MC >= 12106
        g.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, tex, x, y, (float) SRC_U, (float) SRC_V, disp, disp, SRC_S, SRC_S, 16, 16);
        //#else
        //#if MC >= 12102
        //$$ g.drawTexture(id -> net.minecraft.client.render.RenderLayer.getGuiTextured(id), tex, x, y, (float) SRC_U, (float) SRC_V, disp, disp, SRC_S, SRC_S, 16, 16);
        //#else
        g.drawTexture(tex, x, y, disp, disp, (float) SRC_U, (float) SRC_V, SRC_S, SRC_S, 16, 16);
        //#endif
        //#endif
    }
}
