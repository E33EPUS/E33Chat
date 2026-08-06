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
public class E33ChatCommands {
    public static void register() {
        CommandRegistrationCallback.EVENT.register((dispatcher, registryAccess, environment) -> {
            var tpl = net.minecraft.server.command.CommandManager.literal("template")
                //#if MC >= 12111
                .requires(s -> s.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.fromLevel(2))))
                //#else
                //$$ .requires(s -> s.hasPermissionLevel(2))
                //#endif
            ;
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
                    .executes(ctx -> clear(ctx.getSource(), true, "\u804A\u5929")))
                .then(net.minecraft.server.command.CommandManager.literal("whisper")
                    .executes(ctx -> clear(ctx.getSource(), false, "\u79C1\u804A"))));
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
                    //#if MC >= 12111
                    .requires(s -> s.getPermissions().hasPermission(new net.minecraft.command.permission.Permission.Level(net.minecraft.command.permission.PermissionLevel.fromLevel(2))))
                    //#else
                    //$$ .requires(s -> s.hasPermissionLevel(2))
                    //#endif
                    .executes(ctx -> openServerGui(ctx.getSource())))
                .then(tpl));
        });
    }
    private static int openServerGui(ServerCommandSource src) {
        var player = src.getPlayer();
        if (player == null) {
            src.sendError(Text.translatable("e33chat.server.console_only"));
            return 0;
        }
        ServerPlayNetworking.send(player,
            new ServerConfigScreenPayload(ChatBubbleMod.useTpa(), ChatBubbleMod.historyEnabled(),
                ChatBubbleMod.templateDebug(), new ArrayList<>(ChatBubbleMod.chatTemplates()),
                new ArrayList<>(ChatBubbleMod.whisperTemplates())));
        return 1;
    }
    private static List<String> templates(boolean chat) {
        return chat ? ChatBubbleMod.chatTemplates() : ChatBubbleMod.whisperTemplates();
    }
    private static int list(ServerCommandSource src) {
        src.sendFeedback(() -> Text.literal("\u804A\u5929\u6A21\u677F (" + templates(true).size() + " \u6761):"), false);
        printTemplates(src, templates(true));
        src.sendFeedback(() -> Text.literal("\u79C1\u804A\u6A21\u677F (" + templates(false).size() + " \u6761):"), false);
        printTemplates(src, templates(false));
        return 1;
    }
    private static void printTemplates(ServerCommandSource src, List<String> templates) {
        if (templates.isEmpty()) {
            src.sendFeedback(() -> Text.literal("  (\u7A7A \u2014 \u4F7F\u7528\u542F\u53D1\u5F0F\u5B88\u536B\u8BC6\u522B)"), false);
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
            src.sendError(Text.literal("\u6A21\u677F\u65E0\u6548: " + result.error()));
            return 0;
        }
        if (!result.template().unknownFields().isEmpty()) {
            src.sendFeedback(() -> Text.literal("\u8B66\u544A: \u672A\u8BC6\u522B\u5360\u4F4D\u7B26 " + result.template().unknownFields()
                + " \u5C06\u6309\u5B57\u9762\u91CF\u5904\u7406"), false);
        }
        List<String> next = new ArrayList<>(templates(chat));
        if (next.contains(raw)) {
            src.sendError(Text.literal("\u6A21\u677F\u5DF2\u5B58\u5728\uFF0C\u65E0\u9700\u91CD\u590D\u6DFB\u52A0"));
            return 0;
        }
        next.add(raw);
        updateTemplates(src, chat, next);
        src.sendFeedback(() -> Text.literal("\u5DF2\u6DFB\u52A0\u6A21\u677F\uFF08\u5F53\u524D " + next.size() + " \u6761\uFF09: " + raw), false);
        return 1;
    }
    private static int remove(ServerCommandSource src, boolean chat, int index) {
        List<String> next = new ArrayList<>(templates(chat));
        if (index < 1 || index > next.size()) {
            src.sendError(Text.literal("\u7D22\u5F15\u65E0\u6548\uFF081-" + next.size() + "\uFF09\uFF0C\u7528 /e33chat template list \u67E5\u770B"));
            return 0;
        }
        String removed = next.remove(index - 1);
        updateTemplates(src, chat, next);
        src.sendFeedback(() -> Text.literal("\u5DF2\u79FB\u9664: " + removed), false);
        return 1;
    }
    private static int clear(ServerCommandSource src, boolean chat, String kind) {
        updateTemplates(src, chat, List.of());
        src.sendFeedback(() -> Text.literal(kind + "\u6A21\u677F\u5DF2\u6E05\u7A7A\uFF08\u6062\u590D\u5B88\u536B\u8BC6\u522B\uFF09"), false);
        return 1;
    }
    private static int test(ServerCommandSource src, boolean chat, int index, String text) {
        List<String> raws = templates(chat);
        if (index < 1 || index > raws.size()) {
            src.sendError(Text.literal("\u7D22\u5F15\u65E0\u6548\uFF081-" + raws.size() + "\uFF09"));
            return 0;
        }
        TemplateMatcher.CompileResult result = TemplateMatcher.compile(raws.get(index - 1));
        if (result.template() == null) {
            src.sendError(Text.literal("\u8BE5\u6A21\u677F\u5F53\u524D\u65E0\u6CD5\u89E3\u6790: " + result.error()));
            return 0;
        }
        boolean whisper = result.template().whisper();
        var match = TemplateMatcher.match(text,
            whisper ? List.of() : List.of(result.template()),
            whisper ? List.of(result.template()) : List.of(),
            name -> isKnownOnServer(src, name));
        if (match.isEmpty()) {
            src.sendFeedback(() -> Text.literal("\u672A\u5339\u914D \u2014 \u8BE5\u6D88\u606F\u4F1A\u56DE\u843D\u5230\u542F\u53D1\u5F0F\u5B88\u536B"), false);
            return 1;
        }
        var r = match.orElseThrow();
        src.sendFeedback(() -> Text.literal("\u5339\u914D\u6210\u529F (" + (whisper ? "\u79C1\u804A" : "\u804A\u5929") + " \u6A21\u677F):"), false);
        if (r.prefix() != null) src.sendFeedback(() -> Text.literal("  prefix = " + r.prefix()), false);
        if (r.displayName() != null) {
            src.sendFeedback(() -> Text.literal("  " + (whisper ? "sender/target" : "display_name")
                + " = " + r.displayName() + (r.verifiedName() != null ? "\uFF08\u5DF2\u786E\u8BA4\u662F\u73A9\u5BB6\uFF09" : "")), false);
        }
        if (r.sender() != null && r.target() != null) {
            src.sendFeedback(() -> Text.literal("  sender = " + r.sender() + " | target = " + r.target()), false);
        }
        src.sendFeedback(() -> Text.literal("  content = " + r.content()), false);
        src.sendFeedback(() -> Text.literal("  \u504F\u79FB: name[" + r.nameStart() + "," + r.nameEnd()
            + ") content[" + r.contentStart() + "," + r.contentEnd() + ")"), false);
        return 1;
    }
    private static void updateTemplates(ServerCommandSource src, boolean chat, List<String> next) {
        if (chat) ChatBubbleMod.setTemplates(new ArrayList<>(next), ChatBubbleMod.whisperTemplates(), ChatBubbleMod.templateDebug());
        else ChatBubbleMod.setTemplates(ChatBubbleMod.chatTemplates(), new ArrayList<>(next), ChatBubbleMod.templateDebug());
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