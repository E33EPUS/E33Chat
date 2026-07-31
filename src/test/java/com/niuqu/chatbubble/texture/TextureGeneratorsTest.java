package com.niuqu.chatbubble.texture;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class TextureGeneratorsTest {

    @Test void solid_pixelCountMatchesDimensions() {
        int[] px = TextureGenerators.solid(3, 2, 0xFFFF0000);
        assertEquals(6, px.length);
    }

    @Test void solid_allPixelsKeepColor() {
        int color = 0xD01E1E1E;
        int[] px = TextureGenerators.solid(4, 4, color);
        for (int p : px) assertEquals(color, p);
    }

    @Test void solid_zeroSizeYieldsEmpty() {
        assertEquals(0, TextureGenerators.solid(4, 0, 0xFF000000).length);
    }

    @Test void solid_rowMajorLayout() {
        int[] px = TextureGenerators.solid(2, 2, 0xFF000001);
        assertEquals(0xFF000001, px[3]);
    }

    @Test void argbToAbgr_swapsRedAndBlue() {
        assertEquals(0xFFC8DFE8, TextureGenerators.argbToAbgr(0xFFE8DFC8));
    }

    @Test void argbToAbgr_keepsAlpha() {
        assertEquals(0x33C8DFE8, TextureGenerators.argbToAbgr(0x33E8DFC8));
    }

    @Test void argbToAbgr_grayUnchanged() {
        assertEquals(0xD01E1E1E, TextureGenerators.argbToAbgr(0xD01E1E1E));
    }
}
