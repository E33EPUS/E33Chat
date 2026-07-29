package com.niuqu.chatbubble;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AnimationTest {

    @Test void easeOutCubic_endpoints() {
        assertEquals(0f, Animation.easeOutCubic(0f), 0.0001f);
        assertEquals(1f, Animation.easeOutCubic(1f), 0.0001f);
    }

    @Test void easeOutCubic_midpoint() {
        assertEquals(0.875f, Animation.easeOutCubic(0.5f), 0.0001f);
    }

    @Test void easeOutCubic_monotonic() {
        float prev = -1f;
        for (int i = 0; i <= 100; i++) {
            float v = Animation.easeOutCubic(i / 100f);
            assertTrue(v >= prev, "not monotonic at t=" + i / 100f);
            prev = v;
        }
    }

    @Test void lerpTo_movesTowardTarget() {
        assertEquals(5f, Animation.lerpTo(0f, 10f, 0.5f, 0.001f), 0.0001f);
    }

    @Test void lerpTo_snapsWithinThreshold() {
        assertEquals(10f, Animation.lerpTo(9.9995f, 10f, 0.5f, 0.01f), 0.0001f);
    }

    @Test void fadeIn_progression() {
        assertEquals(0, Animation.fadeIn(0, 10));
        assertEquals(127, Animation.fadeIn(5, 10));
        assertEquals(255, Animation.fadeIn(10, 10));
        assertEquals(255, Animation.fadeIn(15, 10));
    }

    @Test void fadeIn_zeroDurationFull() {
        assertEquals(255, Animation.fadeIn(0, 0));
    }

    @Test void fadeOut_progression() {
        assertEquals(255, Animation.fadeOut(0, 10));
        assertEquals(127, Animation.fadeOut(5, 10));
        assertEquals(0, Animation.fadeOut(10, 10));
    }

    @Test void fadeOut_zeroDurationGone() {
        assertEquals(0, Animation.fadeOut(0, 0));
    }

    @Test void fadeInOut_phases() {
        assertEquals(0, Animation.fadeInOut(0, 10, 5, 10));
        assertEquals(255, Animation.fadeInOut(10, 10, 5, 10));
        assertEquals(255, Animation.fadeInOut(14, 10, 5, 10));
        assertEquals(0, Animation.fadeInOut(25, 10, 5, 10));
    }
}
