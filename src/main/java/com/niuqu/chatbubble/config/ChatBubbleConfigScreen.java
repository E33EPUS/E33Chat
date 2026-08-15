package com.niuqu.chatbubble.config;
import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.Animation;
import com.niuqu.chatbubble.render.AnimationStyle;
import com.niuqu.chatbubble.render.RoundRectRenderer;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractSliderButton;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ConfirmScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.neoforged.neoforge.common.ModConfigSpec;

public class ChatBubbleConfigScreen extends Screen {
    private final Screen lastScreen;

    private ChatBubbleTheme.Colors c() {
        return ChatBubbleTheme.DARK.colors();
    }

    private static final int ROW_H = 32;
    // 分区标题行高：与 ROW_H 对齐，使“标题行”和“选项行”垂直节奏一致、线上下留白对称
    private static final int HEADER_H = 32;
    private static final int START_Y = 40;
    private static final int PREVIEW_H = 44;
    private static final int PREVIEW_GAP = 8;
    private static final int CAT_X = 24;
    private static final int CAT_W = 96;
    private static final int CAT_ROW_H = 22;
    private static final int SUB_ROW_H = 18;
    private static final int INPUT_W = 90;
    private static final int ACCENT = 0xFF1E90FF;
    private static final String[] PALETTE = {"#FFFFFF", "#000000", "#FF5555", "#FFAA00", "#55FF55", "#5555FF", "#FF55FF", "#1E90FF"};
    private static final int PALETTE_W = PALETTE.length * 10 - 2;

    private int dividerX, optLabelX, inputX, previewX;
    private int selectedCat;
    // 当前 tab 内选中的分区索引；-1 = 显示该 tab 全部分区
    private int selectedSub = -1;
    private int scrollOffset;
    private int treeScroll;
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    // 左侧树折叠状态（cats 固定 5 个 tab）
    private final boolean[] expanded = {true, true, true, true, true};
    // 右侧选项 / 左侧树各一套平滑滚动(easeOutCubic 时间轴)+滚动条拖拽；
    // scrollOffset/treeScroll 是“当前显示值”，每帧由动画插值写回，wheel/拖拽只设目标
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

    private interface WidgetFactory {
        AbstractWidget create(int y);
    }

    // 一个选项行可生成多个控件（如 [编辑框][删除]），配合 rows 占多行
    private interface WidgetsFactory {
        List<AbstractWidget> create(int y);
    }

    private record Opt(String key, WidgetFactory factory, WidgetsFactory multiFactory,
                       int rows, Supplier<String> previewColor) {
        Opt(String key, WidgetFactory factory, Supplier<String> previewColor) {
            this(key, factory, null, 1, previewColor);
        }
        static Opt header(String key) { return new Opt(key, null, null, 1, null); }
        static Opt multi(String key, WidgetsFactory f, int rows) { return new Opt(key, null, f, rows, null); }
        boolean isHeader() { return factory == null && multiFactory == null; }
    }

    private record Cat(String key, List<Opt> opts) {}

    private List<Cat> cats;

    // 编辑模型：打开时快照所有配置项；退出回滚到快照，保存保留（doClose 显式落盘）
    private interface Tracked {
        boolean changed();
        void revert();
    }
    private <T> Tracked track(ModConfigSpec.ConfigValue<T> v) {
        T snapshot = v.get();
        return new Tracked() {
            @Override public boolean changed() { return !Objects.equals(v.get(), snapshot); }
            @Override public void revert() { v.set(snapshot); }
        };
    }
    private final List<Tracked> tracked = new ArrayList<>();
    private Button doneBtn, exitBtn, saveBtn;

    // “气泡与字体”分区在 chat tab 内的子分类序号（自适应 buildCats 顺序）
    private int bubbleFontSub() {
        int s = 0;
        for (Opt o : cats.get(0).opts()) {
            if (o.isHeader()) {
                if (o.key().equals("e33chat.config.section.bubble_font")) return s;
                s++;
            }
        }
        return -1;
    }

    // 预览带仅在 chat tab 的“气泡与字体”子分类显示
    private boolean showPreview() {
        return selectedCat == 0 && selectedSub == bubbleFontSub();
    }

    private void buildCats() {
        // 不缓存：屏蔽列表分区按当前名单动态生成行（删除/添加后 rebuild 重排），
        // 缓存会把行数定死在首次构建
        cats = new ArrayList<>();

        // 聊天框: panel / bubbles & text / message display
        List<Opt> chat = new ArrayList<>();
        chat.add(Opt.header("e33chat.config.section.panel"));
        chat.add(new Opt("e33chat.config.theme", this::mkThemeButton, null));
        chat.add(new Opt("e33chat.config.panel_width",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.PANEL_WIDTH.get()), 800, 1600, 4, ChatBubbleConfig.PANEL_WIDTH::set), null));
        chat.add(new Opt("e33chat.config.blur_enabled", y -> mkBoolButton(y, ChatBubbleConfig.BLUR_ENABLED), null));
        chat.add(new Opt("e33chat.config.panel_opacity",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.PANEL_OPACITY.get()), 0, 100, 3, ChatBubbleConfig.PANEL_OPACITY::set), null));
        chat.add(new Opt("e33chat.config.animation", y -> mkBoolButton(y, ChatBubbleConfig.ANIMATION_ENABLED), null));
        chat.add(new Opt("e33chat.config.panel_anim_style", this::mkPanelStyleButton, null));
        chat.add(new Opt("e33chat.config.popup_anim_style", this::mkPopupStyleButton, null));
        chat.add(new Opt("e33chat.config.message_anim_style", this::mkMessageStyleButton, null));
        chat.add(new Opt("e33chat.config.avatar_size",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.AVATAR_SIZE.get()), 12, 32, 2, ChatBubbleConfig.AVATAR_SIZE::set), null));
        chat.add(Opt.header("e33chat.config.section.bubble_font"));
        chat.add(new Opt("e33chat.config.bubble_corner_radius",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get()), 0, 10, 2, ChatBubbleConfig.BUBBLE_CORNER_RADIUS::set), null));
        chat.add(new Opt("e33chat.config.own_bubble_color",
            y -> mkHexBox(y, ChatBubbleConfig.OWN_BUBBLE_COLOR.get(), ChatBubbleConfig.OWN_BUBBLE_COLOR::set),
            ChatBubbleConfig.OWN_BUBBLE_COLOR::get));
        chat.add(new Opt("e33chat.config.other_bubble_color",
            y -> mkHexBox(y, ChatBubbleConfig.OTHER_BUBBLE_COLOR.get(), ChatBubbleConfig.OTHER_BUBBLE_COLOR::set),
            ChatBubbleConfig.OTHER_BUBBLE_COLOR::get));
        chat.add(new Opt("e33chat.config.own_text_color",
            y -> mkHexBox(y, ChatBubbleConfig.OWN_TEXT_COLOR.get(), ChatBubbleConfig.OWN_TEXT_COLOR::set),
            ChatBubbleConfig.OWN_TEXT_COLOR::get));
        chat.add(new Opt("e33chat.config.other_text_color",
            y -> mkHexBox(y, ChatBubbleConfig.OTHER_TEXT_COLOR.get(), ChatBubbleConfig.OTHER_TEXT_COLOR::set),
            ChatBubbleConfig.OTHER_TEXT_COLOR::get));
        chat.add(Opt.header("e33chat.config.section.msgdisplay"));
        chat.add(new Opt("e33chat.config.message_gap",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.MESSAGE_GAP.get()), 0, 12, 2, ChatBubbleConfig.MESSAGE_GAP::set), null));
        chat.add(new Opt("e33chat.config.enabled", y -> mkBoolButton(y, ChatBubbleConfig.ENABLED), null));
        chat.add(new Opt("e33chat.config.system_chat_as_bubble", y -> mkBoolButton(y, ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE), null));
        chat.add(new Opt("e33chat.config.anti_spam", y -> mkBoolButton(y, ChatBubbleConfig.ANTI_SPAM), null));
        chat.add(new Opt("e33chat.config.receive_images", y -> mkBoolButton(y, ChatBubbleConfig.RECEIVE_IMAGES), null));
        chat.add(new Opt("e33chat.config.time_separator", this::mkTimeSepButton, null));
        chat.add(new Opt("e33chat.config.color_codes", y -> mkBoolButton(y, ChatBubbleConfig.COLOR_CODES), null));
        chat.add(Opt.header("e33chat.config.section.blocked"));
        // 逐行编辑：每个屏蔽名一行 [编辑框][✕]，下方 [添加玩家] 按钮（学服务端模板编辑交互）
        List<String> blocked = new ArrayList<>(ChatBubbleConfig.BLOCKED_PLAYERS.get());
        for (int i = 0; i < blocked.size(); i++) {
            int idx = i;
            chat.add(Opt.multi("e33chat.config.blocked_players", y -> {
                EditBox box = new EditBox(font, inputX, y, INPUT_W - 24, 20, Component.literal(""));
                box.setValue(blocked.get(idx));
                box.setMaxLength(32);
                box.setResponder(s -> {
                    if (idx < blocked.size() && !s.equals(blocked.get(idx))) {
                        blocked.set(idx, s.trim());
                        ChatBubbleConfig.BLOCKED_PLAYERS.set(blocked);
                        ChatMessageStore.purgeBlocked(blocked);
                    }
                });
                Button rm = Button.builder(Component.literal("✕"), b -> {
                    blocked.remove(idx);
                    ChatBubbleConfig.BLOCKED_PLAYERS.set(blocked);
                    ChatMessageStore.purgeBlocked(blocked);
                    rebuild();
                }).bounds(inputX + INPUT_W - 22, y, 20, 20).build();
                return List.of(box, rm);
            }, 1));
        }
        chat.add(Opt.multi("e33chat.config.blocked_add", y -> {
            Button add = Button.builder(Component.translatable("e33chat.config.blocked_add"), b -> {
                blocked.add("");
                ChatBubbleConfig.BLOCKED_PLAYERS.set(blocked);
                rebuild();
            }).bounds(inputX, y, 72, 20).build();
            return List.of(add);
        }, 1));
        cats.add(new Cat("e33chat.config.cat.chat", chat));

        // HUD: icon / preview / strong hint
        List<Opt> hud = new ArrayList<>();
        hud.add(Opt.header("e33chat.config.section.icon"));
        hud.add(new Opt("e33chat.config.red_dot", y -> mkBoolButton(y, ChatBubbleConfig.RED_DOT_ENABLED), null));
        hud.add(new Opt("e33chat.config.hide_chat_icon", y -> mkBoolButton(y, ChatBubbleConfig.HIDE_CHAT_ICON), null));
        cats.add(new Cat("e33chat.config.cat.hud", hud));

        // 通知: 按消息类型分（@ / 私聊 / 系统）+ 横幅通用 + 音效
        List<Opt> notify = new ArrayList<>();
        notify.add(Opt.header("e33chat.config.section.mention"));
        notify.add(new Opt("e33chat.config.mention_banner_enabled", y -> mkBoolButton(y, ChatBubbleConfig.MENTION_BANNER_ENABLED), null));
        notify.add(new Opt("e33chat.config.mention_sound_enabled", y -> mkBoolButton(y, ChatBubbleConfig.MENTION_SOUND_ENABLED), null));
        notify.add(new Opt("e33chat.config.mention_require_at", y -> mkBoolButton(y, ChatBubbleConfig.MENTION_REQUIRE_AT), null));
        notify.add(Opt.header("e33chat.config.section.whisper"));
        notify.add(new Opt("e33chat.config.mention_whisper_banner", y -> mkBoolButton(y, ChatBubbleConfig.MENTION_WHISPER_BANNER), null));
        notify.add(new Opt("e33chat.config.sound_whisper", y -> mkBoolButton(y, ChatBubbleConfig.SOUND_WHISPER), null));
        notify.add(Opt.header("e33chat.config.section.system"));
        notify.add(new Opt("e33chat.config.system_banner_enabled", y -> mkBoolButton(y, ChatBubbleConfig.SYSTEM_BANNER_ENABLED), null));
        notify.add(new Opt("e33chat.config.sound_system", y -> mkBoolButton(y, ChatBubbleConfig.SOUND_SYSTEM), null));
        notify.add(Opt.header("e33chat.config.section.banner"));
        notify.add(new Opt("e33chat.config.mention_banner_duration",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.MENTION_BANNER_DURATION.get()), 2, 10, 2, ChatBubbleConfig.MENTION_BANNER_DURATION::set), null));
        notify.add(new Opt("e33chat.config.banner_corner_radius",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.BANNER_CORNER_RADIUS.get()), 0, 10, 2, ChatBubbleConfig.BANNER_CORNER_RADIUS::set), null));
        notify.add(new Opt("e33chat.config.banner_offset_x",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.BANNER_OFFSET_X.get()), -1000, 1000, 2, ChatBubbleConfig.BANNER_OFFSET_X::set), null));
        notify.add(new Opt("e33chat.config.banner_offset_y",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.BANNER_OFFSET_Y.get()), -1000, 1000, 2, ChatBubbleConfig.BANNER_OFFSET_Y::set), null));
        notify.add(new Opt("e33chat.config.banner_anim_style", this::mkBannerStyleButton, null));
        notify.add(Opt.header("e33chat.config.section.sound"));
        notify.add(new Opt("e33chat.config.sound_volume",
            y -> mkIntSlider(y, ChatBubbleConfig.SOUND_VOLUME, 0, 100), null));
        notify.add(new Opt("e33chat.config.sound_public", y -> mkBoolButton(y, ChatBubbleConfig.SOUND_PUBLIC), null));
        cats.add(new Cat("e33chat.config.cat.notify", notify));

        // 侧边栏: player list
        List<Opt> sidebar = new ArrayList<>();
        sidebar.add(Opt.header("e33chat.config.section.playerlist"));
        sidebar.add(new Opt("e33chat.config.sidebar_hide_patterns",
            y -> mkPatternBox(y,
                new ArrayList<>(ChatBubbleConfig.SIDEBAR_HIDE_PATTERNS.get()),
                parts -> ChatBubbleConfig.SIDEBAR_HIDE_PATTERNS.set(new ArrayList<>(parts))),
            null));
        cats.add(new Cat("e33chat.config.cat.sidebar", sidebar));

        // 高级: history & input / debug & testing
        List<Opt> advanced = new ArrayList<>();
        advanced.add(Opt.header("e33chat.config.section.history"));
        advanced.add(new Opt("e33chat.config.chat_history", y -> mkBoolButton(y, ChatBubbleConfig.CHAT_HISTORY_ENABLED), null));
        advanced.add(new Opt("e33chat.config.history_retention", y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.HISTORY_RETENTION_DAYS.get()), 0, 365, 3, ChatBubbleConfig.HISTORY_RETENTION_DAYS::set), null));
        advanced.add(new Opt("e33chat.config.preserve_input", y -> mkBoolButton(y, ChatBubbleConfig.PRESERVE_INPUT), null));
        advanced.add(Opt.header("e33chat.config.section.upload"));
        advanced.add(new Opt("e33chat.config.upload_url", y -> {
            EditBox box = new EditBox(font, inputX, y, INPUT_W, 20, Component.literal(""));
            box.setValue(ChatBubbleConfig.UPLOAD_URL.get());
            box.setMaxLength(512);
            box.setResponder(v -> ChatBubbleConfig.UPLOAD_URL.set(v));
            return box;
        }, null));
        advanced.add(Opt.header("e33chat.config.section.debug"));
        advanced.add(new Opt("e33chat.config.debug_log", y -> mkBoolButton(y, ChatBubbleConfig.DEBUG_LOG), null));
        advanced.add(new Opt("e33chat.config.own_mention_notify", y -> mkBoolButton(y, ChatBubbleConfig.OWN_MENTION_NOTIFY), null));
        advanced.add(new Opt("e33chat.config.own_quote_notify", y -> mkBoolButton(y, ChatBubbleConfig.OWN_QUOTE_NOTIFY), null));
        advanced.add(new Opt("e33chat.config.own_whisper_notify", y -> mkBoolButton(y, ChatBubbleConfig.OWN_WHISPER_NOTIFY), null));
        cats.add(new Cat("e33chat.config.cat.advanced", advanced));
    }

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(Component.translatable("e33chat.config.title"));
        this.lastScreen = lastScreen;
        snapshotAll();
    }

    // 快照全部可编辑配置项（常用语除外，那在游戏内面板管理）
    private void snapshotAll() {
        tracked.add(track(ChatBubbleConfig.THEME));
        tracked.add(track(ChatBubbleConfig.PANEL_ANIM_STYLE));
        tracked.add(track(ChatBubbleConfig.BANNER_ANIM_STYLE));
        tracked.add(track(ChatBubbleConfig.POPUP_ANIM_STYLE));
        tracked.add(track(ChatBubbleConfig.MESSAGE_ANIM_STYLE));
        tracked.add(track(ChatBubbleConfig.ENABLED));
        tracked.add(track(ChatBubbleConfig.RED_DOT_ENABLED));
        tracked.add(track(ChatBubbleConfig.HIDE_CHAT_ICON));
        tracked.add(track(ChatBubbleConfig.ANIMATION_ENABLED));
        tracked.add(track(ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE));
        tracked.add(track(ChatBubbleConfig.ANTI_SPAM));
        tracked.add(track(ChatBubbleConfig.RECEIVE_IMAGES));
        tracked.add(track(ChatBubbleConfig.UPLOAD_URL));
        tracked.add(track(ChatBubbleConfig.UPLOAD_FIELD));
        tracked.add(track(ChatBubbleConfig.UPLOAD_EXTRA));
        tracked.add(track(ChatBubbleConfig.UPLOAD_RESPONSE));
        tracked.add(track(ChatBubbleConfig.CHAT_HISTORY_ENABLED));
        tracked.add(track(ChatBubbleConfig.HISTORY_RETENTION_DAYS));
        tracked.add(track(ChatBubbleConfig.TIME_SEPARATOR_MINUTES));
        tracked.add(track(ChatBubbleConfig.PRESERVE_INPUT));
        tracked.add(track(ChatBubbleConfig.COLOR_CODES));
        tracked.add(track(ChatBubbleConfig.SIDEBAR_HIDE_PATTERNS));
        tracked.add(track(ChatBubbleConfig.BLOCKED_PLAYERS));
        tracked.add(track(ChatBubbleConfig.OWN_BUBBLE_COLOR));
        tracked.add(track(ChatBubbleConfig.OTHER_BUBBLE_COLOR));
        tracked.add(track(ChatBubbleConfig.BUBBLE_CORNER_RADIUS));
        tracked.add(track(ChatBubbleConfig.OWN_TEXT_COLOR));
        tracked.add(track(ChatBubbleConfig.OTHER_TEXT_COLOR));
        tracked.add(track(ChatBubbleConfig.PANEL_WIDTH));
        tracked.add(track(ChatBubbleConfig.BLUR_ENABLED));
        tracked.add(track(ChatBubbleConfig.PANEL_OPACITY));
        tracked.add(track(ChatBubbleConfig.DEBUG_LOG));
        tracked.add(track(ChatBubbleConfig.SOUND_SYSTEM));
        tracked.add(track(ChatBubbleConfig.SOUND_WHISPER));
        tracked.add(track(ChatBubbleConfig.SOUND_PUBLIC));
        tracked.add(track(ChatBubbleConfig.SOUND_VOLUME));
        tracked.add(track(ChatBubbleConfig.MENTION_BANNER_ENABLED));
        tracked.add(track(ChatBubbleConfig.SYSTEM_BANNER_ENABLED));
        tracked.add(track(ChatBubbleConfig.MENTION_BANNER_DURATION));
        tracked.add(track(ChatBubbleConfig.MENTION_SOUND_ENABLED));
        tracked.add(track(ChatBubbleConfig.MENTION_REQUIRE_AT));
        tracked.add(track(ChatBubbleConfig.MENTION_WHISPER_BANNER));
        tracked.add(track(ChatBubbleConfig.OWN_MENTION_NOTIFY));
        tracked.add(track(ChatBubbleConfig.OWN_QUOTE_NOTIFY));
        tracked.add(track(ChatBubbleConfig.OWN_WHISPER_NOTIFY));
        tracked.add(track(ChatBubbleConfig.BANNER_CORNER_RADIUS));
    }

    @Override
    protected void init() {
        buildCats();
        scrollWidgets.clear();

        dividerX = CAT_X + CAT_W + 12;
        optLabelX = dividerX + 14;
        previewX = width - 26;
        inputX = previewX - 8 - INPUT_W;

        scrollOffset = Mth.clamp(scrollOffset, 0, calcMaxScroll());
        treeScroll = Mth.clamp(treeScroll, 0, calcTreeMaxScroll());

        int y = viewTop() - scrollOffset;
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) { y += HEADER_H; continue; }
            if (opt.multiFactory() != null) {
                for (AbstractWidget w : opt.multiFactory().create(y)) {
                    w.visible = y >= viewTop() && y + 20 <= viewBottom();
                    scrollWidgets.add(addRenderableWidget(w));
                }
                y += ROW_H * opt.rows();
                continue;
            }
            AbstractWidget w = opt.factory().create(y);
            w.visible = y >= viewTop() && y + 20 <= viewBottom();
            scrollWidgets.add(addRenderableWidget(w));
            y += ROW_H;
        }

        doneBtn = addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> doClose())
            .bounds(width / 2 - 100, height - 32, 200, 20).build());
        exitBtn = addRenderableWidget(Button.builder(Component.translatable("e33chat.config.exit"), btn -> doExit())
            .bounds(width / 2 - 104, height - 32, 100, 20).build());
        saveBtn = addRenderableWidget(Button.builder(Component.translatable("e33chat.config.save"), btn -> doClose())
            .bounds(width / 2 + 4, height - 32, 100, 20).build());
    }

    // 切换 tab：看该 tab 全部分区
    private void switchCategory(int idx) {
        boolean same = idx == selectedCat && selectedSub == -1;
        selectedCat = idx;
        selectedSub = -1;
        expanded[idx] = true;
        if (!same) rebuild();
    }

    // 选中某个子分类：右侧只显示该分区的选项
    private void selectSub(int catIdx, int sub) {
        boolean same = catIdx == selectedCat && sub == selectedSub;
        selectedCat = catIdx;
        selectedSub = sub;
        expanded[catIdx] = true;
        if (!same) rebuild();
    }

    private void rebuild() {
        scrollOffset = 0;
        setFocused(null);
        clearWidgets();
        init();
    }

    // 当前右侧要显示的选项列表：selectedSub<0 全部（含分区头），否则只取目标分区的选项（不含头，避免与左树重复）
    private List<Opt> visibleOpts() {
        List<Opt> all = cats.get(selectedCat).opts();
        if (selectedSub < 0) return all;
        List<Opt> out = new ArrayList<>();
        int seen = 0;
        boolean in = false;
        for (Opt o : all) {
            if (o.isHeader()) {
                if (seen == selectedSub) { in = true; seen++; continue; }
                if (in) break;
                seen++;
                continue;
            }
            if (in) out.add(o);
        }
        return out;
    }

    private Button mkThemeButton(int y) {
        var themes = ChatBubbleTheme.values();
        return Button.builder(
            Component.translatable("e33chat.theme." + ChatBubbleConfig.THEME.get().name().toLowerCase()),
            btn -> {
                int next = (ChatBubbleConfig.THEME.get().ordinal() + 1) % themes.length;
                ChatBubbleConfig.THEME.set(themes[next]);
                btn.setMessage(Component.translatable("e33chat.theme." + themes[next].name().toLowerCase()));
            }
        ).bounds(inputX, y, INPUT_W, 20).build();
    }

    // Animation style cycle buttons (SLIDE → FADE → ZOOM → NONE → ...)
    private Button mkStyleButton(int y, ModConfigSpec.EnumValue<AnimationStyle> cfg) {
        AnimationStyle[] values = AnimationStyle.values();
        return Button.builder(
            Component.translatable("e33chat.config.anim_style." + cfg.get().name().toLowerCase()),
            btn -> {
                int next = (cfg.get().ordinal() + 1) % values.length;
                cfg.set(values[next]);
                btn.setMessage(Component.translatable("e33chat.config.anim_style." + values[next].name().toLowerCase()));
            }
        ).bounds(inputX, y, INPUT_W, 20).build();
    }

    private Button mkPanelStyleButton(int y) { return mkStyleButton(y, ChatBubbleConfig.PANEL_ANIM_STYLE); }
    private Button mkBannerStyleButton(int y) { return mkStyleButton(y, ChatBubbleConfig.BANNER_ANIM_STYLE); }
    private Button mkPopupStyleButton(int y) { return mkStyleButton(y, ChatBubbleConfig.POPUP_ANIM_STYLE); }
    private Button mkMessageStyleButton(int y) { return mkStyleButton(y, ChatBubbleConfig.MESSAGE_ANIM_STYLE); }

    private Button mkBoolButton(int y, ModConfigSpec.BooleanValue cfg) {
        boolean v = cfg.get();
        return Button.builder(
            v ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF,
            btn -> {
                boolean nv = !cfg.get();
                cfg.set(nv);
                btn.setMessage(nv ? CommonComponents.OPTION_ON : CommonComponents.OPTION_OFF);
            }
        ).bounds(inputX, y, INPUT_W, 20).build();
    }

    private IntSlider mkIntSlider(int y, ModConfigSpec.IntValue cfg, int min, int max) {
        return new IntSlider(inputX, y, INPUT_W, 20, cfg, min, max);
    }

    // 整数滑条：复用原版灰滑条渲染，拖拽/键盘映射回 [min,max] 写回配置
    private static class IntSlider extends AbstractSliderButton {
        private final ModConfigSpec.IntValue cfg;
        private final int min, max;
        IntSlider(int x, int y, int w, int h, ModConfigSpec.IntValue cfg, int min, int max) {
            super(x, y, w, h, Component.literal(String.valueOf(cfg.get())),
                (cfg.get() - min) / (double) (max - min));
            this.cfg = cfg;
            this.min = min;
            this.max = max;
        }
        @Override
        protected void applyValue() {
            cfg.set((int) Math.round(min + value * (max - min)));
        }
        @Override
        protected void updateMessage() {
            setMessage(Component.literal(String.valueOf(cfg.get())));
        }
    }

    private static final int[] TIME_SEP_PRESETS = {1, 5, 10, 15, 30, 0};

    private Button mkTimeSepButton(int y) {
        int cur = ChatBubbleConfig.TIME_SEPARATOR_MINUTES.get();
        String label = cur == 0 ? Component.translatable("e33chat.config.time_separator.disable").getString()
            : cur + " " + Component.translatable("e33chat.config.time_separator.minute").getString();
        return Button.builder(Component.literal(label), btn -> {
            int idx = -1;
            for (int i = 0; i < TIME_SEP_PRESETS.length; i++) {
                if (TIME_SEP_PRESETS[i] == ChatBubbleConfig.TIME_SEPARATOR_MINUTES.get()) {
                    idx = i; break;
                }
            }
            int next = TIME_SEP_PRESETS[(idx + 1) % TIME_SEP_PRESETS.length];
            ChatBubbleConfig.TIME_SEPARATOR_MINUTES.set(next);
            String nl = next == 0 ? Component.translatable("e33chat.config.time_separator.disable").getString()
                : next + " " + Component.translatable("e33chat.config.time_separator.minute").getString();
            btn.setMessage(Component.literal(nl));
        }).bounds(inputX, y, INPUT_W, 20).build();
    }

    private EditBox mkHexBox(int y, String initial, java.util.function.Consumer<String> onChange) {
        EditBox box = new EditBox(font, inputX, y, INPUT_W, 20, Component.literal(""));
        box.setValue(initial);
        box.setMaxLength(7);
        box.setResponder(s -> {
            if (!s.matches("#?[0-9a-fA-F]{0,6}")) return;
            if (s.length() == 6 && !s.startsWith("#")) {
                box.setValue("#" + s);
                onChange.accept("#" + s);
            } else if (s.length() == 7) {
                onChange.accept(s);
            }
        });
        return box;
    }

    private EditBox mkIntBox(int y, String initial, int min, int max, int maxLen, java.util.function.Consumer<Integer> onChange) {
        EditBox box = new EditBox(font, inputX, y, INPUT_W, 20, Component.literal(""));
        box.setValue(initial);
        box.setMaxLength(maxLen);
        box.setResponder(s -> {
            if (!s.matches("\\d*")) return;
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) onChange.accept(v);
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    private EditBox mkPatternBox(int y, List<String> initial, java.util.function.Consumer<List<String>> onChange) {
        EditBox box = new EditBox(font, inputX, y, INPUT_W, 20, Component.literal(""));
        box.setValue(String.join(", ", initial));
        box.setMaxLength(200);
        box.setResponder(s -> {
            List<String> parts = new ArrayList<>();
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) parts.add(trimmed);
            }
            onChange.accept(parts);
        });
        return box;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // 1.21.1 的 GuiGraphics 是 batch 缓冲：fill/drawString 先攒着，flush 时按入队序上屏。
        // super.render 第一行会调 renderBackground——若那里画半透明黑，它会排在我们手画文字
        // 之后、按钮之前上屏，于是文字被这块黑盖暗、按钮却亮（Forge 1.20.1 的 GuiGraphics
        // 提交时机不同故不裂）。故 renderBackground 置空、背景改在此处只画一次，super 那次空转。
        // CONFIG_BG 烘焙为 75% 不透明（0xC0 alpha），blit 无 alpha 顶点会丢 alpha 画成
        // 不透明灰块——走带 alpha 顶点的绘制恢复半透明，世界能透出来
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONFIG_BG, ChatBubbleTheme.DARK),
            0, 0, width, height, 0xC0 / 255f);
        tickAnims();  // 先推进平滑动画+同步控件 y，再画（下方绘制循环依赖更新后的 offset）
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, c().configTitle(), false);

        String tooltipKey = null;

        // 左侧标签树：可滚动 + 裁剪到视口，避免全展开时挤出屏幕
        g.enableScissor(CAT_X, START_Y, dividerX, viewBottom());
        int ly = START_Y - treeScroll;
        for (int i = 0; i < cats.size(); i++) {
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= CAT_X && mouseX <= CAT_X + CAT_W && mouseY >= ly && mouseY < ly + CAT_ROW_H;
            if (sel || hover)
                g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG, ChatBubbleTheme.DARK),
                    CAT_X, ly, CAT_W, CAT_ROW_H, 0f, 0f, 1, 1, 1, 1);
            if (sel)
                g.fill(CAT_X, ly, CAT_X + 2, ly + CAT_ROW_H, c().configTitle());
            drawTriangle(g, CAT_X + 6, ly + (CAT_ROW_H - 5) / 2, expanded[i],
                sel ? c().configTitle() : c().configLabel());
            g.drawString(font, Component.translatable(cats.get(i).key()), CAT_X + 18, ly + (CAT_ROW_H - 8) / 2,
                sel ? c().configTitle() : c().configLabel(), false);
            ly += CAT_ROW_H;
            if (expanded[i]) {
                int sub = 0;
                for (Opt o : cats.get(i).opts()) {
                    if (!o.isHeader()) continue;
                    boolean selSub = i == selectedCat && sub == selectedSub;
                    boolean sh = mouseX >= CAT_X + 14 && mouseX <= CAT_X + CAT_W && mouseY >= ly && mouseY < ly + SUB_ROW_H;
                    if (selSub || sh)
                        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG, ChatBubbleTheme.DARK),
                            CAT_X + 14, ly, CAT_W - 14, SUB_ROW_H, 0f, 0f, 1, 1, 1, 1);
                    if (selSub)
                        g.fill(CAT_X + 14, ly, CAT_X + 16, ly + SUB_ROW_H, c().configTitle());
                    g.drawString(font, Component.translatable(o.key()), CAT_X + 24, ly + (SUB_ROW_H - 8) / 2,
                        (selSub || sh) ? c().configTitle() : c().configLabel(), false);
                    sub++;
                    ly += SUB_ROW_H;
                }
            }
        }
        g.disableScissor();
        drawBar(g, tTrackX(), START_Y, viewBottom(), tTotalH(), treeScroll, calcTreeMaxScroll(), mouseX, mouseY, tBarDrag);

        // Divider between categories and options
        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
            dividerX, START_Y - 6, 1, viewBottom() - (START_Y - 6), 0f, 0f, 1, 1, 1, 1);

        // 气泡预览带：仅 chat tab 的“气泡与字体”子分类显示，气泡用当前圆角实时渲染
        if (showPreview()) drawBubblePreview(g);

        // Option rows hard-clipped to the viewport (scissor cuts anything past the bounds)
        g.enableScissor(optLabelX - 4, viewTop(), width, viewBottom());
        int y = viewTop() - scrollOffset;
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) {
                // 分区标题：灰字左对齐 + 字右侧延伸一条细分隔线；字与线在行内垂直居中，上下留白对称
                Component label = Component.translatable(opt.key());
                g.drawString(font, label, optLabelX, y + 11, c().configLabel(), false);
                int lineX = optLabelX + font.width(label) + 8;
                int lineEnd = optLabelX + optAreaW() + 4;
                if (lineX < lineEnd)
            g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
                lineX, y + 15, lineEnd - lineX, 1, 0f, 0f, 1, 1, 1, 1);
                y += HEADER_H;
                continue;
            }
            g.drawString(font, Component.translatable(opt.key()), optLabelX, y + 6, c().configLabel(), false);
            if (opt.previewColor() != null) {
                drawPreview(g, y + 3, opt.previewColor().get());
                int px = paletteX();
                for (int i = 0; i < PALETTE.length; i++) {
                    int bx = px + i * 10, by = y + 12;
                    g.fill(bx, by, bx + 8, by + 8, c().iconHover());
                    g.fill(bx + 1, by + 1, bx + 7, by + 7, ChatBubbleConfig.parseHexColor(PALETTE[i], 0xFF000000));
                }
            }
            if (y >= viewTop() && y + 20 <= viewBottom()
                && mouseX >= optLabelX - 4 && mouseX <= inputX - 10 && mouseY >= y && mouseY <= y + 20)
                tooltipKey = opt.key() + ".desc";
            y += ROW_H;
        }
        g.disableScissor();
        drawBar(g, rTrackX(), viewTop(), viewBottom(), rTotalH(), scrollOffset, calcMaxScroll(), mouseX, mouseY, rBarDrag);

        int changed = changeCount();
        doneBtn.visible = changed == 0;
        exitBtn.visible = changed > 0;
        saveBtn.visible = changed > 0;

        super.render(g, mouseX, mouseY, partialTick);

        if (changed > 0)
            g.drawString(font, Component.translatable("e33chat.config.changed", changed),
                width / 2 + 112, height - 26, c().configLabel(), false);

        if (tooltipKey != null)
            g.renderTooltip(font, font.split(Component.translatable(tooltipKey), 190), mouseX, mouseY);
    }

    // 画两条示例气泡（他人左/灰、自己右/蓝），圆角实时读配置（0=直角自动 fallback）
    private void drawBubblePreview(GuiGraphics g) {
        int top = START_Y;
        int other = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_BUBBLE_COLOR.get(), 0xFF4A4A4A);
        int own = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_BUBBLE_COLOR.get(), ACCENT);
        int otherT = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR.get(), 0xFFFFFFFF);
        int ownT = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR.get(), 0xFFFFFFFF);
        float rad = ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get();
        Component otherMsg = Component.translatable("e33chat.config.preview.sample_other");
        Component ownMsg = Component.translatable("e33chat.config.preview.sample_own");
        int maxW = (optAreaW() - 8) / 2;
        int ow = Math.min(font.width(otherMsg) + 8, maxW);
        RoundRectRenderer.fill(g, optLabelX, top + 4, optLabelX + ow, top + 18, rad, other);
        g.drawString(font, otherMsg, optLabelX + 4, top + 7, otherT, false);
        int mw = Math.min(font.width(ownMsg) + 8, maxW);
        int mx = optLabelX + optAreaW() - mw;
        RoundRectRenderer.fill(g, mx, top + 22, mx + mw, top + 36, rad, own);
        g.drawString(font, ownMsg, mx + 4, top + 25, ownT, false);
        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
            optLabelX - 4, top + PREVIEW_H - 1, optAreaW() + 8, 1, 0f, 0f, 1, 1, 1, 1);
    }

    private void drawPreview(GuiGraphics g, int y, String hex) {
        int color = ChatBubbleConfig.parseHexColor(hex, 0xFF000000);
        g.fill(previewX, y, previewX + 14, y + 14, c().iconHover());
        g.fill(previewX + 1, y + 1, previewX + 13, y + 13, color);
    }

    // 画折叠/展开小三角：down=true 下三角（展开），否则右三角（折叠）
    private void drawTriangle(GuiGraphics g, int x, int y, boolean down, int color) {
        if (down) {
            g.fill(x, y, x + 5, y + 1, color);
            g.fill(x + 1, y + 1, x + 4, y + 2, color);
            g.fill(x + 2, y + 2, x + 3, y + 3, color);
        } else {
            g.fill(x, y, x + 1, y + 1, color);
            g.fill(x, y + 1, x + 2, y + 2, color);
            g.fill(x, y + 2, x + 3, y + 3, color);
            g.fill(x, y + 3, x + 2, y + 4, color);
            g.fill(x, y + 4, x + 1, y + 5, color);
        }
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // 滚动条命中优先于树/色板：拖 thumb 或点轨道翻页（两区 x 不与其它点击区重叠）
        int w = com.niuqu.chatbubble.render.ChatScrollbar.WIDTH;
        int rMax = calcMaxScroll();
        if (rMax > 0 && mouseX >= rTrackX() && mouseX < rTrackX() + w
                && mouseY >= viewTop() && mouseY < viewBottom()) {
            int th = com.niuqu.chatbubble.render.ChatScrollbar.thumbHeight(rTrackH(), rTotalH());
            int ty = com.niuqu.chatbubble.render.ChatScrollbar.thumbY(viewTop(), rTrackH(), th, scrollOffset, rMax);
            if (mouseY < ty) startR(scrollOffset - rTrackH(), 120);
            else if (mouseY > ty + th) startR(scrollOffset + rTrackH(), 120);
            else { rBarDrag = true; rBarDragY = (int) mouseY; rBarDragOff = scrollOffset; }
            return true;
        }
        int tMax = calcTreeMaxScroll();
        if (tMax > 0 && mouseX >= tTrackX() && mouseX < tTrackX() + w
                && mouseY >= START_Y && mouseY < viewBottom()) {
            int th = com.niuqu.chatbubble.render.ChatScrollbar.thumbHeight(tTrackH(), tTotalH());
            int ty = com.niuqu.chatbubble.render.ChatScrollbar.thumbY(START_Y, tTrackH(), th, treeScroll, tMax);
            if (mouseY < ty) startT(treeScroll - tTrackH(), 120);
            else if (mouseY > ty + th) startT(treeScroll + tTrackH(), 120);
            else { tBarDrag = true; tBarDragY = (int) mouseY; tBarDragOff = treeScroll; }
            return true;
        }
        if (button == 0) {
            int ly = START_Y - treeScroll;
            for (int i = 0; i < cats.size(); i++) {
                if (mouseY >= ly && mouseY < ly + CAT_ROW_H && mouseX >= CAT_X && mouseX <= CAT_X + CAT_W) {
                    if (mouseX < CAT_X + 16) {
                        expanded[i] = !expanded[i];   // 箭头区：折叠/展开
                    } else {
                        switchCategory(i);            // 标签区：看该 tab 全部
                    }
                    return true;
                }
                ly += CAT_ROW_H;
                if (expanded[i]) {
                    int sub = 0;
                    for (Opt o : cats.get(i).opts()) {
                        if (!o.isHeader()) continue;
                        if (mouseY >= ly && mouseY < ly + SUB_ROW_H && mouseX >= CAT_X + 14 && mouseX <= CAT_X + CAT_W) {
                            selectSub(i, sub);        // 子分类：右侧只显示该分区
                            return true;
                        }
                        sub++;
                        ly += SUB_ROW_H;
                    }
                }
            }

            // 颜色行的预设色板点击：填入 hex 并同步该行输入框
            int px = paletteX();
            if (mouseX >= px && mouseX < px + PALETTE_W) {
                int y = viewTop() - scrollOffset;
                int wi = 0;
                for (Opt opt : visibleOpts()) {
                    if (opt.isHeader()) { y += HEADER_H; continue; }
                    if (opt.previewColor() != null && mouseY >= y + 12 && mouseY < y + 20) {
                        int idx = Mth.clamp((int) (mouseX - px) / 10, 0, PALETTE.length - 1);
                        String hex = PALETTE[idx];
                        ModConfigSpec.ConfigValue<String> cv = colorConfigOf(opt.key());
                        if (cv != null) cv.set(hex);
                        if (wi < scrollWidgets.size() && scrollWidgets.get(wi) instanceof EditBox eb)
                            eb.setValue(hex);
                        return true;
                    }
                    wi++;
                    y += ROW_H;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    private ModConfigSpec.ConfigValue<String> colorConfigOf(String key) {
        return switch (key) {
            case "e33chat.config.own_bubble_color" -> ChatBubbleConfig.OWN_BUBBLE_COLOR;
            case "e33chat.config.other_bubble_color" -> ChatBubbleConfig.OTHER_BUBBLE_COLOR;
            case "e33chat.config.own_text_color" -> ChatBubbleConfig.OWN_TEXT_COLOR;
            case "e33chat.config.other_text_color" -> ChatBubbleConfig.OTHER_TEXT_COLOR;
            default -> null;
        };
    }

    private int changeCount() {
        int n = 0;
        for (Tracked t : tracked) if (t.changed()) n++;
        return n;
    }

    private void revertAll() {
        for (Tracked t : tracked) t.revert();
    }

    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // no-op：背景已在 render() 开头画一次。这里若再 fill，会被 super.render 排到手画文字
        // 之后上屏而把文字盖暗（见 render() 注释）。
    }

    private int optAreaW() { return previewX - optLabelX - 4; }

    // 预设色板的左缘：紧贴 hex 输入框左侧
    private int paletteX() { return inputX - 8 - PALETTE_W; }

    // 选项滚动区的顶：预览带显示时让出顶部 + 间距，否则从标题下方开始
    private int viewTop() { return showPreview() ? START_Y + PREVIEW_H + PREVIEW_GAP : START_Y; }

    // 选项滚动区的下边界：按钮栏（height-32 起）上方留 8px
    private int viewBottom() { return height - 40; }

    private int calcMaxScroll() {
        int total = 0;
        for (Opt opt : visibleOpts())
            total += opt.isHeader() ? HEADER_H : ROW_H * opt.rows();
        return Math.max(0, viewTop() + total - viewBottom());
    }

    // 左侧标签树的总滚动量
    private int calcTreeMaxScroll() {
        int total = 0;
        for (int i = 0; i < cats.size(); i++) {
            total += CAT_ROW_H;
            if (expanded[i]) for (Opt o : cats.get(i).opts()) if (o.isHeader()) total += SUB_ROW_H;
        }
        return Math.max(0, START_Y + total - viewBottom());
    }

    // 按当前 scrollOffset 重排右侧控件的 y 与可见性
    private void relayoutWidgets() {
        int y = viewTop() - scrollOffset;
        int wi = 0;
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) { y += HEADER_H; continue; }
            // multi 行的控件共享同一 y（水平并排），按行数逐行推进
            int count = opt.multiFactory() != null ? opt.multiFactory().create(0).size() : 1;
            for (int k = 0; k < count; k++) {
                if (wi < scrollWidgets.size()) {
                    AbstractWidget w = scrollWidgets.get(wi++);
                    w.setY(y);
                    w.visible = y >= viewTop() && y + 20 <= viewBottom();
                }
            }
            y += ROW_H * opt.rows();
        }
    }

    // ---- 滚动条 + 平滑滚动：复用 ChatScrollbar 几何 + Animation 缓出，常驻显示 ----

    private int rTrackX() { return width - com.niuqu.chatbubble.render.ChatScrollbar.WIDTH; }
    private int rTrackH() { return viewBottom() - viewTop(); }
    private int rTotalH() { return calcMaxScroll() + rTrackH(); }
    private int tTrackX() { return dividerX - com.niuqu.chatbubble.render.ChatScrollbar.WIDTH - 2; }
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

    // 每帧推进两区缓出动画并同步右侧控件 y；render 开头调一次
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

    // 常驻滚动条：maxScroll<=0 不画；track 淡、thumb 实（拖拽/悬停加亮）
    private void drawBar(GuiGraphics g, int trackX, int top, int bot,
                         int totalH, int offset, int maxScroll,
                         double mx, double my, boolean dragging) {
        if (maxScroll <= 0) return;
        int trackH = bot - top;
        int th = com.niuqu.chatbubble.render.ChatScrollbar.thumbHeight(trackH, totalH);
        int ty = com.niuqu.chatbubble.render.ChatScrollbar.thumbY(top, trackH, th, offset, maxScroll);
        int w = com.niuqu.chatbubble.render.ChatScrollbar.WIDTH;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.SCROLLBAR_TRACK, ChatBubbleTheme.DARK),
            trackX, top, w, bot - top, 0x40 / 255f);
        int base = dragging ? 0xCC
            : com.niuqu.chatbubble.render.ChatScrollbar.isHoveringThumb(mx, my, trackX, ty, th) ? 0xAA : 0x88;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.SCROLLBAR_THUMB, ChatBubbleTheme.DARK),
            trackX, ty, w, th, base / 255f);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        // 鼠标在左树区域滚左树，否则滚右侧选项；wheel 只设目标、开缓出动画，不硬跳
        if (mouseX < dividerX) {
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
            int travel = rTrackH() - com.niuqu.chatbubble.render.ChatScrollbar.thumbHeight(rTrackH(), rTotalH());
            if (travel > 0) {
                int d = (int) mouseY - rBarDragY;
                startR(rBarDragOff + (float) d * calcMaxScroll() / travel, 80);
            }
            return true;
        }
        if (tBarDrag && calcTreeMaxScroll() > 0) {
            int travel = tTrackH() - com.niuqu.chatbubble.render.ChatScrollbar.thumbHeight(tTrackH(), tTotalH());
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

    // 保存：保留改动直接关闭（显式写盘——ModConfigSpec.set() 只改内存，不落盘）
    private void doClose() {
        ChatBubbleMod.saveClientConfig();
        // 纹理走 blit(RL) 懒加载，配置改动无需重新烘焙
        minecraft.setScreen(lastScreen);
    }

    // 退出：全部回滚到打开时的快照
    private void doExit() {
        revertAll();
        minecraft.setScreen(lastScreen);
    }

    @Override
    public void onClose() {
        int changed = changeCount();
        if (changed > 0) {
            minecraft.setScreen(new ConfirmScreen(confirmed -> {
                if (confirmed) doExit();
                else minecraft.setScreen(this);
            },
                Component.translatable("e33chat.config.discard.title"),
                Component.translatable("e33chat.config.discard.message", changed)));
        } else {
            doClose();
        }
    }
}
