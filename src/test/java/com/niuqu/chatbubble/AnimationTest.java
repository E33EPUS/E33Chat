package com.niuqu.chatbubble;
import com.niuqu.chatbubble.render.AnimationStyle;
import com.niuqu.chatbubble.render.Animation;

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

    @Test void easeOutQuad_endpoints() {
        assertEquals(0f, Animation.easeOutQuad(0f), 0.0001f);
        assertEquals(1f, Animation.easeOutQuad(1f), 0.0001f);
        assertEquals(0.75f, Animation.easeOutQuad(0.5f), 0.0001f);
    }

    @Test void easeOutBack_overshootsThenSettles() {
        assertEquals(0f, Animation.easeOutBack(0f), 0.0001f);
        assertEquals(1f, Animation.easeOutBack(1f), 0.0001f);
        assertTrue(Animation.easeOutBack(0.5f) > 1f, "easeOutBack(0.5) should overshoot past 1");
    }

    @Test void styleCurve_noneIsOne() {
        assertEquals(1f, Animation.styleCurve(AnimationStyle.NONE, 0f), 0.0001f);
        assertEquals(1f, Animation.styleCurve(AnimationStyle.NONE, 0.5f), 0.0001f);
    }

    @Test void styleCurve_endpoints() {
        assertEquals(0f, Animation.styleCurve(AnimationStyle.SLIDE, 0f), 0.0001f);
        assertEquals(1f, Animation.styleCurve(AnimationStyle.SLIDE, 1f), 0.0001f);
        assertEquals(0f, Animation.styleCurve(AnimationStyle.FADE, 0f), 0.0001f);
        assertEquals(1f, Animation.styleCurve(AnimationStyle.FADE, 1f), 0.0001f);
        assertEquals(0f, Animation.styleCurve(AnimationStyle.ZOOM, 0f), 0.0001f);
        assertEquals(1f, Animation.styleCurve(AnimationStyle.ZOOM, 1f), 0.0001f);
    }

    @Test void styleCurve_slideMatchesCubic() {
        assertEquals(Animation.easeOutCubic(0.5f), Animation.styleCurve(AnimationStyle.SLIDE, 0.5f), 0.0001f);
    }

    @Test void styleCurve_clamps() {
        assertEquals(0f, Animation.styleCurve(AnimationStyle.SLIDE, -1f), 0.0001f);
        assertEquals(1f, Animation.styleCurve(AnimationStyle.SLIDE, 2f), 0.0001f);
    }
}
