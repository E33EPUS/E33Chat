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
 * 自写 9-slice（stretch 版）——四角不拉伸、边单向拉伸、中心双向拉伸。
 * 与 vanilla blitNineSlicedSprite（tile 平铺，渐变纹理露馅）不同，stretch 对任意贴图形状不变形。
 * 纹理约定：尺寸 = border×4（四角区 border 恒定、中心 border×2 双向拉伸）；
 * UV 按 1/(border×4) 缩放，border 与纹理尺寸自洽，任意圆角半径可表达。
 * 默认圆角纹理由 UiTextureManager 按配置半径生成；1×1 纯色元素（toast/时间分隔符
 * /HUD 提示条等）不经过本类，直接用 ColoredTextureRenderer。
 * 渲染用 POSITION_TEX_COLOR 顶点：tint 色动态着色（白色默认纹理 × 主题色），alpha 通道动态淡入淡出。
 */
public final class NineSliceRenderer {

    private NineSliceRenderer() {}

    /**
     * 9-slice 渲染，tint 着色 + 整体 alpha。
     * @param border 四角保留区（= 圆角半径像素，纹理尺寸应为 border×4；0 = 纯拉伸）
     * @param argb   完整 ARGB：RGB 作为 tint 乘到纹理色上，alpha 作为整体透明度
     */
    public static void drawTinted(GuiGraphics g, ResourceLocation tex,
                                  int x, int y, int w, int h, int border, int argb) {
        if (w <= 0 || h <= 0) return;
        float a = (argb >>> 24) / 255f;
        if (a <= 0.003f) return;
        float r = (argb >> 16 & 0xFF) / 255f;
        float gr = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        g.flush();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorShader);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f pose = g.pose().last().pose();
        BufferBuilder bb = Tesselator.getInstance().begin(VertexFormat.Mode.QUADS, DefaultVertexFormat.POSITION_TEX_COLOR);

        int bw = Math.max(0, Math.min(border, w / 2));
        int bh = Math.max(0, Math.min(border, h / 2));
        if (bw <= 0 || bh <= 0) {
            // 退化：纯拉伸
            ColoredTextureRenderer.drawTinted(g, tex, x, y, w, h, argb);
            return;
        }
        int midW = w - 2 * bw;
        int midH = h - 2 * bh;
        float uw = 1f / (border * 4f);
        float vh = 1f / (border * 4f);

        // 顶行：左角 / 顶边(拉伸) / 右角
        quad(bb, pose, x, y, bw, bh, 0, 0, bw, bh, uw, vh, r, gr, b, a);
        quad(bb, pose, x + bw, y, midW, bh, bw, 0, bw, bh, uw, vh, r, gr, b, a);
        quad(bb, pose, x + bw + midW, y, bw, bh, 2 * bw, 0, bw, bh, uw, vh, r, gr, b, a);
        // 中行：左边(拉伸) / 中心(双向) / 右边(拉伸)
        quad(bb, pose, x, y + bh, bw, midH, 0, bh, bw, bh, uw, vh, r, gr, b, a);
        quad(bb, pose, x + bw, y + bh, midW, midH, bw, bh, bw, bh, uw, vh, r, gr, b, a);
        quad(bb, pose, x + bw + midW, y + bh, bw, midH, 2 * bw, bh, bw, bh, uw, vh, r, gr, b, a);
        // 底行：左角 / 底边(拉伸) / 右角
        quad(bb, pose, x, y + bh + midH, bw, bh, 0, 2 * bh, bw, bh, uw, vh, r, gr, b, a);
        quad(bb, pose, x + bw, y + bh + midH, midW, bh, bw, 2 * bh, bw, bh, uw, vh, r, gr, b, a);
        quad(bb, pose, x + bw + midW, y + bh + midH, bw, bh, 2 * bw, 2 * bh, bw, bh, uw, vh, r, gr, b, a);

        BufferUploader.drawWithShader(bb.buildOrThrow());
        RenderSystem.disableBlend();
    }

    private static void quad(BufferBuilder bb, Matrix4f pose,
                             int x, int y, int w, int h,
                             int u, int v, int sW, int sH,
                             float uw, float vh,
                             float r, float gr, float b, float a) {
        float u0 = u * uw;
        float v0 = v * vh;
        float u1 = (u + sW) * uw;
        float v1 = (v + sH) * vh;
        bb.addVertex(pose, x, y, 0).setUv(u0, v0).setColor(r, gr, b, a);
        bb.addVertex(pose, x, y + h, 0).setUv(u0, v1).setColor(r, gr, b, a);
        bb.addVertex(pose, x + w, y + h, 0).setUv(u1, v1).setColor(r, gr, b, a);
        bb.addVertex(pose, x + w, y, 0).setUv(u1, v0).setColor(r, gr, b, a);
    }
}
