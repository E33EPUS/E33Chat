package com.niuqu.chatbubble.texture;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.BufferBuilder;
import net.minecraft.client.render.BufferRenderer;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.Tessellator;
import net.minecraft.client.render.VertexFormat;
import net.minecraft.client.render.VertexFormats;
import net.minecraft.util.Identifier;
import org.joml.Matrix4f;

/**
 * 带整体透明度的纹理渲染：纹理色 × 白 (1,1,1,alpha)。
 * 用于动态 alpha 的元素（面板开屏淡入、滚动条淡入淡出）——普通 drawTexture 无法携带运行时透明度。
 */
public final class ColoredTextureRenderer {

    private ColoredTextureRenderer() {}

    public static void drawWithAlpha(DrawContext g, Identifier tex,
                                     int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        g.draw();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();
        Matrix4f pose = g.getMatrices().peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);
        bb.vertex(pose, x, y, 0).texture(0f, 0f).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x, y + h, 0).texture(0f, 1f).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x + w, y + h, 0).texture(1f, 1f).color(1f, 1f, 1f, alpha);
        bb.vertex(pose, x + w, y, 0).texture(1f, 0f).color(1f, 1f, 1f, alpha);
        BufferRenderer.drawWithGlobalProgram(bb.end());
        RenderSystem.disableBlend();
    }
}
