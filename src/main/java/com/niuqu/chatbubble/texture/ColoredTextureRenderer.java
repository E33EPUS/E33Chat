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
        RenderSystem.disableBlend();
    }
}
