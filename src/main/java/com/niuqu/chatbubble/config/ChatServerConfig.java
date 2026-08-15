package com.niuqu.chatbubble.config;

import net.minecraftforge.common.ForgeConfigSpec;

import java.util.List;

public class ChatServerConfig {
    public static final ForgeConfigSpec SERVER_CONFIG;
    public static final ForgeConfigSpec.BooleanValue HISTORY_ENABLED;
    public static final ForgeConfigSpec.BooleanValue USE_TPA;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> CHAT_TEMPLATES;
    public static final ForgeConfigSpec.ConfigValue<List<? extends String>> WHISPER_TEMPLATES;
    public static final ForgeConfigSpec.BooleanValue TEMPLATE_DEBUG;
    public static final ForgeConfigSpec.BooleanValue MEDIA_ENABLED;

    static {
        ForgeConfigSpec.Builder builder = new ForgeConfigSpec.Builder();
        builder.comment("E33Chat server settings");
        HISTORY_ENABLED = builder
            .comment("Send recent chat history to players when they join")
            .define("history_enabled", false);
        USE_TPA = builder
            .comment("Make the head-menu teleport with /tpa (request) instead of /tp")
            .define("use_tpa", false);
        CHAT_TEMPLATES = builder
            .comment("Message-format templates for public chat; empty list = disabled (heuristic guards only).",
                "Placeholders: {prefix} {display_name} {name} {content}. Example: \"[{display_name}]: {content}\"",
                "A template must end with {content} and contain one name placeholder; first match wins.")
            .defineList("chat_templates", List.of(), obj -> obj instanceof String);
        WHISPER_TEMPLATES = builder
            .comment("Message-format templates for private chat (whisper); empty list = disabled.",
                "Placeholders: {sender} {target} {prefix} {display_name} {content}. Example: \"{sender} → {target}: {content}\"")
            .defineList("whisper_templates", List.of(), obj -> obj instanceof String);
        TEMPLATE_DEBUG = builder
            .comment("Log failed template matches and parse diagnostics to the client chat log")
            .define("template_debug", false);
        MEDIA_ENABLED = builder
            .comment("Host chat image uploads on the server (e33chat://media/<id>, permanent) instead of the third-party host",
                "When false, clients fall back to the configured third-party host")
            .define("media_enabled", true);
        SERVER_CONFIG = builder.build();
    }
}
