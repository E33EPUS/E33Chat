package com.niuqu.chatbubble.texture;
public final class TextureGenerators {
    private TextureGenerators() {}
    public static int[] solid(int w, int h, int argb) {
        int[] px = new int[w * h];
        java.util.Arrays.fill(px, argb);
        return px;
    }
    public static int argbToAbgr(int argb) {
        return (argb & 0xFF000000) | ((argb & 0xFF) << 16) | (argb & 0x0000FF00) | ((argb >> 16) & 0xFF);
    }
}