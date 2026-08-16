package com.niuqu.chatbubble.render;

/**
 * Scrollbar geometry shared by SmoothScrollPane on Fabric.
 *
 * Fabric keeps the config-screen scrollbar rendering inline (DrawContext +
 * ColoredTextureRenderer), so this class carries only the pure geometry the
 * extracted pane needs; it intentionally has no render method.
 */
public final class ChatScrollbar {
    public static final int WIDTH = 6;
    private static final int MIN_THUMB_H = 8;

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
}
