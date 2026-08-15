package com.niuqu.chatbubble.render;

public record ChatLayout(int panelX, int panelW, int titleY, int msgTop, int msgBottom,
                         int barTop, int screenW, int screenH) {

    public static final int TITLE_H = 24;
    public static final int BAR_H = 26;
    public static final int PAD = 8;
    static final int NOTIF_H = 14;
    public static final int SIDEBAR_W = 90;

}
