package com.niuqu.chatbubble.render;

import java.util.List;

public final class ChatTextSelection {

    public static final int SELECTION_BG = 0x8033B5E5;

    private int anchorMsg = -1, anchorLine = -1, anchorKind = -1, anchorChar = -1;
    private int focusMsg = -1, focusLine = -1, focusKind = -1, focusChar = -1;
    private boolean dragActive;
    private boolean moved;

    public void begin(int msg, int line, int kind, int ch) {
        anchorMsg = focusMsg = msg;
        anchorLine = focusLine = line;
        anchorKind = focusKind = kind;
        anchorChar = focusChar = ch;
        dragActive = true;
        moved = false;
    }

    public void update(int msg, int line, int kind, int ch) {
        if (!dragActive) return;
        if (msg != focusMsg || line != focusLine || kind != focusKind || ch != focusChar) {
            moved = true;
        }
        focusMsg = msg;
        focusLine = line;
        focusKind = kind;
        focusChar = ch;
    }

    public void endDrag() {
        dragActive = false;
    }

    public boolean isDragActive() {
        return dragActive;
    }

    public boolean didMove() {
        return moved;
    }

    public void clear() {
        anchorMsg = focusMsg = -1;
        anchorLine = focusLine = -1;
        anchorKind = focusKind = -1;
        anchorChar = focusChar = -1;
        dragActive = false;
        moved = false;
    }

    public boolean hasSelection() {
        if (anchorMsg < 0 || focusMsg < 0) return false;
        if (anchorMsg != focusMsg || anchorLine != focusLine || anchorKind != focusKind) {
            return true;
        }
        return anchorChar != focusChar;
    }

    private static long key(int msg, int line, int kind) {
        return (long) msg * 100_000L + kind * 10_000L + line;
    }

    public int[] rangeFor(TextSpan span) {
        if (anchorMsg < 0 || focusMsg < 0) return null;
        long a = key(anchorMsg, anchorLine, anchorKind);
        long f = key(focusMsg, focusLine, focusKind);
        long s = span.orderKey();
        int len = span.text().length();
        int start;
        int end;
        if (a == f) {
            if (s != a) return null;
            start = Math.min(anchorChar, focusChar);
            end = Math.max(anchorChar, focusChar);
        } else if (a < f) {
            if (s < a || s > f) return null;
            start = (s == a) ? anchorChar : 0;
            end = (s == f) ? focusChar : len;
        } else {
            if (s < f || s > a) return null;
            start = (s == f) ? focusChar : 0;
            end = (s == a) ? anchorChar : len;
        }
        start = Math.max(0, Math.min(len, start));
        end = Math.max(0, Math.min(len, end));
        if (end <= start) return null;
        return new int[]{start, end};
    }

    public String copyText(List<TextSpan> spans) {
        StringBuilder sb = new StringBuilder();
        boolean first = true;
        for (TextSpan span : spans) {
            int[] r = rangeFor(span);
            if (r == null) continue;
            if (!first) sb.append('\n');
            sb.append(span.text(), r[0], r[1]);
            first = false;
        }
        return sb.toString();
    }
}
