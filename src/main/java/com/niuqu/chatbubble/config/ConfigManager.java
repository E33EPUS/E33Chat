package com.niuqu.chatbubble.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import org.slf4j.LoggerFactory;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ConfigManager() {}

    public static ChatBubbleConfig load(Path path) {
        if (Files.exists(path)) {
            try (Reader r = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                ChatBubbleConfig loaded = GSON.fromJson(r, ChatBubbleConfig.class);
                if (loaded != null) {
                    var merged = mergeWithDefaults(loaded);
                    LoggerFactory.getLogger("e33chat").info("[e33chat] Loaded config | soundPublic=" + merged.soundPublic() + " | soundSystem=" + merged.soundSystem());
                    return merged;
                }
            } catch (Exception e) {
                LoggerFactory.getLogger("e33chat").warn("[e33chat] Failed to load config, using defaults", e);
            }
        }
        ChatBubbleConfig def = ChatBubbleConfig.defaults();
        save(path, def);
        LoggerFactory.getLogger("e33chat").info("[e33chat] Created default config | soundPublic=" + def.soundPublic() + " | soundSystem=" + def.soundSystem());
        return def;
    }

    private static ChatBubbleConfig mergeWithDefaults(ChatBubbleConfig c) {
        ChatBubbleConfig d = ChatBubbleConfig.defaults();
        return new ChatBubbleConfig(
            c.enabled(), c.theme() != null ? c.theme() : d.theme(),
            c.redDotEnabled(), c.hideChatIcon(), c.animationEnabled(),
            c.strongHintEnabled(), c.systemChatAsBubble(),
            c.antiSpam(), c.chatHistoryEnabled(),
            c.previewEnabled(), c.previewLines(), c.previewWidth(), c.timeSeparatorMinutes(),
            c.panelWidth(), c.bubbleCornerRadius(),
            c.ownBubbleColor() != null ? c.ownBubbleColor() : d.ownBubbleColor(),
            c.otherBubbleColor() != null ? c.otherBubbleColor() : d.otherBubbleColor(),
            c.ownTextColor() != null ? c.ownTextColor() : d.ownTextColor(),
            c.otherTextColor() != null ? c.otherTextColor() : d.otherTextColor(),
            c.soundPublic(), c.soundSystem(), c.soundWhisper(),
            c.debugLog(), c.preserveInput(), c.colorCodes(),
            c.sidebarHidePatterns() != null ? c.sidebarHidePatterns() : d.sidebarHidePatterns(),
            c.quickChatPhrases() != null ? c.quickChatPhrases() : d.quickChatPhrases(),
            c.mentionBannerEnabled(),
            c.mentionBannerDuration() > 0 ? c.mentionBannerDuration() : d.mentionBannerDuration(),
            c.mentionSoundEnabled(),
            c.mentionRequireAt(),
            c.mentionWhisperBanner(),
            c.blurEnabled(), c.panelOpacity(), c.soundVolume(),
            c.ownMentionNotify(), c.ownQuoteNotify(), c.bannerCornerRadius());
    }

    public static void save(Path path, ChatBubbleConfig config) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
        } catch (Exception e) {
            LoggerFactory.getLogger("e33chat").warn("[e33chat] Failed to save config", e);
        }
    }
}
