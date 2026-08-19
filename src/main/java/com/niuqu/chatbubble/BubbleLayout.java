package com.niuqu.chatbubble;

/**
 * Pure bubble_size layout math (2.4.0 sync). No Minecraft dependencies so the
 * shared headless test suite can exercise it directly and every version chain
 * uses the same numbers.
 *
 * <p>Design unit = vanilla font height (9 px). bubble_size is the target bubble
 * text height in px (clamped 5-14, default 9 = scale 1.0). Bubble coordinates,
 * padding, corner radius and the quote block are numerically pre-scaled by the
 * same factor; text is then rendered through a matrix scale so hit-testing on
 * the pre-scaled rects needs no inverse transform.</p>
 */
public final class BubbleLayout {
    private BubbleLayout() {}

    /** Clamp the raw config value to the UI range (5-14), null = default 9. */
    public static int clampBubbleSize(Integer configValue) {
        return configValue == null ? 9 : Math.max(5, Math.min(14, configValue));
    }

    /** Bubble scale factor = target px / font height (9). Default 9 -> 1.0. */
    public static float scale(int bubbleSizePx, int fontHeight) {
        return bubbleSizePx / (float) fontHeight;
    }

    /**
     * Text wrap width in design units for a given bubble scale: a bigger bubble
     * fits fewer characters per line. Clamped to a readable minimum.
     */
    public static int scaledWrapWidth(int bubbleMaxW, float scale) {
        return Math.max(16, (int) (bubbleMaxW / scale));
    }
}
