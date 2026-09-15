package com.niuqu.chatbubble.render;

import com.mojang.blaze3d.platform.GlStateManager;
import net.minecraft.client.Minecraft;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import org.lwjgl.opengl.GL30;

/**
 * Panel background blur via GL blit multi-pass downscale.
 * Copies the panel region from the main framebuffer into a temp texture,
 * blurs it through downsample+upsample, then renders the result.
 * No custom shaders → compatible with Oculus/Embeddium.
 */
@OnlyIn(Dist.CLIENT)
public class BlurRenderer {

    private static int fbo0 = -1, tex0 = -1; // 1:1 copy
    private static int fbo1 = -1, tex1 = -1; // 1/2
    private static int fbo2 = -1, tex2 = -1; // 1/4
    private static int fbo3 = -1, tex3 = -1; // 1/8
    private static int fbo4 = -1, tex4 = -1; // 1/16
    private static int cw, ch;
    // 模糊降帧：完整 5 级金字塔每 2 帧刷新一次，中间帧只把上一帧模糊结果
    // （fbo0 缓存）重贴回主缓冲（1 次 blit vs 10 次）。模糊背景无高频细节，
    // 降帧视觉几乎无感——每帧全屏 blit 在核显/GL 转译层（Intel Arc 等）开销
    // 大，是打开聊天界面掉帧的主因
    private static boolean nextFull = true;
    private static boolean recreated = true;

    private static int[] make(int w, int h) {
        w = Math.max(1, w); h = Math.max(1, h);
        int fbo = GL30.glGenFramebuffers();
        int tex = GlStateManager._genTexture();
        GlStateManager._bindTexture(tex);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texParameter(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GlStateManager._texImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, w, h, 0,
            GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL30.GL_TEXTURE_2D, tex, 0);
        return new int[]{fbo, tex};
    }

    private static void ensure(int pw, int ph) {
        int mw = pw, mh = ph; // 1:1 copy
        if (mw == cw && mh == ch) return;
        destroy();
        recreated = true;
        cw = mw; ch = mh;
        int[] a = make(mw,     mh);     fbo0 = a[0]; tex0 = a[1];
        int[] b = make(mw / 2, mh / 2); fbo1 = b[0]; tex1 = b[1];
        int[] c = make(mw / 4, mh / 4); fbo2 = c[0]; tex2 = c[1];
        int[] d = make(mw / 8, mh / 8); fbo3 = d[0]; tex3 = d[1];
        int[] e = make(mw / 16, mh / 16); fbo4 = e[0]; tex4 = e[1];
    }

    private static void destroy() {
        if (fbo0 != -1) {
            GL30.glDeleteFramebuffers(fbo0); GlStateManager._deleteTexture(tex0);
            GL30.glDeleteFramebuffers(fbo1); GlStateManager._deleteTexture(tex1);
            GL30.glDeleteFramebuffers(fbo2); GlStateManager._deleteTexture(tex2);
            GL30.glDeleteFramebuffers(fbo3); GlStateManager._deleteTexture(tex3);
            GL30.glDeleteFramebuffers(fbo4); GlStateManager._deleteTexture(tex4);
            fbo0 = -1; cw = ch = 0;
        }
    }

    private static void blit(int sfbo, int sx0, int sy0, int sx1, int sy1,
                              int dfbo, int dx0, int dy0, int dw, int dh) {
        GL30.glBindFramebuffer(GL30.GL_READ_FRAMEBUFFER, sfbo);
        GL30.glBindFramebuffer(GL30.GL_DRAW_FRAMEBUFFER, dfbo);
        GL30.glBlitFramebuffer(sx0, sy0, sx1, sy1, dx0, dy0, dx0 + dw, dy0 + dh,
            GL30.GL_COLOR_BUFFER_BIT, GL30.GL_LINEAR);
    }

    /**
     * Blur the panel region on the main framebuffer.
     * Call during render(), BEFORE drawing the panel background fill.
     * Parameters are in GUI logical coordinates.
     */

    public static void blurPanel(int x, int y, int w, int h) {
        Minecraft mc = Minecraft.getInstance();
        int mainFb = mc.getMainRenderTarget().frameBufferId;
        int fbH = mc.getMainRenderTarget().height;
        if (w <= 0 || h <= 0) return;

        // Save GL state that MC's rendering pipeline depends on
        int oldFb = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int[] vp = new int[4];
        GL30.glGetIntegerv(GL30.GL_VIEWPORT, vp);
        boolean scissor = GL30.glIsEnabled(GL30.GL_SCISSOR_TEST);

        // Exact (possibly fractional) GUI scale: truncating to int made the blur
        // region drift off the panel rectangle on fractional scales (2.4.4 fix,
        // same root cause as the panel-width pixel bug).
        double s = mc.getWindow().getGuiScale();
        x = (int) Math.round(x * s);
        y = (int) Math.round(y * s);
        w = (int) Math.round(w * s);
        h = (int) Math.round(h * s);

        // 面板贴顶时逻辑高度换算会超出 framebuffer 1-2px（1602 > fbH 1600），
        // 超界源矩形 blit 是实现相关行为——clamp 到 framebuffer 内
        int y2 = Math.min(y + h, fbH);
        if (y2 <= y) return;
        h = y2 - y;
        ensure(w, h);
        int glY0 = fbH - (y + h);
        int glY1 = fbH - y;



        GL30.glDisable(GL30.GL_SCISSOR_TEST);

        // 完整帧：拷贝面板区 → 5 级下采样 → 上采样，fbo0 缓存最新模糊结果；
        // 中间帧：跳过金字塔，直接用上一帧的 fbo0。recreated（窗口缩放重建）
        // 后 fbo0 内容无效，强制走完整路径
        boolean full = nextFull || recreated;
        nextFull = !full;
        if (full) {
            blit(mainFb, x, glY0, x + w, glY1, fbo0, 0, 0, w, h);

            blit(fbo0, 0, 0, w, h,    fbo1, 0, 0, w / 2, h / 2);
            blit(fbo1, 0, 0, w / 2, h / 2, fbo2, 0, 0, w / 4, h / 4);
            blit(fbo2, 0, 0, w / 4, h / 4, fbo3, 0, 0, w / 8, h / 8);
            blit(fbo3, 0, 0, w / 8, h / 8, fbo4, 0, 0, w / 16, h / 16);

            blit(fbo4, 0, 0, w / 16, h / 16, fbo3, 0, 0, w / 8, h / 8);
            blit(fbo3, 0, 0, w / 8, h / 8, fbo2, 0, 0, w / 4, h / 4);
            blit(fbo2, 0, 0, w / 4, h / 4, fbo1, 0, 0, w / 2, h / 2);
            blit(fbo1, 0, 0, w / 2, h / 2, fbo0, 0, 0, w, h);
            recreated = false;
        }

        // 完整帧：写回刚生成的模糊；中间帧：重贴上一帧的模糊缓存
        blit(fbo0, 0, 0, w, h, mainFb, x, glY0, w, h);

        // Restore MC's GL state
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFb);
        GL30.glViewport(vp[0], vp[1], vp[2], vp[3]);
        if (scissor) GL30.glEnable(GL30.GL_SCISSOR_TEST);
    }
}
