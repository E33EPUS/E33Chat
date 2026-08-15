package com.niuqu.chatbubble.config;
import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.network.NetworkHandler;
import com.niuqu.chatbubble.render.Animation;

import com.niuqu.chatbubble.chat.TemplateMatcher;
import com.niuqu.chatbubble.render.ChatScrollbar;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;

/**
 * Server-config GUI (opened by /e33chat gui, filled from a ConfigSnapshotPacket).
 *
 * Geometry mirrors ChatBubbleConfigScreen exactly: same constants, same formulas —
 *   dividerX = CAT_X + CAT_W + 12, optLabelX = dividerX + 14,
 *   previewX = width - 26, inputX = previewX - 8 - INPUT_W (INPUT_W fixed at 90).
 * Every control is INPUT_W wide, right-aligned to inputX, top-aligned to its row.
 * Label area is therefore guaranteed to stay clear (>= ~242px at width 512).
 *
 * Edits live in local copies; Save sends them back via ServerConfigSavePacket —
 * the server re-validates, persists to the toml and rebroadcasts. Esc with
 * changes asks to discard (never sent = never applied, same as a local rollback).
 */
public class ServerConfigScreen extends Screen {
    private final Screen lastScreen;

    // 几何常量：与 ChatBubbleConfigScreen 完全一致
    private static final int ROW_H = 32;
    private static final int START_Y = 40;
    private static final int CAT_X = 24;
    private static final int CAT_W = 96;
    private static final int CAT_ROW_H = 22;
    private static final int INPUT_W = 170;
    // 模板行编辑框与普通输入框统一 170px（用户定）。右侧 ✕ 20px 收在控件组内，
    // 整组右对齐 previewX-8，与其他行控件右缘对齐
    private static final int TEMPLATE_INPUT_W = 170;
    // 操作按钮/开关按钮宽度：对齐客户端配置界面的 INPUT_W=90（除 + / ✕ 小按钮）
    private static final int BUTTON_W = 90;

    // 常见格式预设：基础格式 + 真实插件默认格式（2.2.7 起）。{sep} 匹配
    // >> / 冒号 / » / > 或纯空格，一条模板覆盖多种分隔符风格。
    private static final String[] CHAT_PRESETS = {
        "{display_name}{sep}{content}",
        "<{display_name}> {content}",                       // EssentialsX 默认
        "[{display_name}]: {content}",
        "{prefix}{display_name}{sep}{content}",
        "&7[{group}]&r {display_name}&7:&r {content}",      // EssentialsX 带前后缀示例
        "[Guest] {display_name} > {content}",               // DeluxeChat
        "{display_name} >> {content}",
        "-{display_name}- {content}",
        "【{display_name}】{content}",
    };
    private static final String[] WHISPER_PRESETS = {
        "{sender}悄悄地对你说{sep}{content}",
        "{sender} whispered to you{sep}{content}",
        "[/msg from {sender}] {content}",                   // CMI 接收视角
        "{sender} -> {target}{sep}{content}",               // DeluxeChat
        "[私聊] {sender}{sep}{content}",
        "{sender}私聊 {target}{sep}{content}",
    };

    private static final String[] CAT_KEYS = {
        "e33chat.server.cat.general",
        "e33chat.server.cat.chat",
        "e33chat.server.cat.whisper",
        "e33chat.server.cat.debug",
        "e33chat.server.cat.tutorial",
    };

    // 打开时的快照（用于变更检测）+ 可编辑的本地副本（发送前不生效）
    private final boolean initUseTpa, initHistory, initDebug, initMedia;
    private boolean useTpaV, historyV, debugV, mediaV;
    private final List<String> initChat, initWhisper;
    private final List<String> chatV = new ArrayList<>();
    private final List<String> whisperV = new ArrayList<>();
    private boolean genVisible;
    private String genText = "";
    private String error;
    // 从消息生成失败的红字提示：渲染在生成输入框所在行下方（与模板行警告同风格）
    private String genError;
    private Row genInputRow;
    private String previewChatResult = "", previewWhisperResult = "";
    // 模板行输入时实时校验的错误（box → 错误文本）；rebuild 时清空
    private final java.util.Map<EditBox, String> boxErrors = new java.util.LinkedHashMap<>();
    // 从消息生成成功后，把示例消息代入预览框（重建后填入一次）
    private String pendingPreviewText;

    private int selectedCat;
    private int scrollOffset, treeScroll;
    private float rAnimFrom, rAnimTo;
    private long rAnimStart;
    private int rAnimDur;
    private boolean rAnimOn;
    private boolean rBarDrag;
    private int rBarDragY, rBarDragOff;
    private float tAnimFrom, tAnimTo;
    private long tAnimStart;
    private int tAnimDur;
    private boolean tAnimOn;
    private boolean tBarDrag;
    private int tBarDragY, tBarDragOff;

    // 右区行：label 左对齐 optLabelX；widgets 右对齐 inputX（模板行的 ✕ 例外：紧跟 label 后）
    // extraText 渲染在行内下半部（预览结果）；tooltipKey 非空时悬停显示 key+".desc"
    // title=true 时 label 按分区标题样式画（灰字 + 右侧延伸分割线）
    private record Row(Component label, List<AbstractWidget> widgets,
                       String extraText, int height, String tooltipKey, boolean title) {}
    private final List<Row> rows = new ArrayList<>();
    private Button doneBtn, exitBtn, saveBtn;

    public ServerConfigScreen(Screen lastScreen, boolean useTpa, boolean history, boolean debug,
                              boolean mediaEnabled,
                              List<String> chat, List<String> whisper) {
        super(Component.translatable("e33chat.server.title"));
        this.lastScreen = lastScreen;
        initUseTpa = useTpa;
        initHistory = history;
        initDebug = debug;
        initMedia = mediaEnabled;
        initChat = new ArrayList<>(chat);
        initWhisper = new ArrayList<>(whisper);
        useTpaV = useTpa;
        historyV = history;
        debugV = debug;
        mediaV = mediaEnabled;
        chatV.addAll(chat);
        whisperV.addAll(whisper);
    }

    private ChatBubbleTheme.Colors c() { return ChatBubbleTheme.DARK.colors(); }

    // ===== 几何：公式与客户端一致，控件宽度固定 INPUT_W =====
    private int dividerX() { return CAT_X + CAT_W + 12; }
    private int optLabelX() { return dividerX() + 14; }
    private int previewX() { return width - 26; }
    private int inputX() { return previewX() - 8 - INPUT_W; }
    private int optAreaW() { return previewX() - optLabelX() - 4; }
    // 右区文本（预览结果/错误提示）的最大像素宽度：从标签左缘到控件右缘线
    private int rightAreaW() { return previewX() - 8 - optLabelX(); }
    // 按钮右对齐到控件右缘线 previewX-8（与输入框右缘对齐）；两个按钮时左侧按钮再让 4px
    private int btnRight() { return previewX() - 8 - BUTTON_W; }
    private int btnLeft() { return btnRight() - 4 - BUTTON_W; }
    private int viewTop() { return START_Y; }
    private int viewBottom() { return height - 40; }

    private int totalRowsH() {
        int total = 0;
        for (Row r : rows) total += r.height();
        return total;
    }

    private int calcMaxScroll() {
        return Math.max(0, viewTop() + totalRowsH() - viewBottom());
    }

    private int calcTreeMaxScroll() {
        return Math.max(0, START_Y + CAT_ROW_H * CAT_KEYS.length - viewBottom());
    }

    // ===== 行构建：widgets 必须注册为 renderable，否则不渲染也不响应点击 =====
    private Row row(Component label, List<AbstractWidget> widgets, String extraText, String tooltipKey) {
        for (AbstractWidget w : widgets) addRenderableWidget(w);
        return new Row(label, widgets, extraText, ROW_H, tooltipKey, false);
    }

    private Row textRow(Component label, int height) {
        return new Row(label, List.of(), null, height, null, false);
    }

    private Row titleRow(Component label) {
        return new Row(label, List.of(), null, ROW_H, null, true);
    }

    private void buildRows() {
        rows.clear();
        switch (selectedCat) {
            case 0 -> {
                rows.add(row(Component.translatable("e33chat.server.use_tpa"),
                    List.of(mkToggle(() -> useTpaV, nv -> useTpaV = nv)), null, "e33chat.server.use_tpa"));
                rows.add(row(Component.translatable("e33chat.server.history"),
                    List.of(mkToggle(() -> historyV, nv -> historyV = nv)), null, "e33chat.server.history"));
                rows.add(row(Component.translatable("e33chat.server.media_enabled"),
                    List.of(mkToggle(() -> mediaV, nv -> mediaV = nv)), null, "e33chat.server.media_enabled"));
            }
            case 1 -> buildTemplateRows(chatV, true);
            case 2 -> buildTemplateRows(whisperV, false);
            case 3 -> rows.add(row(Component.translatable("e33chat.server.template_debug"),
                List.of(mkToggle(() -> debugV, nv -> debugV = nv)), null, "e33chat.server.template_debug"));
            case 4 -> buildTutorialRows();
        }
    }

    private void buildTemplateRows(List<String> list, boolean chat) {
        // 模板行：标签(左) + [编辑框][✕](右对齐到控件右缘线 previewX-8，与其他行控件右缘对齐)
        // widgets 必须注册（addRenderableWidget），否则不渲染也不可点
        for (int i = 0; i < list.size(); i++) {
            int idx = i;
            Component label = Component.translatable("e33chat.server.template_n", i + 1);
            EditBox box = mkBox(previewX() - 8 - TEMPLATE_INPUT_W - 24, TEMPLATE_INPUT_W);
            box.setValue(list.get(idx));
            box.setResponder(s -> {
                if (idx < list.size()) list.set(idx, s);
                // 实时校验：编译失败即时红字提示
                var r = TemplateMatcher.compile(s);
                if (r.template() == null) boxErrors.put(box, r.error());
                else boxErrors.remove(box);
            });
            Button rm = Button.builder(Component.literal("✕"), b -> { list.remove(idx); rebuild(); })
                .bounds(previewX() - 8 - 20, 0, 20, 20).build();
            rows.add(new Row(label, List.of(addRenderableWidget(rm), addRenderableWidget(box)), null, ROW_H,
                "e33chat.server.template_n", false));
        }
        // 操作行：添加 / 从消息生成 各 90px，从右往右对齐到控件右缘线（同客户端按钮宽）
        Button add = Button.builder(Component.translatable("e33chat.server.add"), b -> { list.add(""); rebuild(); })
            .bounds(btnLeft(), 0, BUTTON_W, 20).build();
        if (chat) {
            Button genOpen = Button.builder(Component.translatable("e33chat.server.gen_open"),
                b -> { genVisible = true; rebuild(); }).bounds(btnRight(), 0, BUTTON_W, 20).build();
            rows.add(row(Component.translatable("e33chat.server.actions"), List.of(add, genOpen), null, "e33chat.server.actions"));
            if (genVisible) {
                EditBox genBox = mkBox(inputX(), INPUT_W);
                genBox.setValue(genText);
                genBox.setResponder(s -> { genText = s; genError = null; });
                rows.add(genInputRow = row(Component.translatable("e33chat.server.gen"), List.of(genBox), null, "e33chat.server.gen"));
                Button genOk = Button.builder(Component.translatable("e33chat.server.gen_confirm"),
                    b -> generateFromMessage()).bounds(btnLeft(), 0, BUTTON_W, 20).build();
                Button genCancel = Button.builder(Component.translatable("e33chat.server.gen_cancel"),
                    b -> { genVisible = false; genError = null; rebuild(); }).bounds(btnRight(), 0, BUTTON_W, 20).build();
                rows.add(row(Component.translatable("e33chat.server.gen"), List.of(genOk, genCancel), null, null));
            }
        } else {
            rows.add(row(Component.translatable("e33chat.server.actions"), List.of(add), null, "e33chat.server.actions"));
        }
        // 常见格式预设：标题行 + 逐行[模板字符串(标签,按按钮左缘截断)][+正方形按钮右对齐]
        String[] presets = chat ? CHAT_PRESETS : WHISPER_PRESETS;
        rows.add(titleRow(Component.translatable("e33chat.server.preset_section")));
        for (String p : presets) {
            Button pb = Button.builder(Component.literal("+"),
                b -> { if (!list.contains(p)) { list.add(p); rebuild(); } })
                .bounds(inputX() + INPUT_W - 20, 0, 20, 20).build();
            Component label = Component.literal(truncate(p, inputX() - optLabelX() - 8));
            rows.add(new Row(label, List.of(addRenderableWidget(pb)), null, ROW_H, null, false));
        }
        // 预览：输入即出结果，结果渲染在行内下半部
        EditBox preview = mkBox(inputX(), INPUT_W);
        preview.setResponder(s -> {
            if (chat) previewChatResult = runPreview(s, chatV, false);
            else previewWhisperResult = runPreview(s, whisperV, true);
        });
        if (pendingPreviewText != null) {
            preview.setValue(pendingPreviewText);
            pendingPreviewText = null;
        }
        rows.add(row(Component.translatable("e33chat.server.preview"), List.of(preview),
            chat ? previewChatResult : previewWhisperResult, "e33chat.server.preview"));
    }

    // 教程：分节速查。每节 = 标题行 + 段落行（像素换行）+ 间距；原理节放最后（进阶）
    private void buildTutorialRows() {
        for (String key : List.of("quick", "concept", "fields", "faq", "why")) {
            rows.add(titleRow(Component.translatable("e33chat.tutorial." + key + ".title")));
            for (String para : Component.translatable("e33chat.tutorial." + key).getString().split("\n")) {
                if (para.isBlank()) {
                    rows.add(textRow(Component.literal(""), 6));
                    continue;
                }
                for (String line : wrapText(para)) {
                    rows.add(textRow(Component.literal(line), 14));
                }
            }
            rows.add(textRow(Component.literal(""), 10));
        }
    }

    // 按像素宽度逐字符换行（中英文通吃；中文无空格，不能按空格分词——
    // 旧实现 split(" ") 导致中文整段永远不换行、直接溢出右边界）
    private List<String> wrapText(String raw) {
        List<String> out = new ArrayList<>();
        StringBuilder cur = new StringBuilder();
        int maxW = optAreaW();
        int lastSpace = -1;
        for (int i = 0; i < raw.length(); i++) {
            char ch = raw.charAt(i);
            if (ch == ' ') lastSpace = cur.length();
            cur.append(ch);
            if (font.width(cur.toString()) > maxW) {
                int cut = lastSpace > 0 ? lastSpace : cur.length() - 1;
                if (cut <= 0) cut = cur.length() - 1;
                String line = cur.substring(0, cut).trim();
                if (!line.isEmpty()) out.add(line);
                cur.delete(0, cut);
                lastSpace = -1;
            }
        }
        if (cur.length() > 0) {
            String line = cur.toString().trim();
            if (!line.isEmpty()) out.add(line);
        }
        return out;
    }

    // 按像素宽度截断标签文本，超宽加省略号（预设模板字符串可能超过标签区）
    private String truncate(String s, int maxWidth) {
        if (font.width(s) <= maxWidth) return s;
        String cut = font.plainSubstrByWidth(s, maxWidth - 6);
        return cut + "…";
    }

    private void generateFromMessage() {
        String inferred = TemplateMatcher.inferFromMessage(genText, knownNames()).orElse(null);
        if (inferred == null) {
            // 复制功能只复制纯正文（不含玩家名），先提示要贴含名字的完整行
            genError = Component.translatable("e33chat.server.gen_failed").getString()
                + "  " + Component.translatable("e33chat.server.gen_howto").getString();
            return;
        }
        chatV.add(inferred);
        genVisible = false;
        genError = null;
        // 向导反馈：用源消息自动跑一次预览，重建后预览框填入该消息并显示解析结果
        pendingPreviewText = genText;
        genText = "";
        rebuild();
    }

    private List<String> knownNames() {
        LinkedHashSet<String> names = new LinkedHashSet<>();
        var player = Minecraft.getInstance().player;
        if (player != null && player.connection != null) {
            for (var info : player.connection.getOnlinePlayers()) {
                names.add(info.getProfile().getName());
            }
        }
        names.addAll(ChatMessageStore.knownNameVariants());
        return new ArrayList<>(names);
    }

    private String runPreview(String text, List<String> raws, boolean whisper) {
        if (text == null || text.isBlank()) return "";
        List<TemplateMatcher.CompiledTemplate> tpls = new ArrayList<>();
        for (String raw : raws) {
            var r = TemplateMatcher.compile(raw);
            if (r.template() != null) tpls.add(r.template());
        }
        var m = TemplateMatcher.match(text, whisper ? List.of() : tpls, whisper ? tpls : List.of(),
            ChatMessageStore::isKnownPlayerName);
        if (m.isEmpty()) return Component.translatable("e33chat.server.preview_miss").getString();
        var t = m.orElseThrow();
        String name = whisper && t.sender() != null ? t.sender() : t.displayName();
        return Component.translatable("e33chat.server.preview_hit", name, t.content()).getString();
    }

    private Button mkToggle(java.util.function.BooleanSupplier current,
                            java.util.function.Consumer<Boolean> apply) {
        return Button.builder(current.getAsBoolean() ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF,
            b -> {
                boolean nv = !current.getAsBoolean();
                apply.accept(nv);
                b.setMessage(nv ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
            }).bounds(btnRight(), 0, BUTTON_W, 20).build();
    }

    // 创建输入框（统一 maxLength 200）
    private EditBox mkBox(int x, int w) {
        EditBox box = new EditBox(font, x, 0, w, 20, Component.literal(""));
        box.setMaxLength(200);
        return box;
    }

    // ===== 保存 / 变更检测 =====

    private boolean changed() {
        return useTpaV != initUseTpa || historyV != initHistory || debugV != initDebug
            || mediaV != initMedia
            || !Objects.equals(chatV, initChat) || !Objects.equals(whisperV, initWhisper);
    }

    private void save() {
        String err = validate("chat", chatV);
        if (err == null) err = validate("whisper", whisperV);
        if (err != null) {
            error = err;
            return;
        }
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(
            new com.niuqu.chatbubble.packets.ServerConfigSavePayload(
                useTpaV, historyV, debugV, mediaV, new ArrayList<>(chatV), new ArrayList<>(whisperV)));
        doClose();
    }

    private static String validate(String kind, List<String> templates) {
        for (int i = 0; i < templates.size(); i++) {
            TemplateMatcher.CompileResult result = TemplateMatcher.compile(templates.get(i));
            if (result.template() == null) {
                return Component.translatable("e33chat.server.invalid",
                    Component.translatable("e33chat.server." + kind), i + 1, result.error()).getString();
            }
        }
        return null;
    }

    private void doClose() {
        minecraft.setScreen(lastScreen);
    }

    @Override
    public void onClose() {
        if (changed()) {
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) doClose();
                else minecraft.setScreen(this);
            },
                Component.translatable("e33chat.config.discard.title"),
                Component.translatable("e33chat.config.discard.message", changeCount())));
        } else {
            doClose();
        }
    }

    private int changeCount() {
        int n = 0;
        if (useTpaV != initUseTpa) n++;
        if (historyV != initHistory) n++;
        if (debugV != initDebug) n++;
        if (mediaV != initMedia) n++;
        if (!Objects.equals(chatV, initChat)) n++;
        if (!Objects.equals(whisperV, initWhisper)) n++;
        return n;
    }

    @Override
    protected void init() {
        buildRows();
        scrollOffset = Mth.clamp(scrollOffset, 0, calcMaxScroll());
        treeScroll = Mth.clamp(treeScroll, 0, calcTreeMaxScroll());

        doneBtn = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, b -> doClose())
            .bounds(width / 2 - 100, height - 32, 200, 20).build());
        exitBtn = addRenderableWidget(Button.builder(Component.translatable("e33chat.config.exit"), b -> doClose())
            .bounds(width / 2 - 104, height - 32, 100, 20).build());
        saveBtn = addRenderableWidget(Button.builder(Component.translatable("e33chat.server.save"), b -> save())
            .bounds(width / 2 + 4, height - 32, 100, 20).build());

        relayoutWidgets();
    }

    private void rebuild() {
        scrollOffset = 0;
        setFocused(null);
        clearWidgets();
        boxErrors.clear();
        init();
    }

    private void switchCategory(int idx) {
        if (idx == selectedCat) return;
        selectedCat = idx;
        rebuild();
    }

    // 按当前 scrollOffset 重排右侧控件 y 与可见性（控件顶对齐行 y，同客户端）
    private void relayoutWidgets() {
        int y = viewTop() - scrollOffset;
        for (Row row : rows) {
            for (AbstractWidget w : row.widgets()) {
                w.setY(y);
                w.visible = y >= viewTop() && y + row.height() <= viewBottom();
            }
            y += row.height();
        }
    }

    // ===== 滚动条 + 平滑滚动（照抄 ChatBubbleConfigScreen 机制） =====

    private int rTrackX() { return width - ChatScrollbar.WIDTH; }
    private int rTrackH() { return viewBottom() - viewTop(); }
    private int rTotalH() { return calcMaxScroll() + rTrackH(); }
    private int tTrackX() { return dividerX() - ChatScrollbar.WIDTH - 2; }
    private int tTrackH() { return viewBottom() - START_Y; }
    private int tTotalH() { return calcTreeMaxScroll() + tTrackH(); }

    private void startR(float target, int dur) {
        rAnimFrom = scrollOffset;
        rAnimTo = Mth.clamp(target, 0, calcMaxScroll());
        rAnimStart = net.minecraft.Util.getMillis();
        rAnimDur = dur;
        rAnimOn = true;
    }

    private void startT(float target, int dur) {
        tAnimFrom = treeScroll;
        tAnimTo = Mth.clamp(target, 0, calcTreeMaxScroll());
        tAnimStart = net.minecraft.Util.getMillis();
        tAnimDur = dur;
        tAnimOn = true;
    }

    private void tickAnims() {
        if (rAnimOn) {
            float t = Animation.progress(rAnimStart, rAnimDur, false);
            scrollOffset = Math.round(rAnimFrom + (rAnimTo - rAnimFrom) * t);
            if (t >= 1.0f) { scrollOffset = Math.round(rAnimTo); rAnimOn = false; }
        }
        if (tAnimOn) {
            float t = Animation.progress(tAnimStart, tAnimDur, false);
            treeScroll = Math.round(tAnimFrom + (tAnimTo - tAnimFrom) * t);
            if (t >= 1.0f) { treeScroll = Math.round(tAnimTo); tAnimOn = false; }
        }
        scrollOffset = Mth.clamp(scrollOffset, 0, calcMaxScroll());
        treeScroll = Mth.clamp(treeScroll, 0, calcTreeMaxScroll());
        relayoutWidgets();
    }

    private void drawBar(GuiGraphics g, int trackX, int top, int bot,
                         int totalH, int offset, int maxScroll,
                         double mx, double my, boolean dragging) {
        if (maxScroll <= 0) return;
        int trackH = bot - top;
        int th = ChatScrollbar.thumbHeight(trackH, totalH);
        int ty = ChatScrollbar.thumbY(top, trackH, th, offset, maxScroll);
        int w = ChatScrollbar.WIDTH;
        ColoredTextureRenderer.drawWithAlpha(g,
            UiTextureManager.rl(UiElement.SCROLLBAR_TRACK, ChatBubbleTheme.DARK),
            trackX, top, w, bot - top, 0x40 / 255f);
        int base = dragging ? 0xCC
            : ChatScrollbar.isHoveringThumb(mx, my, trackX, ty, th) ? 0xAA : 0x88;
        ColoredTextureRenderer.drawWithAlpha(g,
            UiTextureManager.rl(UiElement.SCROLLBAR_THUMB, ChatBubbleTheme.DARK),
            trackX, ty, w, th, base / 255f);
    }

    // ===== 交互 =====

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int w = ChatScrollbar.WIDTH;
        int rMax = calcMaxScroll();
        if (rMax > 0 && mouseX >= rTrackX() && mouseX < rTrackX() + w
                && mouseY >= viewTop() && mouseY < viewBottom()) {
            int th = ChatScrollbar.thumbHeight(rTrackH(), rTotalH());
            int ty = ChatScrollbar.thumbY(viewTop(), rTrackH(), th, scrollOffset, rMax);
            if (mouseY < ty) startR(scrollOffset - rTrackH(), 120);
            else if (mouseY > ty + th) startR(scrollOffset + rTrackH(), 120);
            else { rBarDrag = true; rBarDragY = (int) mouseY; rBarDragOff = scrollOffset; }
            return true;
        }
        int tMax = calcTreeMaxScroll();
        if (tMax > 0 && mouseX >= tTrackX() && mouseX < tTrackX() + w
                && mouseY >= START_Y && mouseY < viewBottom()) {
            int th = ChatScrollbar.thumbHeight(tTrackH(), tTotalH());
            int ty = ChatScrollbar.thumbY(START_Y, tTrackH(), th, treeScroll, tMax);
            if (mouseY < ty) startT(treeScroll - tTrackH(), 120);
            else if (mouseY > ty + th) startT(treeScroll + tTrackH(), 120);
            else { tBarDrag = true; tBarDragY = (int) mouseY; tBarDragOff = treeScroll; }
            return true;
        }
        if (button == 0) {
            int ly = START_Y - treeScroll;
            for (int i = 0; i < CAT_KEYS.length; i++) {
                if (mouseY >= ly && mouseY < ly + CAT_ROW_H && mouseX >= CAT_X && mouseX <= CAT_X + CAT_W) {
                    switchCategory(i);
                    return true;
                }
                ly += CAT_ROW_H;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (mouseX < dividerX()) {
            if (calcTreeMaxScroll() <= 0) return false;
            startT(treeScroll - (float) (scrollY * 20), 120);
            return true;
        }
        if (calcMaxScroll() <= 0) return false;
        startR(scrollOffset - (float) (scrollY * 20), 120);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (rBarDrag && calcMaxScroll() > 0) {
            int travel = rTrackH() - ChatScrollbar.thumbHeight(rTrackH(), rTotalH());
            if (travel > 0) {
                int d = (int) mouseY - rBarDragY;
                startR(rBarDragOff + (float) d * calcMaxScroll() / travel, 80);
            }
            return true;
        }
        if (tBarDrag && calcTreeMaxScroll() > 0) {
            int travel = tTrackH() - ChatScrollbar.thumbHeight(tTrackH(), tTotalH());
            if (travel > 0) {
                int d = (int) mouseY - tBarDragY;
                startT(tBarDragOff + (float) d * calcTreeMaxScroll() / travel, 80);
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        rBarDrag = false;
        tBarDrag = false;
        return super.mouseReleased(mouseX, mouseY, button);
    }

    // ===== 渲染 =====

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        g.blit(UiTextureManager.rl(UiElement.CONFIG_BG, ChatBubbleTheme.DARK),
            0, 0, width, height, 0f, 0f, 1, 1, 1, 1);
        tickAnims();
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, c().configTitle(), false);

        String tooltipKey = null;

        // 左侧分类树（照抄客户端：选中高亮 + 左侧竖条，裁剪到视口）
        g.enableScissor(CAT_X, START_Y, dividerX(), viewBottom());
        int ly = START_Y - treeScroll;
        for (int i = 0; i < CAT_KEYS.length; i++) {
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= CAT_X && mouseX <= CAT_X + CAT_W && mouseY >= ly && mouseY < ly + CAT_ROW_H;
            if (sel || hover)
                g.blit(UiTextureManager.rl(UiElement.HOVER_BG, ChatBubbleTheme.DARK),
                    CAT_X, ly, CAT_W, CAT_ROW_H, 0f, 0f, 1, 1, 1, 1);
            if (sel)
                g.fill(CAT_X, ly, CAT_X + 2, ly + CAT_ROW_H, c().configTitle());
            g.drawString(font, Component.translatable(CAT_KEYS[i]), CAT_X + 18, ly + (CAT_ROW_H - 8) / 2,
                sel ? c().configTitle() : c().configLabel(), false);
            ly += CAT_ROW_H;
        }
        g.disableScissor();
        drawBar(g, tTrackX(), START_Y, viewBottom(), tTotalH(), treeScroll, calcTreeMaxScroll(),
            mouseX, mouseY, tBarDrag);

        // 分类与选项区分隔线
        g.blit(UiTextureManager.rl(UiElement.DIVIDER, ChatBubbleTheme.DARK),
            dividerX(), START_Y - 6, 1, viewBottom() - (START_Y - 6), 0f, 0f, 1, 1, 1, 1);

        // 右区选项行，硬裁剪到视口；普通行 label 垂直居中对齐按钮（y+6），教程小行顶部对齐（y+2）
        g.enableScissor(optLabelX() - 4, viewTop(), width, viewBottom());
        int y = viewTop() - scrollOffset;
        for (Row row : rows) {
            if (row.title()) {
                // 分区标题：灰字左对齐 + 字右侧延伸一条细分隔线（同客户端配置界面）
                Component label = row.label();
                g.drawString(font, label, optLabelX(), y + 11, c().configLabel(), false);
                int lineX = optLabelX() + font.width(label) + 8;
                int lineEnd = optLabelX() + optAreaW() + 4;
                if (lineX < lineEnd)
                    g.blit(UiTextureManager.rl(UiElement.DIVIDER, ChatBubbleTheme.DARK),
                        lineX, y + 15, lineEnd - lineX, 1, 0f, 0f, 1, 1, 1, 1);
                y += row.height();
                continue;
            }
            if (!row.label().getString().isEmpty()) {
                int labelY = row.height() == ROW_H ? y + 6 : y + 2;
                g.drawString(font, row.label(), optLabelX(), labelY, c().configLabel(), false);
                if (row.tooltipKey() != null && y >= viewTop() && y + 20 <= viewBottom()
                    && mouseX >= optLabelX() - 4 && mouseX <= inputX() - 10 && mouseY >= y && mouseY <= y + 20)
                    tooltipKey = row.tooltipKey();
            }
            if (row.extraText() != null && !row.extraText().isEmpty()) {
                g.drawString(font, Component.literal(truncate(row.extraText(), rightAreaW())),
                    optLabelX(), y + 21, c().textSecondary(), false);
            }
            // 实时校验错误：行内控件下方红字（模板行），像素截断防溢出
            for (AbstractWidget w : row.widgets()) {
                if (w instanceof EditBox eb && boxErrors.containsKey(eb)) {
                    g.drawString(font, Component.literal(truncate(boxErrors.get(eb), rightAreaW())),
                        optLabelX(), y + 22, 0xFFFF4444, false);
                    break;
                }
            }
            // 生成失败提示：对齐在生成输入框所在行下方（与模板警告同风格），像素截断
            if (genError != null && row == genInputRow) {
                g.drawString(font, Component.literal(truncate(genError, rightAreaW())),
                    optLabelX(), y + 22, 0xFFFF4444, false);
            }
            y += row.height();
        }
        g.disableScissor();
        drawBar(g, rTrackX(), viewTop(), viewBottom(), rTotalH(), scrollOffset, calcMaxScroll(),
            mouseX, mouseY, rBarDrag);

        int changed = changeCount();
        doneBtn.visible = changed == 0;
        exitBtn.visible = changed > 0;
        saveBtn.visible = changed > 0;

        super.render(g, mouseX, mouseY, partialTick);

        if (changed > 0)
            g.drawString(font, Component.translatable("e33chat.config.changed", changed),
                width / 2 + 112, height - 26, c().configLabel(), false);

        if (error != null) {
            // 保存校验失败：底部固定红字（模板行/生成行的实时错误已在行下方各自显示），像素截断
            g.drawString(font, Component.literal(truncate(error, rightAreaW())),
                optLabelX(), viewBottom() - 12, 0xFFFF4444, false);
        }

        if (tooltipKey != null)
            g.renderTooltip(font, font.split(Component.translatable(tooltipKey + ".desc"), 190), mouseX, mouseY);
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // no-op：背景已在 render() 开头画一次（同客户端）
    }

    @Override
    public boolean isPauseScreen() { return true; }
}
