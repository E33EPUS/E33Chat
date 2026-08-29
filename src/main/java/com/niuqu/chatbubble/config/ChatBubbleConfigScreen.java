package com.niuqu.chatbubble.config;
import com.niuqu.chatbubble.ChatBubbleClientSetup;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.AnimationStyle;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.tooltip.HoveredTooltipPositioner;
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.screen.ScreenTexts;
import net.minecraft.text.Text;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

public class ChatBubbleConfigScreen extends Screen {
    private final Screen lastScreen;

    private ChatBubbleTheme.Colors c() {
        return ChatBubbleTheme.DARK.colors();
    }

    private static final int ROW_H = 32;
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
    private static final int SCROLLBAR_W = 6;
    private static final String[] PALETTE = {"#FFFFFF", "#000000", "#FF5555", "#FFAA00", "#55FF55", "#5555FF", "#FF55FF", "#1E90FF"};
    private static final int PALETTE_W = PALETTE.length * 10 - 2;

    private int dividerX, optLabelX, inputX, previewX;
    private int selectedCat;
    private int selectedSub = -1;
    private final com.niuqu.chatbubble.render.SmoothScrollPane rightPane = new com.niuqu.chatbubble.render.SmoothScrollPane();
    private final com.niuqu.chatbubble.render.SmoothScrollPane treePane = new com.niuqu.chatbubble.render.SmoothScrollPane();
    private final List<ClickableWidget> scrollWidgets = new ArrayList<>();
    private final boolean[] expanded = {true, true, true, true, true};

    // ---- mutable copies (loadFromConfig → widget edits → saveToConfig) ----
    private ChatBubbleTheme theme;
    private boolean enabled, redDotEnabled, hideChatIcon, animationEnabled;
    private boolean systemChatAsBubble;
    private boolean antiSpam, chatHistoryEnabled;
    private boolean receiveImages;
    private String uploadUrl = "";
    private boolean soundPublic, soundSystem, soundWhisper;
    private boolean debugLog, preserveInput, colorCodes, closeChatOnSend;
    private boolean mentionBannerEnabled, systemBannerEnabled, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner;
    private boolean blurEnabled, ownMentionNotify, ownQuoteNotify, ownWhisperNotify;
    private int mentionBannerDuration, timeSeparatorMinutes;
    private int panelWidth, bubbleCornerRadius, panelOpacity, soundVolume, bannerCornerRadius, bannerOpacity;
    private boolean panelFullscreen;
    private int bannerOffsetX, bannerOffsetY, bannerMaxStack;
    private String panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle;
    private int historyRetentionDays;
    private String ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor;
    private int messageGap, avatarSize, bubbleSize;
    private boolean hideRepeatedAvatars;
    private List<String> sidebarHidePatterns;
    private List<String> blockedPlayers;

    // 打开时的快照——用于 changeCount / revertAll
    private ChatBubbleConfig snapshot;

    // ---- track（Fabric 没有 ModConfigSpec，用 snapshot/revert 自闭环） ----
    private interface Tracked {
        boolean changed();
        void revert();
    }
    private final List<Tracked> tracked = new ArrayList<>();

    private ButtonWidget doneBtn, exitBtn, saveBtn;

    private interface WidgetFactory {
        ClickableWidget create(int y);
    }

    // 一个选项行可生成多个控件（如 [编辑框][删除]），配合 rows 占多行
    private interface WidgetsFactory {
        List<ClickableWidget> create(int y);
    }

    private record Opt(String key, WidgetFactory factory, WidgetsFactory multiFactory,
                       int rows, Supplier<String> previewColor, Ref<?> value) {
        Opt(String key, WidgetFactory factory, Supplier<String> previewColor) {
            this(key, factory, null, 1, previewColor, null);
        }
        Opt(String key, WidgetFactory factory, Supplier<String> previewColor, Ref<?> value) {
            this(key, factory, null, 1, previewColor, value);
        }
        static Opt header(String key) { return new Opt(key, null, null, 1, null, null); }
        static Opt multi(String key, WidgetsFactory f, int rows) { return new Opt(key, null, f, rows, null, null); }
        boolean isHeader() { return factory == null && multiFactory == null; }
    }

    private record Cat(String key, List<Opt> opts) {}

    private List<Cat> cats;

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

    private boolean showPreview() {
        return selectedCat == 0 && selectedSub == bubbleFontSub();
    }

    private void snapshotAll() {
        snapshot = ChatBubbleClientSetup.config();
    }

    private <T> Tracked track(java.util.function.Supplier<T> getter, java.util.function.Consumer<T> setter) {
        T snap = getter.get();
        return new Tracked() {
            @Override public boolean changed() { return !Objects.equals(getter.get(), snap); }
            @Override public void revert() { setter.accept(snap); }
        };
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private void trackConfigFields() {
        tracked.clear();
        for (List<SectionDef> cat : CAT_SECTIONS)
            for (SectionDef s : cat)
                for (OptionDef d : s.opts()) {
                    Ref r = d.ref();
                    tracked.add(track(r.getter(), r.setter()));
                }
        // 屏蔽列表在注册表外（动态行）
        tracked.add(track(() -> new ArrayList<>(blockedPlayers), v -> blockedPlayers = new ArrayList<>(v)));
    }

    private int changeCount() {
        int n = 0;
        for (Tracked t : tracked) if (t.changed()) n++;
        return n;
    }

    private void revertAll() {
        for (Tracked t : tracked) t.revert();
    }

    private void saveAll() {
        ChatBubbleClientSetup.saveConfig(new ChatBubbleConfig(
            enabled, theme.name().toLowerCase(), redDotEnabled, hideChatIcon, animationEnabled,
            systemChatAsBubble, antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, panelFullscreen, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes,
            sidebarHidePatterns,
            blockedPlayers,
            ChatBubbleClientSetup.config().quickChatPhrases(),
            mentionBannerEnabled, systemBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius,
            bannerOffsetX, bannerOffsetY, bannerMaxStack, panelAnimStyle, bannerAnimStyle, popupAnimStyle, messageAnimStyle,
            ChatBubbleClientSetup.config().imageRenderEnabled(),
            receiveImages,
            uploadUrl.isEmpty() ? null : uploadUrl,
            ChatBubbleClientSetup.config().uploadField(),
            ChatBubbleClientSetup.config().uploadExtra(),
            ChatBubbleClientSetup.config().uploadResponse(),
            messageGap, avatarSize, hideRepeatedAvatars, closeChatOnSend, bannerOpacity, bubbleSize));
    }

    private void loadFromConfig() {
        var cfg = ChatBubbleClientSetup.config();
        try { theme = ChatBubbleTheme.valueOf(cfg.theme().toUpperCase()); } catch (Exception e) { theme = ChatBubbleTheme.DARK; }
        enabled = cfg.enabled(); redDotEnabled = cfg.redDotEnabled();
        hideChatIcon = cfg.hideChatIcon(); animationEnabled = cfg.animationEnabled();
        systemChatAsBubble = cfg.systemChatAsBubble(); antiSpam = cfg.antiSpam();
        receiveImages = cfg.receiveImages() != null && cfg.receiveImages();
        uploadUrl = cfg.uploadUrl() != null ? cfg.uploadUrl() : "";
        chatHistoryEnabled = cfg.chatHistoryEnabled();
        soundPublic = cfg.soundPublic();
        soundSystem = cfg.soundSystem();
        soundWhisper = cfg.soundWhisper(); debugLog = cfg.debugLog();
        preserveInput = cfg.preserveInput(); colorCodes = cfg.colorCodes();
        closeChatOnSend = cfg.closeChatOnSend();
        mentionBannerEnabled = cfg.mentionBannerEnabled();
        systemBannerEnabled = cfg.systemBannerEnabled();
        mentionBannerDuration = cfg.mentionBannerDuration();
        mentionSoundEnabled = cfg.mentionSoundEnabled();
        mentionRequireAt = cfg.mentionRequireAt();
        mentionWhisperBanner = cfg.mentionWhisperBanner();
        blurEnabled = cfg.blurEnabled(); panelOpacity = cfg.panelOpacity();
        soundVolume = cfg.soundVolume();
        ownMentionNotify = cfg.ownMentionNotify(); ownQuoteNotify = cfg.ownQuoteNotify();
        ownWhisperNotify = cfg.ownWhisperNotify();
        bannerCornerRadius = cfg.bannerCornerRadius();
        bannerOpacity = cfg.bannerOpacity();
        bannerOffsetX = cfg.bannerOffsetX();
        bannerOffsetY = cfg.bannerOffsetY();
        bannerMaxStack = cfg.bannerMaxStack() != null ? cfg.bannerMaxStack() : 3;
        panelAnimStyle = cfg.panelAnimStyle(); bannerAnimStyle = cfg.bannerAnimStyle();
        popupAnimStyle = cfg.popupAnimStyle(); messageAnimStyle = cfg.messageAnimStyle();
        historyRetentionDays = cfg.historyRetentionDays();
        timeSeparatorMinutes = cfg.timeSeparatorMinutes(); panelWidth = cfg.panelWidth();
        panelFullscreen = cfg.panelFullscreen();
        bubbleCornerRadius = cfg.bubbleCornerRadius();
        ownBubbleColor = cfg.ownBubbleColor(); otherBubbleColor = cfg.otherBubbleColor();
        ownTextColor = cfg.ownTextColor(); otherTextColor = cfg.otherTextColor();
        sidebarHidePatterns = new ArrayList<>(cfg.sidebarHidePatterns());
        blockedPlayers = new ArrayList<>(cfg.blockedPlayers());
        messageGap = cfg.messageGap() != null ? cfg.messageGap() : 6;
        avatarSize = cfg.avatarSize() != null ? cfg.avatarSize() : 20;
        bubbleSize = cfg.bubbleSize() != null ? cfg.bubbleSize() : 9;
        hideRepeatedAvatars = cfg.hideRepeatedAvatars() != null && cfg.hideRepeatedAvatars();
    }

    // ---- ChatScrollbar geometry inline ----
    private static int sbThumbH(int trackH, int totalH) {
        return Math.max(8, (int) ((long) trackH * trackH / totalH));
    }
    private static int sbThumbY(int top, int trackH, int th, int offset, int maxScroll) {
        if (maxScroll <= 0 || trackH <= th) return top;
        return top + (int) ((long) (trackH - th) * offset / maxScroll);
    }
    private static boolean sbHovering(int mx, int my, int tx, int ty, int th) {
        return mx >= tx && mx < tx + SCROLLBAR_W && my >= ty && my < ty + th;
    }

    // Chatsheet geometry helpers (scrollbar width constant from render/ChatScrollbar)
    private int rTrackX() { return width - SCROLLBAR_W; }
    private int rTrackH() { return viewBottom() - viewTop(); }
    private int rTotalH() { return calcMaxScroll() + rTrackH(); }
    private int tTrackX() { return dividerX - SCROLLBAR_W - 2; }
    private int tTrackH() { return viewBottom() - START_Y; }
    private int tTotalH() { return calcTreeMaxScroll() + tTrackH(); }

    private void startR(float target, int dur) {
        rightPane.animateTo(target, calcMaxScroll(), dur);
    }

    private void startT(float target, int dur) {
        treePane.animateTo(target, calcTreeMaxScroll(), dur);
    }

    private void tickAnims() {
        rightPane.tick(calcMaxScroll());
        treePane.tick(calcTreeMaxScroll());
        relayoutWidgets();
    }

    private void drawBar(DrawContext g, int trackX, int top, int bot,
                         int totalH, int offset, int maxScroll,
                         double mx, double my, boolean dragging) {
        if (maxScroll <= 0) return;
        int trackH = bot - top;
        int th = sbThumbH(trackH, totalH);
        int ty = sbThumbY(top, trackH, th, offset, maxScroll);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.SCROLLBAR_TRACK, ChatBubbleTheme.DARK),
            trackX, top, SCROLLBAR_W, bot - top, 0x40 / 255f);
        int base = dragging ? 0xCC
            : sbHovering((int) mx, (int) my, trackX, ty, th) ? 0xAA : 0x88;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.SCROLLBAR_THUMB, ChatBubbleTheme.DARK),
            trackX, ty, SCROLLBAR_W, th, base / 255f);
    }

    private void relayoutWidgets() {
        int y = viewTop() - rightPane.offset();
        int wi = 0;
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) { y += HEADER_H; continue; }
            int count = opt.multiFactory() != null ? opt.multiFactory().create(0).size() : 1;
            // multi 行的控件共享同一 y（水平并排），逐行推进
            if (opt.multiFactory() != null) {
                for (int k = 0; k < count; k++) {
                    if (wi < scrollWidgets.size()) {
                        ClickableWidget w = scrollWidgets.get(wi++);
                        w.setY(y);
                        w.visible = y >= viewTop() && y + 20 <= viewBottom();
                    }
                }
            } else if (wi < scrollWidgets.size()) {
                ClickableWidget w = scrollWidgets.get(wi++);
                w.setY(y);
                w.visible = y >= viewTop() && y + 20 <= viewBottom();
            }
            y += ROW_H * opt.rows();
        }
    }

    private int optAreaW() { return previewX - optLabelX - 4; }
    private int paletteX() { return inputX - 8 - PALETTE_W; }
    private int viewTop() { return showPreview() ? START_Y + PREVIEW_H + PREVIEW_GAP : START_Y; }
    private int viewBottom() { return height - 40; }

    private int calcMaxScroll() {
        int total = 0;
        for (Opt opt : visibleOpts())
            total += opt.isHeader() ? HEADER_H : ROW_H * opt.rows();
        return Math.max(0, viewTop() + total - viewBottom());
    }

    private int calcTreeMaxScroll() {
        int total = 0;
        for (int i = 0; i < cats.size(); i++) {
            total += CAT_ROW_H;
            if (expanded[i]) for (Opt o : cats.get(i).opts()) if (o.isHeader()) total += SUB_ROW_H;
        }
        return Math.max(0, START_Y + total - viewBottom());
    }

    // ---- UI construction ----

    // ===== OptionDef 注册表（2.3.15，D4）=====
    // 单一事实来源：注册表描述"哪个配置键放哪个 GUI 行"。配置键名/默认值/范围
    // 仍在 ChatBubbleConfig 原样保留（红线不动）。buildCats() / trackConfigFields()
    // / 色板点击全部由注册表派生，杜绝手写清单漂移。
    // Fabric 无 ModConfigSpec：Ref 是字段的 getter/setter 对（Screen 的 mutable copies）。
    // 注册表引实例字段，故非 static；lambda 惰性求值，loadFromConfig 前只建引用。
    private enum Kind { BOOL, INT, SLIDER, HEX, TEXT, PATTERN, ENUM_CYCLE, THEME_CYCLE, TIME_SEP }

    private record Ref<T>(java.util.function.Supplier<T> getter, java.util.function.Consumer<T> setter) {
        static Ref<Boolean> b(java.util.function.BooleanSupplier g, java.util.function.Consumer<Boolean> s) {
            return new Ref<>(g::getAsBoolean, s);
        }
        static Ref<Integer> i(java.util.function.IntSupplier g, java.util.function.IntConsumer s) {
            return new Ref<>(g::getAsInt, s::accept);
        }
        static Ref<String> s(java.util.function.Supplier<String> g, java.util.function.Consumer<String> s) {
            return new Ref<>(g, s);
        }
        static Ref<List<String>> l(java.util.function.Supplier<List<String>> g, java.util.function.Consumer<List<String>> s) {
            return new Ref<>(g, s);
        }
    }

    private record OptionDef(String key, Kind kind, Ref<?> ref,
                             int min, int max, int maxLen, Supplier<String> previewColor) {
        static OptionDef bool(String key, Ref<Boolean> r) {
            return new OptionDef(key, Kind.BOOL, r, 0, 0, 0, null);
        }
        static OptionDef intBox(String key, Ref<Integer> r, int min, int max, int maxLen) {
            return new OptionDef(key, Kind.INT, r, min, max, maxLen, null);
        }
        static OptionDef slider(String key, Ref<Integer> r, int min, int max) {
            return new OptionDef(key, Kind.SLIDER, r, min, max, 0, null);
        }
        static OptionDef hex(String key, Ref<String> r) {
            return new OptionDef(key, Kind.HEX, r, 0, 0, 7, () -> r.getter().get());
        }
        static OptionDef text(String key, Ref<String> r) {
            return new OptionDef(key, Kind.TEXT, r, 0, 0, 0, null);
        }
        static OptionDef pattern(String key, Ref<List<String>> r) {
            return new OptionDef(key, Kind.PATTERN, r, 0, 0, 0, null);
        }
        static OptionDef enumCycle(String key, Ref<String> r) {
            return new OptionDef(key, Kind.ENUM_CYCLE, r, 0, 0, 0, null);
        }
        static OptionDef themeCycle(String key, Ref<ChatBubbleTheme> r) {
            return new OptionDef(key, Kind.THEME_CYCLE, r, 0, 0, 0, null);
        }
        static OptionDef timeSep(String key, Ref<Integer> r) {
            return new OptionDef(key, Kind.TIME_SEP, r, 0, 0, 0, null);
        }
    }

    private record SectionDef(String key, List<OptionDef> opts) {
        static SectionDef of(String key, OptionDef... opts) { return new SectionDef(key, List.of(opts)); }
    }

    private final List<SectionDef> CHAT_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.panel",
            OptionDef.themeCycle("e33chat.config.theme", new Ref<>(() -> theme, v -> theme = v)),
            OptionDef.intBox("e33chat.config.panel_width", Ref.i(() -> panelWidth, v -> panelWidth = v), 400, 1600, 4),
            OptionDef.bool("e33chat.config.panel_fullscreen", Ref.b(() -> panelFullscreen, v -> panelFullscreen = v)),
            OptionDef.bool("e33chat.config.blur_enabled", Ref.b(() -> blurEnabled, v -> blurEnabled = v)),
            OptionDef.intBox("e33chat.config.panel_opacity", Ref.i(() -> panelOpacity, v -> panelOpacity = v), 0, 100, 3),
            OptionDef.bool("e33chat.config.animation", Ref.b(() -> animationEnabled, v -> animationEnabled = v)),
            OptionDef.enumCycle("e33chat.config.panel_anim_style", Ref.s(() -> panelAnimStyle, v -> panelAnimStyle = v)),
            OptionDef.enumCycle("e33chat.config.popup_anim_style", Ref.s(() -> popupAnimStyle, v -> popupAnimStyle = v)),
            OptionDef.enumCycle("e33chat.config.message_anim_style", Ref.s(() -> messageAnimStyle, v -> messageAnimStyle = v)),
            OptionDef.intBox("e33chat.config.avatar_size", Ref.i(() -> avatarSize, v -> avatarSize = v), 12, 32, 2),
            OptionDef.bool("e33chat.config.hide_repeated_avatars", Ref.b(() -> hideRepeatedAvatars, v -> hideRepeatedAvatars = v))),
        SectionDef.of("e33chat.config.section.bubble_font",
            OptionDef.intBox("e33chat.config.bubble_size", Ref.i(() -> bubbleSize, v -> bubbleSize = v), 5, 14, 2),
            OptionDef.intBox("e33chat.config.bubble_corner_radius", Ref.i(() -> bubbleCornerRadius, v -> bubbleCornerRadius = v), 0, 10, 2),
            OptionDef.hex("e33chat.config.own_bubble_color", Ref.s(() -> ownBubbleColor, v -> ownBubbleColor = v)),
            OptionDef.hex("e33chat.config.other_bubble_color", Ref.s(() -> otherBubbleColor, v -> otherBubbleColor = v)),
            OptionDef.hex("e33chat.config.own_text_color", Ref.s(() -> ownTextColor, v -> ownTextColor = v)),
            OptionDef.hex("e33chat.config.other_text_color", Ref.s(() -> otherTextColor, v -> otherTextColor = v))),
        SectionDef.of("e33chat.config.section.msgdisplay",
            OptionDef.intBox("e33chat.config.message_gap", Ref.i(() -> messageGap, v -> messageGap = v), 0, 12, 2),
            OptionDef.bool("e33chat.config.enabled", Ref.b(() -> enabled, v -> enabled = v)),
            OptionDef.bool("e33chat.config.system_chat_as_bubble", Ref.b(() -> systemChatAsBubble, v -> systemChatAsBubble = v)),
            OptionDef.bool("e33chat.config.anti_spam", Ref.b(() -> antiSpam, v -> antiSpam = v)),
            OptionDef.bool("e33chat.config.receive_images", Ref.b(() -> receiveImages, v -> receiveImages = v)),
            OptionDef.timeSep("e33chat.config.time_separator", Ref.i(() -> timeSeparatorMinutes, v -> timeSeparatorMinutes = v)),
            OptionDef.bool("e33chat.config.color_codes", Ref.b(() -> colorCodes, v -> colorCodes = v)))
    );

    private final List<SectionDef> HUD_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.icon",
            OptionDef.bool("e33chat.config.red_dot", Ref.b(() -> redDotEnabled, v -> redDotEnabled = v)),
            OptionDef.bool("e33chat.config.hide_chat_icon", Ref.b(() -> hideChatIcon, v -> hideChatIcon = v)))
    );

    private final List<SectionDef> NOTIFY_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.mention",
            OptionDef.bool("e33chat.config.mention_banner_enabled", Ref.b(() -> mentionBannerEnabled, v -> mentionBannerEnabled = v)),
            OptionDef.bool("e33chat.config.mention_sound_enabled", Ref.b(() -> mentionSoundEnabled, v -> mentionSoundEnabled = v)),
            OptionDef.bool("e33chat.config.mention_require_at", Ref.b(() -> mentionRequireAt, v -> mentionRequireAt = v))),
        SectionDef.of("e33chat.config.section.whisper",
            OptionDef.bool("e33chat.config.mention_whisper_banner", Ref.b(() -> mentionWhisperBanner, v -> mentionWhisperBanner = v)),
            OptionDef.bool("e33chat.config.sound_whisper", Ref.b(() -> soundWhisper, v -> soundWhisper = v))),
        SectionDef.of("e33chat.config.section.system",
            OptionDef.bool("e33chat.config.system_banner_enabled", Ref.b(() -> systemBannerEnabled, v -> systemBannerEnabled = v)),
            OptionDef.bool("e33chat.config.sound_system", Ref.b(() -> soundSystem, v -> soundSystem = v))),
        SectionDef.of("e33chat.config.section.banner",
            OptionDef.intBox("e33chat.config.mention_banner_duration", Ref.i(() -> mentionBannerDuration, v -> mentionBannerDuration = v), 2, 10, 2),
            OptionDef.intBox("e33chat.config.banner_max_stack", Ref.i(() -> bannerMaxStack, v -> bannerMaxStack = v), 1, 5, 2),
            OptionDef.intBox("e33chat.config.banner_corner_radius", Ref.i(() -> bannerCornerRadius, v -> bannerCornerRadius = v), 0, 10, 2),
            OptionDef.intBox("e33chat.config.banner_opacity", Ref.i(() -> bannerOpacity, v -> bannerOpacity = v), 0, 100, 3),
            // Fabric 输入范围 -500~500 与 Forge/Neo -1000~1000 不同——既有差异，红线不动
            OptionDef.intBox("e33chat.config.banner_offset_x", Ref.i(() -> bannerOffsetX, v -> bannerOffsetX = v), -500, 500, 2),
            OptionDef.intBox("e33chat.config.banner_offset_y", Ref.i(() -> bannerOffsetY, v -> bannerOffsetY = v), -500, 500, 2),
            OptionDef.enumCycle("e33chat.config.banner_anim_style", Ref.s(() -> bannerAnimStyle, v -> bannerAnimStyle = v))),
        SectionDef.of("e33chat.config.section.sound",
            OptionDef.slider("e33chat.config.sound_volume", Ref.i(() -> soundVolume, v -> soundVolume = v), 0, 100),
            OptionDef.bool("e33chat.config.sound_public", Ref.b(() -> soundPublic, v -> soundPublic = v)))
    );

    private final List<SectionDef> SIDEBAR_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.playerlist",
            OptionDef.pattern("e33chat.config.sidebar_hide_patterns",
                Ref.l(() -> new ArrayList<>(sidebarHidePatterns), v -> sidebarHidePatterns = v)))
    );

    private final List<SectionDef> ADVANCED_SECTIONS = List.of(
        SectionDef.of("e33chat.config.section.history",
            OptionDef.bool("e33chat.config.chat_history", Ref.b(() -> chatHistoryEnabled, v -> chatHistoryEnabled = v)),
            OptionDef.intBox("e33chat.config.history_retention", Ref.i(() -> historyRetentionDays, v -> historyRetentionDays = v), 0, 365, 3),
            OptionDef.bool("e33chat.config.preserve_input", Ref.b(() -> preserveInput, v -> preserveInput = v)),
            OptionDef.bool("e33chat.config.close_chat_on_send", Ref.b(() -> closeChatOnSend, v -> closeChatOnSend = v))),
        SectionDef.of("e33chat.config.section.upload",
            OptionDef.text("e33chat.config.upload_url", Ref.s(() -> uploadUrl, v -> uploadUrl = v))),
        SectionDef.of("e33chat.config.section.debug",
            OptionDef.bool("e33chat.config.debug_log", Ref.b(() -> debugLog, v -> debugLog = v)),
            OptionDef.bool("e33chat.config.own_mention_notify", Ref.b(() -> ownMentionNotify, v -> ownMentionNotify = v)),
            OptionDef.bool("e33chat.config.own_quote_notify", Ref.b(() -> ownQuoteNotify, v -> ownQuoteNotify = v)),
            OptionDef.bool("e33chat.config.own_whisper_notify", Ref.b(() -> ownWhisperNotify, v -> ownWhisperNotify = v)))
    );

    private final List<List<SectionDef>> CAT_SECTIONS = List.of(
        CHAT_SECTIONS, HUD_SECTIONS, NOTIFY_SECTIONS, SIDEBAR_SECTIONS, ADVANCED_SECTIONS);
    private final String[] CAT_KEYS = {
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
        for (int i = 0; i < blockedPlayers.size(); i++) {
            int idx = i;
            chat.add(Opt.multi("e33chat.config.blocked_players", y -> {
                TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W - 24, 20, Text.literal(""));
                box.setText(blockedPlayers.get(idx));
                box.setMaxLength(32);
                box.setChangedListener(s -> {
                    if (idx < blockedPlayers.size() && !s.equals(blockedPlayers.get(idx))) {
                        blockedPlayers.set(idx, s.trim());
                        ChatMessageStore.purgeBlocked(blockedPlayers);
                    }
                });
                ButtonWidget rm = ButtonWidget.builder(Text.literal("✕"), b -> {
                    blockedPlayers.remove(idx);
                    ChatMessageStore.purgeBlocked(blockedPlayers);
                    rebuild();
                }).position(inputX + INPUT_W - 22, y).size(20, 20).build();
                return List.of(box, rm);
            }, 1));
        }
        chat.add(Opt.multi("e33chat.config.blocked_add", y -> {
            ButtonWidget add = ButtonWidget.builder(Text.translatable("e33chat.config.blocked_add"), b -> {
                blockedPlayers.add("");
                rebuild();
            }).position(inputX, y).size(72, 20).build();
            return List.of(add);
        }, 1));
    }

    @SuppressWarnings("unchecked")
    private Opt optOf(OptionDef d) {
        return switch (d.kind()) {
            case BOOL -> {
                Ref<Boolean> r = (Ref<Boolean>) d.ref();
                yield new Opt(d.key(), y -> mkBoolButton(y, r.getter()::get, r.setter()), d.previewColor(), d.ref());
            }
            case INT -> {
                Ref<Integer> r = (Ref<Integer>) d.ref();
                yield new Opt(d.key(), y -> mkIntBox(y, String.valueOf(r.getter().get()), d.min(), d.max(), d.maxLen(), r.setter()::accept),
                    d.previewColor(), d.ref());
            }
            case SLIDER -> {
                Ref<Integer> r = (Ref<Integer>) d.ref();
                yield new Opt(d.key(), y -> mkIntSlider(y, r.getter()::get, r.setter()::accept, d.min(), d.max()), d.previewColor(), d.ref());
            }
            case HEX -> {
                Ref<String> r = (Ref<String>) d.ref();
                yield new Opt(d.key(), y -> mkHexBox(y, r.getter().get(), r.setter()), d.previewColor(), d.ref());
            }
            case TEXT -> {
                Ref<String> r = (Ref<String>) d.ref();
                yield new Opt(d.key(), y -> {
                    TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
                    box.setText(r.getter().get());
                    box.setMaxLength(512);
                    box.setChangedListener(r.setter()::accept);
                    return box;
                }, d.previewColor(), d.ref());
            }
            case PATTERN -> {
                Ref<List<String>> r = (Ref<List<String>>) d.ref();
                yield new Opt(d.key(), y -> mkPatternBox(y, new ArrayList<>(r.getter().get()), r.setter()), d.previewColor(), d.ref());
            }
            case ENUM_CYCLE -> {
                Ref<String> r = (Ref<String>) d.ref();
                yield new Opt(d.key(), y -> mkStyleButton(y, r.getter(), r.setter()), d.previewColor(), d.ref());
            }
            case THEME_CYCLE -> new Opt(d.key(), this::mkThemeButton, d.previewColor(), d.ref());
            case TIME_SEP -> new Opt(d.key(), this::mkTimeSepButton, d.previewColor(), d.ref());
        };
    }

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(Text.translatable("e33chat.config.title"));
        this.lastScreen = lastScreen;
        loadFromConfig();
        snapshotAll();
        trackConfigFields();
    }

    @Override
    protected void init() {
        buildCats();
        scrollWidgets.clear();

        dividerX = CAT_X + CAT_W + 12;
        optLabelX = dividerX + 14;
        previewX = width - 26;
        inputX = previewX - 8 - INPUT_W;

        rightPane.setOffset(MathHelper.clamp(rightPane.offset(), 0, calcMaxScroll()));
        treePane.setOffset(MathHelper.clamp(treePane.offset(), 0, calcTreeMaxScroll()));

        int y = viewTop() - rightPane.offset();
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) { y += HEADER_H; continue; }
            if (opt.multiFactory() != null) {
                for (ClickableWidget w : opt.multiFactory().create(y)) {
                    w.visible = y >= viewTop() && y + 20 <= viewBottom();
                    scrollWidgets.add(addDrawableChild(w));
                }
                y += ROW_H * opt.rows();
                continue;
            }
            ClickableWidget w = opt.factory().create(y);
            w.visible = y >= viewTop() && y + 20 <= viewBottom();
            scrollWidgets.add(addDrawableChild(w));
            y += ROW_H;
        }

        doneBtn = addDrawableChild(ButtonWidget.builder(ScreenTexts.DONE, btn -> doClose())
            .position(width / 2 - 100, height - 32).size(200, 20).build());
        exitBtn = addDrawableChild(ButtonWidget.builder(Text.translatable("e33chat.config.exit"), btn -> doExit())
            .position(width / 2 - 104, height - 32).size(100, 20).build());
        saveBtn = addDrawableChild(ButtonWidget.builder(Text.translatable("e33chat.config.save"), btn -> doClose())
            .position(width / 2 + 4, height - 32).size(100, 20).build());
    }

    private void switchCategory(int idx) {
        boolean same = idx == selectedCat && selectedSub == -1;
        selectedCat = idx;
        selectedSub = -1;
        expanded[idx] = true;
        if (!same) rebuild();
    }

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
        clearChildren();
        init();
    }

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

    // ---- widget factories ----

    private ButtonWidget mkThemeButton(int y) {
        return ButtonWidget.builder(
            Text.translatable("e33chat.theme." + theme.name().toLowerCase()),
            btn -> {
                int next = (theme.ordinal() + 1) % ChatBubbleTheme.values().length;
                theme = ChatBubbleTheme.values()[next];
                btn.setMessage(Text.translatable("e33chat.theme." + theme.name().toLowerCase()));
            }
        ).position(inputX, y).size(INPUT_W, 20).build();
    }

    // Animation style cycle buttons (SLIDE → FADE → ZOOM → NONE → ...)
    private ButtonWidget mkStyleButton(int y, java.util.function.Supplier<String> getter, java.util.function.Consumer<String> setter) {
        return ButtonWidget.builder(
            Text.translatable("e33chat.config.anim_style." + getter.get()),
            btn -> {
                AnimationStyle[] values = AnimationStyle.values();
                int next = (java.util.Arrays.asList(values).indexOf(AnimationStyle.valueOf(getter.get().toUpperCase())) + 1) % values.length;
                setter.accept(values[next].name().toLowerCase());
                btn.setMessage(Text.translatable("e33chat.config.anim_style." + getter.get()));
            }
        ).position(inputX, y).size(INPUT_W, 20).build();
    }

    // 色板点击写入 hex 字段（注册表行的 Ref 均为字符串）
    @SuppressWarnings("unchecked")
    private static void setHexValue(Ref<?> ref, String hex) {
        ((Ref<String>) ref).setter().accept(hex);
    }

    private ButtonWidget mkBoolButton(int y, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        boolean v = getter.getAsBoolean();
        return ButtonWidget.builder(
            v ? ScreenTexts.ON : ScreenTexts.OFF,
            btn -> {
                boolean nv = !getter.getAsBoolean();
                setter.accept(nv);
                btn.setMessage(nv ? ScreenTexts.ON : ScreenTexts.OFF);
            }
        ).position(inputX, y).size(INPUT_W, 20).build();
    }

    private SliderWidget mkIntSlider(int y, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter, int min, int max) {
        return new IntSlider(inputX, y, INPUT_W, 20, getter, setter, min, max);
    }

    private static class IntSlider extends SliderWidget {
        private final java.util.function.IntSupplier getter;
        private final java.util.function.IntConsumer setter;
        private final int min, max;

        IntSlider(int x, int y, int w, int h, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter, int min, int max) {
            super(x, y, w, h, Text.literal(String.valueOf(getter.getAsInt())),
                (getter.getAsInt() - min) / (double) (max - min));
            this.getter = getter;
            this.setter = setter;
            this.min = min;
            this.max = max;
        }

        @Override
        protected void applyValue() {
            setter.accept((int) Math.round(min + value * (max - min)));
        }

        @Override
        protected void updateMessage() {
            setMessage(Text.literal(String.valueOf(getter.getAsInt())));
        }
    }

    private static final int[] TIME_SEP_PRESETS = {1, 5, 10, 15, 30, 0};

    private ButtonWidget mkTimeSepButton(int y) {
        int cur = timeSeparatorMinutes;
        String label = cur == 0 ? Text.translatable("e33chat.config.time_separator.disable").getString()
            : cur + " " + Text.translatable("e33chat.config.time_separator.minute").getString();
        return ButtonWidget.builder(Text.literal(label), btn -> {
            int idx = -1;
            for (int i = 0; i < TIME_SEP_PRESETS.length; i++) {
                if (TIME_SEP_PRESETS[i] == timeSeparatorMinutes) { idx = i; break; }
            }
            int next = TIME_SEP_PRESETS[(idx + 1) % TIME_SEP_PRESETS.length];
            timeSeparatorMinutes = next;
            String nl = next == 0 ? Text.translatable("e33chat.config.time_separator.disable").getString()
                : next + " " + Text.translatable("e33chat.config.time_separator.minute").getString();
            btn.setMessage(Text.literal(nl));
        }).position(inputX, y).size(INPUT_W, 20).build();
    }

    private TextFieldWidget mkHexBox(int y, String initial, java.util.function.Consumer<String> onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
        box.setText(initial);
        box.setMaxLength(7);
        box.setChangedListener(s -> {
            if (!s.matches("#?[0-9a-fA-F]{0,6}")) return;
            if (s.length() == 6 && !s.startsWith("#")) {
                box.setText("#" + s);
                onChange.accept("#" + s);
            } else if (s.length() == 7) {
                onChange.accept(s);
            }
        });
        return box;
    }

    private TextFieldWidget mkIntBox(int y, String initial, int min, int max, int maxLen, java.util.function.IntConsumer onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
        box.setText(initial);
        box.setMaxLength(maxLen);
        box.setChangedListener(s -> {
            if (!s.matches("\\d*")) return;
            try {
                int v = Integer.parseInt(s);
                if (v >= min && v <= max) onChange.accept(v);
            } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    private TextFieldWidget mkPatternBox(int y, List<String> initial, java.util.function.Consumer<List<String>> onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
        box.setText(String.join(", ", initial));
        box.setMaxLength(200);
        box.setChangedListener(s -> {
            List<String> parts = new ArrayList<>();
            for (String part : s.split(",")) {
                String trimmed = part.trim();
                if (!trimmed.isEmpty()) parts.add(trimmed);
            }
            onChange.accept(parts);
        });
        return box;
    }

    // ---- rendering ----

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float partialTick) {
        // CONFIG_BG 烘焙为 75% 不透明（0xC0 alpha），drawTexture 无 alpha 顶点会丢 alpha 画成
        // 不透明灰块——走带 alpha 顶点的绘制恢复半透明，世界能透出来
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONFIG_BG, ChatBubbleTheme.DARK),
            0, 0, width, height, 0xC0 / 255f);
        tickAnims();
        g.drawText(textRenderer, title, width / 2 - textRenderer.getWidth(title) / 2, 14, c().configTitle(), false);

        String tooltipKey = null;

        // 左侧标签树
        g.enableScissor(CAT_X, START_Y, dividerX, viewBottom());
        int ly = START_Y - treePane.offset();
        for (int i = 0; i < cats.size(); i++) {
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= CAT_X && mouseX <= CAT_X + CAT_W && mouseY >= ly && mouseY < ly + CAT_ROW_H;
            if (sel || hover)
                g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG, ChatBubbleTheme.DARK),
                    CAT_X, ly, CAT_W, CAT_ROW_H, 0f, 0f, 16, 16, 16, 16);
            if (sel)
                g.fill(CAT_X, ly, CAT_X + 2, ly + CAT_ROW_H, c().configTitle());
            drawTriangle(g, CAT_X + 6, ly + (CAT_ROW_H - 5) / 2, expanded[i],
                sel ? c().configTitle() : c().configLabel());
            g.drawText(textRenderer, Text.translatable(cats.get(i).key()), CAT_X + 18, ly + (CAT_ROW_H - 8) / 2,
                sel ? c().configTitle() : c().configLabel(), false);
            ly += CAT_ROW_H;
            if (expanded[i]) {
                int sub = 0;
                for (Opt o : cats.get(i).opts()) {
                    if (!o.isHeader()) continue;
                    boolean selSub = i == selectedCat && sub == selectedSub;
                    boolean sh = mouseX >= CAT_X + 14 && mouseX <= CAT_X + CAT_W && mouseY >= ly && mouseY < ly + SUB_ROW_H;
                    if (selSub || sh)
                        g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.HOVER_BG, ChatBubbleTheme.DARK),
                            CAT_X + 14, ly, CAT_W - 14, SUB_ROW_H, 0f, 0f, 16, 16, 16, 16);
                    if (selSub)
                        g.fill(CAT_X + 14, ly, CAT_X + 16, ly + SUB_ROW_H, c().configTitle());
                    g.drawText(textRenderer, Text.translatable(o.key()), CAT_X + 24, ly + (SUB_ROW_H - 8) / 2,
                        (selSub || sh) ? c().configTitle() : c().configLabel(), false);
                    sub++;
                    ly += SUB_ROW_H;
                }
            }
        }
        g.disableScissor();
        drawBar(g, tTrackX(), START_Y, viewBottom(), tTotalH(), treePane.offset(), calcTreeMaxScroll(), mouseX, mouseY, treePane.dragging());

        g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
            dividerX, START_Y - 6, 1, viewBottom() - (START_Y - 6), 0f, 0f, 16, 16, 16, 16);

        if (showPreview()) drawBubblePreview(g);

        g.enableScissor(optLabelX - 4, viewTop(), width, viewBottom());
        int y = viewTop() - rightPane.offset();
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) {
                Text label = Text.translatable(opt.key());
                g.drawText(textRenderer, label, optLabelX, y + 11, c().configLabel(), false);
                int lineX = optLabelX + textRenderer.getWidth(label) + 8;
                int lineEnd = optLabelX + optAreaW() + 4;
                if (lineX < lineEnd)
            g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
                lineX, y + 15, lineEnd - lineX, 1, 0f, 0f, 16, 16, 16, 16);
                y += HEADER_H;
                continue;
            }
            g.drawText(textRenderer, Text.translatable(opt.key()), optLabelX, y + 6, c().configLabel(), false);
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
            g.drawText(textRenderer, Text.translatable("e33chat.config.changed", changed),
                width / 2 + 112, height - 26, c().configLabel(), false);

        if (tooltipKey != null)
            // wrap to 190px like Forge/Neo's font.split — the single-Text overload
            // renders one unwrapped line and long descriptions overflow the screen
            g.drawTooltip(textRenderer,
                textRenderer.wrapLines(Text.translatable(tooltipKey), 190),
                HoveredTooltipPositioner.INSTANCE, mouseX, mouseY);
    }

    private void drawBubblePreview(DrawContext g) {
        int top = START_Y;
        int other = ChatBubbleConfig.parseHexColor(otherBubbleColor, 0xFF4A4A4A);
        int own = ChatBubbleConfig.parseHexColor(ownBubbleColor, ACCENT);
        int otherT = ChatBubbleConfig.parseHexColor(otherTextColor, 0xFFFFFFFF);
        int ownT = ChatBubbleConfig.parseHexColor(ownTextColor, 0xFFFFFFFF);
        float rad = bubbleCornerRadius;
        Text otherMsg = Text.translatable("e33chat.config.preview.sample_other");
        Text ownMsg = Text.translatable("e33chat.config.preview.sample_own");
        int maxW = (optAreaW() - 8) / 2;
        int ow = Math.min(textRenderer.getWidth(otherMsg) + 8, maxW);
        RoundRectRenderer.fill(g, optLabelX, top + 4, optLabelX + ow, top + 18, rad, other);
        g.drawText(textRenderer, otherMsg, optLabelX + 4, top + 7, otherT, false);
        int mw = Math.min(textRenderer.getWidth(ownMsg) + 8, maxW);
        int mx = optLabelX + optAreaW() - mw;
        RoundRectRenderer.fill(g, mx, top + 22, mx + mw, top + 36, rad, own);
        g.drawText(textRenderer, ownMsg, mx + 4, top + 25, ownT, false);
        g.drawTexture(com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
            optLabelX - 4, top + PREVIEW_H - 1, optAreaW() + 8, 1, 0f, 0f, 16, 16, 16, 16);
    }

    private void drawPreview(DrawContext g, int y, String hex) {
        int color = ChatBubbleConfig.parseHexColor(hex, 0xFF000000);
        g.fill(previewX, y, previewX + 14, y + 14, c().iconHover());
        g.fill(previewX + 1, y + 1, previewX + 13, y + 13, color);
    }

    private void drawTriangle(DrawContext g, int x, int y, boolean down, int color) {
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
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float partialTick) {
        // no-op：背景已在 render() 开头画，避免 1.21.1 batch 缓冲叠暗文字
    }

    // ---- input ----

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        int w = SCROLLBAR_W;
        int rMax = calcMaxScroll();
        if (rMax > 0 && mouseX >= rTrackX() && mouseX < rTrackX() + w
                && mouseY >= viewTop() && mouseY < viewBottom()) {
            int th = sbThumbH(rTrackH(), rTotalH());
            int ty = sbThumbY(viewTop(), rTrackH(), th, rightPane.offset(), rMax);
            if (mouseY < ty) startR(rightPane.offset() - rTrackH(), 120);
            else if (mouseY > ty + th) startR(rightPane.offset() + rTrackH(), 120);
            else rightPane.dragStart((int) mouseY, rightPane.offset());
            return true;
        }
        int tMax = calcTreeMaxScroll();
        if (tMax > 0 && mouseX >= tTrackX() && mouseX < tTrackX() + w
                && mouseY >= START_Y && mouseY < viewBottom()) {
            int th = sbThumbH(tTrackH(), tTotalH());
            int ty = sbThumbY(START_Y, tTrackH(), th, treePane.offset(), tMax);
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
                        expanded[i] = !expanded[i];
                    } else {
                        switchCategory(i);
                    }
                    return true;
                }
                ly += CAT_ROW_H;
                if (expanded[i]) {
                    int sub = 0;
                    for (Opt o : cats.get(i).opts()) {
                        if (!o.isHeader()) continue;
                        if (mouseY >= ly && mouseY < ly + SUB_ROW_H && mouseX >= CAT_X + 14 && mouseX <= CAT_X + CAT_W) {
                            selectSub(i, sub);
                            return true;
                        }
                        sub++;
                        ly += SUB_ROW_H;
                    }
                }
            }

            int px = paletteX();
            if (mouseX >= px && mouseX < px + PALETTE_W) {
                int y = viewTop() - rightPane.offset();
                int wi = 0;
                for (Opt opt : visibleOpts()) {
                    if (opt.isHeader()) { y += HEADER_H; continue; }
                    if (opt.previewColor() != null && mouseY >= y + 12 && mouseY < y + 20) {
                        int idx = MathHelper.clamp((int) (mouseX - px) / 10, 0, PALETTE.length - 1);
                        String hex = PALETTE[idx];
                        if (opt.value() instanceof Ref<?> ref && ref.getter().get() instanceof String)
                            setHexValue(ref, hex);
                        if (wi < scrollWidgets.size() && scrollWidgets.get(wi) instanceof TextFieldWidget eb)
                            eb.setText(hex);
                        return true;
                    }
                    wi++;
                    y += ROW_H;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
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

    private void doClose() {
        saveAll();
        // 纹理走 drawTexture(Identifier) 懒加载，配置改动无需重新烘焙
        client.setScreen(lastScreen);
    }

    private void doExit() {
        revertAll();
        client.setScreen(lastScreen);
    }

    @Override
    public void close() {
        int changed = changeCount();
        if (changed > 0) {
            client.setScreen(new ConfirmScreen((BooleanConsumer) confirmed -> {
                if (confirmed) doExit();
                else client.setScreen(this);
            },
                Text.translatable("e33chat.config.discard.title"),
                Text.translatable("e33chat.config.discard.message", changed)));
        } else {
            doClose();
        }
    }
}
