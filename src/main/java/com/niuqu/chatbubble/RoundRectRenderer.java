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
 * <p>MC &lt; 1.20.0 and MC &ge; 1.21.2: falls back to scan-line quarter-circle
 * approximation via {@link RenderHelper#fill}.</p>
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
        try {
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
        } catch (Throwable t) {
            ctx.fill(x1, y1, x2, y2, argb);
        }
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

            RenderHelper.fill(g, x1 + ir - dxi, y1 + i, x1 + ir, y1 + i + 1, argb);
            RenderHelper.fill(g, x2 - ir, y1 + i, x2 - ir + dxi, y1 + i + 1, argb);
            RenderHelper.fill(g, x1 + ir - dxi, y2 - i - 1, x1 + ir, y2 - i, argb);
            RenderHelper.fill(g, x2 - ir, y2 - i - 1, x2 - ir + dxi, y2 - i, argb);
        }
    }
}
