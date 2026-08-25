package com.niuqu.chatbubble.render;

import org.junit.jupiter.api.Test;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ChatTextSelectionTest {

    private static TextSpan span(int msg, int line, int kind, String text) {
        return new TextSpan(msg, line, kind, 0, 0, 10, 10, text, 1f);
    }

    @Test
    void sameLineForwardSelection() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 1);
        sel.update(0, 0, TextSpan.KIND_CONTENT, 4);
        assertArrayEquals(new int[]{1, 4},
            sel.rangeFor(span(0, 0, TextSpan.KIND_CONTENT, "hello")));
        assertEquals("ell", sel.copyText(List.of(
            span(0, 0, TextSpan.KIND_CONTENT, "hello"))));
    }

    @Test
    void sameLineReverseSelection() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 4);
        sel.update(0, 0, TextSpan.KIND_CONTENT, 1);
        assertArrayEquals(new int[]{1, 4},
            sel.rangeFor(span(0, 0, TextSpan.KIND_CONTENT, "hello")));
    }

    @Test
    void multiLineSelectionCopiesWithNewline() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 1);
        sel.update(0, 1, TextSpan.KIND_CONTENT, 2);
        List<TextSpan> spans = List.of(
            span(0, 0, TextSpan.KIND_CONTENT, "hello"),
            span(0, 1, TextSpan.KIND_CONTENT, "world"));
        assertEquals("ello\nwo", sel.copyText(spans));
    }

    @Test
    void multiMessageSelectionCopiesInVisualOrder() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(1, 0, TextSpan.KIND_CONTENT, 0);
        sel.update(2, 0, TextSpan.KIND_CONTENT, 3);
        List<TextSpan> spans = List.of(
            span(1, 0, TextSpan.KIND_CONTENT, "aaa"),
            span(2, 0, TextSpan.KIND_CONTENT, "bbbb"));
        assertEquals("aaa\nbbb", sel.copyText(spans));
    }

    @Test
    void emptySelectionReturnsEmptyString() {
        ChatTextSelection sel = new ChatTextSelection();
        assertFalse(sel.hasSelection());
        assertEquals("", sel.copyText(List.of(
            span(0, 0, TextSpan.KIND_CONTENT, "hello"))));
    }

    @Test
    void clearedSelectionHasNoRange() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 1);
        sel.update(0, 0, TextSpan.KIND_CONTENT, 3);
        sel.clear();
        assertNull(sel.rangeFor(span(0, 0, TextSpan.KIND_CONTENT, "hello")));
    }

    @Test
    void markMovedMarksDragAsMoved() {
        ChatTextSelection sel = new ChatTextSelection();
        sel.begin(0, 0, TextSpan.KIND_CONTENT, 0);
        assertFalse(sel.didMove());
        sel.markMoved();
        assertTrue(sel.didMove());
    }
}
