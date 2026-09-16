package com.niuqu.chatbubble.config;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.regex.Pattern;

public record ChatBubbleConfig(
    boolean enabled,
    String theme,
    boolean redDotEnabled,
    boolean hideChatIcon,
    @SerializedName("hud_icon_x") Integer hudIconX,
    @SerializedName("hud_icon_y") Integer hudIconY,
    boolean animationEnabled,
    boolean systemChatAsBubble,
    boolean antiSpam,
    boolean chatHistoryEnabled,
    int historyRetentionDays,
    int timeSeparatorMinutes,
    int panelWidth,
    boolean panelFullscreen,
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
    Integer mentionBannerDuration,
    boolean mentionSoundEnabled,
    boolean mentionRequireAt,
    boolean mentionWhisperBanner,
    boolean blurEnabled,
    Integer panelOpacity,
    Integer soundVolume,
    boolean ownMentionNotify,
    boolean ownQuoteNotify,
    boolean ownWhisperNotify,
    Integer bannerCornerRadius,
    @SerializedName("banner_offset_x") int bannerOffsetX,
    @SerializedName("banner_offset_y") int bannerOffsetY,
    @SerializedName("banner_max_stack") Integer bannerMaxStack,
    String panelAnimStyle,
    String bannerAnimStyle,
    String popupAnimStyle,
    String messageAnimStyle,
    Boolean imageRenderEnabled,
    Boolean receiveImages,
    // Image upload host (2.3.11). null/blank = uguu.se default (~3h expiry);
    // response: "text" (body is the URL) or "json:<field>" (default json:files[0].url).
    String uploadUrl,
    String uploadField,
    String uploadExtra,
    String uploadResponse,
    Integer messageGap,
    Integer avatarSize,
    Boolean hideRepeatedAvatars,
    boolean closeChatOnSend,
    Integer bannerOpacity,
    Integer bubbleSize,
    // 2.4.10 custom panel background (client-only). null/blank = default texture.
    @SerializedName("panel_bg_image") String panelBgImage,
    @SerializedName("panel_bg_opacity") Integer panelBgOpacity,
    @SerializedName("panel_bg_crop") String panelBgCrop
) {
    public static ChatBubbleConfig defaults() {
        return new ChatBubbleConfig(
            true, "dark", true, false, 3, 20, true,
            false, true,
            false, 0, 5, 1000, false, 4,
            "#1E90FF", "#4A4A4A", "#FFFFFF", "#FFFFFF",
            false, false, true, false, true, false,
            List.of(), List.of(), List.of(),
            true, true, 4, true, true, true,
            false, 80, 80, false, false, false, 4, 0, 0, 3,
            "slide", "slide", "fade", "fade",
            true, true,
            null, null, null, null,
            6, 20, false, false, 100, 9,
            "", 100, null
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
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, hudIconX, hudIconY, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, panelFullscreen, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, blockedPlayers, quickChatPhrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY, bannerMaxStack,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse, messageGap, avatarSize, hideRepeatedAvatars, closeChatOnSend, bannerOpacity, bubbleSize,
            panelBgImage, panelBgOpacity, panelBgCrop);
    }

    public ChatBubbleConfig withQuickChatPhrases(List<String> phrases) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, hudIconX, hudIconY, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, panelFullscreen, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, blockedPlayers, phrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY, bannerMaxStack,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse, messageGap, avatarSize, hideRepeatedAvatars, closeChatOnSend, bannerOpacity, bubbleSize, panelBgImage, panelBgOpacity, panelBgCrop);
    }

    /** Framing of the custom panel background; blank clears it back to centered. */
    public ChatBubbleConfig withPanelBgCrop(String crop) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, hudIconX, hudIconY, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, panelFullscreen, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, blockedPlayers, quickChatPhrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY, bannerMaxStack,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse, messageGap, avatarSize, hideRepeatedAvatars, closeChatOnSend, bannerOpacity, bubbleSize, panelBgImage, panelBgOpacity, crop);
    }

    public ChatBubbleConfig withSidebarHidePatterns(List<String> patterns) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, hudIconX, hudIconY, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, panelFullscreen, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, patterns, blockedPlayers, quickChatPhrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY, bannerMaxStack,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse, messageGap, avatarSize, hideRepeatedAvatars, closeChatOnSend, bannerOpacity, bubbleSize, panelBgImage, panelBgOpacity, panelBgCrop);
    }

    public ChatBubbleConfig withBlockedPlayers(List<String> blocked) {
        return new ChatBubbleConfig(enabled, theme, redDotEnabled, hideChatIcon, hudIconX, hudIconY, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, panelFullscreen, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes, sidebarHidePatterns, blocked, quickChatPhrases,
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius, bannerOffsetX, bannerOffsetY, bannerMaxStack,
            panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle, imageRenderEnabled, receiveImages,
            uploadUrl, uploadField, uploadExtra, uploadResponse, messageGap, avatarSize, hideRepeatedAvatars, closeChatOnSend, bannerOpacity, bubbleSize, panelBgImage, panelBgOpacity, panelBgCrop);
    }

    public boolean isSidebarHidden(String playerName) {
        if (playerName == null || playerName.isEmpty()) return false;
        if (sidebarHidePatterns == null || sidebarHidePatterns.isEmpty()) return false;
        for (String pattern : sidebarHidePatterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            if (com.niuqu.chatbubble.chat.WildcardPatterns.matches(playerName, pattern)) return true;
        }
        return false;
    }
}
