package com.niuqu.chatbubble.render;

public record ChatLayout(int panelX, int panelW, int titleY, int msgTop, int msgBottom,
                         int barTop, int screenW, int screenH) {

    public static final int TITLE_H = 24;
    public static final int BAR_H = 26;
    public static final int PAD = 8;
    static final int NOTIF_H = 14;
    public static final int SIDEBAR_W = 90;

    public static ChatLayout of(int screenW, int screenH, int panelWidthConfig, int guiScale,
                                boolean sidebarOpen) {
        int pw = Math.max(100, Math.min(panelWidthConfig / guiScale, screenW));
        int px = sidebarOpen ? SIDEBAR_W : 0;
        if (px + pw > screenW) pw = screenW - px;
        int bt = screenH - BAR_H;
        return new ChatLayout(px, pw, 0, TITLE_H + 1, bt - 1, bt, screenW, screenH);
    }

    public int effectiveMsgBottom(boolean hasNewMessages) {
        return hasNewMessages ? barTop - NOTIF_H - 1 : msgBottom;
    }
}
