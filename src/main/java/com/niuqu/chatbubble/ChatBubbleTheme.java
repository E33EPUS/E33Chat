package com.niuqu.chatbubble;

public enum ChatBubbleTheme {
    DARK,
    LIGHT;

    public record Colors(
        int panelBg, int titleBg, int barBg,
        int sidebarBg, int sidebarItemHover, int sidebarItemSelected, int sidebarDivider,
        int divider, int inputBg,
        int textPrimary, int textSecondary, int textMuted,
        int nameColor, int timeColor,
        int popupBg, int popupHover, int popupText,
        int contextBg, int contextHover, int contextText,
        int iconHover,
        int notificationText, int whisperBar,
        int toastBg, int toastText,
        int scrollbar, int scrollbarHover,
        int closeBg, int closeHoverBg, int closeText,
        int systemText, int quoteBar, int duplicateLabel,
        int redDot, int redDotMention,
        int configTitle, int configSection, int configLabel, int configBg,
        int bannerBg, int bannerText, int bannerBar
    ) {}

    public Colors colors() {
        return switch (this) {
            case DARK -> new Colors(
                0xD01E1E1E, 0xFF242424, 0xFF242424,
                0xFF1A1A1A, 0xFF2A2A2A, 0xFF3A3A3A, 0xFF333333,
                0xFF333333, 0xFF2A2A2A,
                0xFFFFFFFF, 0xFFAAAAAA, 0xFF888888,
                0xFFCCCCCC, 0xFF999999,
                0xB31E1E1E, 0xB3444444, 0xFFFFFFFF,
                0xEE2A2A2A, 0xFF4A4A4A, 0xFFFFFFFF,
                0xFF444444,
                0xFFFFFF55, 0xAA7B2D8B,
                0xCC000000, 0xFFFFFFFF,
                0x00FFFFFF, 0x00FFFFFF,
                0xFF333333, 0xFF555555, 0xFFCCCCCC,
                0xFF888888, 0xFFFFFFFF, 0xFFFFAA00,
                0xFFFF0000, 0xFFFF4444,
                0xFFFFFFFF, 0xFFFFAA00, 0xFFFFFFFF, 0xC0101010,
                0xEE333333, 0xFFFFFF55, 0xFFFFFFFF
            );
            case LIGHT -> new Colors(
                0x9AE8DFC8, 0xFFF0EDD8, 0xFFF0EDD8,
                0xFFD8D5C2, 0xFFDED9C4, 0xFFC9C6B3, 0xFFC8C3AC,
                0xFFC8C3AC, 0xFFD8CEB5,
                0xFF2D2D1F, 0xFF5A5A45, 0xFF787860,
                0xFF2D2D1F, 0xFF787860,
                0xDDF0EDD8, 0xDDE0DBC8, 0xFF2D2D1F,
                0xFFF0EDD8, 0xFFDDD8C0, 0xFF2D2D1F,
                0xFFD5D0B8,
                0xFFFFAA00, 0xAA9966CC,
                0xCC2D2D1F, 0xFFF5F0E0,
                0x60706858, 0x90505848,
                0xFFD5D0B8, 0xFFC0BBA3, 0xFF5A5A45,
                0xFF787860, 0xFFCC6600, 0xFFCC6600,
                0xFFCC0000, 0xFFFF2222,
                0xFF2D2D1F, 0xFFCC6600, 0xFF2D2D1F, 0xC0EDE8D3,
                0xEEF0EDD8, 0xFFCC6600, 0xFF471900
            );
        };
    }

    public static int argb(int a, int r, int g, int b) {
        return (a << 24) | (r << 16) | (g << 8) | b;
    }

    public static int alphaBlend(int color, int alpha) {
        return (alpha << 24) | (color & 0x00FFFFFF);
    }
}