package com.niuqu.chatbubble.render;

import net.minecraft.util.Mth;

/**
 * One scroll region's animation + drag state, shared by the two config screens
 * (which previously carried ~250 lines of duplicated right/tree scroll state).
 *
 * Behaviour contract (kept identical to the original):
 * - wheel animates 120ms ease-out, drag animates 80ms (durations passed in)
 * - drag maps mouse travel to scroll offset via thumb travel ratio
 * - tick() clamps the offset to [0, max] every frame
 */
public class SmoothScrollPane {
    private int offset;
    private float animFrom, animTo;
    private long animStart;
    private int animDur;
    private boolean animOn;
    private boolean barDrag;
    private int barDragY, barDragOff;

    public SmoothScrollPane() {}

    public int offset() { return offset; }

    public void setOffset(int v) { offset = v; }

    public void animateTo(float target, int max, int dur) {
        animFrom = offset;
        animTo = Mth.clamp(target, 0, max);
        animStart = net.minecraft.Util.getMillis();
        animDur = dur;
        animOn = true;
    }

    /** Advance the ease-out animation one frame; call once per render tick. */
    public void tick(int max) {
        if (animOn) {
            float t = Animation.progress(animStart, animDur, false);
            offset = Math.round(animFrom + (animTo - animFrom) * t);
            if (t >= 1.0f) { offset = Math.round(animTo); animOn = false; }
        }
        offset = Mth.clamp(offset, 0, max);
    }

    public boolean dragging() { return barDrag; }

    public void dragStart(int mouseY, int currentOffset) {
        barDrag = true;
        barDragY = mouseY;
        barDragOff = currentOffset;
    }

    public void dragTo(int mouseY, int trackH, int totalH, int maxScroll, int dur) {
        if (!barDrag || maxScroll <= 0) return;
        int travel = trackH - ChatScrollbar.thumbHeight(trackH, totalH);
        if (travel > 0) {
            int d = mouseY - barDragY;
            animateTo(barDragOff + (float) d * maxScroll / travel, maxScroll, dur);
        }
    }

    public void dragEnd() { barDrag = false; }

    /** Wheel scroll: sets a target and starts the 120ms animation, no hard jump. */
    public void wheel(double delta, int max, int dur) {
        animateTo(offset - (int) (delta * 20), max, dur);
    }
}
