package com.niuqu.chatbubble;

import net.minecraft.util.math.MathHelper;

public final class Animation {
    private Animation() {}

    public static float easeOutCubic(float t) {
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
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
        long elapsed = net.minecraft.util.Util.getMeasuringTimeMs() - startMs;
        float t = MathHelper.clamp((float) elapsed / durationMs, 0f, 1f);
        if (closing) return 1.0f - (t * t);
        return easeOutCubic(t);
    }
}