package com.niuqu.chatbubble;

import net.minecraft.client.MinecraftClient;
import org.lwjgl.opengl.GL30;

/**
 * Panel background blur via GL blit multi-pass downscale.
 *
 * <p>Copies the panel region from the main framebuffer into a temp texture,
 * blurs it through downsample+upsample (5-level pyramid), then renders the
 * result back. Uses raw GL30 calls for cross-version compatibility — no
 * custom shaders, compatible with Oculus/Embeddium.</p>
 *
 * <p>For MC &lt; 1.17.0 (where GL30 framebuffer APIs may not be fully
 * available), falls back to a layered semi-transparent overlay.</p>
 */
public class BlurRenderer {

    //#if MC >= 11700
    private static int fbo0 = -1, tex0 = -1; // 1:1 copy
    private static int fbo1 = -1, tex1 = -1; // 1/2
    private static int fbo2 = -1, tex2 = -1; // 1/4
    private static int fbo3 = -1, tex3 = -1; // 1/8
    private static int fbo4 = -1, tex4 = -1; // 1/16
    private static int cw, ch;
    // Frame-skip: full 5-level pyramid refreshes every other frame; intermediate
    // frames just blit the previous blurred result (fbo0 cache) back (1 blit vs 10).
    // Blur background has no high-frequency detail, so frame-skipping is visually
    // imperceptible — full-screen blit every frame is expensive on integrated
    // graphics / GL translation layers (Intel Arc etc.), main cause of chat UI lag.
    private static boolean nextFull = true;
    private static boolean recreated = true;

    private static int[] make(int w, int h) {
        w = Math.max(1, w); h = Math.max(1, h);
        int fbo = GL30.glGenFramebuffers();
        int tex = GL30.glGenTextures();
        GL30.glBindTexture(GL30.GL_TEXTURE_2D, tex);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MIN_FILTER, GL30.GL_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_MAG_FILTER, GL30.GL_LINEAR);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_S, GL30.GL_CLAMP_TO_EDGE);
        GL30.glTexParameteri(GL30.GL_TEXTURE_2D, GL30.GL_TEXTURE_WRAP_T, GL30.GL_CLAMP_TO_EDGE);
        GL30.glTexImage2D(GL30.GL_TEXTURE_2D, 0, GL30.GL_RGBA8, w, h, 0,
            GL30.GL_RGBA, GL30.GL_UNSIGNED_BYTE, (java.nio.ByteBuffer) null);
        GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, fbo);
        GL30.glFramebufferTexture2D(GL30.GL_FRAMEBUFFER, GL30.GL_COLOR_ATTACHMENT0,
            GL30.GL_TEXTURE_2D, tex, 0);
        return new int[]{fbo, tex};
    }

    private static void ensure(int pw, int ph) {
        int mw = pw, mh = ph;
        if (mw == cw && mh == ch) return;
        destroy();
        recreated = true;
        cw = mw; ch = mh;
        int[] a = make(mw,       mh);       fbo0 = a[0]; tex0 = a[1];
        int[] b = make(mw / 2,   mh / 2);   fbo1 = b[0]; tex1 = b[1];
        int[] c = make(mw / 4,   mh / 4);   fbo2 = c[0]; tex2 = c[1];
        int[] d = make(mw / 8,   mh / 8);   fbo3 = d[0]; tex3 = d[1];
        int[] e = make(mw / 16,  mh / 16);  fbo4 = e[0]; tex4 = e[1];
    }

    private static void destroy() {
        if (fbo0 != -1) {
            GL30.glDeleteFramebuffers(fbo0); GL30.glDeleteTextures(tex0);
            GL30.glDeleteFramebuffers(fbo1); GL30.glDeleteTextures(tex1);
            GL30.glDeleteFramebuffers(fbo2); GL30.glDeleteTextures(tex2);
            GL30.glDeleteFramebuffers(fbo3); GL30.glDeleteTextures(tex3);
            GL30.glDeleteFramebuffers(fbo4); GL30.glDeleteTextures(tex4);
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
    //#endif

    /**
     * Draw a blurred panel background over the given region.
     *
     * @param g DrawContext (1.20+) or MatrixStack (1.16.5-1.19.2) — used only
     *          for the fallback overlay path
     * @param x left edge (inclusive, GUI-space)
     * @param y top edge (inclusive, GUI-space)
     * @param w width
     * @param h height
     */
    public static void blurPanel(Object g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        int guiX = x;
        int guiY = y;
        int guiW = w;
        int guiH = h;

        //#if MC >= 11700
        var mc = MinecraftClient.getInstance();
        // Use GL query instead of mc.getFramebuffer().fbo for cross-version compat
        // (Framebuffer.fbo field was removed in MC 1.21.5+)
        int mainFb = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
        int fbH = mc.getWindow().getHeight();
        if (w <= 0 || h <= 0) return;

        try {
            int oldFb = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int[] vp = new int[4];
            GL30.glGetIntegerv(GL30.GL_VIEWPORT, vp);
            boolean scissor = GL30.glIsEnabled(GL30.GL_SCISSOR_TEST);

            int s = (int) mc.getWindow().getScaleFactor();
            x *= s; y *= s; w *= s; h *= s;

            // When the panel sticks to the top, the logical height can exceed
            // the framebuffer by 1-2px (1602 > fbH 1600). Out-of-bounds source
            // rect blit is implementation-defined — clamp to framebuffer.
            int y2 = Math.min(y + h, fbH);
            if (y2 <= y) return;
            h = y2 - y;
            ensure(w, h);
            int glY0 = fbH - (y + h);
            int glY1 = fbH - y;

            GL30.glDisable(GL30.GL_SCISSOR_TEST);

            // Full frame: copy panel region -> 5-level downsample -> upsample,
            // fbo0 caches the latest blur result; intermediate frames skip the
            // pyramid and just reuse the previous frame's fbo0. After a window
            // resize (recreated), fbo0 content is invalid — force full path.
            boolean full = nextFull || recreated;
            nextFull = !full;
            if (full) {
                blit(mainFb, x, glY0, x + w, glY1, fbo0, 0, 0, w, h);

                blit(fbo0, 0, 0, w, h,       fbo1, 0, 0, w / 2, h / 2);
                blit(fbo1, 0, 0, w / 2, h / 2, fbo2, 0, 0, w / 4, h / 4);
                blit(fbo2, 0, 0, w / 4, h / 4, fbo3, 0, 0, w / 8, h / 8);
                blit(fbo3, 0, 0, w / 8, h / 8, fbo4, 0, 0, w / 16, h / 16);

                blit(fbo4, 0, 0, w / 16, h / 16, fbo3, 0, 0, w / 8, h / 8);
                blit(fbo3, 0, 0, w / 8, h / 8,   fbo2, 0, 0, w / 4, h / 4);
                blit(fbo2, 0, 0, w / 4, h / 4,   fbo1, 0, 0, w / 2, h / 2);
                blit(fbo1, 0, 0, w / 2, h / 2,   fbo0, 0, 0, w, h);
                recreated = false;
            }

            // Full frame: write back the just-generated blur; intermediate: re-blit previous blur cache
            blit(fbo0, 0, 0, w, h, mainFb, x, glY0, w, h);

            GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, oldFb);
            GL30.glViewport(vp[0], vp[1], vp[2], vp[3]);
            if (scissor) GL30.glEnable(GL30.GL_SCISSOR_TEST);
        } catch (Throwable t) {
            //#if MC >= 26000
            //$$ // 26.x: the GL fallback overlay looks like an extra world shadow
            //$$ // behind the chat panel. If framebuffer blur fails, skip the
            //$$ // fallback and let the normal configurable panel opacity draw.
            //#else
            // Fallback to overlay if GL operations fail
            overlayFallback(g, guiX, guiY, guiW, guiH);
            //#endif
        }
        //#else
        //$$ overlayFallback(g, x, y, w, h);
        //#endif
    }

    /**
     * Fallback frosted-glass overlay (no real blur) for older MC versions
     * or when GL framebuffer operations fail.
     */
    private static void overlayFallback(Object g, int x, int y, int w, int h) {
        // Layer 1: dark base — the main "darkening" that simulates blur
        RenderHelper.fill(g, x, y, x + w, y + h, 0x99000000);
        // Layer 2: subtle vertical gradient at top for frosted highlight
        int gradH = Math.min(h / 3, 20);
        if (gradH > 0) {
            for (int i = 0; i < gradH; i++) {
                int alpha = (int) (0x33 * (1.0f - (float) i / gradH));
                RenderHelper.fill(g, x, y + i, x + w, y + i + 1, (alpha << 24) | 0xFFFFFF);
            }
        }
    }
}
