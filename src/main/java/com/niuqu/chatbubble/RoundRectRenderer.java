package com.niuqu.chatbubble;

//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
import net.minecraft.util.Identifier;

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
        if (radius < 1) {
            RenderHelper.fill(g, x1, y1, x2, y2, argb);
            return;
        }
        //#if MC >= 12000
        // 1.20+: GPU-filtered corner masks (see fillMasked). The CPU scanline
        // cannot reach sub-GUI-pixel smoothness at higher GUI scales.
        fillMasked(g, x1, y1, x2, y2, radius, argb);
        //#else
        //$$ scanLineFill(g, x1, y1, x2, y2, radius, argb);
        //#endif
    }

    //#if MC >= 12000
    /**
     * Mask-based rounded rect: the straight edges are plain per-row fills (no
     * internal shared-edge seams), the 4 corners are 4x-baked SDF mask textures
     * drawn downscaled with linear filtering — the GPU interpolates the alpha
     * per physical pixel, giving shader-quality corner curves at any GUI scale.
     */
    private static void fillMasked(Object g, int x1, int y1, int x2, int y2, float radius, int argb) {
        int R = Math.max(1, (int) Math.round(radius));
        int w = x2 - x1;
        int h = y2 - y1;
        int baseAlpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0x00FFFFFF;
        int fullColor = (baseAlpha << 24) | rgb;

        // Per-row strips: the corner squares stay empty (the masks cover them).
        for (int py = 0; py < h; py++) {
            int rowY = y1 + py;
            if (py >= R && py < h - R) {
                RenderHelper.fill(g, x1, rowY, x2, rowY + 1, fullColor);
            } else if (w > 2 * R) {
                RenderHelper.fill(g, x1 + R, rowY, x2 - R, rowY + 1, fullColor);
            }
        }

        Identifier mask = cornerMask(R);
        if (mask == null) return;
        net.minecraft.client.gui.DrawContext dc = (net.minecraft.client.gui.DrawContext) g;
        int m = R * 4;
        DrawHelper.drawTexture(dc, mask, x1, y1, R, R, 0f, 0f, m, m, m, m, argb);
        DrawHelper.drawTexture(dc, mask, x2 - R, y1, R, R, 0f, 0f, m, m, m, m, argb);
        DrawHelper.drawTexture(dc, mask, x1, y2 - R, R, R, 0f, 0f, m, m, m, m, argb);
        DrawHelper.drawTexture(dc, mask, x2 - R, y2 - R, R, R, 0f, 0f, m, m, m, m, argb);
    }

    private static final java.util.Map<Integer, Identifier> MASK_CACHE = new java.util.HashMap<>();

    /**
     * Lazily bakes a white round-corner mask for an integer radius: 4x the draw
     * size with a 1px SDF coverage band, so the downscaled draw is filtered into
     * a smooth sub-pixel alpha gradient. The mask is drawn tinted by the fill
     * color (GUI_TEXTURED multiplies texture x color, white RGB passes through).
     */
    private static Identifier cornerMask(int radius) {
        Identifier id = MASK_CACHE.get(radius);
        if (id != null) return id;
        try {
            int m = radius * 4;
            net.minecraft.client.texture.NativeImage img =
                new net.minecraft.client.texture.NativeImage(net.minecraft.client.texture.NativeImage.Format.RGBA, m, m, false);
            for (int my = 0; my < m; my++) {
                for (int mx = 0; mx < m; mx++) {
                    // draw-space position of this mask texel's center (mask is 4x)
                    float px = (mx + 0.5f) / 4f;
                    float py = (my + 0.5f) / 4f;
                    float dx = px - radius;
                    float dy = py - radius;
                    float d = (float) Math.sqrt(dx * dx + dy * dy);
                    float cov = radius + 0.5f - d;
                    int a = (int) (255 * (cov < 0f ? 0f : (cov > 1f ? 1f : cov)));
                    int argb = (a << 24) | 0x00FFFFFF; // white + alpha
                    //#if MC >= 12102
                    img.setColorArgb(mx, my, argb);
                    //#else
                    //#if MC >= 11800
                    //$$ img.setColor(mx, my, (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16));
                    //#else
                    //$$ img.setPixelColor(mx, my, (argb & 0xFF00FF00) | ((argb & 0x00FF0000) >> 16) | ((argb & 0x000000FF) << 16));
                    //#endif
                    //#endif
                }
            }
            Identifier tex;
            //#if MC >= 11900
            tex = Identifier.of("e33chat", "corner_" + radius);
            //#else
            //$$ tex = new Identifier("e33chat", "corner_" + radius);
            //#endif
            net.minecraft.client.texture.TextureManager tm =
                net.minecraft.client.MinecraftClient.getInstance().getTextureManager();
            //#if MC >= 12105
            tm.registerTexture(tex, new net.minecraft.client.texture.NativeImageBackedTexture(() -> tex.toString(), img));
            //#else
            //$$ tm.registerTexture(tex, new net.minecraft.client.texture.NativeImageBackedTexture(img));
            //#endif
            MASK_CACHE.put(radius, tex);
            return tex;
        } catch (Throwable t) {
            E33Log.warn("[e33chat] corner mask gen failed: {}", String.valueOf(t));
            return null;
        }
    }
    //#endif

    /**
     * Half-width of the SDF coverage band, in GUI px (fallback path, MC < 1.20).
     */
    private static final float BAND = 0.75f;

    /**
     * Supersampling factor per axis (fallback path, MC < 1.20).
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
     * Rounded-rect SDF coverage for one GUI pixel, averaged over N×N sub-samples
     * (fallback path, MC < 1.20).
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