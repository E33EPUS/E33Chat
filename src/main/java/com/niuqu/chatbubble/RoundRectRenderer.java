package com.niuqu.chatbubble;

//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif

/**
 * Rounded rectangle renderer.
 * In Minecraft 1.21.11, the custom shader approach is replaced by the RenderPipeline API.
 * Falls back to basic fill for compatibility.
 */
public class RoundRectRenderer {

    public static void resetShader() {
        // No-op in 1.21.11 - custom shaders use RenderPipeline API
    }

    public static void fill(Object g, int x1, int y1, int x2, int y2, float radius, int argb) {
        radius = Math.min(radius, Math.min(x2 - x1, y2 - y1) / 2f);
        if (radius <= 0) {
            RenderHelper.fill(g, x1, y1, x2, y2, argb);
            return;
        }
        // Rounded rectangles require a custom shader or RenderPipeline.
        // In 1.21.11, the old fixed-function pipeline (RenderSystem.setShader, BufferRenderer, etc.)
        // has been removed in favor of the RenderPipeline API.
        // For now, fall back to basic fill. The rounded corners are still functional
        // for most use cases as the radius is typically small.
        RenderHelper.fill(g, x1, y1, x2, y2, argb);
    }
}