package com.niuqu.chatbubble.render;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class ChatScrollbarTest {

    @Test
    void thumbHeight_proportionalWhenTrackSmallerThanTotal() {
        int result = ChatScrollbar.thumbHeight(100, 200);
        assertEquals(50, result);
    }

    @Test
    void thumbHeight_cappedAtTrackH() {
        int result = ChatScrollbar.thumbHeight(100, 90);
        assertEquals(100, result);
    }

    @Test
    void thumbHeight_belowMinClampsUp() {
        int result = ChatScrollbar.thumbHeight(100, 10000);
        assertEquals(8, result); // 100*100/10000=1 → clamped to MIN_THUMB_H
    }

    @Test
    void thumbHeight_totalHZeroReturnsTrackH() {
        int result = ChatScrollbar.thumbHeight(100, 0);
        assertEquals(100, result);
    }

    @Test
    void thumbY_startOffset() {
        int y = ChatScrollbar.thumbY(50, 300, 30, 0, 100);
        assertEquals(50, y);
    }

    @Test
    void thumbY_endOffset() {
        int y = ChatScrollbar.thumbY(50, 300, 30, 100, 100);
        assertEquals(320, y);
    }

    @Test
    void thumbY_noTravelReturnsTop() {
        int y = ChatScrollbar.thumbY(50, 30, 30, 50, 100);
        assertEquals(50, y);
    }

    @Test
    void alphaTarget_oneWhenHovering() {
        assertEquals(1f, ChatScrollbar.alphaTarget(true, false, 0), 0f);
    }

    @Test
    void alphaTarget_oneWhenDragging() {
        assertEquals(1f, ChatScrollbar.alphaTarget(false, true, 0), 0f);
    }

    @Test
    void alphaTarget_oneWhenRecentlyScrolled() {
        assertEquals(1f, ChatScrollbar.alphaTarget(false, false, System.currentTimeMillis()), 0f);
    }

    @Test
    void alphaTarget_zeroWhenAway() {
        assertEquals(0f, ChatScrollbar.alphaTarget(false, false, 0), 0f);
    }

    @Test
    void isHoveringThumb_inside() {
        assertTrue(ChatScrollbar.isHoveringThumb(5, 5, 0, 0, 10));
    }

    @Test
    void isHoveringThumb_outside() {
        assertFalse(ChatScrollbar.isHoveringThumb(15, 5, 0, 0, 10));
    }
}
