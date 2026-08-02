package com.niuqu.chatbubble.texture;

/**
 * 纯像素纹理生成器——零 Minecraft 依赖（不 import 任何 MC/Forge 类），可直接 JUnit 测试。
 * 返回行主序 ARGB int 数组（0xAARRGGBB）。
 */
public final class TextureGenerators {

    private TextureGenerators() {}

    /** 纯色纹理。 */
    public static int[] solid(int w, int h, int argb) {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, argb);
        return px;
    }

    /**
     * ARGB (0xAARRGGBB) → NativeImage 像素格式 ABGR (0xAABBGGRR)。
     * MC 的 NativeImage.setPixelRGBA 按 redOffset=0/greenOffset=8/blueOffset=16 解释 int，
     * 直接写入 ARGB 会导致 R/B 通道互换（浅色主题米黄变青蓝）。
     */
    public static int argbToAbgr(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFF) << 16) | (argb & 0x0000FF00) | ((argb >> 16) & 0xFF);
    }

    /**
     * 圆角矩形纹理（行主序 ARGB），圆角外像素透明（0x00000000）。
     * radius 会被钳制到 min(w,h)/2；borderWidth=0 时无边框，borderArgb 忽略。
     * 默认纹理基准尺寸约定 16×16，9-slice 四角不拉伸、边拉伸。
     */
    public static int[] roundedRect(int w, int h, int radius, int fillArgb, int borderWidth, int borderArgb) {
        int[] px = new int[w * h];
        radius = Math.max(0, Math.min(radius, Math.min(w, h) / 2));
        if (borderWidth > 0) {
            int innerR = Math.max(0, radius - borderWidth);
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (insideRoundedRect(x, y, w, h, radius, 0, 0)) {
                        px[y * w + x] = insideRoundedRect(x, y, w - 2 * borderWidth, h - 2 * borderWidth, innerR, borderWidth, borderWidth)
                            ? fillArgb : borderArgb;
                    }
                }
            }
        } else {
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    if (insideRoundedRect(x, y, w, h, radius, 0, 0)) {
                        px[y * w + x] = fillArgb;
                    }
                }
            }
        }
        return px;
    }

    /** 像素 (x,y) 是否位于以 (ox,oy) 为原点、宽 w 高 h 圆角 r 的矩形内（圆角区域用圆心距离判断）。 */
    private static boolean insideRoundedRect(int x, int y, int w, int h, int r, int ox, int oy) {
        if (r <= 0) {
            return x >= ox && x < ox + w && y >= oy && y < oy + h;
        }
        int cx = Math.max(ox + r, Math.min(x, ox + w - 1 - r));
        int cy = Math.max(oy + r, Math.min(y, oy + h - 1 - r));
        int dx = x - cx;
        int dy = y - cy;
        return dx * dx + dy * dy <= r * r;
    }
}
