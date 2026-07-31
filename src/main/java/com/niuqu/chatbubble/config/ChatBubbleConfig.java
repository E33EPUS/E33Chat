package com.niuqu.chatbubble.config;

import java.util.List;

public record ChatBubbleConfig(
    boolean enabled,
    String theme,
    boolean redDotEnabled,
    boolean hideChatIcon,
    boolean animationEnabled,
    boolean strongHintEnabled,
    boolean systemChatAsBubble,
    boolean antiSpam,
    boolean chatHistoryEnabled,
    boolean previewEnabled,
    int previewLines,
    int previewWidth,
    int timeSeparatorMinutes,
    int panelWidth,
    int bubbleCornerRadius,
    String ownBubbleColor,
    String otherBubbleColor,
    String ownTextColor,
    String otherTextColor,
    boolean soundPublic,
    boolean soundSystem,
    boolean soundWhisper,
    boolean debugLog,
    boolean preserveInput,
    boolean colorCodes,
    List<String> sidebarHidePatterns,
    List<String> quickChatPhrases,
    boolean mentionBannerEnabled,
    int mentionBannerDuration,
    boolean mentionSoundEnabled,
    boolean mentionRequireAt,
    boolean mentionWhisperBanner,
    boolean blurEnabled,
    int panelOpacity,
    int soundVolume,
    boolean ownMentionNotify,
    boolean ownQuoteNotify,
    int bannerCornerRadius
) {
    public static ChatBubbleConfig defaults() {
        return new ChatBubbleConfig(
            true, "dark", true, false, true,
            true, false, false,
            false, true, 3, 200, 5, 1000, 4,
            "#1E90FF", "#4A4A4A", "#FFFFFF", "#FFFFFF",
            false, false, true, true, true, false,
            List.of(), List.of(),
            true, 4, true, true, true,
            true, 80, 80, false, false, 4
        );
    }

    public static int parseHexColor(String hex, int defaultColor) {
        if (hex == null) return defaultColor;
        try {
            String h = hex.replace("#", "").trim();
            if (h.length() != 6) return defaultColor;
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }

    public ChatBubbleConfig withTheme(String theme) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, animationEnabled,
            strongHintEnabled, systemChatAsBubble, antiSpam,
            chatHistoryEnabled, previewEnabled, previewLines, previewWidth, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, quickChatPhrases,
            mentionBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, bannerCornerRadius);
    }

    public ChatBubbleConfig withQuickChatPhrases(List<String> phrases) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, animationEnabled,
            strongHintEnabled, systemChatAsBubble, antiSpam,
            chatHistoryEnabled, previewEnabled, previewLines, previewWidth, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, phrases,
            mentionBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, bannerCornerRadius);
    }

    public ChatBubbleConfig withSidebarHidePatterns(List<String> patterns) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, animationEnabled,
            strongHintEnabled, systemChatAsBubble, antiSpam,
            chatHistoryEnabled, previewEnabled, previewLines, previewWidth, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, patterns, quickChatPhrases,
            mentionBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, bannerCornerRadius);
    }

    public boolean isSidebarHidden(String playerName) {
        if (sidebarHidePatterns == null || sidebarHidePatterns.isEmpty()) return false;
        String lowerName = playerName.toLowerCase();
        for (String pattern : sidebarHidePatterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            String regex = "^" + pattern.toLowerCase()
                .replace("*", ".*")
                .replace("?", ".") + "$";
            if (lowerName.matches(regex)) return true;
        }
        return false;
    }
}
