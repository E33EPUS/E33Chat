package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.render.ChatBubbleTheme;
import net.minecraft.client.gui.GuiGraphics;

public final class ChatScrollbar {

    public static final int WIDTH = 6;
    private static final int MIN_THUMB_H = 8;
    public static final int HOVER_ZONE = 20;
    private static final long FADE_MS = 1000;

    private ChatScrollbar() {}

    public static int thumbHeight(int trackH, int totalH) {
        if (totalH <= 0) return trackH;
        int h = Math.max(MIN_THUMB_H, (int) ((long) trackH * trackH / totalH));
        return Math.min(h, trackH);
    }

    public static int thumbY(int trackTop, int trackH, int thumbH, int scrollOffset, int maxScroll) {
        int travelRange = trackH - thumbH;
        if (travelRange <= 0) return trackTop;
        return trackTop + (int) ((long) scrollOffset * travelRange / maxScroll);
    }

    public static float alphaTarget(boolean inZone, boolean dragging, long lastScrollTime) {
        long since = net.minecraft.Util.getMillis() - lastScrollTime; // lastScrollTime 由 Util.getMillis() 赋值，同钟比较
        return (inZone || dragging || since < FADE_MS) ? 1f : 0f;
    }

    public static boolean isHoveringThumb(double mouseX, double mouseY,
                                          int trackX, int thumbY, int thumbH) {
        return mouseX >= trackX && mouseX < trackX + WIDTH
            && mouseY >= thumbY && mouseY < thumbY + thumbH;
    }

    public static boolean isInZone(double mouseX, int panelX, int panelW,
                                   double mouseY, int msgTop, int effectiveMsgBottom) {
        return mouseX >= panelX + panelW - HOVER_ZONE
            && mouseX <= panelX + panelW
            && mouseY >= msgTop && mouseY < effectiveMsgBottom;
    }

    public static void render(GuiGraphics g, ChatLayout layout, int mouseX, int mouseY,
                              int maxScroll, int messageTotalH, int scrollOffset,
                              boolean dragging, float alpha,
                              int effectiveMsgBottom, int colorRgb) {
        if (maxScroll <= 0) return;
        if (alpha <= 0.005f && !dragging) return;

        int trackX = layout.panelX() + layout.panelW() - WIDTH;
        int trackH = effectiveMsgBottom - layout.msgTop();
        int thumbH = thumbHeight(trackH, messageTotalH);
        int thumbY = thumbY(layout.msgTop(), trackH, thumbH, scrollOffset, maxScroll);

        // 纯色填充替代 drawWithAlpha：绕开消息路径 blend/flush 污染
        // （4844270a 同机制：上游 drawWithAlpha → scrollbar 渐显只显最后一帧）
        g.fill(trackX, layout.msgTop(), trackX + WIDTH, layout.msgTop() + trackH,
            ChatBubbleTheme.alphaBlend(colorRgb, (int) (0x1A * alpha)));

        float thumbBase = dragging ? 0xAA : isHoveringThumb(mouseX, mouseY, trackX, thumbY, thumbH) ? 0x88 : 0x66;
        g.fill(trackX, thumbY, trackX + WIDTH, thumbY + thumbH,
            ChatBubbleTheme.alphaBlend(colorRgb, (int) (thumbBase * alpha)));
    }
}
