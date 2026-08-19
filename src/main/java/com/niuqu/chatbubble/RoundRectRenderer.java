package com.niuqu.chatbubble;

//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif

/**
 * Rounded rectangle renderer.
 *
 * <p>Uses per-pixel SDF coverage anti-aliasing via {@link RenderHelper#fill}
 * (a pure GUI-operation, no raw GL / RenderSystem state changes). Fully-covered
 * pixels are batched into horizontal runs; only circle-boundary pixels invoke
 * individual fill calls with proportional alpha for smooth edges.</p>
 *
 * <p>IMPORTANT: an earlier revision used a custom SDF fragment shader
 * (RenderSystem.setShader / BufferRenderer.drawWithGlobalProgram) for
 * MC 1.20.0 - 1.21.1. That path leaked blend/shader state and caused a black
 * screen when exiting a server. It has been removed — this renderer now only
 * ever calls {@link RenderHelper#fill}, which goes through DrawContext.fill()
 * and is safe inside Minecraft's render pipeline.</p>
 */
public class RoundRectRenderer {

    public static void resetShader() {
        // No-op — no custom shader is used.
    }

    /**
     * Fill a rounded rectangle.
     *
     * @param g      DrawContext (1.20+) or MatrixStack (1.16.5-1.19.2)
     * @param x1     left edge
     * @param y1     top edge
     * @param x2     right edge
     * @param y2     bottom edge
     * @param radius corner radius
     * @param argb   color (ARGB int)
     */
    public static void fill(Object g, int x1, int y1, int x2, int y2, float radius, int argb) {
        int w = x2 - x1;
        int h = y2 - y1;
        if (w <= 0 || h <= 0) return;
        radius = Math.min(radius, Math.min(w, h) / 2f);

        scanLineFill(g, x1, y1, x2, y2, radius, argb);
    }

    /**
     * Half-width of the SDF coverage band, in GUI px. A wider band spreads the
     * alpha transition over several pixels, masking the GUI-pixel staircase that
     * shows at higher GUI scales (the GPU shader samples per screen pixel; the
     * CPU can only step per GUI pixel, so a wider band is the close equivalent).
     */
    private static final float BAND = 0.75f;

    /**
     * Supersampling factor per axis: each GUI pixel is split into N×N sub-samples
     * and the SDF coverage is averaged over them for a smooth alpha gradient.
     */
    private static final int N = 4;

    private static void scanLineFill(Object g, int x1, int y1, int x2, int y2, float radius, int argb) {
        if (radius < 1) {
            RenderHelper.fill(g, x1, y1, x2, y2, argb);
            return;
        }

        int w = x2 - x1;
        int h = y2 - y1;
        int cr = (int) Math.ceil(radius + BAND);
        int baseAlpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0x00FFFFFF;
        int fullColor = (baseAlpha << 24) | rgb;

        // Per-row scanline: every row is drawn as one set of contiguous runs, so
        // the old "3 solid rects + 4 corner grids" structure's internal horizontal
        // seams (which showed as thin lines on the deferred 1.21.11 renderer) are
        // impossible by construction. Corner coverage is supersampled per pixel.
        for (int py = 0; py < h; py++) {
            int rowY = y1 + py;
            if (py >= cr && py < h - cr) {
                // Straight middle rows: one full run.
                RenderHelper.fill(g, x1, rowY, x2, rowY + 1, fullColor);
                continue;
            }
            int runStart = -1;
            for (int px = 0; px < w; px++) {
                float cov = pixelCoverage(px, py, w, h, radius, cr);
                if (cov >= 1f) {
                    // Fully inside — extend or start a run
                    if (runStart < 0) runStart = px;
                } else if (cov <= 0f) {
                    // Fully outside — flush any pending run
                    if (runStart >= 0) {
                        RenderHelper.fill(g, x1 + runStart, rowY, x1 + px, rowY + 1, fullColor);
                        runStart = -1;
                    }
                } else {
                    // Anti-aliasing band — partial coverage
                    if (runStart >= 0) {
                        RenderHelper.fill(g, x1 + runStart, rowY, x1 + px, rowY + 1, fullColor);
                        runStart = -1;
                    }
                    int a = (int) (baseAlpha * cov);
                    RenderHelper.fill(g, x1 + px, rowY, x1 + px + 1, rowY + 1, (a << 24) | rgb);
                }
            }
            // Flush remaining full-coverage run
            if (runStart >= 0) {
                RenderHelper.fill(g, x1 + runStart, rowY, x1 + w, rowY + 1, fullColor);
            }
        }
    }

    /**
     * Rounded-rect SDF coverage for one GUI pixel, averaged over N×N sub-samples.
     * Pixels outside the four corner squares are fully inside (the straight
     * edges). The circle center is picked by the pixel's half-plane so narrow
     * rects whose corner regions overlap still get the correct nearest-arc value.
     */
    private static float pixelCoverage(int px, int py, int w, int h, float radius, int cr) {
        boolean nearLeft = px < cr;
        boolean nearRight = px >= w - cr;
        boolean nearTop = py < cr;
        boolean nearBottom = py >= h - cr;
        if (!((nearLeft || nearRight) && (nearTop || nearBottom))) return 1f;
        float cx = (px < w / 2f) ? radius : w - radius;
        float cy = (py < h / 2f) ? radius : h - radius;
        float covSum = 0f;
        for (int sy = 0; sy < N; sy++) {
            float pyy = py + (sy + 0.5f) / N;
            float dy = pyy - cy;
            float dy2 = dy * dy;
            for (int sx = 0; sx < N; sx++) {
                float pxx = px + (sx + 0.5f) / N;
                float dx = pxx - cx;
                float d = (float) Math.sqrt(dx * dx + dy2);
                float cov = (radius + BAND - d) / (2 * BAND);
                covSum += cov < 0f ? 0f : (cov > 1f ? 1f : cov);
            }
        }
        return covSum / (N * N);
    }
}