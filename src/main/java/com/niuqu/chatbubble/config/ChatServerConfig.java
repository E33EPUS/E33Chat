package com.niuqu.chatbubble.config;

import net.neoforged.neoforge.common.ModConfigSpec;

import java.util.List;

public class ChatServerConfig {
    public static final ModConfigSpec SERVER_CONFIG;
    public static final ModConfigSpec.BooleanValue HISTORY_ENABLED;
    public static final ModConfigSpec.BooleanValue USE_TPA;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> CHAT_TEMPLATES;
    public static final ModConfigSpec.ConfigValue<List<? extends String>> WHISPER_TEMPLATES;
    public static final ModConfigSpec.BooleanValue TEMPLATE_DEBUG;
    public static final ModConfigSpec.BooleanValue MEDIA_ENABLED;
    public static final ModConfigSpec.BooleanValue MEDIA_AUTO_CLEAN;
    public static final ModConfigSpec.BooleanValue EASY_BOT_COMPAT;

    static {
        ModConfigSpec.Builder builder = new ModConfigSpec.Builder();
        builder.comment("E33Chat server settings");
        HISTORY_ENABLED = builder
            .comment("Send recent chat history to players when they join")
            .define("history_enabled", false);
        USE_TPA = builder
            .comment("Make the head-menu teleport with /tpa (request) instead of /tp")
            .define("use_tpa", false);
        CHAT_TEMPLATES = builder
            .comment("Message-format templates for public chat; empty list = disabled (heuristic guards only).",
                "Placeholders: {prefix} {display_name} {name} {sep} {content}. Example: \"[{display_name}]: {content}\"",
                "Exactly one {content} (any position); one name placeholder; first match wins.")
            .defineList("chat_templates", List.of(), obj -> obj instanceof String);
        WHISPER_TEMPLATES = builder
            .comment("Message-format templates for private chat (whisper); empty list = disabled.",
                "Placeholders: {sender} {target} {prefix} {display_name} {sep} {content}. Example: \"{sender} → {target}: {content}\"")
            .defineList("whisper_templates", List.of(), obj -> obj instanceof String);
        TEMPLATE_DEBUG = builder
            .comment("Log failed template matches and parse diagnostics to the client chat log")
            .define("template_debug", false);
        MEDIA_ENABLED = builder
            .comment("Host chat image uploads on the server (e33chat://media/<id>, permanent) instead of the third-party host",
                "When false, clients fall back to the configured third-party host")
            .define("media_enabled", true);
        MEDIA_AUTO_CLEAN = builder
            .comment("Auto-delete server-hosted media files older than 7 days (checked on server start, then at most every 6h after uploads)",
                "When false, uploaded images are kept forever")
            .define("media_auto_clean", true);
        EASY_BOT_COMPAT = builder
            .comment("Parse EasyBot QQ group messages relayed to the game as player messages.",
                "Recognizes the default EasyBot format like \"[群名] <昵称(QQ号)> 内容\".",
                "Enabled by default; set to false to keep EasyBot messages in the system-message channel.",
                "Also enables receiving EasyBot/ChatImage CICode images in bubbles.")
            .define("easybot_compat", true);
        SERVER_CONFIG = builder.build();
    }
}
