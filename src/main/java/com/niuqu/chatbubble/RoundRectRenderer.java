package com.niuqu.chatbubble;

//#if MC >= 12000
//#if MC < 12102
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.LoggerFactory;
//#endif
//#endif

/**
 * Rounded rectangle renderer.
 *
 * <p>MC 1.20.0 - 1.21.1: uses a signed-distance-field fragment shader for
 * anti-aliased rounded corners (standard sdRoundedBox by Inigo Quilez).</p>
 *
 * <p>MC &lt; 1.20.0 and MC &ge; 1.21.2: uses per-pixel SDF coverage
 * anti-aliasing via {@link RenderHelper#fill}. Fully-covered pixels are
 * batched into horizontal runs; only circle-boundary pixels invoke
 * individual fill calls with proportional alpha for smooth edges.</p>
 */
public class RoundRectRenderer {

    //#if MC >= 12000
    //#if MC < 12102
    private static ShaderProgram shader;
    private static boolean loadAttempted;

    private static ShaderProgram getShader() {
        if (!loadAttempted) {
            loadAttempted = true;
            try {
                shader = new ShaderProgram(
                    MinecraftClient.getInstance().getResourceManager(),
                    "rendertype_round_rect",
                    VertexFormats.POSITION_COLOR);
            } catch (Exception e) {
                LoggerFactory.getLogger("e33chat")
                    .warn("[e33chat] round rect shader failed to load, falling back to square corners", e);
            }
        }
        return shader;
    }
    //#endif
    //#endif

    public static void resetShader() {
        //#if MC >= 12000
        //#if MC < 12102
        loadAttempted = false;
        shader = null;
        //#endif
        //#endif
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

        //#if MC >= 12000
        //#if MC < 12102
        DrawContext ctx = (DrawContext) g;
        ShaderProgram sh = getShader();
        if (sh == null || radius <= 0) {
            ctx.fill(x1, y1, x2, y2, argb);
            return;
        }
        ctx.draw();

        Matrix4f pose = ctx.getMatrices().peek().getPositionMatrix();
        Vector4f center = pose.transform(new Vector4f((x1 + x2) / 2f, (y1 + y2) / 2f, 0f, 1f));

        GlUniform uRect = sh.getUniform("u_Rect");
        GlUniform uRadius = sh.getUniform("u_Radius");
        if (uRect == null || uRadius == null) {
            ctx.fill(x1, y1, x2, y2, argb);
            return;
        }
        uRect.set(0, center.x);
        uRect.set(1, center.y);
        uRect.set(2, (x2 - x1) / 2f);
        uRect.set(3, (y2 - y1) / 2f);
        uRadius.set(0, radius);

        float a = (argb >>> 24) / 255f;
        float r = (argb >> 16 & 0xFF) / 255f;
        float gr = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        RenderSystem.setShader(() -> sh);

        //#if MC >= 12100
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        //#else
        //$$ BufferBuilder bb = Tessellator.getInstance().getBuffer();
        //$$ bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        //#endif
        bb.vertex(pose, x1, y1, 0).color(r, gr, b, a);
        bb.vertex(pose, x1, y2, 0).color(r, gr, b, a);
        bb.vertex(pose, x2, y2, 0).color(r, gr, b, a);
        bb.vertex(pose, x2, y1, 0).color(r, gr, b, a);
        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.disableBlend();
        //#else
        //$$ scanLineFill(g, x1, y1, x2, y2, radius, argb);
        //#endif
        //#else
        //$$ scanLineFill(g, x1, y1, x2, y2, radius, argb);
        //#endif
    }

    private static void scanLineFill(Object g, int x1, int y1, int x2, int y2, float radius, int argb) {
        if (radius < 1) {
            RenderHelper.fill(g, x1, y1, x2, y2, argb);
            return;
        }

        int ir = (int) Math.ceil(radius);
        int baseAlpha = (argb >>> 24) & 0xFF;
        int rgb = argb & 0x00FFFFFF;

        // Solid rectangular regions (no corner rounding needed)
        RenderHelper.fill(g, x1, y1 + ir, x2, y2 - ir, argb);
        RenderHelper.fill(g, x1 + ir, y1, x2 - ir, y1 + ir, argb);
        RenderHelper.fill(g, x1 + ir, y2 - ir, x2 - ir, y2, argb);

        // Anti-aliased corners via per-pixel SDF coverage
        // Circle center relative to each corner's origin:
        //   left corners: ccx = radius;  right corners: ccx = ir - radius
        //   top  corners: ccy = radius;  bottom corners: ccy = ir - radius
        float ccxL = radius;
        float ccxR = ir - radius;
        float ccyT = radius;
        float ccyB = ir - radius;

        for (int py = 0; py < ir; py++) {
            float pcy = py + 0.5f;
            drawAaCornerRow(g, x1,       y1 + py,         ir, pcy, ccxL, ccyT, radius, baseAlpha, rgb);
            drawAaCornerRow(g, x2 - ir,  y1 + py,         ir, pcy, ccxR, ccyT, radius, baseAlpha, rgb);
            drawAaCornerRow(g, x1,       y2 - ir + py,    ir, pcy, ccxL, ccyB, radius, baseAlpha, rgb);
            drawAaCornerRow(g, x2 - ir,  y2 - ir + py,    ir, pcy, ccxR, ccyB, radius, baseAlpha, rgb);
        }
    }

    /**
     * Draws one pixel row of a rounded corner with anti-aliased edges.
     *
     * <p>Uses a signed-distance-field approach: for each pixel, computes the
     * distance from the pixel center to the corner circle center, then maps
     * it to a coverage value in [0, 1] with a 1-pixel-wide transition band.
     * Fully-covered pixels are batched into horizontal runs for efficiency;
     * only boundary pixels invoke individual fill calls with partial alpha.</p>
     *
     * @param originX    corner region left edge (screen coordinate)
     * @param originY    top of this pixel row (screen coordinate)
     * @param ir         corner region width in pixels
     * @param pcy        pixel center y relative to corner origin
     * @param ccx        circle center x relative to corner origin
     * @param ccy        circle center y relative to corner origin
     * @param radius     corner radius
     * @param baseAlpha  base alpha (0-255)
     * @param rgb        RGB color bits (without alpha)
     */
    private static void drawAaCornerRow(Object g, int originX, int originY, int ir,
                                        float pcy, float ccx, float ccy,
                                        float radius, int baseAlpha, int rgb) {
        float dy = pcy - ccy;
        float dy2 = dy * dy;
        float r2 = radius * radius;
        if (dy2 >= r2) return;                        // row center outside circle

        float dx = (float) Math.sqrt(r2 - dy2);
        float edgeLeft  = ccx - dx;
        float edgeRight = ccx + dx;

        // Clip to corner region [0, ir]
        float fillLeft  = Math.max(0, edgeLeft);
        float fillRight = Math.min(ir, edgeRight);
        if (fillRight <= fillLeft) return;

        int pxStart = Math.max(0, (int) Math.floor(fillLeft));
        int pxEnd   = Math.min(ir, (int) Math.ceil(fillRight));

        // Precompute squared thresholds to avoid sqrt for non-boundary pixels
        float rInSq  = (radius - 0.5f) * (radius - 0.5f);
        float rOutSq = (radius + 0.5f) * (radius + 0.5f);
        int fullColor = (baseAlpha << 24) | rgb;

        int runStart = -1;
        for (int px = pxStart; px < pxEnd; px++) {
            float pcx = px + 0.5f;
            float distSq = (pcx - ccx) * (pcx - ccx) + dy2;

            if (distSq <= rInSq) {
                // Fully inside circle — extend or start a run
                if (runStart < 0) runStart = px;
            } else if (distSq >= rOutSq) {
                // Fully outside circle — flush any pending run
                if (runStart >= 0) {
                    RenderHelper.fill(g, originX + runStart, originY, originX + px, originY + 1, fullColor);
                    runStart = -1;
                }
            } else {
                // Anti-aliasing band — partial coverage
                if (runStart >= 0) {
                    RenderHelper.fill(g, originX + runStart, originY, originX + px, originY + 1, fullColor);
                    runStart = -1;
                }
                float dist = (float) Math.sqrt(distSq);
                float cov = (radius + 0.5f) - dist;
                int a = (int) (baseAlpha * cov);
                RenderHelper.fill(g, originX + px, originY, originX + px + 1, originY + 1, (a << 24) | rgb);
            }
        }
        // Flush remaining full-coverage run
        if (runStart >= 0) {
            RenderHelper.fill(g, originX + runStart, originY, originX + pxEnd, originY + 1, fullColor);
        }
    }
}
