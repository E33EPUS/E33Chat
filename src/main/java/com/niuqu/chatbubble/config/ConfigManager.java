package com.niuqu.chatbubble.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
//#if MC >= 11800
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
//#else
//$$ import org.apache.logging.log4j.LogManager;
//$$ import org.apache.logging.log4j.Logger;
//#endif

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    //#if MC >= 11800
    private static final Logger LOGGER = LoggerFactory.getLogger("e33chat");
    //#else
    //$$ private static final Logger LOGGER = LogManager.getLogger("e33chat");
    //#endif
    private ConfigManager() {}

    public static ChatBubbleConfig load(Path path) {
        if (Files.exists(path)) {
            try (Reader r = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                ChatBubbleConfig loaded = GSON.fromJson(r, ChatBubbleConfig.class);
                if (loaded != null) {
                    var merged = mergeWithDefaults(loaded);
                    LOGGER.info("[e33chat] Loaded config | soundPublic=" + merged.soundPublic() + " | soundSystem=" + merged.soundSystem());
                    return merged;
                }
            } catch (Exception e) {
                LOGGER.warn("[e33chat] Failed to load config, using defaults", e);
                try {
                    Path bak = path.resolveSibling(path.getFileName() + ".bak");
                    Files.move(path, bak, StandardCopyOption.REPLACE_EXISTING);
                    LOGGER.warn("[e33chat] Corrupt config preserved as {}", bak);
                } catch (Exception ignore) {}
            }
        }
        ChatBubbleConfig def = ChatBubbleConfig.defaults();
        save(path, def);
        LOGGER.info("[e33chat] Created default config | soundPublic=" + def.soundPublic() + " | soundSystem=" + def.soundSystem());
        return def;
    }

    private static ChatBubbleConfig mergeWithDefaults(ChatBubbleConfig c) {
        ChatBubbleConfig d = ChatBubbleConfig.defaults();
        return new ChatBubbleConfig(
            c.enabled(), c.theme() != null ? c.theme() : d.theme(),
            c.redDotEnabled(), c.hideChatIcon(), c.animationEnabled(),
            c.systemChatAsBubble(),
            c.antiSpam(), c.chatHistoryEnabled(),
            c.historyRetentionDays(), c.timeSeparatorMinutes(),
            c.panelWidth(), c.bubbleCornerRadius(),
            c.ownBubbleColor() != null ? c.ownBubbleColor() : d.ownBubbleColor(),
            c.otherBubbleColor() != null ? c.otherBubbleColor() : d.otherBubbleColor(),
            c.ownTextColor() != null ? c.ownTextColor() : d.ownTextColor(),
            c.otherTextColor() != null ? c.otherTextColor() : d.otherTextColor(),
            c.soundPublic(), c.soundSystem(), c.soundWhisper(),
            c.debugLog(), c.preserveInput(), c.colorCodes(),
            c.sidebarHidePatterns() != null ? c.sidebarHidePatterns() : d.sidebarHidePatterns(),
            c.blockedPlayers() != null ? c.blockedPlayers() : d.blockedPlayers(),
            c.quickChatPhrases() != null ? c.quickChatPhrases() : d.quickChatPhrases(),
            c.mentionBannerEnabled(),
            c.systemBannerEnabled(),
            c.mentionBannerDuration() > 0 ? c.mentionBannerDuration() : d.mentionBannerDuration(),
            c.mentionSoundEnabled(),
            c.mentionRequireAt(),
            c.mentionWhisperBanner(),
            c.blurEnabled(),
            c.panelOpacity() > 0 ? c.panelOpacity() : d.panelOpacity(),
            c.soundVolume() > 0 ? c.soundVolume() : d.soundVolume(),
            c.ownMentionNotify(), c.ownQuoteNotify(), c.ownWhisperNotify(),
            c.bannerCornerRadius() > 0 ? c.bannerCornerRadius() : d.bannerCornerRadius(),
            c.bannerOffsetX(), c.bannerOffsetY(),
            c.panelAnimStyle() != null ? c.panelAnimStyle() : d.panelAnimStyle(),
            c.bannerAnimStyle() != null ? c.bannerAnimStyle() : d.bannerAnimStyle(),
            c.popupAnimStyle() != null ? c.popupAnimStyle() : d.popupAnimStyle(),
            c.messageAnimStyle() != null ? c.messageAnimStyle() : d.messageAnimStyle(),
            c.imageRenderEnabled() != null ? c.imageRenderEnabled() : d.imageRenderEnabled(),
            // receiveImages is the user-facing switch; legacy imageRenderEnabled
            // (2.3.10 early builds) migrates into it when the new key is absent.
            c.receiveImages() != null ? c.receiveImages()
                : (c.imageRenderEnabled() != null ? c.imageRenderEnabled() : d.receiveImages()),
            c.uploadUrl() != null ? c.uploadUrl() : d.uploadUrl(),
            c.uploadField() != null ? c.uploadField() : d.uploadField(),
            c.uploadExtra() != null ? c.uploadExtra() : d.uploadExtra(),
            c.uploadResponse() != null ? c.uploadResponse() : d.uploadResponse(),
            c.messageGap() != null ? c.messageGap() : d.messageGap(),
            c.avatarSize() != null ? c.avatarSize() : d.avatarSize(),
            c.hideRepeatedAvatars() != null ? c.hideRepeatedAvatars() : d.hideRepeatedAvatars(),
            c.closeChatOnSend(),
            c.bannerOpacity() != null ? c.bannerOpacity() : d.bannerOpacity(),
            c.bubbleScale() != null ? c.bubbleScale() : d.bubbleScale());
    }

    public static void save(Path path, ChatBubbleConfig config) {
        try {
            Files.createDirectories(path.getParent());
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(tmp), StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
            try {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (java.nio.file.AtomicMoveNotSupportedException e) {
                Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (Exception e) {
            LOGGER.warn("[e33chat] Failed to save config", e);
        }
    }
}
