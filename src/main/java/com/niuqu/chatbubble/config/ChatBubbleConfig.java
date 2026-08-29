package com.niuqu.chatbubble.config;

import com.google.gson.annotations.SerializedName;
import java.util.List;

public record ChatBubbleConfig(
    boolean enabled,
    String theme,
    boolean redDotEnabled,
    boolean hideChatIcon,
    boolean animationEnabled,
    boolean systemChatAsBubble,
    boolean antiSpam,
    boolean chatHistoryEnabled,
    int historyRetentionDays,
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
    List<String> blockedPlayers,
    List<String> quickChatPhrases,
    boolean mentionBannerEnabled,
    boolean systemBannerEnabled,
    int mentionBannerDuration,
    boolean mentionSoundEnabled,
    boolean mentionRequireAt,
    boolean mentionWhisperBanner,
    boolean blurEnabled,
    int panelOpacity,
    int soundVolume,
    boolean ownMentionNotify,
    boolean ownQuoteNotify,
    boolean ownWhisperNotify,
    int bannerCornerRadius,
    @SerializedName("banner_offset_x") int bannerOffsetX,
    @SerializedName("banner_offset_y") int bannerOffsetY,
    String panelAnimStyle,
    String bannerAnimStyle,
    String popupAnimStyle,
    String messageAnimStyle,
    Boolean imageRenderEnabled,
    Boolean receiveImages,
    // Image upload host (2.3.11). null/blank = Litterbox default; response:
    // "text" (body is the URL) or "json:<field>".
    String uploadUrl,
    String uploadField,
    String uploadExtra,
    String uploadResponse,
    Integer messageGap,
    Integer avatarSize,
    Boolean hideRepeatedAvatars,
    // 2.4.0 sync: banner opacity 0-100 (default 100; only shadow+background fade)
    Integer bannerOpacity,
    // 2.4.0 sync: close the chat screen right after sending (default off = chain sends)
    boolean closeChatOnSend,
    // 2.4.0 sync: bubble text height in px 5-14 (default 9 = scale 1.0)
    Integer bubbleSize,
    // 2.4.4 sync: fill the whole screen width with the chat panel (ignores panel_width)
    Boolean panelFullscreen
) {
    public static ChatBubbleConfig defaults() {
        return new ChatBubbleConfig(
            true, "dark", true, false, true,
            false, true,
            false, 0, 5, 1000, 4,
            "#1E90FF", "#4A4A4A", "#FFFFFF", "#FFFFFF",
            false, false, true, false, true, false,
            List.of(), List.of(), List.of(),
            true, true, 4, true, true, true,
            false, 80, 80, false, false, false, 4, 0, 0,
            "slide", "slide", "fade", "fade",
            true, true,
            null, null, null, null,
            6, 20, false,
            100, false, 9,
            false
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
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, blockedPlayers, quickChatPhrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse,
            messageGap, avatarSize, hideRepeatedAvatars,
            bannerOpacity, closeChatOnSend, bubbleSize, panelFullscreen);
    }

    public ChatBubbleConfig withQuickChatPhrases(List<String> phrases) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, blockedPlayers, phrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse,
            messageGap, avatarSize, hideRepeatedAvatars,
            bannerOpacity, closeChatOnSend, bubbleSize, panelFullscreen);
    }

    public ChatBubbleConfig withSidebarHidePatterns(List<String> patterns) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, patterns, blockedPlayers, quickChatPhrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse,
            messageGap, avatarSize, hideRepeatedAvatars,
            bannerOpacity, closeChatOnSend, bubbleSize, panelFullscreen);
    }

    public ChatBubbleConfig withBlockedPlayers(List<String> blocked) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, blocked, quickChatPhrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse,
            messageGap, avatarSize, hideRepeatedAvatars,
            bannerOpacity, closeChatOnSend, bubbleSize, panelFullscreen);
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
