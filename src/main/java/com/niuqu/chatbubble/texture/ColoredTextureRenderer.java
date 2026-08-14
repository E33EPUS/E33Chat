package com.niuqu.chatbubble.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.BufferBuilder;
import com.mojang.blaze3d.vertex.BufferUploader;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import org.joml.Matrix4f;

/**
 * 带整体透明度的纹理渲染：纹理色 × 白 (1,1,1,alpha)。
 * 用于动态 alpha 的元素（面板开屏淡入、滚动条淡入淡出）——普通 blit 无法携带运行时透明度。
 */
public final class ColoredTextureRenderer {

    private ColoredTextureRenderer() {}

    public static void drawWithAlpha(GuiGraphics g, ResourceLocation tex,
                                     int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        g.flush();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        // 保存调用前 blend 状态，绘制后恢复——保持调用方 blend 状态不变（GL 状态卫生）
        boolean blendEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        bb.vertex(pose, x, y, 0).color(1f, 1f, 1f, alpha).uv(0f, 0f).endVertex();
        bb.vertex(pose, x, y + h, 0).color(1f, 1f, 1f, alpha).uv(0f, 1f).endVertex();
        bb.vertex(pose, x + w, y + h, 0).color(1f, 1f, 1f, alpha).uv(1f, 1f).endVertex();
        bb.vertex(pose, x + w, y, 0).color(1f, 1f, 1f, alpha).uv(1f, 0f).endVertex();
        BufferUploader.drawWithShader(bb.end());
        if (!blendEnabled) RenderSystem.disableBlend();
    }

    /** 带整体 tint 色的纹理渲染：纹理色 × tint(r,g,b,a)。用于白色默认纹理 × 主题色动态着色。 */
    public static void drawTinted(GuiGraphics g, ResourceLocation tex,
                                  int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        float a = (argb >>> 24) / 255f;
        float r = (argb >> 16 & 0xFF) / 255f;
        float gr = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;
        g.flush();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        // 保存调用前 blend 状态，绘制后恢复——保持调用方 blend 状态不变（GL 状态卫生）
        boolean blendEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        bb.vertex(pose, x, y, 0).color(r, gr, b, a).uv(0f, 0f).endVertex();
        bb.vertex(pose, x, y + h, 0).color(r, gr, b, a).uv(0f, 1f).endVertex();
        bb.vertex(pose, x + w, y + h, 0).color(r, gr, b, a).uv(1f, 1f).endVertex();
        bb.vertex(pose, x + w, y, 0).color(r, gr, b, a).uv(1f, 0f).endVertex();
        BufferUploader.drawWithShader(bb.end());
        if (!blendEnabled) RenderSystem.disableBlend();
    }

    /**
     * 带整体透明度 + UV 采样的纹理渲染：等价 blit 的
     * (u,v,regionWidth,regionHeight,textureWidth,textureHeight) 语义，但带动态 alpha。
     * 图标/带采样区域的元素淡入用（blit 走 POSITION_TEX 不吃 setShaderColor）。
     */
    public static void drawWithAlpha(GuiGraphics g, ResourceLocation tex,
                                     int x, int y, int w, int h,
                                     float u, float v, int regionW, int regionH,
                                     int texW, int texH, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        g.flush();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionColorTexShader);
        // 保存调用前 blend 状态，绘制后恢复——保持调用方 blend 状态不变（GL 状态卫生）
        boolean blendEnabled = org.lwjgl.opengl.GL11.glIsEnabled(org.lwjgl.opengl.GL11.GL_BLEND);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        float u1 = u / texW, u2 = (u + regionW) / texW;
        float v1 = v / texH, v2 = (v + regionH) / texH;
        Matrix4f pose = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().getBuilder();
        bb.begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_COLOR_TEX);
        bb.vertex(pose, x, y, 0).color(1f, 1f, 1f, alpha).uv(u1, v1).endVertex();
        bb.vertex(pose, x, y + h, 0).color(1f, 1f, 1f, alpha).uv(u1, v2).endVertex();
        bb.vertex(pose, x + w, y + h, 0).color(1f, 1f, 1f, alpha).uv(u2, v2).endVertex();
        bb.vertex(pose, x + w, y, 0).color(1f, 1f, 1f, alpha).uv(u2, v1).endVertex();
        BufferUploader.drawWithShader(bb.end());
        if (!blendEnabled) RenderSystem.disableBlend();
    }
}
