package com.niuqu.chatbubble;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import net.minecraftforge.common.ForgeConfigSpec;

public class ChatBubbleConfig {
    public static final ForgeConfigSpec CLIENT_CONFIG;

    public static final ForgeConfigSpec.EnumValue<ChatBubbleTheme> THEME;
    public static final ForgeConfigSpec.BooleanValue ENABLED;
    public static final ForgeConfigSpec.BooleanValue RED_DOT_ENABLED;
    public static final ForgeConfigSpec.BooleanValue HIDE_CHAT_ICON;
    public static final ForgeConfigSpec.BooleanValue ANIMATION_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SYSTEM_CHAT_AS_BUBBLE;
    public static final ForgeConfigSpec.BooleanValue ANTI_SPAM;
    public static final ForgeConfigSpec.BooleanValue CHAT_HISTORY_ENABLED;
    public static final ForgeConfigSpec.IntValue HISTORY_RETENTION_DAYS;
    public static final ForgeConfigSpec.IntValue TIME_SEPARATOR_MINUTES;
    public static final ForgeConfigSpec.ConfigValue<String> OWN_BUBBLE_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> OTHER_BUBBLE_COLOR;
    public static final ForgeConfigSpec.IntValue BUBBLE_CORNER_RADIUS;
    public static final ForgeConfigSpec.ConfigValue<String> OWN_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<String> OTHER_TEXT_COLOR;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> QUICK_CHAT_PHRASES;
    public static final ForgeConfigSpec.IntValue PANEL_WIDTH;
    public static final ForgeConfigSpec.BooleanValue BLUR_ENABLED;
    public static final ForgeConfigSpec.IntValue PANEL_OPACITY;
    public static final ForgeConfigSpec.BooleanValue DEBUG_LOG;
    public static final ForgeConfigSpec.BooleanValue SOUND_SYSTEM;
    public static final ForgeConfigSpec.BooleanValue SOUND_WHISPER;
    public static final ForgeConfigSpec.BooleanValue SOUND_PUBLIC;
    public static final ForgeConfigSpec.IntValue SOUND_VOLUME;
    public static final ForgeConfigSpec.BooleanValue PRESERVE_INPUT;
    public static final ForgeConfigSpec.BooleanValue COLOR_CODES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> SIDEBAR_HIDE_PATTERNS;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> BLOCKED_PLAYERS;

    // mention
    public static final ForgeConfigSpec.BooleanValue MENTION_BANNER_ENABLED;
    public static final ForgeConfigSpec.BooleanValue SYSTEM_BANNER_ENABLED;
    public static final ForgeConfigSpec.IntValue MENTION_BANNER_DURATION;
    public static final ForgeConfigSpec.BooleanValue MENTION_SOUND_ENABLED;
    public static final ForgeConfigSpec.BooleanValue MENTION_REQUIRE_AT;
    public static final ForgeConfigSpec.BooleanValue MENTION_WHISPER_BANNER;
    public static final ForgeConfigSpec.BooleanValue OWN_MENTION_NOTIFY;
    public static final ForgeConfigSpec.BooleanValue OWN_QUOTE_NOTIFY;
    public static final ForgeConfigSpec.BooleanValue OWN_WHISPER_NOTIFY;
    public static final ForgeConfigSpec.IntValue BANNER_CORNER_RADIUS;
    public static final ForgeConfigSpec.IntValue BANNER_OFFSET_X;
    public static final ForgeConfigSpec.IntValue BANNER_OFFSET_Y;
    public static final ForgeConfigSpec.EnumValue<AnimationStyle> PANEL_ANIM_STYLE;
    public static final ForgeConfigSpec.EnumValue<AnimationStyle> BANNER_ANIM_STYLE;
    public static final ForgeConfigSpec.EnumValue<AnimationStyle> POPUP_ANIM_STYLE;
    public static final ForgeConfigSpec.EnumValue<AnimationStyle> MESSAGE_ANIM_STYLE;
    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();

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

        SYSTEM_CHAT_AS_BUBBLE = builder
            .comment("Render system messages as chat bubbles")
            .translation("e33chat.config.system_chat_as_bubble")
            .define("system_chat_as_bubble", false);

        ANTI_SPAM = builder
            .comment("Collapse consecutive identical messages into one bubble with a counter")
            .translation("e33chat.config.anti_spam")
            .define("anti_spam", true);

        CHAT_HISTORY_ENABLED = builder
            .comment("Keep per-world chat history (restored when you rejoin)")
            .translation("e33chat.config.chat_history")
            .define("chat_history", false);

        HISTORY_RETENTION_DAYS = builder
            .comment("Delete history files older than this many days on world join (0 = keep forever)")
            .translation("e33chat.config.history_retention")
            .defineInRange("history_retention_days", 0, 0, 365);

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
            .defineListAllowEmpty("sidebar_hide_patterns", ArrayList::new, o -> o instanceof String);

        BLOCKED_PLAYERS = builder
            .comment("Blocked players: their messages vanish entirely — vanilla chat, bubbles, banners and sounds (comma-separated exact names)")
            .translation("e33chat.config.blocked_players")
            .defineListAllowEmpty("blocked_players", ArrayList::new, o -> o instanceof String);

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
            .defineListAllowEmpty("phrases", ArrayList::new, o -> o instanceof String);

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

        SYSTEM_BANNER_ENABLED = builder
            .comment("Pop a banner for system messages (deaths/joins/broadcasts).")
            .translation("e33chat.config.system_banner_enabled")
            .define("system_banner_enabled", true);

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

        OWN_WHISPER_NOTIFY = builder
            .comment("Notify (sound + banner) when you whisper yourself (manual /msg) — testing aid")
            .translation("e33chat.config.own_whisper_notify")
            .define("own_whisper_notify", false);

        BANNER_CORNER_RADIUS = builder
            .comment("Banner corner radius (0 = square, max 10)")
            .translation("e33chat.config.banner_corner_radius")
            .defineInRange("banner_corner_radius", 6, 0, 10);

        BANNER_OFFSET_X = builder
            .comment("Banner horizontal offset in px (negative = left). Nudge to avoid HUD overlaps (e.g. Jade).")
            .translation("e33chat.config.banner_offset_x")
            .defineInRange("banner_offset_x", 0, -1000, 1000);

        BANNER_OFFSET_Y = builder
            .comment("Banner vertical offset in px (negative = up). Nudge to avoid HUD overlaps (e.g. Jade).")
            .translation("e33chat.config.banner_offset_y")
            .defineInRange("banner_offset_y", 0, -1000, 1000);

        PANEL_ANIM_STYLE = builder
            .comment("Chat panel/sidebar open-close animation style: SLIDE, FADE, ZOOM or NONE")
            .translation("e33chat.config.panel_anim_style")
            .defineEnum("panel_anim_style", AnimationStyle.SLIDE);

        BANNER_ANIM_STYLE = builder
            .comment("Notification banner appear/leave animation style: SLIDE, FADE, ZOOM or NONE")
            .translation("e33chat.config.banner_anim_style")
            .defineEnum("banner_anim_style", AnimationStyle.SLIDE);

        POPUP_ANIM_STYLE = builder
            .comment("Popup panel open animation style (settings/emoji/quick-chat/search): SLIDE, FADE, ZOOM or NONE")
            .translation("e33chat.config.popup_anim_style")
            .defineEnum("popup_anim_style", AnimationStyle.FADE);

        MESSAGE_ANIM_STYLE = builder
            .comment("New message bubble enter animation style (slide up + fade, staggered): SLIDE, FADE, ZOOM or NONE")
            .translation("e33chat.config.message_anim_style")
            .defineEnum("message_anim_style", AnimationStyle.FADE);

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
