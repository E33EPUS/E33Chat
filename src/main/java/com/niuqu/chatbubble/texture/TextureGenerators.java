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
}
