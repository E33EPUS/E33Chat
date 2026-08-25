package com.niuqu.chatbubble.render;

public record TextSpan(int messageIndex, int lineIndex, int kind,
                       int x, int y, int w, int h,
                       String text, float scale) {

    public static final int KIND_NAME = 0;
    public static final int KIND_CONTENT = 1;
    public static final int KIND_QUOTE = 2;

    public TextSpan withPosition(int nx, int ny, int nw, int nh) {
        return new TextSpan(messageIndex, lineIndex, kind,
            nx, ny, nw, nh, text, scale);
    }

    public long orderKey() {
        return (long) messageIndex * 100_000L + kind * 10_000L + lineIndex;
    }
}
