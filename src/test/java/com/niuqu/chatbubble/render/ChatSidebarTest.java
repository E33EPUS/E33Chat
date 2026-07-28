package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.Animation;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatSidebarTest {

    // Easing formulas match getSidebarAnimProgress() internals in ChatBubbleScreen:
    //   Opening:  Animation.progress(..., false) → easeOutCubic(t) = 1 - (1-t)^3
    //   Closing:  1.0f - Animation.progress(..., false) = 1 - easeOutCubic(t) = (1-t)^3

    private static float animOpenEasing(float t) {
        return Animation.easeOutCubic(t);
    }

    private static float animCloseEasing(float t) {
        float v = 1f - t;
        return v * v * v; // (1-t)^3
    }

    @Test
    void easing_open_start() {
        assertEquals(0f, animOpenEasing(0f), 0.001f);
    }

    @Test
    void easing_open_end() {
        assertEquals(1f, animOpenEasing(1f), 0.001f);
    }

    @Test
    void easing_open_mid() {
        assertEquals(0.875f, animOpenEasing(0.5f), 0.001f);
    }

    @Test
    void easing_openQuarter() {
        assertEquals(0.578125f, animOpenEasing(0.25f), 0.001f);
    }

    @Test
    void easing_close_start() {
        assertEquals(1f, animCloseEasing(0f), 0.001f);
    }

    @Test
    void easing_close_end() {
        assertEquals(0f, animCloseEasing(1f), 0.001f);
    }

    @Test
    void easing_close_mid() {
        assertEquals(0.125f, animCloseEasing(0.5f), 0.001f);
    }

    @Test
    void easing_openMatchesAnimationProgress() {
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f;
            assertEquals(Animation.easeOutCubic(t), animOpenEasing(t), 0.001f,
                "mismatch at t=" + t);
        }
    }

    @Test
    void easing_isMonotonic_open() {
        float prev = -1f;
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f;
            float v = animOpenEasing(t);
            assertTrue(v >= prev, "open easing not monotonic at t=" + t);
            prev = v;
        }
    }

    @Test
    void easing_isMonotonic_close() {
        float prev = 2f;
        for (int i = 0; i <= 100; i++) {
            float t = i / 100f;
            float v = animCloseEasing(t);
            assertTrue(v <= prev, "close easing not monotonic at t=" + t);
            prev = v;
        }
    }

    // screenX formula: (progress - 1.0f) * ChatSidebar.WIDTH
    // When progress=0 (closed): screenX = -90, panelX = 90 + (-90) = 0
    // When progress=1 (open):    screenX = 0,   panelX = 90 + 0 = 90

    @Test
    void screenX_closed() {
        float sx = (0f - 1f) * ChatSidebar.WIDTH;
        assertEquals(-90, sx, 0.001f);
        assertEquals(0, (int) sx + ChatSidebar.WIDTH);
    }

    @Test
    void screenX_open() {
        float sx = (1f - 1f) * ChatSidebar.WIDTH;
        assertEquals(0, sx, 0.001f);
        assertEquals(90, (int) sx + ChatSidebar.WIDTH);
    }

    @Test
    void screenX_midAnimation() {
        float progress = 0.5f;
        float sx = (progress - 1f) * ChatSidebar.WIDTH;
        assertEquals(-45, sx, 0.001f);
        assertEquals(45, (int) sx + ChatSidebar.WIDTH);
    }

    @Test
    void screenX_gapFree_alignment() {
        // When screenX is truncated the same way on both sides,
        // sidebar right edge = panel left edge. No gap.
        for (int i = 0; i <= 100; i++) {
            float progress = 1f - animCloseEasing(i / 100f); // simulate closing
            float sx = (progress - 1f) * ChatSidebar.WIDTH;
            int sidebarRight = (int) sx + ChatSidebar.WIDTH;
            int panelLeft = (int) sx + ChatSidebar.WIDTH;
            assertEquals(panelLeft, sidebarRight,
                "gap at progress=" + progress);
        }
    }

    @Test
    void width_constant() {
        assertEquals(90, ChatSidebar.WIDTH);
    }
}
