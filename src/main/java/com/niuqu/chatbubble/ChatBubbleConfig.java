package com.niuqu.chatbubble;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ChatBubbleConfig {
    public static final ModConfigSpec CLIENT_CONFIG;

    public static final ModConfigSpec.EnumValue<ChatBubbleTheme> THEME;
    public static final ModConfigSpec.BooleanValue ENABLED;
    public static final ModConfigSpec.BooleanValue RED_DOT_ENABLED;
    public static final ModConfigSpec.BooleanValue HIDE_CHAT_ICON;
    public static final ModConfigSpec.BooleanValue ANIMATION_ENABLED;
    public static final ModConfigSpec.BooleanValue STRONG_HINT_ENABLED;
    public static final ModConfigSpec.BooleanValue SYSTEM_CHAT_AS_BUBBLE;
    public static final ModConfigSpec.BooleanValue ANTI_SPAM;
    public static final ModConfigSpec.BooleanValue CHAT_HISTORY_ENABLED;
    public static final ModConfigSpec.BooleanValue PREVIEW_ENABLED;
    public static final ModConfigSpec.IntValue PREVIEW_LINES;
    public static final ModConfigSpec.IntValue PREVIEW_WIDTH;
    public static final ModConfigSpec.IntValue TIME_SEPARATOR_MINUTES;
    public static final ModConfigSpec.ConfigValue<String> OWN_BUBBLE_COLOR;
    public static final ModConfigSpec.ConfigValue<String> OTHER_BUBBLE_COLOR;
    public static final ModConfigSpec.IntValue BUBBLE_CORNER_RADIUS;
    public static final ModConfigSpec.ConfigValue<String> OWN_TEXT_COLOR;
    public static final ModConfigSpec.ConfigValue<String> OTHER_TEXT_COLOR;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> QUICK_CHAT_PHRASES;
    public static final ModConfigSpec.IntValue PANEL_WIDTH;
    public static final ModConfigSpec.BooleanValue BLUR_ENABLED;
    public static final ModConfigSpec.IntValue PANEL_OPACITY;
    public static final ModConfigSpec.BooleanValue DEBUG_LOG;
    public static final ModConfigSpec.BooleanValue SOUND_SYSTEM;
    public static final ModConfigSpec.BooleanValue SOUND_WHISPER;
    public static final ModConfigSpec.BooleanValue SOUND_PUBLIC;
    public static final ModConfigSpec.IntValue SOUND_VOLUME;
    public static final ModConfigSpec.BooleanValue PRESERVE_INPUT;
    public static final ModConfigSpec.BooleanValue COLOR_CODES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> SIDEBAR_HIDE_PATTERNS;

    // mention
    public static final ModConfigSpec.BooleanValue MENTION_BANNER_ENABLED;
    public static final ModConfigSpec.IntValue MENTION_BANNER_DURATION;
    public static final ModConfigSpec.BooleanValue MENTION_SOUND_ENABLED;
    public static final ModConfigSpec.BooleanValue MENTION_REQUIRE_AT;
    public static final ModConfigSpec.BooleanValue MENTION_WHISPER_BANNER;
    public static final ModConfigSpec.BooleanValue OWN_MENTION_NOTIFY;
    public static final ModConfigSpec.BooleanValue OWN_QUOTE_NOTIFY;
    public static final ModConfigSpec.IntValue BANNER_CORNER_RADIUS;
    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();

        builder.comment("E33Chat client settings");
        builder.push("general");

        THEME = builder
            .comment("Color theme: DARK (default) or LIGHT")
            .translation("e33chat.config.theme")
            .defineEnum("theme", ChatBubbleTheme.DARK);

        ENABLED = builder
            .comment("Enable the custom chat overlay (disable to restore vanilla chat)")
            .translation("e33chat.config.enabled")
            .define("enabled", true);

        RED_DOT_ENABLED = builder
            .comment("Show an unread red dot on the HUD chat icon")
            .translation("e33chat.config.red_dot")
            .define("red_dot", true);

        HIDE_CHAT_ICON = builder
            .comment("Hide the HUD chat icon (including the red dot)")
            .translation("e33chat.config.hide_chat_icon")
            .define("hide_chat_icon", false);

        ANIMATION_ENABLED = builder
            .comment("Chat screen open/close animation")
            .translation("e33chat.config.animation")
            .define("animation", true);

        DEBUG_LOG = builder
            .comment("Verbose message-pipeline logging for troubleshooting (writes chat text to latest.log)")
            .translation("e33chat.config.debug_log")
            .define("debug_log", false);

        PANEL_WIDTH = builder
            .comment("Chat panel width in physical screen pixels (800-1600, independent of GUI scale and aspect ratio)")
            .translation("e33chat.config.panel_width")
            .defineInRange("panel_width", 1000, 800, 1600);

        BLUR_ENABLED = builder
            .comment("Enable gaussian blur effect behind the chat panel background")
            .translation("e33chat.config.blur_enabled")
            .define("blur_enabled", true);

        PANEL_OPACITY = builder
            .comment("Chat panel background opacity percentage (0-100). 0 = fully transparent, 100 = fully opaque")
            .translation("e33chat.config.panel_opacity")
            .defineInRange("panel_opacity", 80, 0, 100);

        STRONG_HINT_ENABLED = builder
            .comment("Show system messages as a strong hint above the hotbar (otherwise they go to the message preview)")
            .translation("e33chat.config.strong_hint")
            .define("strong_hint", true);

        SYSTEM_CHAT_AS_BUBBLE = builder
            .comment("Render system messages as chat bubbles")
            .translation("e33chat.config.system_chat_as_bubble")
            .define("system_chat_as_bubble", false);

        ANTI_SPAM = builder
            .comment("Collapse consecutive identical messages into one bubble with a counter")
            .translation("e33chat.config.anti_spam")
            .define("anti_spam", false);

        CHAT_HISTORY_ENABLED = builder
            .comment("Keep per-world chat history (restored when you rejoin)")
            .translation("e33chat.config.chat_history")
            .define("chat_history", false);

        PREVIEW_ENABLED = builder
            .comment("Show a recent-message preview above the HUD icon")
            .translation("e33chat.config.preview_enabled")
            .define("preview_enabled", true);

        PREVIEW_LINES = builder
            .comment("Preview line count (3-10)")
            .translation("e33chat.config.preview_lines")
            .defineInRange("preview_lines", 3, 3, 10);

        PREVIEW_WIDTH = builder
            .comment("Preview width in pixels (50-400)")
            .translation("e33chat.config.preview_width")
            .defineInRange("preview_width", 200, 50, 400);

        TIME_SEPARATOR_MINUTES = builder
            .comment("Minutes between time separators in the chat list (0 = off, 1-60)")
            .translation("e33chat.config.time_separator")
            .defineInRange("time_separator_minutes", 5, 0, 60);

        PRESERVE_INPUT = builder
            .comment("Keep typed text in the input box when the chat closes, restoring it on reopen")
            .translation("e33chat.config.preserve_input")
            .define("preserve_input", true);

        COLOR_CODES = builder
            .comment("Interpret & color/format codes as color in YOUR OWN outgoing bubble (local only). The raw & is sent unchanged (never §), so it never kicks; color plugins color it for everyone, plain servers show literal & to others. Off by default so normal text like 'B&B' isn't colored locally")
            .translation("e33chat.config.color_codes")
            .define("color_codes", false);

        SIDEBAR_HIDE_PATTERNS = builder
            .comment("Hide players matching these wildcard patterns from the sidebar whisper list (comma-separated, * = wildcard, e.g. Islot_*, *[NPC]*)")
            .translation("e33chat.config.sidebar_hide_patterns")
            .defineListAllowEmpty("sidebar_hide_patterns", ArrayList::new, () -> "", o -> o instanceof String);

        builder.pop();
        builder.push("bubble");

        OWN_BUBBLE_COLOR = builder
            .comment("Your bubble color (hex RRGGBB)")
            .translation("e33chat.config.own_bubble_color")
            .define("own_color", "#1E90FF");

        OTHER_BUBBLE_COLOR = builder
            .comment("Other players' bubble color (hex RRGGBB)")
            .translation("e33chat.config.other_bubble_color")
            .define("other_color", "#4A4A4A");

        BUBBLE_CORNER_RADIUS = builder
            .comment("Bubble corner radius (0 = square, max 10)")
            .translation("e33chat.config.bubble_corner_radius")
            .defineInRange("corner_radius", 4, 0, 10);

        builder.pop();
        builder.push("text");

        OWN_TEXT_COLOR = builder
            .comment("Your text color (hex RRGGBB)")
            .translation("e33chat.config.own_text_color")
            .define("own_color", "#FFFFFF");

        OTHER_TEXT_COLOR = builder
            .comment("Other players' text color (hex RRGGBB)")
            .translation("e33chat.config.other_text_color")
            .define("other_color", "#FFFFFF");

        builder.pop();
        builder.push("quick_chat");

        QUICK_CHAT_PHRASES = builder
            .comment("Quick chat phrase list")
            .translation("e33chat.config.quick_chat_phrases")
            .defineListAllowEmpty("phrases", new ArrayList<>(), () -> "", o -> o instanceof String);

        builder.pop();
        builder.push("sound");

        SOUND_VOLUME = builder
            .comment("Master volume for all notification sounds (0-100)")
            .translation("e33chat.config.sound_volume")
            .defineInRange("sound_volume", 80, 0, 100);

        SOUND_SYSTEM = builder
            .comment("Play a notification sound for system messages")
            .translation("e33chat.config.sound_system")
            .define("sound_system", false);

        SOUND_WHISPER = builder
            .comment("Play a notification sound for private / whisper messages")
            .translation("e33chat.config.sound_whisper")
            .define("sound_whisper", true);

        SOUND_PUBLIC = builder
            .comment("Play a notification sound for public chat messages")
            .translation("e33chat.config.sound_public")
            .define("sound_public", false);

        builder.pop();
        builder.push("mention");

        MENTION_BANNER_ENABLED = builder
            .comment("Show a notification banner when you are @mentioned (phone-style slide-in)")
            .translation("e33chat.config.mention_banner_enabled")
            .define("banner_enabled", true);

        MENTION_BANNER_DURATION = builder
            .comment("How long the notification banner stays visible (seconds, 2-10)")
            .translation("e33chat.config.mention_banner_duration")
            .defineInRange("banner_duration", 4, 2, 10);

        MENTION_SOUND_ENABLED = builder
            .comment("Play a notification sound when you are @mentioned or quoted")
            .translation("e33chat.config.mention_sound_enabled")
            .define("sound_enabled", true);

        MENTION_REQUIRE_AT = builder
            .comment("Only trigger @mention notifications when preceded by @ symbol (otherwise bare name also triggers)")
            .translation("e33chat.config.mention_require_at")
            .define("require_at", true);

        MENTION_WHISPER_BANNER = builder
            .comment("Show a notification banner for incoming private / whisper messages")
            .translation("e33chat.config.mention_whisper_banner")
            .define("whisper_banner", true);

        OWN_MENTION_NOTIFY = builder
            .comment("Notify (sound + banner) when you @ yourself — testing aid")
            .translation("e33chat.config.own_mention_notify")
            .define("own_mention_notify", false);

        OWN_QUOTE_NOTIFY = builder
            .comment("Notify (sound + banner) when you quote yourself — testing aid")
            .translation("e33chat.config.own_quote_notify")
            .define("own_quote_notify", false);

        BANNER_CORNER_RADIUS = builder
            .comment("Banner corner radius (0 = square, max 10)")
            .translation("e33chat.config.banner_corner_radius")
            .defineInRange("banner_corner_radius", 6, 0, 10);

        builder.pop();

        CLIENT_CONFIG = builder.build();
    }

    public static boolean isSidebarHidden(String name) {
        if (name == null || name.isEmpty()) return false;
        String lower = name.toLowerCase();
        for (String pattern : SIDEBAR_HIDE_PATTERNS.get()) {
            if (pattern == null || pattern.isBlank()) continue;
            String regex = Pattern.quote(pattern.trim())
                .replace("\\*", ".*");
            if (lower.matches(regex)) return true;
        }
        return false;
    }

    // 总音量比例 0.0-1.0，乘到各提示音的 volume 上
    public static float soundVolume() {
        return SOUND_VOLUME.get() / 100f;
    }

    public static int parseHexColor(String hex, int defaultColor) {
        try {
            String h = hex.replace("#", "").trim();
            if (h.length() != 6) return defaultColor;
            return 0xFF000000 | Integer.parseInt(h, 16);
        } catch (NumberFormatException e) {
            return defaultColor;
        }
    }
}
