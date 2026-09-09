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
    public static final ModConfigSpec.BooleanValue GROUPS_ENABLED;
    public static final ModConfigSpec.IntValue GROUP_MAX_COUNT;
    public static final ModConfigSpec.IntValue GROUP_MAX_MEMBERS;
    public static final ModConfigSpec.BooleanValue GROUP_CREATE_OP_ONLY;

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
        GROUPS_ENABLED = builder
            .comment("Enable in-mod chat groups (client tab strip + member-only routing).",
                "Groups are stored in serverconfig/e33chat-groups.json")
            .define("groups_enabled", true);
        GROUP_MAX_COUNT = builder
            .comment("Maximum number of groups that can exist on the server")
            .defineInRange("group_max_count", 20, 1, 500);
        GROUP_MAX_MEMBERS = builder
            .comment("Maximum members per group")
            .defineInRange("group_max_members", 50, 2, 1000);
        GROUP_CREATE_OP_ONLY = builder
            .comment("When true, only operators (permission level 2+) can create groups")
            .define("group_create_op_only", false);
        SERVER_CONFIG = builder.build();
    }
}
