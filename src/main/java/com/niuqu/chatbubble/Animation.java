package com.niuqu.chatbubble;

import net.minecraft.util.Mth;

public final class Animation {
    private Animation() {}

    public static float easeOutCubic(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    public static float easeOutQuad(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t);
    }

    /**
     * Overshooting ease-out (goes past 1 then settles back), used by the banner
     * slide-in and the ZOOM scale.
     */
    public static float easeOutBack(float t) {
        float c1 = 1.70158f;
        float c3 = c1 + 1.0f;
        return 1.0f + c3 * (float) Math.pow(t - 1, 3) + c1 * (float) Math.pow(t - 1, 2);
    }

    /**
     * Entrance curve for an animation style: maps raw progress t in [0,1] to an
     * eased progress in [0,1]. NONE always returns 1 (no animation). Callers
     * that need the ZOOM overshoot use {@link #easeOutBack(float)} directly.
     */
    public static float styleCurve(AnimationStyle style, float t) {
        t = Mth.clamp(t, 0f, 1f);
        if (style == null || style == AnimationStyle.NONE) return 1f;
        if (style == AnimationStyle.FADE) return easeOutQuad(t);
        return easeOutCubic(t); // SLIDE / ZOOM
    }

    public static float lerpTo(float current, float target, float speed, float snapThreshold) {
        float next = current + (target - current) * speed;
        if (Math.abs(next - target) < snapThreshold) return target;
        return next;
    }

    public static int fadeIn(int ticks, int duration) {
        if (duration <= 0 || ticks >= duration) return 255;
        return ticks * 255 / duration;
    }

    public static int fadeOut(int ticks, int duration) {
        if (duration <= 0 || ticks >= duration) return 0;
        return (duration - ticks) * 255 / duration;
    }

    public static int fadeInOut(int ticks, int fadeInDur, int holdDur, int fadeOutDur) {
        if (ticks < fadeInDur) return fadeIn(ticks, fadeInDur);
        ticks -= fadeInDur;
        if (ticks < holdDur) return 255;
        ticks -= holdDur;
        return fadeOut(ticks, fadeOutDur);
    }

    public static float progress(long startMs, int durationMs, boolean closing) {
        long elapsed = net.minecraft.Util.getMillis() - startMs;
        float t = Mth.clamp((float) elapsed / durationMs, 0f, 1f);
        if (closing) return 1.0f - (t * t);
        return easeOutCubic(t);
    }
}
