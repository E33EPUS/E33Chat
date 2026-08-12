package com.niuqu.chatbubble.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageLoaderTest {

    @Test
    void rateLimitWindowAllowsWindowSize() {
        ImageLoader.clearCacheForTest();
        // first four acquisitions pass
        assertTrue(ImageLoader.tryAcquireSlotForTest());
        assertTrue(ImageLoader.tryAcquireSlotForTest());
        assertTrue(ImageLoader.tryAcquireSlotForTest());
        assertTrue(ImageLoader.tryAcquireSlotForTest());
        // fifth is rejected inside the window
        assertFalse(ImageLoader.tryAcquireSlotForTest());
    }

    @Test
    void scaledSizeKeepsSmallImagesUnchanged() {
        int[] r = ImageLoader.scaledSize(200, 100);
        assertArrayEquals(new int[]{200, 100}, r);
    }

    @Test
    void scaledSizeDownscalesWideImages() {
        int[] r = ImageLoader.scaledSize(2000, 1125);
        assertEquals(320, r[0]);
        assertEquals(180, r[1]); // 1125 * 320/2000 = 180
    }

    @Test
    void scaledSizeDownscalesTallImages() {
        int[] r = ImageLoader.scaledSize(500, 1000);
        assertEquals(90, r[0]); // 500 * 180/1000 = 90
        assertEquals(180, r[1]);
    }

    @Test
    void scaledSizeKeepsAspectRatio() {
        int[] r = ImageLoader.scaledSize(4000, 3000);
        assertEquals(240, r[0]); // 4000 * 180/3000 = 240
        assertEquals(180, r[1]);
        // aspect preserved within rounding: 240/180 == 4/3
        assertTrue(Math.abs(r[0] / (double) r[1] - 4.0 / 3.0) < 0.01);
    }

    @Test
    void scaledSizeHandlesDegenerateInput() {
        assertArrayEquals(new int[]{1, 1}, ImageLoader.scaledSize(0, 0));
        assertArrayEquals(new int[]{1, 1}, ImageLoader.scaledSize(-5, 10));
    }

    @Test
    void usableUrlChecks() {
        assertTrue(ImageLoader.isUsableUrl("https://a.com/x.png"));
        assertTrue(ImageLoader.isUsableUrl("http://localhost:8080/x.png"));
        assertFalse(ImageLoader.isUsableUrl(null));
        assertFalse(ImageLoader.isUsableUrl(""));
        assertFalse(ImageLoader.isUsableUrl("ftp://a.com/x.png"));
        assertFalse(ImageLoader.isUsableUrl("not a url"));
        assertFalse(ImageLoader.isUsableUrl("https://"));
    }
}
