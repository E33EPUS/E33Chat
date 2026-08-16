package com.niuqu.chatbubble;

import net.minecraft.client.MinecraftClient;
//#if MC >= 11700
import org.lwjgl.opengl.GL30;
//#endif

/**
 * Panel background blur via GL blit multi-pass downscale.
 *
 * <p>Copies the panel region from the main framebuffer into a temp texture,
 * blurs it through downsample+upsample (5-level pyramid), then renders the
 * result back. Uses raw GL30 calls for cross-version compatibility — no
 * custom shaders, compatible with Oculus/Embeddium.</p>
 *
 * <p>GL state (FBO binding, viewport, scissor) is saved before blur and
 * restored in a {@code finally} block to prevent the state leaks that caused
 * black screens on server disconnect in earlier versions.</p>
 */
public class BlurRenderer {

    static volatile boolean disconnecting = false;

    //#if MC >= 11700
    private static int fbo0 = -1, tex0 = -1;
    private static int fbo1 = -1, tex1 = -1;
    private static int fbo2 = -1, tex2 = -1;
    private static int fbo3 = -1, tex3 = -1;
    private static int fbo4 = -1, tex4 = -1;
    private static int cw, ch;
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
        if (pw == cw && ph == ch) return;
        destroy();
        recreated = true;
        cw = pw; ch = ph;
        int[] a = make(pw,       ph);       fbo0 = a[0]; tex0 = a[1];
        int[] b = make(pw / 2,   ph / 2);   fbo1 = b[0]; tex1 = b[1];
        int[] c = make(pw / 4,   ph / 4);   fbo2 = c[0]; tex2 = c[1];
        int[] d = make(pw / 8,   ph / 8);   fbo3 = d[0]; tex3 = d[1];
        int[] e = make(pw / 16,  ph / 16);  fbo4 = e[0]; tex4 = e[1];
    }

    private static void destroy() {
        if (fbo0 != -1) {
            GL30.glDeleteFramebuffers(fbo0); GL30.glDeleteTextures(tex0);
            GL30.glDeleteFramebuffers(fbo1); GL30.glDeleteTextures(tex1);
            GL30.glDeleteFramebuffers(fbo2); GL30.glDeleteTextures(tex2);
            GL30.glDeleteFramebuffers(fbo3); GL30.glDeleteTextures(tex3);
            GL30.glDeleteFramebuffers(fbo4); GL30.glDeleteTextures(tex4);
            fbo0 = fbo1 = fbo2 = fbo3 = fbo4 = -1;
            tex0 = tex1 = tex2 = tex3 = tex4 = -1;
            cw = ch = 0;
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

    public static void cleanup() {
        disconnecting = true;
        //#if MC >= 11700
        MinecraftClient.getInstance().execute(() -> destroy());
        //#endif
    }

    public static boolean isDisconnecting() {
        return disconnecting;
    }

    public static void blurPanel(Object g, int x, int y, int w, int h) {
        if (w <= 0 || h <= 0) return;
        if (disconnecting) return;
        var mc = MinecraftClient.getInstance();
        if (mc.world == null || mc.player == null) return;

        //#if MC >= 11700
        try {
            int mainFb = GL30.glGetInteger(GL30.GL_FRAMEBUFFER_BINDING);
            int fbH = mc.getWindow().getHeight();
            int[] vp = new int[4];
            GL30.glGetIntegerv(GL30.GL_VIEWPORT, vp);
            boolean scissor = GL30.glIsEnabled(GL30.GL_SCISSOR_TEST);

            try {
                int s = (int) mc.getWindow().getScaleFactor();
                int px = x * s, py = y * s, pw = w * s, ph = h * s;
                int y2 = Math.min(py + ph, fbH);
                if (y2 <= py) return;
                ph = y2 - py;

                ensure(pw, ph);
                int glY0 = fbH - (py + ph);
                int glY1 = fbH - py;

                GL30.glDisable(GL30.GL_SCISSOR_TEST);

                boolean full = nextFull || recreated;
                nextFull = !full;
                if (full) {
                    blit(mainFb, px, glY0, px + pw, glY1, fbo0, 0, 0, pw, ph);
                    blit(fbo0, 0, 0, pw, ph,       fbo1, 0, 0, pw / 2, ph / 2);
                    blit(fbo1, 0, 0, pw / 2, ph / 2, fbo2, 0, 0, pw / 4, ph / 4);
                    blit(fbo2, 0, 0, pw / 4, ph / 4, fbo3, 0, 0, pw / 8, ph / 8);
                    blit(fbo3, 0, 0, pw / 8, ph / 8, fbo4, 0, 0, pw / 16, ph / 16);
                    blit(fbo4, 0, 0, pw / 16, ph / 16, fbo3, 0, 0, pw / 8, ph / 8);
                    blit(fbo3, 0, 0, pw / 8, ph / 8,   fbo2, 0, 0, pw / 4, ph / 4);
                    blit(fbo2, 0, 0, pw / 4, ph / 4,   fbo1, 0, 0, pw / 2, ph / 2);
                    blit(fbo1, 0, 0, pw / 2, ph / 2,   fbo0, 0, 0, pw, ph);
                    recreated = false;
                }

                blit(fbo0, 0, 0, pw, ph, mainFb, px, glY0, pw, ph);
            } finally {
                GL30.glBindFramebuffer(GL30.GL_FRAMEBUFFER, mainFb);
                GL30.glViewport(vp[0], vp[1], vp[2], vp[3]);
                if (scissor) GL30.glEnable(GL30.GL_SCISSOR_TEST);
            }
        } catch (Throwable t) {
            // GL error — panel opacity still provides visual separation
        }
        //#endif
    }
}
