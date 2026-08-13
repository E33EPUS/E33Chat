//#if MC < 12000
package net.minecraft.client.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumerProvider;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;
//#if MC < 11903
import net.minecraft.util.math.Matrix4f;
//#endif
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;

/**
 * Polyfill DrawContext for pre-1.20 versions (1.16.5, 1.18.2).
 * Wraps a MatrixStack and delegates rendering to DrawableHelper / TextRenderer.
 */
public class DrawContext extends DrawableHelper {
    private final MatrixStack matrices;
    private final VertexConsumerProvider.Immediate immediate;

    public DrawContext(MatrixStack matrices) {
        this.matrices = matrices;
        this.immediate = VertexConsumerProvider.immediate(Tessellator.getInstance().getBuffer());
    }

    public MatrixStack getMatrices() {
        return matrices;
    }

    public void draw() {
        immediate.draw();
    }

    // ---- fill ----
    public void fill(int x1, int y1, int x2, int y2, int color) {
        DrawableHelper.fill(matrices, x1, y1, x2, y2, color);
    }

    public void fillGradient(int x1, int y1, int x2, int y2, int color1, int color2) {
        fillGradient(matrices, x1, y1, x2, y2, color1, color2);
    }

    // ---- drawText (3 overloads) ----
    public int drawText(TextRenderer renderer, Text text, int x, int y, int color, boolean shadow) {
        return drawText(renderer, text.asOrderedText(), x, y, color, shadow);
    }

    public int drawText(TextRenderer renderer, String text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            return renderer.drawWithShadow(matrices, text, x, y, color);
        } else {
            return renderer.draw(matrices, text, x, y, color);
        }
    }

    public int drawText(TextRenderer renderer, OrderedText text, int x, int y, int color, boolean shadow) {
        if (shadow) {
            return renderer.drawWithShadow(matrices, text, x, y, color);
        } else {
            return renderer.draw(matrices, text, x, y, color);
        }
    }

    // ---- drawTexture ----
    // Simple 7-param: (Identifier, x, y, u, v, w, h)
    public void drawTexture(Identifier texture, int x, int y, float u, float v, int width, int height) {
        drawTexture(texture, x, y, width, height, u, v, width, height, 256, 256);
    }

    // 8 param: (Identifier, x, y, u, v, w, h, regionSize)
    public void drawTexture(Identifier texture, int x, int y, float u, float v, int width, int height, int regionWidth, int regionHeight) {
        drawTexture(texture, x, y, width, height, u, v, regionWidth, regionHeight, 256, 256);
    }

    // 10 param: (Identifier, x, y, w, h, u, v, regionW, regionH, texW, texH)
    public void drawTexture(Identifier texture, int x, int y, int width, int height,
                            float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        //#if MC >= 11800
        RenderSystem.setShaderTexture(0, texture);
        DrawableHelper.drawTexture(matrices, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        //#else
        //$$ RenderSystem.setShaderTexture(0, texture);
        //$$ Matrix4f model = matrices.peek().getModel();
        //$$ drawTexturedQuad(model, x, x + width, y, y + height,
        //$$         u / textureWidth, (u + regionWidth) / textureWidth,
        //$$         v / textureHeight, (v + regionHeight) / textureHeight);
        //#endif
    }

    // 11 param: (Identifier, x, y, u, v, w, h, regionW, regionH, texW, texH) with int u,v
    public void drawTexture(Identifier texture, int x, int y, int width, int height,
                            int u, int v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        drawTexture(texture, x, y, width, height, (float) u, (float) v, regionWidth, regionHeight, textureWidth, textureHeight);
    }

    //#if MC < 11800
    //$$ private static void drawTexturedQuad(Matrix4f matrix, int x1, int x2, int y1, int y2,
    //$$         float u1, float u2, float v1, float v2) {
    //$$     RenderSystem.setShader(GameRenderer::getPositionTexShader);
    //$$     BufferBuilder buffer = Tessellator.getInstance().getBuffer();
    //$$     buffer.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE);
    //$$     buffer.vertex(matrix, x1, y2, 0).texture(u1, v2).next();
    //$$     buffer.vertex(matrix, x2, y2, 0).texture(u2, v2).next();
    //$$     buffer.vertex(matrix, x2, y1, 0).texture(u2, v1).next();
    //$$     buffer.vertex(matrix, x1, y1, 0).texture(u1, v1).next();
    //$$     buffer.end();
    //$$     BufferRenderer.draw(buffer);
    //$$ }
    //#endif

    // ---- scissor ----
    //#if MC < 11900
    public void enableScissor(int x1, int y1, int x2, int y2) {
        RenderSystem.enableScissor(x1, y1, x2, y2);
    }

    public void disableScissor() {
        RenderSystem.disableScissor();
    }
    //#endif

    // ---- drawTooltip (stubs - actual tooltip rendering handled by GuiCompat for pre-1.20) ----
    public void drawTooltip(TextRenderer renderer, Text text, int x, int y) {
        // Pre-1.20 tooltip rendering is handled via Screen.renderTooltip in GuiCompat
    }

    public void drawTooltip(TextRenderer renderer, java.util.List<OrderedText> lines, Object positioner, int x, int y, boolean useShadow) {
        // Pre-1.20 tooltip rendering is handled via Screen.renderTooltip in GuiCompat
    }
}
//#endif
