package com.niuqu.chatbubble.command;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.ChatServerConfig;
import com.niuqu.chatbubble.ChatServerListener;
import com.niuqu.chatbubble.chat.TemplateMatcher;
import com.niuqu.chatbubble.packets.ServerConfigScreenPayload;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.StringArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.common.ModConfigSpec;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.network.PacketDistributor;

import java.util.ArrayList;
import java.util.List;

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
            .then(Commands.literal("chat").executes(ctx -> clear(ctx.getSource(), ChatServerConfig.CHAT_TEMPLATES, "聊天")))
            .then(Commands.literal("whisper").executes(ctx -> clear(ctx.getSource(), ChatServerConfig.WHISPER_TEMPLATES, "私聊"))));

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
        PacketDistributor.sendToPlayer(player,
            new ServerConfigScreenPayload(ChatServerConfig.USE_TPA.get(),
                ChatServerConfig.HISTORY_ENABLED.get(), ChatServerConfig.TEMPLATE_DEBUG.get(),
                new ArrayList<>(ChatServerConfig.CHAT_TEMPLATES.get()),
                new ArrayList<>(ChatServerConfig.WHISPER_TEMPLATES.get())));
        return 1;
    }

    private static int list(CommandSourceStack src) {
        src.sendSuccess(() -> Component.literal("聊天模板 (" + ChatServerConfig.CHAT_TEMPLATES.get().size() + " 条):"), false);
        printTemplates(src, ChatServerConfig.CHAT_TEMPLATES.get());
        src.sendSuccess(() -> Component.literal("私聊模板 (" + ChatServerConfig.WHISPER_TEMPLATES.get().size() + " 条):"), false);
        printTemplates(src, ChatServerConfig.WHISPER_TEMPLATES.get());
        return 1;
    }

    private static void printTemplates(CommandSourceStack src, List<? extends String> templates) {
        if (templates.isEmpty()) {
            src.sendSuccess(() -> Component.literal("  (空 — 使用启发式守卫识别)"), false);
            return;
        }
        int i = 1;
        for (String t : templates) {
            int idx = i++;
            src.sendSuccess(() -> Component.literal("  " + idx + ". " + t), false);
        }
    }

    private static int set(CommandSourceStack src, ModConfigSpec.ConfigValue<List<? extends String>> spec, String raw) {
        TemplateMatcher.CompileResult result = TemplateMatcher.compile(raw);
        if (result.template() == null) {
            src.sendFailure(Component.literal("模板无效: " + result.error()));
            return 0;
        }
        if (!result.template().unknownFields().isEmpty()) {
            src.sendSuccess(() -> Component.literal("警告: 未识别占位符 " + result.template().unknownFields()
                + " 将按字面量处理"), false);
        }
        List<String> next = new ArrayList<>(spec.get());
        if (next.contains(raw)) {
            src.sendFailure(Component.literal("模板已存在，无需重复添加"));
            return 0;
        }
        next.add(raw);
        updateTemplates(src, spec, next);
        src.sendSuccess(() -> Component.literal("已添加模板（当前 " + next.size() + " 条）: " + raw), false);
        return 1;
    }

    private static int remove(CommandSourceStack src, ModConfigSpec.ConfigValue<List<? extends String>> spec, int index) {
        List<String> next = new ArrayList<>(spec.get());
        if (index < 1 || index > next.size()) {
            src.sendFailure(Component.literal("索引无效（1-" + next.size() + "），用 /e33chat template list 查看") );
            return 0;
        }
        String removed = next.remove(index - 1);
        updateTemplates(src, spec, next);
        src.sendSuccess(() -> Component.literal("已移除: " + removed), false);
        return 1;
    }

    private static int clear(CommandSourceStack src, ModConfigSpec.ConfigValue<List<? extends String>> spec, String kind) {
        updateTemplates(src, spec, List.of());
        src.sendSuccess(() -> Component.literal(kind + "模板已清空（恢复守卫识别）"), false);
        return 1;
    }

    private static int test(CommandSourceStack src, ModConfigSpec.ConfigValue<List<? extends String>> spec, int index, String text) {
        List<? extends String> raws = spec.get();
        if (index < 1 || index > raws.size()) {
            src.sendFailure(Component.literal("索引无效（1-" + raws.size() + "）"));
            return 0;
        }
        TemplateMatcher.CompileResult result = TemplateMatcher.compile(raws.get(index - 1));
        if (result.template() == null) {
            src.sendFailure(Component.literal("该模板当前无法解析: " + result.error()));
            return 0;
        }
        boolean whisper = result.template().whisper();
        var match = TemplateMatcher.match(text,
            whisper ? List.of() : List.of(result.template()),
            whisper ? List.of(result.template()) : List.of(),
            name -> isKnownOnServer(src, name));
        if (match.isEmpty()) {
            src.sendSuccess(() -> Component.literal("未匹配 — 该消息会回落到启发式守卫"), false);
            return 1;
        }
        var r = match.orElseThrow();
        src.sendSuccess(() -> Component.literal("匹配成功 (" + (whisper ? "私聊" : "聊天") + " 模板):"), false);
        if (r.prefix() != null) src.sendSuccess(() -> Component.literal("  prefix = " + r.prefix()), false);
        if (r.displayName() != null) {
            src.sendSuccess(() -> Component.literal("  " + (whisper ? "sender/target" : "display_name")
                + " = " + r.displayName() + (r.verifiedName() != null ? "（已确认是玩家）" : "")), false);
        }
        if (r.sender() != null && r.target() != null) {
            src.sendSuccess(() -> Component.literal("  sender = " + r.sender() + " | target = " + r.target()), false);
        }
        src.sendSuccess(() -> Component.literal("  content = " + r.content()), false);
        src.sendSuccess(() -> Component.literal("  偏移: name[" + r.nameStart() + "," + r.nameEnd()
            + ") content[" + r.contentStart() + "," + r.contentEnd() + ")"), false);
        return 1;
    }

    private static void updateTemplates(CommandSourceStack src, ModConfigSpec.ConfigValue<List<? extends String>> spec,
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
