package com.niuqu.chatbubble;

import net.minecraft.client.gui.GuiGraphics;

/**
 * 几何绘制原语：圆角填充/描边/裁剪/命中 + 透明度合成，全项目统一入口。
 * 填充委托 RoundRectRenderer 的 shader 实现（更快、抗锯齿）；描边/裁剪/命中
 * 用逐行算法，无 shader 依赖。
 */
public final class UiPrimitives {
    private UiPrimitives() {}

    public static int withOpacity(int color, float opacity) {
        int sourceAlpha = color >>> 24;
        int alpha = Math.round(sourceAlpha * Math.max(0f, Math.min(1f, opacity)));
        return (alpha << 24) | (color & 0x00FFFFFF);
    }

    /** 圆角填充（委托 shader，失败时回退方形）。 */
    public static void fillRounded(GuiGraphics g, int x1, int y1, int x2, int y2, int radius, int color) {
        RoundRectRenderer.fill(g, x1, y1, x2, y2, radius, color);
    }

    /** 圆角描边（逐行）。 */
    public static void strokeRounded(GuiGraphics g, int x1, int y1, int x2, int y2, int radius, int width, int color) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0 || (color >>> 24) == 0) return;
        int safeWidth = Math.max(0, Math.min(width, Math.min(w, h) / 2));
        if (safeWidth == 0) return;
        int safeRadius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        for (int row = 0; row < h; row++) {
            int outerInset = roundedInset(h, safeRadius, row);
            int outerLeft = x1 + outerInset;
            int outerRight = x2 - outerInset;
            int innerRow = row - safeWidth;
            int innerHeight = h - safeWidth * 2;
            if (innerRow < 0 || innerRow >= innerHeight || w <= safeWidth * 2) {
                g.fill(outerLeft, y1 + row, outerRight, y1 + row + 1, color);
                continue;
            }
            int innerRadius = Math.max(0, safeRadius - safeWidth);
            int innerInset = roundedInset(innerHeight, innerRadius, innerRow);
            int innerLeft = x1 + safeWidth + innerInset;
            int innerRight = x2 - safeWidth - innerInset;
            g.fill(outerLeft, y1 + row, Math.min(outerRight, innerLeft), y1 + row + 1, color);
            g.fill(Math.max(outerLeft, innerRight), y1 + row, outerRight, y1 + row + 1, color);
        }
    }

    /** 在圆角区域内执行绘制（scissor 逐行裁切）。 */
    public static void withRoundedClip(GuiGraphics g, int x1, int y1, int x2, int y2, int radius, Runnable draw) {
        int w = x2 - x1, h = y2 - y1;
        if (w <= 0 || h <= 0 || draw == null) return;
        int safeRadius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (safeRadius == 0) {
            g.enableScissor(x1, y1, x2, y2);
            try {
                draw.run();
            } finally {
                g.disableScissor();
            }
            return;
        }
        int middleTop = y1 + safeRadius;
        int middleBottom = y2 - safeRadius;
        if (middleBottom > middleTop) {
            drawClippedBand(g, x1, middleTop, x2, middleBottom, draw);
        }
        for (int row = 0; row < safeRadius; row++) {
            int inset = cornerInset(safeRadius, row);
            int left = x1 + inset;
            int right = x2 - inset;
            drawClippedBand(g, left, y1 + row, right, y1 + row + 1, draw);
            drawClippedBand(g, left, y2 - row - 1, right, y2 - row, draw);
        }
    }

    /** 圆角区域命中检测。 */
    public static boolean containsRounded(int x1, int y1, int x2, int y2, int radius, int mx, int my) {
        if (mx < x1 || mx >= x2 || my < y1 || my >= y2) return false;
        int w = x2 - x1, h = y2 - y1;
        int safeRadius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (safeRadius == 0) return true;
        int row = my - y1;
        int edgeRow = Math.min(row, h - 1 - row);
        int edgeInset = edgeRow >= safeRadius ? 0 : cornerInset(safeRadius, edgeRow);
        return mx >= x1 + edgeInset && mx < x2 - edgeInset;
    }

    private static void drawClippedBand(GuiGraphics g, int left, int top, int right, int bottom, Runnable draw) {
        if (right <= left || bottom <= top) return;
        g.enableScissor(left, top, right, bottom);
        try {
            draw.run();
        } finally {
            g.disableScissor();
        }
    }

    private static int roundedInset(int height, int radius, int row) {
        if (radius <= 0 || (row >= radius && row < height - radius)) return 0;
        int edgeRow = Math.min(row, height - 1 - row);
        return cornerInset(radius, edgeRow);
    }

    private static int cornerInset(int radius, int row) {
        double centerDistance = radius - row - 0.5D;
        double horizontal = Math.sqrt(Math.max(0.0D, radius * radius - centerDistance * centerDistance));
        return Math.max(0, radius - (int) Math.floor(horizontal));
    }
}
