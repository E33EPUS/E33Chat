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
 * 自写 9-slice（stretch 版）——四角不拉伸、边单向拉伸、中心双向拉伸。
 * 与 vanilla nine-slice（tile 平铺，渐变纹理露馅）不同，stretch 对任意贴图形状不变形。
 * border 从纹理实际尺寸推导（UiTextureManager.borderFor = 短边/4），保证采样与贴图严格 1:1——
 * 任意尺寸贴图都不会放大失配：默认圆角纹理（半径×4）和资源包覆盖贴图自动适配。
 * 1×1 纯色元素（toast/时间分隔符/HUD 提示条等）border=0 退化纯拉伸，不经过本类（用 ColoredTextureRenderer）。
 * 渲染用 POSITION_TEXTURE_COLOR 顶点：tint 色动态着色（白色默认纹理 × 主题色），alpha 通道动态淡入淡出。
 */
public final class NineSliceRenderer {

    private NineSliceRenderer() {}

    /**
     * 9-slice 渲染，tint 着色 + 整体 alpha。
     * @param argb 完整 ARGB：RGB 作为 tint 乘到纹理色上，alpha 作为整体透明度
     */
    public static void drawTinted(DrawContext g, Identifier tex,
                                  int x, int y, int w, int h, int argb) {
        if (w <= 0 || h <= 0) return;
        float a = (argb >>> 24) / 255f;
        if (a <= 0.003f) return;
        float r = (argb >> 16 & 0xFF) / 255f;
        float gr = (argb >> 8 & 0xFF) / 255f;
        float b = (argb & 0xFF) / 255f;

        g.draw();
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexColorProgram);
        RenderSystem.enableBlend();
        RenderSystem.defaultBlendFunc();

        Matrix4f pose = g.getMatrices().peek().getPositionMatrix();
        BufferBuilder bb = Tessellator.getInstance().begin(VertexFormat.DrawMode.QUADS, VertexFormats.POSITION_TEXTURE_COLOR);

        int border = UiTextureManager.borderFor(tex);
        int bw = Math.max(0, Math.min(border, w / 2));
        int bh = Math.max(0, Math.min(border, h / 2));
        if (bw <= 0 || bh <= 0) {
            // 退化：纯拉伸
            ColoredTextureRenderer.drawTinted(g, tex, x, y, w, h, argb);
            return;
        }
        int midW = w - 2 * bw;
        int midH = h - 2 * bh;
        int texSize = border * 4;
        float uw = 1f / texSize;
        float vh = 1f / texSize;

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

        BufferRenderer.drawWithGlobalProgram(bb.end());
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
        bb.vertex(pose, x, y, 0).texture(u0, v0).color(r, gr, b, a);
        bb.vertex(pose, x, y + h, 0).texture(u0, v1).color(r, gr, b, a);
        bb.vertex(pose, x + w, y + h, 0).texture(u1, v1).color(r, gr, b, a);
        bb.vertex(pose, x + w, y, 0).texture(u1, v0).color(r, gr, b, a);
    }
}
