package com.niuqu.chatbubble.command;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.config.ChatServerConfig;
import com.niuqu.chatbubble.server.ChatServerListener;
import com.niuqu.chatbubble.network.NetworkHandler;
import com.niuqu.chatbubble.chat.TemplateMatcher;
import com.niuqu.chatbubble.packets.ServerConfigScreenPacket;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/** Server-side management of message-format templates (/e33chat template ...). */
public class E33ChatCommands {

    @SubscribeEvent
    public void onRegisterCommands(RegisterCommandsEvent event) {
        var tpl = Commands.literal("template").requires(s -> s.hasPermission(2));
        tpl.then(Commands.literal("list").executes(ctx -> list(ctx.getSource())));

        tpl.then(Commands.literal("set")
            .then(Commands.literal("chat").then(Commands.argument("template", StringArgumentType.greedyString())
                .executes(ctx -> set(ctx.getSource(), ChatServerConfig.CHAT_TEMPLATES,
                    StringArgumentType.getString(ctx, "template")))))
            .then(Commands.literal("whisper").then(Commands.argument("template", StringArgumentType.greedyString())
                .executes(ctx -> set(ctx.getSource(), ChatServerConfig.WHISPER_TEMPLATES,
                    StringArgumentType.getString(ctx, "template"))))));

        tpl.then(Commands.literal("remove")
            .then(Commands.literal("chat").then(Commands.argument("index", IntegerArgumentType.integer(1))
                .executes(ctx -> remove(ctx.getSource(), ChatServerConfig.CHAT_TEMPLATES,
                    IntegerArgumentType.getInteger(ctx, "index")))))
            .then(Commands.literal("whisper").then(Commands.argument("index", IntegerArgumentType.integer(1))
                .executes(ctx -> remove(ctx.getSource(), ChatServerConfig.WHISPER_TEMPLATES,
                    IntegerArgumentType.getInteger(ctx, "index"))))));

        tpl.then(Commands.literal("clear")
            .then(Commands.literal("chat").executes(ctx -> clear(ctx.getSource(), ChatServerConfig.CHAT_TEMPLATES, true)))
            .then(Commands.literal("whisper").executes(ctx -> clear(ctx.getSource(), ChatServerConfig.WHISPER_TEMPLATES, false))));

        tpl.then(Commands.literal("test")
            .then(Commands.literal("chat").then(Commands.argument("index", IntegerArgumentType.integer(1))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> test(ctx.getSource(), ChatServerConfig.CHAT_TEMPLATES,
                        IntegerArgumentType.getInteger(ctx, "index"),
                        StringArgumentType.getString(ctx, "text"))))))
            .then(Commands.literal("whisper").then(Commands.argument("index", IntegerArgumentType.integer(1))
                .then(Commands.argument("text", StringArgumentType.greedyString())
                    .executes(ctx -> test(ctx.getSource(), ChatServerConfig.WHISPER_TEMPLATES,
                        IntegerArgumentType.getInteger(ctx, "index"),
                        StringArgumentType.getString(ctx, "text")))))));

        event.getDispatcher().register(Commands.literal("e33chat")
            .then(Commands.literal("gui").requires(s -> s.hasPermission(2))
                .executes(ctx -> openServerGui(ctx.getSource())))
            .then(tpl));
    }

    // Opens the server-config GUI on the executing player's client (S2C snapshot)
    private static int openServerGui(CommandSourceStack src) {
        var player = src.getPlayer();
        if (player == null) {
            src.sendFailure(Component.translatable("e33chat.server.console_only"));
            return 0;
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new ServerConfigScreenPacket(ChatServerConfig.USE_TPA.get(),
                ChatServerConfig.HISTORY_ENABLED.get(), ChatServerConfig.TEMPLATE_DEBUG.get(),
                ChatServerConfig.MEDIA_ENABLED.get(), ChatServerConfig.MEDIA_AUTO_CLEAN.get(),
                new ArrayList<>(ChatServerConfig.CHAT_TEMPLATES.get()),
                new ArrayList<>(ChatServerConfig.WHISPER_TEMPLATES.get())));
        return 1;
    }

    private static int list(CommandSourceStack src) {
        src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_list_chat_header", ChatServerConfig.CHAT_TEMPLATES.get().size()), false);
        printTemplates(src, ChatServerConfig.CHAT_TEMPLATES.get());
        src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_list_whisper_header", ChatServerConfig.WHISPER_TEMPLATES.get().size()), false);
        printTemplates(src, ChatServerConfig.WHISPER_TEMPLATES.get());
        return 1;
    }

    private static void printTemplates(CommandSourceStack src, List<? extends String> templates) {
        if (templates.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_list_empty"), false);
            return;
        }
        int i = 1;
        for (String t : templates) {
            int idx = i++;
            src.sendSuccess(() -> Component.literal("  " + idx + ". " + t), false);
        }
    }

    private static int set(CommandSourceStack src, ForgeConfigSpec.ConfigValue<List<? extends String>> spec, String raw) {
        TemplateMatcher.CompileResult result = TemplateMatcher.compile(raw);
        if (result.template() == null) {
            src.sendFailure(Component.translatable("e33chat.server.tpl_set_invalid", result.error()));
            return 0;
        }
        if (!result.template().unknownFields().isEmpty()) {
            src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_set_unknown_fields", result.template().unknownFields()), false);
        }
        List<String> next = new ArrayList<>(spec.get());
        if (next.contains(raw)) {
            src.sendFailure(Component.translatable("e33chat.server.tpl_set_duplicate"));
            return 0;
        }
        next.add(raw);
        updateTemplates(src, spec, next);
        src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_set_added", next.size(), raw), false);
        return 1;
    }

    private static int remove(CommandSourceStack src, ForgeConfigSpec.ConfigValue<List<? extends String>> spec, int index) {
        List<String> next = new ArrayList<>(spec.get());
        if (index < 1 || index > next.size()) {
            src.sendFailure(Component.translatable("e33chat.server.tpl_remove_bad_index", next.size()) );
            return 0;
        }
        String removed = next.remove(index - 1);
        updateTemplates(src, spec, next);
        src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_remove_done", removed), false);
        return 1;
    }

    private static int clear(CommandSourceStack src, ForgeConfigSpec.ConfigValue<List<? extends String>> spec, boolean chat) {
        updateTemplates(src, spec, List.of());
        src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_clear_done",
            Component.translatable(chat ? "e33chat.server.kind_chat" : "e33chat.server.kind_whisper")), false);
        return 1;
    }

    private static int test(CommandSourceStack src, ForgeConfigSpec.ConfigValue<List<? extends String>> spec, int index, String text) {
        List<? extends String> raws = spec.get();
        if (index < 1 || index > raws.size()) {
            src.sendFailure(Component.translatable("e33chat.server.tpl_test_bad_index", raws.size()));
            return 0;
        }
        TemplateMatcher.CompileResult result = TemplateMatcher.compile(raws.get(index - 1));
        if (result.template() == null) {
            src.sendFailure(Component.translatable("e33chat.server.tpl_test_unparseable", result.error()));
            return 0;
        }
        boolean whisper = result.template().whisper();
        var match = TemplateMatcher.match(text,
            whisper ? List.of() : List.of(result.template()),
            whisper ? List.of(result.template()) : List.of(),
            name -> isKnownOnServer(src, name));
        if (match.isEmpty()) {
            src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_test_no_match"), false);
            return 1;
        }
        var r = match.orElseThrow();
        src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_test_matched",
                Component.translatable(whisper ? "e33chat.server.kind_whisper" : "e33chat.server.kind_chat")), false);
        if (r.prefix() != null) src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_test_field_prefix", r.prefix()), false);
        if (r.displayName() != null) {
            src.sendSuccess(() -> {
                var field = Component.translatable("e33chat.server.tpl_test_field_name",
                    whisper ? "sender/target" : "display_name", r.displayName());
                return r.verifiedName() != null
                    ? field.copy().append(Component.translatable("e33chat.server.tpl_test_verified"))
                    : field;
            }, false);
        }
        if (r.sender() != null && r.target() != null) {
            src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_test_field_sender", r.sender(), r.target()), false);
        }
        src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_test_field_content", r.content()), false);
        src.sendSuccess(() -> Component.translatable("e33chat.server.tpl_test_field_offset",
                r.nameStart(), r.nameEnd(), r.contentStart(), r.contentEnd()), false);
        return 1;
    }

    private static void updateTemplates(CommandSourceStack src, ForgeConfigSpec.ConfigValue<List<? extends String>> spec,
                                        List<String> next) {
        spec.set(next);
        spec.clearCache();
        ChatBubbleMod.saveServerConfig();
        ChatServerListener.broadcastServerConfig();
    }

    // Server-side stand-in for the client's name-resolution gate: the executing
    // player is the client's self, and all online players are candidate names
    private static boolean isKnownOnServer(CommandSourceStack src, String name) {
        if (name == null || name.isEmpty()) return false;
        var server = src.getServer();
        var self = src.getPlayer();
        if (self != null) {
            String selfName = self.getName().getString();
            if (!selfName.isEmpty() && (name.equals(selfName) || name.contains(selfName))) return true;
        }
        for (var p : server.getPlayerList().getPlayers()) {
            String n = p.getName().getString();
            if (!n.isEmpty() && (name.equals(n) || name.contains(n))) return true;
        }
        return false;
    }
}
