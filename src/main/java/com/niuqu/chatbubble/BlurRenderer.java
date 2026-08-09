package com.niuqu.chatbubble;

/**
 * Cross-version panel blur / frosted-glass effect.
 *
 * <p>Real Gaussian blur requires framebuffer capture + post-processing shader,
 * which differs significantly across MC versions and is fragile in the
 * RenderPipeline era (1.21.5+). This implementation uses a layered
 * semi-transparent overlay that approximates the frosted-glass look
 * without touching GL state.</p>
 */
public class BlurRenderer {

    /**
     * Draw a frosted-glass effect over the given region.
     *
     * @param g DrawContext (1.20+) or MatrixStack (1.16.5–1.19.2)
     * @param x left edge (inclusive)
     * @param y top edge (inclusive)
     * @param w width
     * @param h height
     */
    public static void blurPanel(Object g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        // Layer 1: dark base — the main "darkening" that simulates blur
        RenderHelper.fill(g, x, y, x + w, y + h, 0x99000000);
        // Layer 2: subtle vertical gradient at top for frosted highlight
        int gradH = Math.min(h / 3, 20);
        if (gradH > 0) {
            for (int i = 0; i < gradH; i++) {
                int alpha = (int) (0x33 * (1.0f - (float) i / gradH));
                RenderHelper.fill(g, x, y + i, x + w, y + i + 1, (alpha << 24) | 0xFFFFFF);
            }
        }
    }
}
