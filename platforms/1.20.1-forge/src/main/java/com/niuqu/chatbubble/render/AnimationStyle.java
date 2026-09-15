package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.render.Animation;

/**
 * Per-element animation style. SLIDE keeps the classic slide-in/out, FADE
 * animates opacity only, ZOOM scales in around the element center (with a
 * slight overshoot), NONE disables animation for that element entirely.
 */
public enum AnimationStyle {
    SLIDE,
    FADE,
    ZOOM,
    NONE;

    /** Case-insensitive parse with fallback (config strings are lower-case). */
    public static AnimationStyle parse(String s) {
        if (s == null) return SLIDE;
        try {
            return valueOf(s.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SLIDE;
        }
    }
}
