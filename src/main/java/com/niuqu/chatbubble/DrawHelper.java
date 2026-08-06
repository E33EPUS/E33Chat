package com.niuqu.chatbubble;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;

public class DrawHelper {
    // 基础 drawTexture (无颜色)
    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight) {
        //#if MC >= 12106
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight);
        //#else
        //#if MC >= 12102
        //$$ context.drawTexture(id -> net.minecraft.client.render.RenderLayer.getGuiTextured(id), texture, x, y, (int)u, (int)v, width, height, textureWidth, textureHeight);
        //#else
        //$$ context.drawTexture(texture, x, y, (int)u, (int)v, width, height, textureWidth, textureHeight);
        //#endif
        //#endif
    }

    // drawTexture 带颜色
    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, float u, float v, int width, int height, int textureWidth, int textureHeight, int color) {
        //#if MC >= 12106
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, textureWidth, textureHeight, color);
        //#else
        //#if MC >= 12102
        //$$ context.drawTexture(id -> net.minecraft.client.render.RenderLayer.getGuiTextured(id), texture, x, y, (int)u, (int)v, width, height, textureWidth, textureHeight, color);
        //#else
        //$$ context.drawTexture(texture, x, y, (int)u, (int)v, width, height, textureWidth, textureHeight);
        //$$ // 1.21.1 不支持颜色参数，忽略
        //#endif
        //#endif
    }

    // drawTexture 基础重载 (int u, int v) — 支持 regionWidth/regionHeight
    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight) {
        //#if MC >= 12106
        // RenderPipelines API 有 12 参数重载: (pipeline, tex, x, y, u, v, w, h, regionW, regionH, texW, texH)
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        //#else
        //#if MC >= 12102
        //$$ context.drawTexture(id -> net.minecraft.client.render.RenderLayer.getGuiTextured(id), texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight);
        //#else
        //$$ context.drawTexture(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        //#endif
        //#endif
    }

    // drawTexture 带颜色 (int u, int v) — 支持 regionWidth/regionHeight
    public static void drawTexture(DrawContext context, Identifier texture, int x, int y, int width, int height, float u, float v, int regionWidth, int regionHeight, int textureWidth, int textureHeight, int color) {
        //#if MC >= 12106
        context.drawTexture(net.minecraft.client.gl.RenderPipelines.GUI_TEXTURED, texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
        //#else
        //#if MC >= 12102
        //$$ context.drawTexture(id -> net.minecraft.client.render.RenderLayer.getGuiTextured(id), texture, x, y, u, v, width, height, regionWidth, regionHeight, textureWidth, textureHeight, color);
        //#else
        //$$ context.drawTexture(texture, x, y, width, height, u, v, regionWidth, regionHeight, textureWidth, textureHeight);
        //$$ // 1.21.1 不支持颜色参数，忽略
        //#endif
        //#endif
    }
}