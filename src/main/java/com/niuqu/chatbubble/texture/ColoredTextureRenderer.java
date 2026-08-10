package com.niuqu.chatbubble.texture;
import com.niuqu.chatbubble.RenderHelper;
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
import net.minecraft.util.Identifier;
public final class ColoredTextureRenderer {
    private ColoredTextureRenderer() {}
    public static void drawWithAlpha(Object g, Identifier tex,
                                     int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        RenderHelper.drawTexture(g, tex, x, y, w, h, 0, 0, w, h, w, h, color);
    }

    public static void drawWithAlpha(Object g, Identifier tex,
                                     int x, int y, int w, int h,
                                     float u, float v, int regionW, int regionH,
                                     int texW, int texH, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        RenderHelper.drawTexture(g, tex, x, y, w, h, u, v, regionW, regionH, texW, texH, color);
    }
}
