package com.niuqu.chatbubble.command;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.chat.TemplateMatcher;
import com.niuqu.chatbubble.config.ServerConfig;
import com.niuqu.chatbubble.config.ServerConfigManager;
import com.niuqu.chatbubble.network.ServerConfigScreenPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.fabricmc.fabric.api.command.v2.CommandRegistrationCallback;
import net.fabricmc.fabric.api.networking.v1.ServerPlayNetworking;
import net.minecraft.command.CommandSource;
import net.minecraft.server.command.ServerCommandSource;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.text.Text;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/** Server-side management of message-format templates (/e33chat template ...). */
public class E33ChatCommands {

    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var tpl = net.minecraft.server.command.CommandManager.literal("template")
                .requires(s -> s.hasPermissionLevel(2));
            tpl.then(net.minecraft.server.command.CommandManager.literal("list")
                .executes(ctx -> list(ctx.getSource())));

            tpl.then(net.minecraft.server.command.CommandManager.literal("set")
                .then(net.minecraft.server.command.CommandManager.literal("chat")
                    .then(net.minecraft.server.command.CommandManager.argument("template", StringArgumentType.greedyString())
                        .executes(ctx -> set(ctx.getSource(), true,
                            StringArgumentType.getString(ctx, "template")))))
                .then(net.minecraft.server.command.CommandManager.literal("whisper")
                    .then(net.minecraft.server.command.CommandManager.argument("template", StringArgumentType.greedyString())
                        .executes(ctx -> set(ctx.getSource(), false,
                            StringArgumentType.getString(ctx, "template"))))));

            tpl.then(net.minecraft.server.command.CommandManager.literal("remove")
                .then(net.minecraft.server.command.CommandManager.literal("chat")
                    .then(net.minecraft.server.command.CommandManager.argument("index", IntegerArgumentType.integer(1))
                        .executes(ctx -> remove(ctx.getSource(), true,
                            IntegerArgumentType.getInteger(ctx, "index")))))
                .then(net.minecraft.server.command.CommandManager.literal("whisper")
                    .then(net.minecraft.server.command.CommandManager.argument("index", IntegerArgumentType.integer(1))
                        .executes(ctx -> remove(ctx.getSource(), false,
                            IntegerArgumentType.getInteger(ctx, "index"))))));

            tpl.then(net.minecraft.server.command.CommandManager.literal("clear")
                .then(net.minecraft.server.command.CommandManager.literal("chat")
                    .executes(ctx -> clear(ctx.getSource(), true)))
                .then(net.minecraft.server.command.CommandManager.literal("whisper")
                    .executes(ctx -> clear(ctx.getSource(), false))));

            tpl.then(net.minecraft.server.command.CommandManager.literal("test")
                .then(net.minecraft.server.command.CommandManager.literal("chat")
                    .then(net.minecraft.server.command.CommandManager.argument("index", IntegerArgumentType.integer(1))
                        .then(net.minecraft.server.command.CommandManager.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> test(ctx.getSource(), true,
                                IntegerArgumentType.getInteger(ctx, "index"),
                                StringArgumentType.getString(ctx, "text"))))))
                .then(net.minecraft.server.command.CommandManager.literal("whisper")
                    .then(net.minecraft.server.command.CommandManager.argument("index", IntegerArgumentType.integer(1))
                        .then(net.minecraft.server.command.CommandManager.argument("text", StringArgumentType.greedyString())
                            .executes(ctx -> test(ctx.getSource(), false,
                                IntegerArgumentType.getInteger(ctx, "index"),
                                StringArgumentType.getString(ctx, "text")))))));

            dispatcher.register(net.minecraft.server.command.CommandManager.literal("e33chat")
                .then(net.minecraft.server.command.CommandManager.literal("gui")
                    .requires(s -> s.hasPermissionLevel(2))
                    .executes(ctx -> openServerGui(ctx.getSource())))
                .then(tpl));
        });
    }

    // Opens the server-config GUI on the executing player's client (S2C snapshot)
    private static int openServerGui(ServerCommandSource src) {
        var player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.translatable("e33chat.server.console_only"));
            return 0;
        }
        ServerPlayNetworking.send(player,
            new ServerConfigScreenPayload(ChatBubbleMod.useTpa(), ChatBubbleMod.historyEnabled(),
                ChatBubbleMod.templateDebug(), ChatBubbleMod.mediaEnabled(), ChatBubbleMod.mediaAutoClean(),
                new ArrayList<>(ChatBubbleMod.chatTemplates()),
                new ArrayList<>(ChatBubbleMod.whisperTemplates())));
        return 1;
    }

    private static List<String> templates(boolean chat) {
        return chat ? ChatBubbleMod.chatTemplates() : ChatBubbleMod.whisperTemplates();
    }

    private static int list(ServerCommandSource src) {
        src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_list_chat_header", templates(true).size()), false);
        printTemplates(src, templates(true));
        src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_list_whisper_header", templates(false).size()), false);
        printTemplates(src, templates(false));
        return 1;
    }

    private static void printTemplates(ServerCommandSource src, List<String> templates) {
        if (templates.isEmpty()) {
            src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_list_empty"), false);
            return;
        }
        int i = 1;
        for (String t : templates) {
            int idx = i++;
            src.sendFeedback(() -> Text.literal("  " + idx + ". " + t), false);
        }
    }

    private static int set(ServerCommandSource src, boolean chat, String raw) {
        TemplateMatcher.CompileResult result = TemplateMatcher.compile(raw);
        if (result.template() == null) {
            src.sendError(Text.translatable("e33chat.server.tpl_set_invalid", result.error()));
            return 0;
        }
        if (!result.template().unknownFields().isEmpty()) {
            src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_set_unknown_fields", result.template().unknownFields()), false);
        }
        List<String> next = new ArrayList<>(templates(chat));
        if (next.contains(raw)) {
            src.sendError(Text.translatable("e33chat.server.tpl_set_duplicate"));
            return 0;
        }
        next.add(raw);
        updateTemplates(src, chat, next);
        src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_set_added", next.size(), raw), false);
        return 1;
    }

    private static int remove(ServerCommandSource src, boolean chat, int index) {
        List<String> next = new ArrayList<>(templates(chat));
        if (index < 1 || index > next.size()) {
            src.sendError(Text.translatable("e33chat.server.tpl_remove_bad_index", next.size()));
            return 0;
        }
        String removed = next.remove(index - 1);
        updateTemplates(src, chat, next);
        src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_remove_done", removed), false);
        return 1;
    }

    private static int clear(ServerCommandSource src, boolean chat) {
        updateTemplates(src, chat, List.of());
        src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_clear_done",
            Text.translatable(chat ? "e33chat.server.kind_chat" : "e33chat.server.kind_whisper")), false);
        return 1;
    }

    private static int test(ServerCommandSource src, boolean chat, int index, String text) {
        List<String> raws = templates(chat);
        if (index < 1 || index > raws.size()) {
            src.sendError(Text.translatable("e33chat.server.tpl_test_bad_index", raws.size()));
            return 0;
        }
        TemplateMatcher.CompileResult result = TemplateMatcher.compile(raws.get(index - 1));
        if (result.template() == null) {
            src.sendError(Text.translatable("e33chat.server.tpl_test_unparseable", result.error()));
            return 0;
        }
        boolean whisper = result.template().whisper();
        var match = TemplateMatcher.match(text,
            whisper ? List.of() : List.of(result.template()),
            whisper ? List.of(result.template()) : List.of(),
            name -> isKnownOnServer(src, name));
        if (match.isEmpty()) {
            src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_test_no_match"), false);
            return 1;
        }
        var r = match.orElseThrow();
        src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_test_matched",
                Text.translatable(whisper ? "e33chat.server.kind_whisper" : "e33chat.server.kind_chat")), false);
        if (r.prefix() != null) src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_test_field_prefix", r.prefix()), false);
        if (r.displayName() != null) {
            src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_test_field_name",
                    whisper ? "sender/target" : "display_name", r.displayName())
                .copy().append(Text.translatable("e33chat.server.tpl_test_verified")), false);
        }
        if (r.sender() != null && r.target() != null) {
            src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_test_field_sender", r.sender(), r.target()), false);
        }
        src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_test_field_content", r.content()), false);
        src.sendFeedback(() -> Text.translatable("e33chat.server.tpl_test_field_offset",
                r.nameStart(), r.nameEnd(), r.contentStart(), r.contentEnd()), false);
        return 1;
    }

    private static void updateTemplates(ServerCommandSource src, boolean chat, List<String> next) {
        if (chat) ChatBubbleMod.setTemplates(new ArrayList<>(next), ChatBubbleMod.whisperTemplates(), ChatBubbleMod.templateDebug());
        else ChatBubbleMod.setTemplates(ChatBubbleMod.chatTemplates(), new ArrayList<>(next), ChatBubbleMod.templateDebug());
        // Persist to the per-world JSON and rebroadcast
        var server = src.getServer();
        var path = server.getSavePath(net.minecraft.util.WorldSavePath.ROOT)
            .resolve("serverconfig").resolve("e33chat-server.json");
        ServerConfig cfg = new ServerConfig();
        cfg.use_tpa = ChatBubbleMod.useTpa();
        cfg.history_enabled = ChatBubbleMod.historyEnabled();
        cfg.template_debug = ChatBubbleMod.templateDebug();
        cfg.chat_templates = new ArrayList<>(ChatBubbleMod.chatTemplates());
        cfg.whisper_templates = new ArrayList<>(ChatBubbleMod.whisperTemplates());
        ServerConfigManager.save(path, cfg);
        ChatBubbleMod.broadcastServerConfig(server);
    }

    // Server-side stand-in for the client's name-resolution gate: the executing
    // player is the client's self, and all online players are candidate names
    private static boolean isKnownOnServer(ServerCommandSource src, String name) {
        if (name == null || name.isEmpty()) return false;
        var server = src.getServer();
        var self = src.getPlayer();
        if (self != null) {
            String selfName = self.getName().getString();
            if (!selfName.isEmpty() && (name.equals(selfName) || name.contains(selfName))) return true;
        }
        for (var p : server.getPlayerManager().getPlayerList()) {
            String n = p.getName().getString();
            if (!n.isEmpty() && (name.equals(n) || name.contains(n))) return true;
        }
        return false;
    }
}
