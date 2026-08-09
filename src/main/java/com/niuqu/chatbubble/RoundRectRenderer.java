package com.niuqu.chatbubble;

//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif

/**
 * Rounded rectangle renderer using scan-line quarter-circle approximation.
 * Works across all MC versions via {@link RenderHelper#fill}.
 */
public class RoundRectRenderer {

    public static void resetShader() {
        // No custom shader needed — uses standard fill primitives.
    }

    /**
     * Fill a rounded rectangle. Corners are approximated with horizontal
     * scan lines that trace a quarter circle of the given radius.
     */
    public static void fill(Object g, int x1, int y1, int x2, int y2, float radius, int argb) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        radius = Math.min(radius, Math.min(w, h) / 2f);
        if (radius < 1) {
            RenderHelper.fill(g, x1, y1, x2, y2, argb);
            return;
        }
        int ir = (int) Math.ceil(radius);
        float r2 = radius * radius;

        // Center band (full width, excluding top/bottom corner rows)
        RenderHelper.fill(g, x1, y1 + ir, x2, y2 - ir, argb);
        // Top bar (between left and right corners)
        RenderHelper.fill(g, x1 + ir, y1, x2 - ir, y1 + ir, argb);
        // Bottom bar (between left and right corners)
        RenderHelper.fill(g, x1 + ir, y2 - ir, x2 - ir, y2, argb);

        // Corner scan lines — quarter circle approximation
        for (int i = 0; i < ir; i++) {
            float dy = radius - i - 0.5f;
            float dx = (float) Math.sqrt(Math.max(0, r2 - dy * dy));
            int dxi = (int) Math.ceil(dx);
            if (dxi <= 0) continue;

            // Top-left: center at (x1+ir, y1+ir)
            RenderHelper.fill(g, x1 + ir - dxi, y1 + i, x1 + ir, y1 + i + 1, argb);
            // Top-right: center at (x2-ir, y1+ir)
            RenderHelper.fill(g, x2 - ir, y1 + i, x2 - ir + dxi, y1 + i + 1, argb);
            // Bottom-left: center at (x1+ir, y2-ir)
            RenderHelper.fill(g, x1 + ir - dxi, y2 - i - 1, x1 + ir, y2 - i, argb);
            // Bottom-right: center at (x2-ir, y2-ir)
            RenderHelper.fill(g, x2 - ir, y2 - i - 1, x2 - ir + dxi, y2 - i, argb);
        }
    }
}
