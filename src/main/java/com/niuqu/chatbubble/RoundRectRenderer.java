package com.niuqu.chatbubble;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlUniform;
import net.minecraft.client.gl.ShaderProgram;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.*;
import org.joml.Matrix4f;
import org.joml.Vector4f;
import org.slf4j.LoggerFactory;

public class RoundRectRenderer {
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

    public static void resetShader() {
        loadAttempted = false;
        shader = null;
    }

    public static void fill(DrawContext g, int x1, int y1, int x2, int y2, float radius, int argb) {
        ShaderProgram sh = getShader();
        radius = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2f);
        if (sh == null || radius <= 0) {
            g.fill(x1, y1, x2, y2, argb);
            return;
        }
        g.draw();

        Matrix4f pose = g.getMatrices().peek().getPositionMatrix();
        Vector4f center = pose.transform(new Vector4f((x1 + x2) / 2f, (y1 + y2) / 2f, 0f, 1f));

        GlUniform uRect = sh.getUniform("u_Rect");
        GlUniform uRadius = sh.getUniform("u_Radius");
        if (uRect == null || uRadius == null) {
            g.fill(x1, y1, x2, y2, argb);
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

        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_COLOR);
        bb.vertex(pose, x1, y1, 0).color(r, gr, b, a);
        bb.vertex(pose, x1, y2, 0).color(r, gr, b, a);
        bb.vertex(pose, x2, y2, 0).color(r, gr, b, a);
        bb.vertex(pose, x2, y1, 0).color(r, gr, b, a);
        BufferRenderer.drawWithGlobalProgram(bb.end());

        RenderSystem.disableBlend();
    }
}
