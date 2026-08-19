package com.niuqu.chatbubble;

import net.minecraft.client.MinecraftClient;
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import com.mojang.blaze3d.systems.RenderSystem;
//$$ import net.minecraft.client.gui.DrawableHelper;
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
//#if MC >= 12106
import net.minecraft.client.gui.ScreenRect;
import org.joml.Matrix3x2f;
//#endif
import net.minecraft.client.font.TextRenderer;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Cross-version GUI rendering facade.
 *
 * <p>1.20+ receives {@code DrawContext}; 1.16.5/1.18.2/1.19.2 receives
 * {@code MatrixStack}. Public methods intentionally accept {@code Object} so
 * version-specific call sites can pass either type after preprocessing.</p>
 */
public final class RenderHelper {
    private RenderHelper() {}

    // --- Alpha multiplier for cross-version fade animations ---
    // MC >= 1.21.2: applied through color parameters (setShaderColor removed)
    // MC <  1.21.2: not used (ChatBubbleScreen uses RenderSystem.setShaderColor instead)
    private static float alphaMultiplier = 1f;

    public static void setAlphaMultiplier(float alpha) {
        alphaMultiplier = Math.max(0f, Math.min(1f, alpha));
    }

    public static float getAlphaMultiplier() { return alphaMultiplier; }

    public static void resetAlphaMultiplier() { alphaMultiplier = 1f; }

    private static int applyAlpha(int color) {
        if (alphaMultiplier >= 0.999f) return color;
        int a = (color >>> 24) & 0xFF;
        a = (int) (a * alphaMultiplier);
        return (a << 24) | (color & 0x00FFFFFF);
    }

    public static void drawText(Object ctx, TextRenderer tr, Text text, int x, int y, int color, boolean shadow) {
        color = applyAlpha(color);
        //#if MC >= 12000
        ((DrawContext) ctx).drawText(tr, text, x, y, color, shadow);
        //#else
        //$$ if (shadow) {
        //$$     tr.drawWithShadow((MatrixStack) ctx, text, x, y, color);
        //$$ } else {
        //$$     tr.draw((MatrixStack) ctx, text, x, y, color);
        //$$ }
        //#endif
    }

    public static void drawText(Object ctx, TextRenderer tr, String text, int x, int y, int color, boolean shadow) {
        color = applyAlpha(color);
        //#if MC >= 12000
        ((DrawContext) ctx).drawText(tr, text, x, y, color, shadow);
        //#else
        //$$ if (shadow) {
        //$$     tr.drawWithShadow((MatrixStack) ctx, text, x, y, color);
        //$$ } else {
        //$$     tr.draw((MatrixStack) ctx, text, x, y, color);
        //$$ }
        //#endif
    }

    public static void drawText(Object ctx, TextRenderer tr, OrderedText text, int x, int y, int color, boolean shadow) {
        color = applyAlpha(color);
        //#if MC >= 12000
        ((DrawContext) ctx).drawText(tr, text, x, y, color, shadow);
        //#else
        //$$ if (shadow) {
        //$$     tr.drawWithShadow((MatrixStack) ctx, text, x, y, color);
        //$$ } else {
        //$$     tr.draw((MatrixStack) ctx, text, x, y, color);
        //$$ }
        //#endif
    }

    public static void fill(Object ctx, int x1, int y1, int x2, int y2, int color) {
        color = applyAlpha(color);
        //#if MC >= 12000
        ((DrawContext) ctx).fill(x1, y1, x2, y2, color);
        //#else
        //$$ DrawableHelper.fill((MatrixStack) ctx, x1, y1, x2, y2, color);
        //#endif
    }

    public static void fillGradient(Object ctx, int x1, int y1, int x2, int y2, int c1, int c2) {
        c1 = applyAlpha(c1);
        c2 = applyAlpha(c2);
        //#if MC >= 12000
        ((DrawContext) ctx).fillGradient(x1, y1, x2, y2, c1, c2);
        //#else
        //$$ DrawableHelper.fill((MatrixStack) ctx, x1, y1, x2, y2, c1);
        //#endif
    }

    public static void drawTexture(Object ctx, Identifier texture, int x, int y, float u, float v,
                                   int width, int height, int textureWidth, int textureHeight) {
        //#if MC >= 12000
        //#if MC >= 12102
        // MC >= 1.21.2: use color overload so alphaMultiplier takes effect
        DrawHelper.drawTexture((DrawContext) ctx, texture, x, y, u, v, width, height, textureWidth, textureHeight, applyAlpha(0xFFFFFFFF));
        //#else
        //$$ DrawHelper.drawTexture((DrawContext) ctx, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //#endif
        //#else
        //$$ bindTexture(texture);
        //$$ DrawableHelper.drawTexture((MatrixStack) ctx, x, y, (int) u, (int) v, width, height, textureWidth, textureHeight);
        //#endif
    }

    public static void drawTexture(Object ctx, Identifier texture, int x, int y, float u, float v,
                                   int width, int height, int textureWidth, int textureHeight, int color) {
        color = applyAlpha(color);
        //#if MC >= 12000
        DrawHelper.drawTexture((DrawContext) ctx, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
        //#else
        //$$ setShaderColor(color);
        //$$ drawTexture(ctx, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //$$ resetShaderColor();
        //#endif
    }

    public static void drawTexture(Object ctx, Identifier texture, int x, int y, int width, int height,
                                   float u, float v, int regionWidth, int regionHeight,
                                   int textureWidth, int textureHeight) {
        //#if MC >= 12000
        //#if MC >= 12102
        // MC >= 1.21.2: use color overload so alphaMultiplier takes effect
        DrawHelper.drawTexture((DrawContext) ctx, texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight, applyAlpha(0xFFFFFFFF));
        //#else
        //$$ DrawHelper.drawTexture((DrawContext) ctx, texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        //#endif
        //#else
        //$$ bindTexture(texture);
        //$$ DrawableHelper.drawTexture((MatrixStack) ctx, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        //#endif
    }

    public static void drawTexture(Object ctx, Identifier texture, int x, int y, int width, int height,
                                   float u, float v, int regionWidth, int regionHeight,
                                   int textureWidth, int textureHeight, int color) {
        color = applyAlpha(color);
        //#if MC >= 12000
        DrawHelper.drawTexture((DrawContext) ctx, texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight, color);
        //#else
        //$$ setShaderColor(color);
        //$$ drawTexture(ctx, texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        //$$ resetShaderColor();
        //#endif
    }

    public static void enableScissor(Object ctx, int x1, int y1, int x2, int y2) {
        //#if MC >= 12106
        // Track the screen-space scissor rect ourselves so deferred elements
        // (e.g. the SDF rounded-rect pipeline) can attach the same clip.
        ScreenRect rect = new ScreenRect(x1, y1, x2 - x1, y2 - y1)
            .transform(new Matrix3x2f(((DrawContext) ctx).getMatrices()));
        SCISSOR_RECTS.push(rect);
        //#endif
        //#if MC >= 12000
        ((DrawContext) ctx).enableScissor(x1, y1, x2, y2);
        //#else
        //$$ MinecraftClient client = MinecraftClient.getInstance();
        //$$ double scale = client.getWindow().getScaleFactor();
        //$$ int windowHeight = client.getWindow().getHeight();
        //$$ RenderSystem.enableScissor(
        //$$     (int) (x1 * scale),
        //$$     (int) (windowHeight - y2 * scale),
        //$$     (int) ((x2 - x1) * scale),
        //$$     (int) ((y2 - y1) * scale)
        //$$ );
        //#endif
    }

    public static void disableScissor(Object ctx) {
        //#if MC >= 12106
        if (!SCISSOR_RECTS.isEmpty()) SCISSOR_RECTS.pop();
        //#endif
        //#if MC >= 12000
        ((DrawContext) ctx).disableScissor();
        //#else
        //$$ RenderSystem.disableScissor();
        //#endif
    }

    //#if MC >= 12106
    private static final java.util.ArrayDeque<ScreenRect> SCISSOR_RECTS = new java.util.ArrayDeque<>();

    /** Current screen-space scissor rect (null when none), for deferred elements. */
    public static ScreenRect currentScissor() {
        return SCISSOR_RECTS.peekLast();
    }
    //#endif

    public static void pushMatrix(Object ctx) {
        //#if MC >= 12106
        ((DrawContext) ctx).getMatrices().pushMatrix();
        //#else
        //#if MC >= 12000
        //$$ ((DrawContext) ctx).getMatrices().push();
        //#else
        //$$ ((MatrixStack) ctx).push();
        //#endif
        //#endif
    }

    public static void popMatrix(Object ctx) {
        //#if MC >= 12106
        ((DrawContext) ctx).getMatrices().popMatrix();
        //#else
        //#if MC >= 12000
        //$$ ((DrawContext) ctx).getMatrices().pop();
        //#else
        //$$ ((MatrixStack) ctx).pop();
        //#endif
        //#endif
    }

    public static void translate(Object ctx, float x, float y, float z) {
        //#if MC >= 12106
        ((DrawContext) ctx).getMatrices().translate(x, y);
        //#else
        //#if MC >= 12000
        ((DrawContext) ctx).getMatrices().translate(x, y, z);
        //#else
        //$$ ((MatrixStack) ctx).translate(x, y, z);
        //#endif
        //#endif
    }

    public static void translate(Object ctx, float x, float y) {
        translate(ctx, x, y, 0.0F);
    }

    public static void scale(Object ctx, float x, float y, float z) {
        //#if MC >= 12106
        ((DrawContext) ctx).getMatrices().scale(x, y);
        //#else
        //#if MC >= 12000
        ((DrawContext) ctx).getMatrices().scale(x, y, z);
        //#else
        //$$ ((MatrixStack) ctx).scale(x, y, z);
        //#endif
        //#endif
    }

    public static void scale(Object ctx, float x, float y) {
        scale(ctx, x, y, 1.0F);
    }

    public static void bindTexture(Identifier texture) {
        //#if MC < 12000
        //$$ MinecraftClient.getInstance().getTextureManager().bindTexture(texture);
        //#endif
    }

    public static void setShaderColor(int argb) {
        //#if MC < 12000
        //$$ float a = ((argb >>> 24) & 0xFF) / 255.0F;
        //$$ float r = ((argb >>> 16) & 0xFF) / 255.0F;
        //$$ float g = ((argb >>> 8) & 0xFF) / 255.0F;
        //$$ float b = (argb & 0xFF) / 255.0F;
        //#if MC >= 11700
        //$$ RenderSystem.setShaderColor(r, g, b, a);
        //#else
        //$$ RenderSystem.color4f(r, g, b, a);
        //#endif
        //#endif
    }

    public static void resetShaderColor() {
        //#if MC < 12000
        //#if MC >= 11700
        //$$ RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        //#else
        //$$ RenderSystem.color4f(1.0F, 1.0F, 1.0F, 1.0F);
        //#endif
        //#endif
    }
}
