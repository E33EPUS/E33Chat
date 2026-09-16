package com.niuqu.chatbubble.ui;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Grid hit-testing math in the emoji panel. On a shrunken panel the right-hand
 * padding band used to divide into a column index past the last column, which
 * then wrapped onto the next row's first cell - clicking empty padding sent
 * the wrong emote (and on the custom page, the wrong image).
 */
class ChatEmojiPanelGridTest {

    private static final int SLOT = 18;
    private static final int EMOTE_SLOT = 26;

    @Test
    void columnsInsideTheGridResolve() {
        // px=100, cell=18, 5 columns -> x from 104 to 104+5*18-1.
        for (int col = 0; col < 5; col++) {
            int mx = 100 + 4 + col * SLOT;
            assertEquals(col, ChatEmojiPanel.gridColumn(mx, 100, SLOT, 5),
                "cell start must resolve to its own column");
        }
    }

    @Test
    void rightPaddingBandIsNotAColumn() {
        // 5 columns of 18 = 90px used; the panel is wider, so the tail is padding.
        int firstPaddingX = 100 + 4 + 5 * SLOT;
        assertEquals(-1, ChatEmojiPanel.gridColumn(firstPaddingX, 100, SLOT, 5),
            "the padding band must not resolve to a column (it used to wrap to the next row)");
        assertEquals(-1, ChatEmojiPanel.gridColumn(firstPaddingX + 12, 100, SLOT, 5));
    }

    @Test
    void leftOfTheGridIsNotAColumn() {
        // The 4px inset floors to column 0 (integer division truncates toward
        // zero), so a click clearly left of the panel is the case to pin.
        assertEquals(0, ChatEmojiPanel.gridColumn(100 + 4, 100, SLOT, 5),
            "the inset start floors to column 0");
        assertEquals(-1, ChatEmojiPanel.gridColumn(100 - SLOT, 100, SLOT, 5),
            "a full cell left of the panel is not a column");
        assertEquals(-1, ChatEmojiPanel.gridColumn(50, 100, SLOT, 5), "outside the panel is not a cell");
    }

    @Test
    void emoteGridUsesTheSameGuard() {
        assertEquals(0, ChatEmojiPanel.gridColumn(100 + 4, 100, EMOTE_SLOT, 3));
        assertEquals(-1, ChatEmojiPanel.gridColumn(100 + 4 + 3 * EMOTE_SLOT, 100, EMOTE_SLOT, 3));
    }

    @Test
    void degenerateSizesAreRejected() {
        assertEquals(-1, ChatEmojiPanel.gridColumn(110, 100, 0, 5));
        assertEquals(-1, ChatEmojiPanel.gridColumn(110, 100, SLOT, 0));
    }
}
