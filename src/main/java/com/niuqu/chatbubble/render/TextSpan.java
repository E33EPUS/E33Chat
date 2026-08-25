package com.niuqu.chatbubble.render;

public record TextSpan(int messageIndex, int lineIndex, int kind,
                       int x, int y, int w, int h,
                       String text, float scale, Object visualLine,
                       int[] prefixWidths, int selectionBg, int selectionFg) {

    public static final int KIND_NAME = 0;
    public static final int KIND_CONTENT = 1;
    public static final int KIND_QUOTE = 2;

    public TextSpan(int messageIndex, int lineIndex, int kind,
                    int x, int y, int w, int h,
                    String text, float scale) {
        this(messageIndex, lineIndex, kind, x, y, w, h, text, scale, null, null, 0, 0);
    }

    public TextSpan(int messageIndex, int lineIndex, int kind,
                    int x, int y, int w, int h,
                    String text, float scale, Object visualLine) {
        this(messageIndex, lineIndex, kind, x, y, w, h, text, scale, visualLine, null, 0, 0);
    }

    public TextSpan withPosition(int nx, int ny, int nw, int nh) {
        return new TextSpan(messageIndex, lineIndex, kind,
            nx, ny, nw, nh, text, scale, visualLine, prefixWidths, selectionBg, selectionFg);
    }

    public long orderKey() {
        return (long) messageIndex * 100_000L + kind * 10_000L + lineIndex;
    }
}
