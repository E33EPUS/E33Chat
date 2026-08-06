package com.niuqu.chatbubble.texture;
import com.niuqu.chatbubble.DrawHelper;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.Identifier;
public final class ColoredTextureRenderer {
    private ColoredTextureRenderer() {}
    public static void drawWithAlpha(DrawContext g, Identifier tex,
                                     int x, int y, int w, int h, float alpha) {
        if (w <= 0 || h <= 0 || alpha <= 0.003f) return;
        int color = ((int) (alpha * 255) << 24) | 0xFFFFFF;
        DrawHelper.drawTexture(g, tex, x, y, w, h, 0, 0, w, h, w, h, color);
    }
}