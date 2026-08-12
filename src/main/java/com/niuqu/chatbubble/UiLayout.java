package com.niuqu.chatbubble;

public final class UiLayout {
    private UiLayout() {}

    public static int centerX(int containerLeft, int containerWidth, int elemWidth) {
        return containerLeft + (containerWidth - elemWidth) / 2;
    }

    public static int clampX(int x, int minX) {
        return Math.max(x, minX);
    }

    public static int clampW(int x, int w, int maxX) {
        if (x + w > maxX) return maxX - x;
        return w;
    }
}
