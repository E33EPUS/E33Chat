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
}
