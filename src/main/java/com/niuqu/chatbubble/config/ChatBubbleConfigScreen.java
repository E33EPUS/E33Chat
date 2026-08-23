package com.niuqu.chatbubble.config;
import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.store.ChatMessageStore;
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
    private final com.niuqu.chatbubble.render.SmoothScrollPane rightPane = new com.niuqu.chatbubble.render.SmoothScrollPane();
    private final com.niuqu.chatbubble.render.SmoothScrollPane treePane = new com.niuqu.chatbubble.render.SmoothScrollPane();
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();
    // 左侧树折叠状态（cats 固定 5 个 tab）
    private final boolean[] expanded = {true, true, true, true, true};

    private interface WidgetFactory {
        AbstractWidget create(int y);
    }

    // 一个选项行可生成多个控件（如 [编辑框][删除]），配合 rows 占多行
    private interface WidgetsFactory {
        List<AbstractWidget> create(int y);
    }

    private record Opt(String key, WidgetFactory factory, WidgetsFactory multiFactory,
                       int rows, Supplier<String> previewColor, ModConfigSpec.ConfigValue<?> value) {
        Opt(String key, WidgetFactory factory, Supplier<String> previewColor) {
            this(key, factory, null, 1, previewColor, null);
        }
        Opt(String key, WidgetFactory factory, Supplier<String> previewColor, ModConfigSpec.ConfigValue<?> value) {
            this(key, factory, null, 1, previewColor, value);
        }
        static Opt header(String key) { return new Opt(key, null, null, 1, null, null); }
        static Opt multi(String key, WidgetsFactory f, int rows) { return new Opt(key, null, f, rows, null, null); }
        boolean isHeader() { return factory == null && multiFactory == null; }
    }

    private record Cat(String key, List<Opt> opts) {}

    private List<Cat> cats;

    // 编辑模型：打开时快照所有配置项；退出回滚到快照，保存保留（doClose 显式落盘）
    private interface Tracked {
        boolean changed();
        void revert();
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    private Tracked track(ModConfigSpec.ConfigValue v) {
        Object snapshot = v.get();
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

    // ===== OptionDef 注册表（2.3.15，D4）=====
    // 单一事实来源：注册表描述"哪个 toml 键放哪个 GUI 行"。toml 键名/默认值/范围
    // 仍在 ChatBubbleConfig define*() 原样保留（红线不动）。buildCats() / snapshotAll()
    // / 色板点击全部由注册表派生，杜绝手写清单漂移。
    private enum Kind { BOOL, INT, SLIDER, HEX, TEXT, PATTERN, ENUM_CYCLE, THEME_CYCLE, TIME_SEP }

    private record OptionDef(String key, Kind kind, ModConfigSpec.ConfigValue<?> value,
                             int min, int max, int maxLen, Supplier<String> previewColor) {
        static OptionDef bool(String key, ModConfigSpec.BooleanValue v) {
            return new OptionDef(key, Kind.BOOL, v, 0, 0, 0, null);
        }
        static OptionDef intBox(String key, ModConfigSpec.IntValue v, int min, int max, int maxLen) {
            return new OptionDef(key, Kind.INT, v, min, max, maxLen, null);
        }
        static OptionDef slider(String key, ModConfigSpec.IntValue v, int min, int max) {
            return new OptionDef(key, Kind.SLIDER, v, min, max, 0, null);
        }
        static OptionDef hex(String key, ModConfigSpec.ConfigValue<String> v) {
            return new OptionDef(key, Kind.HEX, v, 0, 0, 7, v::get);
        }
        static OptionDef text(String key, ModConfigSpec.ConfigValue<String> v) {
            return new OptionDef(key, Kind.TEXT, v, 0, 0, 0, null);
        }
        static OptionDef pattern(String key, ModConfigSpec.ConfigValue<List<? extends String>> v) {
            return new OptionDef(key, Kind.PATTERN, v, 0, 0, 0, null);
        }
        static OptionDef enumCycle(String key, ModConfigSpec.EnumValue<AnimationStyle> v) {
            return new OptionDef(key, Kind.ENUM_CYCLE, v, 0, 0, 0, null);
        }
        static OptionDef themeCycle(String key, ModConfigSpec.EnumValue<ChatBubbleTheme> v) {
            return new OptionDef(key, Kind.THEME_CYCLE, v, 0, 0, 0, null);
        }
        static OptionDef timeSep(String key, ModConfigSpec.IntValue v) {
            return new OptionDef(key, Kind.TIME_SEP, v, 0, 0, 0, null);
        }
    }

    private record SectionDef(String key, List<OptionDef> opts) {
        static SectionDef of(String key, OptionDef... opts) { return new SectionDef(key, List.of(opts)); }
    }

    private static final List<SectionDef> CHAT_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.panel",
            OptionDef.themeCycle("e33chat.config.theme", ChatBubbleConfig.THEME),
            OptionDef.intBox("e33chat.config.panel_width", ChatBubbleConfig.PANEL_WIDTH, 800, 1600, 4),
            OptionDef.bool("e33chat.config.blur_enabled", ChatBubbleConfig.BLUR_ENABLED),
            OptionDef.intBox("e33chat.config.panel_opacity", ChatBubbleConfig.PANEL_OPACITY, 0, 100, 3),
            OptionDef.bool("e33chat.config.animation", ChatBubbleConfig.ANIMATION_ENABLED),
            OptionDef.enumCycle("e33chat.config.panel_anim_style", ChatBubbleConfig.PANEL_ANIM_STYLE),
            OptionDef.enumCycle("e33chat.config.popup_anim_style", ChatBubbleConfig.POPUP_ANIM_STYLE),
            OptionDef.enumCycle("e33chat.config.message_anim_style", ChatBubbleConfig.MESSAGE_ANIM_STYLE),
            OptionDef.intBox("e33chat.config.avatar_size", ChatBubbleConfig.AVATAR_SIZE, 12, 32, 2),
            OptionDef.bool("e33chat.config.hide_repeated_avatars", ChatBubbleConfig.HIDE_REPEATED_AVATARS)),
        SectionDef.of("e33chat.config.section.bubble_font",
            OptionDef.intBox("e33chat.config.bubble_size", ChatBubbleConfig.BUBBLE_SIZE, 5, 14, 2),
            OptionDef.intBox("e33chat.config.bubble_corner_radius", ChatBubbleConfig.BUBBLE_CORNER_RADIUS, 0, 10, 2),
            OptionDef.hex("e33chat.config.own_bubble_color", ChatBubbleConfig.OWN_BUBBLE_COLOR),
            OptionDef.hex("e33chat.config.other_bubble_color", ChatBubbleConfig.OTHER_BUBBLE_COLOR),
            OptionDef.hex("e33chat.config.own_text_color", ChatBubbleConfig.OWN_TEXT_COLOR),
            OptionDef.hex("e33chat.config.other_text_color", ChatBubbleConfig.OTHER_TEXT_COLOR)),
        SectionDef.of("e33chat.config.section.msgdisplay",
            OptionDef.intBox("e33chat.config.message_gap", ChatBubbleConfig.MESSAGE_GAP, 0, 12, 2),
            OptionDef.bool("e33chat.config.enabled", ChatBubbleConfig.ENABLED),
            OptionDef.bool("e33chat.config.system_chat_as_bubble", ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE),
            OptionDef.bool("e33chat.config.anti_spam", ChatBubbleConfig.ANTI_SPAM),
            OptionDef.bool("e33chat.config.receive_images", ChatBubbleConfig.RECEIVE_IMAGES),
            OptionDef.timeSep("e33chat.config.time_separator", ChatBubbleConfig.TIME_SEPARATOR_MINUTES),
            OptionDef.bool("e33chat.config.color_codes", ChatBubbleConfig.COLOR_CODES))
    );

    private static final List<SectionDef> HUD_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.icon",
            OptionDef.bool("e33chat.config.red_dot", ChatBubbleConfig.RED_DOT_ENABLED),
            OptionDef.bool("e33chat.config.hide_chat_icon", ChatBubbleConfig.HIDE_CHAT_ICON))
    );

    private static final List<SectionDef> NOTIFY_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.mention",
            OptionDef.bool("e33chat.config.mention_banner_enabled", ChatBubbleConfig.MENTION_BANNER_ENABLED),
            OptionDef.bool("e33chat.config.mention_sound_enabled", ChatBubbleConfig.MENTION_SOUND_ENABLED),
            OptionDef.bool("e33chat.config.mention_require_at", ChatBubbleConfig.MENTION_REQUIRE_AT)),
        SectionDef.of("e33chat.config.section.whisper",
            OptionDef.bool("e33chat.config.mention_whisper_banner", ChatBubbleConfig.MENTION_WHISPER_BANNER),
            OptionDef.bool("e33chat.config.sound_whisper", ChatBubbleConfig.SOUND_WHISPER)),
        SectionDef.of("e33chat.config.section.system",
            OptionDef.bool("e33chat.config.system_banner_enabled", ChatBubbleConfig.SYSTEM_BANNER_ENABLED),
            OptionDef.bool("e33chat.config.sound_system", ChatBubbleConfig.SOUND_SYSTEM)),
        SectionDef.of("e33chat.config.section.banner",
            OptionDef.intBox("e33chat.config.mention_banner_duration", ChatBubbleConfig.MENTION_BANNER_DURATION, 2, 10, 2),
            OptionDef.intBox("e33chat.config.banner_max_stack", ChatBubbleConfig.BANNER_MAX_STACK, 1, 5, 2),
            OptionDef.intBox("e33chat.config.banner_corner_radius", ChatBubbleConfig.BANNER_CORNER_RADIUS, 0, 10, 2),
            OptionDef.intBox("e33chat.config.banner_opacity", ChatBubbleConfig.BANNER_OPACITY, 0, 100, 3),
            OptionDef.intBox("e33chat.config.banner_offset_x", ChatBubbleConfig.BANNER_OFFSET_X, -1000, 1000, 2),
            OptionDef.intBox("e33chat.config.banner_offset_y", ChatBubbleConfig.BANNER_OFFSET_Y, -1000, 1000, 2),
            OptionDef.enumCycle("e33chat.config.banner_anim_style", ChatBubbleConfig.BANNER_ANIM_STYLE)),
        SectionDef.of("e33chat.config.section.sound",
            OptionDef.slider("e33chat.config.sound_volume", ChatBubbleConfig.SOUND_VOLUME, 0, 100),
            OptionDef.bool("e33chat.config.sound_public", ChatBubbleConfig.SOUND_PUBLIC))
    );

    private static final List<SectionDef> SIDEBAR_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.playerlist",
            OptionDef.pattern("e33chat.config.sidebar_hide_patterns", ChatBubbleConfig.SIDEBAR_HIDE_PATTERNS))
    );

    private static final List<SectionDef> ADVANCED_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.history",
            OptionDef.bool("e33chat.config.chat_history", ChatBubbleConfig.CHAT_HISTORY_ENABLED),
            OptionDef.intBox("e33chat.config.history_retention", ChatBubbleConfig.HISTORY_RETENTION_DAYS, 0, 365, 3),
            OptionDef.bool("e33chat.config.preserve_input", ChatBubbleConfig.PRESERVE_INPUT),
            OptionDef.bool("e33chat.config.close_chat_on_send", ChatBubbleConfig.CLOSE_CHAT_ON_SEND)),
        SectionDef.of("e33chat.config.section.upload",
            OptionDef.text("e33chat.config.upload_url", ChatBubbleConfig.UPLOAD_URL)),
        SectionDef.of("e33chat.config.section.debug",
            OptionDef.bool("e33chat.config.debug_log", ChatBubbleConfig.DEBUG_LOG),
            OptionDef.bool("e33chat.config.own_mention_notify", ChatBubbleConfig.OWN_MENTION_NOTIFY),
            OptionDef.bool("e33chat.config.own_quote_notify", ChatBubbleConfig.OWN_QUOTE_NOTIFY),
            OptionDef.bool("e33chat.config.own_whisper_notify", ChatBubbleConfig.OWN_WHISPER_NOTIFY))
    );

    private static final List<List<SectionDef>> CAT_SECTIONS = List.of(
        CHAT_SECTIONS, HUD_SECTIONS, NOTIFY_SECTIONS, SIDEBAR_SECTIONS, ADVANCED_SECTIONS);
    private static final String[] CAT_KEYS = {
        "e33chat.config.cat.chat", "e33chat.config.cat.hud", "e33chat.config.cat.notify",
        "e33chat.config.cat.sidebar", "e33chat.config.cat.advanced",
    };

    private void buildCats() {
        // 不缓存：屏蔽列表分区按当前名单动态生成行（删除/添加后 rebuild 重排），
        // 缓存会把行数定死在首次构建
        cats = new ArrayList<>();
        for (int i = 0; i < CAT_KEYS.length; i++) {
            List<Opt> opts = new ArrayList<>();
            for (SectionDef s : CAT_SECTIONS.get(i)) {
                opts.add(Opt.header(s.key()));
                for (OptionDef d : s.opts()) opts.add(optOf(d));
            }
            if (i == 0) buildBlockedRows(opts);
            cats.add(new Cat(CAT_KEYS[i], opts));
        }
    }

    // 屏蔽列表：动态行数，注册表外（每行 [编辑框][✕]，下方 [添加玩家]）
    private void buildBlockedRows(List<Opt> chat) {
        chat.add(Opt.header("e33chat.config.section.blocked"));
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
    }

    @SuppressWarnings("unchecked")
    private Opt optOf(OptionDef d) {
        return switch (d.kind()) {
            case BOOL -> new Opt(d.key(), y -> mkBoolButton(y, (ModConfigSpec.BooleanValue) d.value()),
                d.previewColor(), d.value());
            case INT -> new Opt(d.key(), y -> mkIntBox(y, String.valueOf(((ModConfigSpec.IntValue) d.value()).get()),
                d.min(), d.max(), d.maxLen(), ((ModConfigSpec.IntValue) d.value())::set), d.previewColor(), d.value());
            case SLIDER -> new Opt(d.key(), y -> mkIntSlider(y, (ModConfigSpec.IntValue) d.value(), d.min(), d.max()),
                d.previewColor(), d.value());
            case HEX -> new Opt(d.key(), y -> mkHexBox(y, ((ModConfigSpec.ConfigValue<String>) d.value()).get(),
                ((ModConfigSpec.ConfigValue<String>) d.value())::set), d.previewColor(), d.value());
            case TEXT -> new Opt(d.key(), y -> {
                EditBox box = new EditBox(font, inputX, y, INPUT_W, 20, Component.literal(""));
                box.setValue(((ModConfigSpec.ConfigValue<String>) d.value()).get());
                box.setMaxLength(512);
                box.setResponder(v -> ((ModConfigSpec.ConfigValue<String>) d.value()).set(v));
                return box;
            }, d.previewColor(), d.value());
            case PATTERN -> new Opt(d.key(), y -> mkPatternBox(y,
                new ArrayList<>(((ModConfigSpec.ConfigValue<List<? extends String>>) d.value()).get()),
                parts -> ((ModConfigSpec.ConfigValue<List<? extends String>>) d.value()).set(new ArrayList<>(parts))),
                d.previewColor(), d.value());
            case ENUM_CYCLE -> new Opt(d.key(), y -> mkStyleButton(y, (ModConfigSpec.EnumValue<AnimationStyle>) d.value()),
                d.previewColor(), d.value());
            case THEME_CYCLE -> new Opt(d.key(), this::mkThemeButton, d.previewColor(), d.value());
            case TIME_SEP -> new Opt(d.key(), this::mkTimeSepButton, d.previewColor(), d.value());
        };
    }

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(Component.translatable("e33chat.config.title"));
        this.lastScreen = lastScreen;
        snapshotAll();
    }

    // 快照全部可编辑配置项（常用语除外，那在游戏内面板管理）
    private void snapshotAll() {
        for (List<SectionDef> cat : CAT_SECTIONS)
            for (SectionDef s : cat)
                for (OptionDef d : s.opts())
                    track(d.value());
        track(ChatBubbleConfig.BLOCKED_PLAYERS);
    }

    @Override
    protected void init() {
        buildCats();
        scrollWidgets.clear();

        dividerX = CAT_X + CAT_W + 12;
        optLabelX = dividerX + 14;
        previewX = width - 26;
        inputX = previewX - 8 - INPUT_W;

        rightPane.setOffset(Mth.clamp(rightPane.offset(), 0, calcMaxScroll()));
        treePane.setOffset(Mth.clamp(treePane.offset(), 0, calcTreeMaxScroll()));

        int y = viewTop() - rightPane.offset();
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
        rightPane.setOffset(0);
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
        int ly = START_Y - treePane.offset();
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
        drawBar(g, tTrackX(), START_Y, viewBottom(), tTotalH(), treePane.offset(), calcTreeMaxScroll(), mouseX, mouseY, treePane.dragging());

        // Divider between categories and options
        g.blit(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
            dividerX, START_Y - 6, 1, viewBottom() - (START_Y - 6), 0f, 0f, 1, 1, 1, 1);

        // 气泡预览带：仅 chat tab 的“气泡与字体”子分类显示，气泡用当前圆角实时渲染
        if (showPreview()) drawBubblePreview(g);

        // Option rows hard-clipped to the viewport (scissor cuts anything past the bounds)
        g.enableScissor(optLabelX - 4, viewTop(), width, viewBottom());
        int y = viewTop() - rightPane.offset();
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
        drawBar(g, rTrackX(), viewTop(), viewBottom(), rTotalH(), rightPane.offset(), calcMaxScroll(), mouseX, mouseY, rightPane.dragging());

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
            int ty = com.niuqu.chatbubble.render.ChatScrollbar.thumbY(viewTop(), rTrackH(), th, rightPane.offset(), rMax);
            if (mouseY < ty) startR(rightPane.offset() - rTrackH(), 120);
            else if (mouseY > ty + th) startR(rightPane.offset() + rTrackH(), 120);
            else rightPane.dragStart((int) mouseY, rightPane.offset());
            return true;
        }
        int tMax = calcTreeMaxScroll();
        if (tMax > 0 && mouseX >= tTrackX() && mouseX < tTrackX() + w
                && mouseY >= START_Y && mouseY < viewBottom()) {
            int th = com.niuqu.chatbubble.render.ChatScrollbar.thumbHeight(tTrackH(), tTotalH());
            int ty = com.niuqu.chatbubble.render.ChatScrollbar.thumbY(START_Y, tTrackH(), th, treePane.offset(), tMax);
            if (mouseY < ty) startT(treePane.offset() - tTrackH(), 120);
            else if (mouseY > ty + th) startT(treePane.offset() + tTrackH(), 120);
            else treePane.dragStart((int) mouseY, treePane.offset());
            return true;
        }
        if (button == 0) {
            int ly = START_Y - treePane.offset();
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
                int y = viewTop() - rightPane.offset();
                int wi = 0;
                for (Opt opt : visibleOpts()) {
                    if (opt.isHeader()) { y += HEADER_H; continue; }
                    if (opt.previewColor() != null && mouseY >= y + 12 && mouseY < y + 20) {
                        int idx = Mth.clamp((int) (mouseX - px) / 10, 0, PALETTE.length - 1);
                        String hex = PALETTE[idx];
                        if (opt.value() != null && opt.value().get() instanceof String)
                            setHexValue(opt.value(), hex);
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

    // 色板点击写入 hex 配置（注册表行的 ConfigValue 均为字符串类型）
    @SuppressWarnings("unchecked")
    private static void setHexValue(ModConfigSpec.ConfigValue<?> v, String hex) {
        ((ModConfigSpec.ConfigValue<String>) v).set(hex);
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

    // 按当前 rightPane.offset() 重排右侧控件的 y 与可见性
    private void relayoutWidgets() {
        int y = viewTop() - rightPane.offset();
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
        rightPane.animateTo(target, calcMaxScroll(), dur);
    }

    private void startT(float target, int dur) {
        treePane.animateTo(target, calcTreeMaxScroll(), dur);
    }

    // 每帧推进两区缓出动画并同步右侧控件 y；render 开头调一次
    private void tickAnims() {
        rightPane.tick(calcMaxScroll());
        treePane.tick(calcTreeMaxScroll());
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
            treePane.wheel(scrollY, calcTreeMaxScroll(), 120);
            return true;
        }
        if (calcMaxScroll() <= 0) return false;
        rightPane.wheel(scrollY, calcMaxScroll(), 120);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        if (rightPane.dragging()) {
            rightPane.dragTo((int) mouseY, rTrackH(), rTotalH(), calcMaxScroll(), 80);
            return true;
        }
        if (treePane.dragging()) {
            treePane.dragTo((int) mouseY, tTrackH(), tTotalH(), calcTreeMaxScroll(), 80);
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dx, dy);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        rightPane.dragEnd();
        treePane.dragEnd();
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
