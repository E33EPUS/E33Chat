package com.niuqu.chatbubble.texture;

import com.niuqu.chatbubble.RenderHelper;
//#if MC >= 12000
//#if MC < 12102
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import org.joml.Matrix4f;
//#endif
//#endif
import net.minecraft.util.Identifier;

/**
 * 带整体透明度的纹理渲染：纹理色 × 白 (1,1,1,alpha)。
 * 用于动态 alpha 的元素（面板开屏淡入、滚动条淡入淡出）——普通 drawTexture 无法携带运行时透明度。
 *
 * <p>MC 1.20.0–1.21.1: custom BufferBuilder rendering (drawTexture 不支持颜色参数)。
 * 其他版本: 通过 RenderHelper.drawTexture 带颜色参数实现。</p>
 */
public final class ColoredTextureRenderer {

    private ColoredTextureRenderer() {}

    public static void drawWithAlpha(Object g, Identifier tex,
                                     int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        //#if MC >= 12000
        //#if MC < 12102
        DrawContext ctx = (DrawContext) g;
        ctx.draw();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        boolean blendEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = ctx.getMatrices().peek().getPositionMatrix();
        //#if MC >= 12100
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        //#else
        //$$ BufferBuilder bb = Tessellator.getInstance().getBuffer();
        //$$ bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        //#endif
        bb.vertex(pose, x, y, 0).texture(0f, 0f).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x, y + h, 0).texture(0f, 1f).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x + w, y + h, 0).texture(1f, 1f).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x + w, y, 0).texture(0f, 0f).color(1f, 1f, 1f, alpha);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        if (!blendEnabled) RenderSystem.disableBlend();
        //#else
        //$$ int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        //$$ RenderHelper.drawTexture(g, tex, x, y, w, h, 0, 0, w, h, w, h, color);
        //#endif
        //#else
        //$$ int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        //$$ RenderHelper.drawTexture(g, tex, x, y, w, h, 0, 0, w, h, w, h, color);
        //#endif
    }

    /** 带整体 tint 色的纹理渲染：纹理色 × tint(r,g,b,a)。用于白色默认纹理 × 主题色动态着色。 */
    public static void drawTinted(Object g, Identifier tex,
                                  int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        float a = (argb >>> 24) / 255f;
        float r = (argb >> 16 & 0xFF) / 255f;
        float gr = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        //#if MC >= 12000
        //#if MC < 12102
        DrawContext ctx = (DrawContext) g;
        ctx.draw();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        boolean blendEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = ctx.getMatrices().peek().getPositionMatrix();
        //#if MC >= 12100
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        //#else
        //$$ BufferBuilder bb = Tessellator.getInstance().getBuffer();
        //$$ bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        //#endif
        bb.vertex(pose, x, y, 0).texture(0f, 0f).color(r, gr, b, a);
        bb.vertex(pose, x, y + h, 0).texture(0f, 1f).color(r, gr, b, a);
        bb.vertex(pose, x + w, y + h, 0).texture(1f, 1f).color(r, gr, b, a);
        bb.vertex(pose, x + w, y, 0).texture(1f, 0f).color(r, gr, b, a);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        if (!blendEnabled) RenderSystem.disableBlend();
        //#else
        //$$ RenderHelper.drawTexture(g, tex, x, y, w, h, 0, 0, w, h, w, h, argb);
        //#endif
        //#else
        //$$ RenderHelper.drawTexture(g, tex, x, y, w, h, 0, 0, w, h, w, h, argb);
        //#endif
    }

    /**
     * 带整体透明度 + UV 采样的纹理渲染：等价 drawTexture 的
     * (u,v,regionWidth,regionHeight,textureWidth,textureHeight) 语义，但带动态 alpha。
     * 图标/带采样区域的元素淡入用（drawTexture 走 POSITION_TEXTURE 不吃 setShaderColor）。
     */
    public static void drawWithAlpha(Object g, Identifier tex,
                                     int x, int y, int w, int h,
                                     float u, float v, int regionW, int regionH,
                                     int texW, int texH, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        //#if MC >= 12000
        //#if MC < 12102
        DrawContext ctx = (DrawContext) g;
        ctx.draw();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        boolean blendEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float u1 = u / texW, u2 = (u + regionW) / texW;
        float v1 = v / texH, v2 = (v + regionH) / texH;
        Matrix4f pose = ctx.getMatrices().peek().getPositionMatrix();
        //#if MC >= 12100
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        //#else
        //$$ BufferBuilder bb = Tessellator.getInstance().getBuffer();
        //$$ bb.begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        //#endif
        bb.vertex(pose, x, y, 0).texture(u1, v1).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x, y + h, 0).texture(u1, v2).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x + w, y + h, 0).texture(u2, v2).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x + w, y, 0).texture(u2, v1).color(1f, 1f, 1f, alpha);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        if (!blendEnabled) RenderSystem.disableBlend();
        //#else
        //$$ int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        //$$ RenderHelper.drawTexture(g, tex, x, y, w, h, u, v, regionW, regionH, texW, texH, color);
        //#endif
        //#else
        //$$ int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        //$$ RenderHelper.drawTexture(g, tex, x, y, w, h, u, v, regionW, regionH, texW, texH, color);
        //#endif
    }
}
