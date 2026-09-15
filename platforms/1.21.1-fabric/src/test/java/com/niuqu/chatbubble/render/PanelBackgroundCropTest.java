package com.niuqu.chatbubble.render;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Framing maths for the custom panel background (2.4.12). Pure functions, so
 * they run headless; the point of the tests is that the default crop keeps the
 * old behaviour exactly and that no crop can ever point outside the picture.
 */
class PanelBackgroundCropTest {

    // ---- parse / format round trip ----

    @Test void blankOrMalformedCropFallsBackToDefault() {
        assertEquals(PanelBackground.Crop.DEFAULT, PanelBackground.parseCrop(null));
        assertEquals(PanelBackground.Crop.DEFAULT, PanelBackground.parseCrop(""));
        assertEquals(PanelBackground.Crop.DEFAULT, PanelBackground.parseCrop("   "));
        assertEquals(PanelBackground.Crop.DEFAULT, PanelBackground.parseCrop("0.5,0.5"));
        assertEquals(PanelBackground.Crop.DEFAULT, PanelBackground.parseCrop("a,b,c"));
        assertEquals(PanelBackground.Crop.DEFAULT, PanelBackground.parseCrop("0.5,0.5,NaN"));
    }

    @Test void cropValuesAreClampedToUsableRanges() {
        PanelBackground.Crop c = PanelBackground.parseCrop("-5,9,0.25");
        assertEquals(0f, c.centerX());
        assertEquals(1f, c.centerY());
        // zoom below 1 would show the area around the picture; clamp to cover
        assertEquals(1f, c.zoom());
    }

    @Test void formatRoundTripsThroughParse() {
        PanelBackground.Crop c = new PanelBackground.Crop(0.25f, 0.75f, 1.5f);
        PanelBackground.Crop back = PanelBackground.parseCrop(PanelBackground.formatCrop(c));
        assertEquals(c.centerX(), back.centerX(), 1e-3);
        assertEquals(c.centerY(), back.centerY(), 1e-3);
        assertEquals(c.zoom(), back.zoom(), 1e-3);
    }

    // ---- source rect ----

    @Test void defaultCropReproducesTheOldCenterCrop() {
        // Wide picture, narrow panel: cover keeps the full height and crops the
        // sides evenly — byte-for-byte the pre-2.4.12 behaviour.
        int[] r = PanelBackground.sourceRect(1920, 1080, 500, 1000, PanelBackground.Crop.DEFAULT);
        int w = r[2], h = r[3];
        assertEquals(1080, h, "full height kept for a wide picture");
        assertEquals(540, w, "width follows the panel aspect ratio");
        assertEquals(690, r[0], "centered horizontally");
        assertEquals(0, r[1]);
    }

    @Test void zoomTightensTheSourceRect() {
        int[] wide = PanelBackground.sourceRect(1920, 1080, 500, 1000, PanelBackground.Crop.DEFAULT);
        int[] tight = PanelBackground.sourceRect(1920, 1080, 500, 1000,
            new PanelBackground.Crop(0.5f, 0.5f, 2f));
        assertTrue(tight[2] < wide[2], "zoom 2 takes half the width");
        assertTrue(tight[3] < wide[3] || tight[3] == wide[3]);
        assertEquals(wide[2] / 2, tight[2], 1);
    }

    @Test void centeringMovesTheWindowAcrossThePicture() {
        int[] left = PanelBackground.sourceRect(1920, 1080, 500, 1000,
            new PanelBackground.Crop(0f, 0.5f, 1.5f));
        int[] right = PanelBackground.sourceRect(1920, 1080, 500, 1000,
            new PanelBackground.Crop(1f, 0.5f, 1.5f));
        assertEquals(0, left[0], "centerX=0 pins to the left edge");
        assertEquals(1920 - right[2], right[0], "centerX=1 pins to the right edge");
    }

    @Test void sourceRectNeverLeavesThePicture() {
        // Sweep the crop space; the window must always sit inside the image, and
        // never collapse to zero pixels.
        for (float cx = 0f; cx <= 1f; cx += 0.1f) {
            for (float cy = 0f; cy <= 1f; cy += 0.1f) {
                for (float zoom = 1f; zoom <= 4f; zoom += 0.25f) {
                    int[] r = PanelBackground.sourceRect(640, 480, 300, 900,
                        new PanelBackground.Crop(cx, cy, zoom));
                    assertTrue(r[2] >= 1 && r[3] >= 1, "non-empty: " + cx + "," + cy + "," + zoom);
                    assertTrue(r[0] >= 0 && r[1] >= 0, "origin in bounds");
                    assertTrue(r[0] + r[2] <= 640, "right edge inside: " + r[0] + "+" + r[2]);
                    assertTrue(r[1] + r[3] <= 480, "bottom edge inside: " + r[1] + "+" + r[3]);
                }
            }
        }
    }

    @Test void extremeZoomIsBoundedByThePicture() {
        // Absurd zoom must not try to sample a sub-pixel window or escape the image.
        int[] r = PanelBackground.sourceRect(100, 100, 400, 400,
            new PanelBackground.Crop(0.5f, 0.5f, 1000f));
        assertTrue(r[2] >= 1 && r[3] >= 1);
        assertTrue(r[0] + r[2] <= 100 && r[1] + r[3] <= 100);
    }

    @Test void degenerateInputsDoNotBlowUp() {
        int[] r = PanelBackground.sourceRect(0, 0, 100, 100, PanelBackground.Crop.DEFAULT);
        assertEquals(1, r[2]);
        assertEquals(1, r[3]);
        r = PanelBackground.sourceRect(100, 100, 0, 0, PanelBackground.Crop.DEFAULT);
        assertEquals(1, r[2]);
        assertEquals(1, r[3]);
    }
}
