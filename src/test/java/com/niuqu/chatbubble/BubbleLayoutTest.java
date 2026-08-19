package com.niuqu.chatbubble;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/** Pure bubble_size math (2.4.0 sync) — no MC classes needed. */
class BubbleLayoutTest {

    @Test
    void defaultSizeIsNineAndScaleOne() {
        assertEquals(9, BubbleLayout.clampBubbleSize(null));
        assertEquals(1.0f, BubbleLayout.scale(9, 9));
    }

    @Test
    void clampsConfigRange() {
        assertEquals(5, BubbleLayout.clampBubbleSize(1));
        assertEquals(14, BubbleLayout.clampBubbleSize(99));
        assertEquals(12, BubbleLayout.clampBubbleSize(12));
        assertEquals(5, BubbleLayout.clampBubbleSize(5));
        assertEquals(14, BubbleLayout.clampBubbleSize(14));
    }

    @Test
    void scaleIsProportional() {
        assertEquals(14f / 9f, BubbleLayout.scale(14, 9), 1e-6f);
        assertEquals(5f / 9f, BubbleLayout.scale(5, 9), 1e-6f);
    }

    @Test
    void scaledWrapWidthNarrowsForBiggerBubbles() {
        // bigger bubble (s>1) -> fewer chars per line in design units
        assertEquals(266, BubbleLayout.scaledWrapWidth(400, 1.5f));
        assertEquals(400, BubbleLayout.scaledWrapWidth(400, 1.0f));
        assertEquals(800, BubbleLayout.scaledWrapWidth(400, 0.5f));
    }

    @Test
    void scaledWrapWidthClampedToReadableMinimum() {
        assertEquals(16, BubbleLayout.scaledWrapWidth(20, 1.5f));
        assertEquals(16, BubbleLayout.scaledWrapWidth(16, 1.0f));
        assertEquals(16, BubbleLayout.scaledWrapWidth(1, 3f));
    }
}
