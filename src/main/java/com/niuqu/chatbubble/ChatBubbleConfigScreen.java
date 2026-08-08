package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import it.unimi.dsi.fastutil.booleans.BooleanConsumer;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.function.Supplier;
//#if MC >= 12106
import net.minecraft.client.gl.RenderPipelines;
//#endif
//#if MC >= 12109
import net.minecraft.client.gui.Click;
//#endif
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
import net.minecraft.client.gui.screen.ConfirmScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.SliderWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
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
    private int scrollOffset;
    private int treeScroll;
    private final List<ClickableWidget> scrollWidgets = new ArrayList<>();
    private final boolean[] expanded = {true, true, true, true, true};
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

    // ---- mutable copies (loadFromConfig → widget edits → saveToConfig) ----
    private ChatBubbleTheme theme;
    private boolean enabled, redDotEnabled, hideChatIcon, animationEnabled;
    private boolean strongHintEnabled, systemChatAsBubble, systemBannerEnabled;
    private boolean antiSpam, chatHistoryEnabled;
    private boolean soundPublic, soundSystem, soundWhisper;
    private boolean debugLog, preserveInput, colorCodes;
    private boolean mentionBannerEnabled, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner;
    private boolean blurEnabled, ownMentionNotify, ownQuoteNotify, ownWhisperNotify;
    private int mentionBannerDuration, timeSeparatorMinutes;
    private int panelWidth, bubbleCornerRadius, panelOpacity, soundVolume, bannerCornerRadius;
    private int historyRetentionDays;
    private String ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor;
    private List<String> sidebarHidePatterns;

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

    private record Opt(String key, WidgetFactory factory, Supplier<String> previewColor) {
        static Opt header(String key) { return new Opt(key, null, null); }
        boolean isHeader() { return factory == null; }
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

    private void trackConfigFields() {
        tracked.clear();
        tracked.add(track(() -> theme.name().toLowerCase(), v -> { try { theme = ChatBubbleTheme.valueOf(v.toUpperCase()); } catch (Exception e) { theme = ChatBubbleTheme.DARK; } }));
        tracked.add(track(() -> enabled, v -> enabled = v));
        tracked.add(track(() -> redDotEnabled, v -> redDotEnabled = v));
        tracked.add(track(() -> hideChatIcon, v -> hideChatIcon = v));
        tracked.add(track(() -> animationEnabled, v -> animationEnabled = v));
        tracked.add(track(() -> strongHintEnabled, v -> strongHintEnabled = v));
        tracked.add(track(() -> systemChatAsBubble, v -> systemChatAsBubble = v));
        tracked.add(track(() -> antiSpam, v -> antiSpam = v));
        tracked.add(track(() -> chatHistoryEnabled, v -> chatHistoryEnabled = v));
        tracked.add(track(() -> historyRetentionDays, v -> historyRetentionDays = v));
        tracked.add(track(() -> timeSeparatorMinutes, v -> timeSeparatorMinutes = v));
        tracked.add(track(() -> preserveInput, v -> preserveInput = v));
        tracked.add(track(() -> colorCodes, v -> colorCodes = v));
        tracked.add(track(() -> new ArrayList<>(sidebarHidePatterns), v -> sidebarHidePatterns = new ArrayList<>(v)));
        tracked.add(track(() -> ownBubbleColor, v -> ownBubbleColor = v));
        tracked.add(track(() -> otherBubbleColor, v -> otherBubbleColor = v));
        tracked.add(track(() -> bubbleCornerRadius, v -> bubbleCornerRadius = v));
        tracked.add(track(() -> ownTextColor, v -> ownTextColor = v));
        tracked.add(track(() -> otherTextColor, v -> otherTextColor = v));
        tracked.add(track(() -> panelWidth, v -> panelWidth = v));
        tracked.add(track(() -> blurEnabled, v -> blurEnabled = v));
        tracked.add(track(() -> panelOpacity, v -> panelOpacity = v));
        tracked.add(track(() -> debugLog, v -> debugLog = v));
        tracked.add(track(() -> soundSystem, v -> soundSystem = v));
        tracked.add(track(() -> soundWhisper, v -> soundWhisper = v));
        tracked.add(track(() -> soundPublic, v -> soundPublic = v));
        tracked.add(track(() -> soundVolume, v -> soundVolume = v));
        tracked.add(track(() -> mentionBannerEnabled, v -> mentionBannerEnabled = v));
        tracked.add(track(() -> mentionBannerDuration, v -> mentionBannerDuration = v));
        tracked.add(track(() -> mentionSoundEnabled, v -> mentionSoundEnabled = v));
        tracked.add(track(() -> mentionRequireAt, v -> mentionRequireAt = v));
        tracked.add(track(() -> mentionWhisperBanner, v -> mentionWhisperBanner = v));
        tracked.add(track(() -> ownMentionNotify, v -> ownMentionNotify = v));
        tracked.add(track(() -> ownQuoteNotify, v -> ownQuoteNotify = v));
        tracked.add(track(() -> ownWhisperNotify, v -> ownWhisperNotify = v));
        tracked.add(track(() -> bannerCornerRadius, v -> bannerCornerRadius = v));
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
            strongHintEnabled, systemChatAsBubble,
            //#if MC < 12111
            systemBannerEnabled,
            //#endif
            antiSpam,
            chatHistoryEnabled, historyRetentionDays, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes,
            sidebarHidePatterns,
            ChatBubbleClientSetup.config().quickChatPhrases(),
            mentionBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            blurEnabled, panelOpacity, soundVolume, ownMentionNotify, ownQuoteNotify, ownWhisperNotify, bannerCornerRadius));
    }

    private void loadFromConfig() {
        var cfg = ChatBubbleClientSetup.config();
        try { theme = ChatBubbleTheme.valueOf(cfg.theme().toUpperCase()); } catch (Exception e) { theme = ChatBubbleTheme.DARK; }
        enabled = cfg.enabled(); redDotEnabled = cfg.redDotEnabled();
        hideChatIcon = cfg.hideChatIcon(); animationEnabled = cfg.animationEnabled();
        strongHintEnabled = cfg.strongHintEnabled();
        systemChatAsBubble = cfg.systemChatAsBubble();
        //#if MC < 12111
        systemBannerEnabled = cfg.systemBannerEnabled();
        //#endif
        antiSpam = cfg.antiSpam();
        chatHistoryEnabled = cfg.chatHistoryEnabled();
        soundPublic = cfg.soundPublic();
        soundSystem = cfg.soundSystem();
        soundWhisper = cfg.soundWhisper(); debugLog = cfg.debugLog();
        preserveInput = cfg.preserveInput(); colorCodes = cfg.colorCodes();
        mentionBannerEnabled = cfg.mentionBannerEnabled();
        mentionBannerDuration = cfg.mentionBannerDuration();
        mentionSoundEnabled = cfg.mentionSoundEnabled();
        mentionRequireAt = cfg.mentionRequireAt();
        mentionWhisperBanner = cfg.mentionWhisperBanner();
        blurEnabled = cfg.blurEnabled(); panelOpacity = cfg.panelOpacity();
        soundVolume = cfg.soundVolume();
        ownMentionNotify = cfg.ownMentionNotify(); ownQuoteNotify = cfg.ownQuoteNotify();
        ownWhisperNotify = cfg.ownWhisperNotify();
        bannerCornerRadius = cfg.bannerCornerRadius();
        historyRetentionDays = cfg.historyRetentionDays();
        timeSeparatorMinutes = cfg.timeSeparatorMinutes(); panelWidth = cfg.panelWidth();
        bubbleCornerRadius = cfg.bubbleCornerRadius();
        ownBubbleColor = cfg.ownBubbleColor(); otherBubbleColor = cfg.otherBubbleColor();
        ownTextColor = cfg.ownTextColor(); otherTextColor = cfg.otherTextColor();
        sidebarHidePatterns = new ArrayList<>(cfg.sidebarHidePatterns());
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
        rAnimFrom = scrollOffset;
        rAnimTo = MathHelper.clamp(target, 0, calcMaxScroll());
        rAnimStart = Util.getMeasuringTimeMs();
        rAnimDur = dur;
        rAnimOn = true;
    }

    private void startT(float target, int dur) {
        tAnimFrom = treeScroll;
        tAnimTo = MathHelper.clamp(target, 0, calcTreeMaxScroll());
        tAnimStart = Util.getMeasuringTimeMs();
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
        scrollOffset = MathHelper.clamp(scrollOffset, 0, calcMaxScroll());
        treeScroll = MathHelper.clamp(treeScroll, 0, calcTreeMaxScroll());
        relayoutWidgets();
    }

    private void drawBar(Object g, int trackX, int top, int bot,
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
        int y = viewTop() - scrollOffset;
        int wi = 0;
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) { y += HEADER_H; continue; }
            if (wi < scrollWidgets.size()) {
                ClickableWidget w = scrollWidgets.get(wi++);
                GuiCompat.setWidgetY(w, y);
                w.visible = y >= viewTop() && y + 20 <= viewBottom();
            }
            y += ROW_H;
        }
    }

    private int optAreaW() { return previewX - optLabelX - 4; }
    private int paletteX() { return inputX - 8 - PALETTE_W; }
    private int viewTop() { return showPreview() ? START_Y + PREVIEW_H + PREVIEW_GAP : START_Y; }
    private int viewBottom() { return height - 40; }

    private int calcMaxScroll() {
        int total = 0;
        for (Opt opt : visibleOpts())
            total += opt.isHeader() ? HEADER_H : ROW_H;
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

    private void buildCats() {
        if (cats != null) return;
        cats = new ArrayList<>();

        List<Opt> chat = new ArrayList<>();
        chat.add(Opt.header("e33chat.config.section.panel"));
        chat.add(new Opt("e33chat.config.theme", this::mkThemeButton, null));
        chat.add(new Opt("e33chat.config.panel_width",
            y -> mkIntBox(y, String.valueOf(panelWidth), 800, 1600, 4, v -> panelWidth = v), null));
        chat.add(new Opt("e33chat.config.blur_enabled", y -> mkBoolButton(y, () -> blurEnabled, v -> blurEnabled = v), null));
        chat.add(new Opt("e33chat.config.panel_opacity",
            y -> mkIntBox(y, String.valueOf(panelOpacity), 0, 100, 3, v -> panelOpacity = v), null));
        chat.add(new Opt("e33chat.config.animation", y -> mkBoolButton(y, () -> animationEnabled, v -> animationEnabled = v), null));
        chat.add(Opt.header("e33chat.config.section.bubble_font"));
        chat.add(new Opt("e33chat.config.bubble_corner_radius",
            y -> mkIntBox(y, String.valueOf(bubbleCornerRadius), 0, 10, 2, v -> bubbleCornerRadius = v), null));
        chat.add(new Opt("e33chat.config.own_bubble_color",
            y -> mkHexBox(y, ownBubbleColor, v -> ownBubbleColor = v),
            () -> ownBubbleColor));
        chat.add(new Opt("e33chat.config.other_bubble_color",
            y -> mkHexBox(y, otherBubbleColor, v -> otherBubbleColor = v),
            () -> otherBubbleColor));
        chat.add(new Opt("e33chat.config.own_text_color",
            y -> mkHexBox(y, ownTextColor, v -> ownTextColor = v),
            () -> ownTextColor));
        chat.add(new Opt("e33chat.config.other_text_color",
            y -> mkHexBox(y, otherTextColor, v -> otherTextColor = v),
            () -> otherTextColor));
        chat.add(Opt.header("e33chat.config.section.msgdisplay"));
        chat.add(new Opt("e33chat.config.enabled", y -> mkBoolButton(y, () -> enabled, v -> enabled = v), null));
        chat.add(new Opt("e33chat.config.system_chat_as_bubble", y -> mkBoolButton(y, () -> systemChatAsBubble, v -> systemChatAsBubble = v), null));
        chat.add(new Opt("e33chat.config.anti_spam", y -> mkBoolButton(y, () -> antiSpam, v -> antiSpam = v), null));
        chat.add(new Opt("e33chat.config.time_separator", this::mkTimeSepButton, null));
        chat.add(new Opt("e33chat.config.color_codes", y -> mkBoolButton(y, () -> colorCodes, v -> colorCodes = v), null));
        cats.add(new Cat("e33chat.config.cat.chat", chat));

        List<Opt> hud = new ArrayList<>();
        hud.add(Opt.header("e33chat.config.section.icon"));
        hud.add(new Opt("e33chat.config.red_dot", y -> mkBoolButton(y, () -> redDotEnabled, v -> redDotEnabled = v), null));
        hud.add(new Opt("e33chat.config.hide_chat_icon", y -> mkBoolButton(y, () -> hideChatIcon, v -> hideChatIcon = v), null));
        cats.add(new Cat("e33chat.config.cat.hud", hud));

        // 通知: 按消息类型分（@ / 私聊 / 系统）+ 横幅通用 + 音效
        List<Opt> notify = new ArrayList<>();
        notify.add(Opt.header("e33chat.config.section.mention"));
        notify.add(new Opt("e33chat.config.mention_banner_enabled", y -> mkBoolButton(y, () -> mentionBannerEnabled, v -> mentionBannerEnabled = v), null));
        notify.add(new Opt("e33chat.config.mention_sound_enabled", y -> mkBoolButton(y, () -> mentionSoundEnabled, v -> mentionSoundEnabled = v), null));
        notify.add(new Opt("e33chat.config.mention_require_at", y -> mkBoolButton(y, () -> mentionRequireAt, v -> mentionRequireAt = v), null));
        notify.add(Opt.header("e33chat.config.section.whisper"));
        notify.add(new Opt("e33chat.config.mention_whisper_banner", y -> mkBoolButton(y, () -> mentionWhisperBanner, v -> mentionWhisperBanner = v), null));
        notify.add(new Opt("e33chat.config.sound_whisper", y -> mkBoolButton(y, () -> soundWhisper, v -> soundWhisper = v), null));
        notify.add(Opt.header("e33chat.config.section.system"));
        //#if MC < 12111
        notify.add(new Opt("e33chat.config.system_banner_enabled", y -> mkBoolButton(y, () -> systemBannerEnabled, v -> systemBannerEnabled = v), null));
        //#endif
        notify.add(new Opt("e33chat.config.sound_system", y -> mkBoolButton(y, () -> soundSystem, v -> soundSystem = v), null));
        notify.add(Opt.header("e33chat.config.section.banner"));
        notify.add(new Opt("e33chat.config.mention_banner_duration",
            y -> mkIntBox(y, String.valueOf(mentionBannerDuration), 2, 10, 2, v -> mentionBannerDuration = v), null));
        notify.add(new Opt("e33chat.config.banner_corner_radius",
            y -> mkIntBox(y, String.valueOf(bannerCornerRadius), 0, 10, 2, v -> bannerCornerRadius = v), null));
        notify.add(Opt.header("e33chat.config.section.sound"));
        notify.add(new Opt("e33chat.config.sound_volume",
            y -> mkIntSlider(y, () -> soundVolume, v -> soundVolume = v, 0, 100), null));
        notify.add(new Opt("e33chat.config.sound_public", y -> mkBoolButton(y, () -> soundPublic, v -> soundPublic = v), null));
        cats.add(new Cat("e33chat.config.cat.notify", notify));

        List<Opt> sidebar = new ArrayList<>();
        sidebar.add(Opt.header("e33chat.config.section.playerlist"));
        sidebar.add(new Opt("e33chat.config.sidebar_hide_patterns",
            y -> mkPatternBox(y, new ArrayList<>(sidebarHidePatterns), v -> sidebarHidePatterns = v), null));
        cats.add(new Cat("e33chat.config.cat.sidebar", sidebar));

        List<Opt> advanced = new ArrayList<>();
        advanced.add(Opt.header("e33chat.config.section.history"));
        advanced.add(new Opt("e33chat.config.chat_history", y -> mkBoolButton(y, () -> chatHistoryEnabled, v -> chatHistoryEnabled = v), null));
        advanced.add(new Opt("e33chat.config.history_retention", y -> mkIntBox(y, String.valueOf(historyRetentionDays), 0, 365, 3, v -> historyRetentionDays = v), null));
        advanced.add(new Opt("e33chat.config.preserve_input", y -> mkBoolButton(y, () -> preserveInput, v -> preserveInput = v), null));
        advanced.add(Opt.header("e33chat.config.section.debug"));
        advanced.add(new Opt("e33chat.config.debug_log", y -> mkBoolButton(y, () -> debugLog, v -> debugLog = v), null));
        advanced.add(new Opt("e33chat.config.own_mention_notify", y -> mkBoolButton(y, () -> ownMentionNotify, v -> ownMentionNotify = v), null));
        advanced.add(new Opt("e33chat.config.own_quote_notify", y -> mkBoolButton(y, () -> ownQuoteNotify, v -> ownQuoteNotify = v), null));
        advanced.add(new Opt("e33chat.config.own_whisper_notify", y -> mkBoolButton(y, () -> ownWhisperNotify, v -> ownWhisperNotify = v), null));
        cats.add(new Cat("e33chat.config.cat.advanced", advanced));
    }

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(com.niuqu.chatbubble.Txt.translatable("e33chat.config.title"));
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

        scrollOffset = MathHelper.clamp(scrollOffset, 0, calcMaxScroll());
        treeScroll = MathHelper.clamp(treeScroll, 0, calcTreeMaxScroll());

        int y = viewTop() - scrollOffset;
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) { y += HEADER_H; continue; }
            ClickableWidget w = opt.factory().create(y);
            w.visible = y >= viewTop() && y + 20 <= viewBottom();
            scrollWidgets.add(GuiCompat.addDrawableChild(this, w));
            y += ROW_H;
        }

        doneBtn = GuiCompat.addDrawableChild(this, GuiCompat.button(GuiCompat.doneText(), btn -> doClose(),
            width / 2 - 100, height - 32, 200, 20));
        exitBtn = GuiCompat.addDrawableChild(this, GuiCompat.button(com.niuqu.chatbubble.Txt.translatable("e33chat.config.exit"), btn -> doExit(),
            width / 2 - 104, height - 32, 100, 20));
        saveBtn = GuiCompat.addDrawableChild(this, GuiCompat.button(com.niuqu.chatbubble.Txt.translatable("e33chat.config.save"), btn -> doClose(),
            width / 2 + 4, height - 32, 100, 20));
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
        scrollOffset = 0;
        setFocused(null);
        GuiCompat.clearChildren(this);
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
        return GuiCompat.button(
            com.niuqu.chatbubble.Txt.translatable("e33chat.theme." + theme.name().toLowerCase()),
            btn -> {
                int next = (theme.ordinal() + 1) % ChatBubbleTheme.values().length;
                theme = ChatBubbleTheme.values()[next];
                btn.setMessage(com.niuqu.chatbubble.Txt.translatable("e33chat.theme." + theme.name().toLowerCase()));
            },
            inputX, y, INPUT_W, 20
        );
    }

    private ButtonWidget mkBoolButton(int y, java.util.function.BooleanSupplier getter, java.util.function.Consumer<Boolean> setter) {
        boolean v = getter.getAsBoolean();
        return GuiCompat.button(
            v ? GuiCompat.onText() : GuiCompat.offText(),
            btn -> {
                boolean nv = !getter.getAsBoolean();
                setter.accept(nv);
                btn.setMessage(nv ? GuiCompat.onText() : GuiCompat.offText());
            },
            inputX, y, INPUT_W, 20
        );
    }

    private SliderWidget mkIntSlider(int y, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter, int min, int max) {
        return new IntSlider(inputX, y, INPUT_W, 20, getter, setter, min, max);
    }

    private static class IntSlider extends SliderWidget {
        private final java.util.function.IntSupplier getter;
        private final java.util.function.IntConsumer setter;
        private final int min, max;

        IntSlider(int x, int y, int w, int h, java.util.function.IntSupplier getter, java.util.function.IntConsumer setter, int min, int max) {
            super(x, y, w, h, com.niuqu.chatbubble.Txt.literal(String.valueOf(getter.getAsInt())),
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
            setMessage(com.niuqu.chatbubble.Txt.literal(String.valueOf(getter.getAsInt())));
        }
    }

    private static final int[] TIME_SEP_PRESETS = {1, 5, 10, 15, 30, 0};

    private ButtonWidget mkTimeSepButton(int y) {
        int cur = timeSeparatorMinutes;
        String label = cur == 0 ? com.niuqu.chatbubble.Txt.translatable("e33chat.config.time_separator.disable").getString()
            : cur + " " + com.niuqu.chatbubble.Txt.translatable("e33chat.config.time_separator.minute").getString();
        return GuiCompat.button(com.niuqu.chatbubble.Txt.literal(label), btn -> {
            int idx = -1;
            for (int i = 0; i < TIME_SEP_PRESETS.length; i++) {
                if (TIME_SEP_PRESETS[i] == timeSeparatorMinutes) { idx = i; break; }
            }
            int next = TIME_SEP_PRESETS[(idx + 1) % TIME_SEP_PRESETS.length];
            timeSeparatorMinutes = next;
            String nl = next == 0 ? com.niuqu.chatbubble.Txt.translatable("e33chat.config.time_separator.disable").getString()
                : next + " " + com.niuqu.chatbubble.Txt.translatable("e33chat.config.time_separator.minute").getString();
            btn.setMessage(com.niuqu.chatbubble.Txt.literal(nl));
        }, inputX, y, INPUT_W, 20);
    }

    private TextFieldWidget mkHexBox(int y, String initial, java.util.function.Consumer<String> onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, com.niuqu.chatbubble.Txt.literal(""));
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
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, com.niuqu.chatbubble.Txt.literal(""));
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
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, com.niuqu.chatbubble.Txt.literal(""));
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
    //#if MC >= 12000
    //#if MC >= 26000
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    //#else
    public void render(DrawContext g, int mouseX, int mouseY, float partialTick) {
    //#endif
    //#else
    //$$ public void render(MatrixStack g, int mouseX, int mouseY, float partialTick) {
    //#endif
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g,
            com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.CONFIG_BG, ChatBubbleTheme.DARK),
            0, 0, width, height, 0xC0 / 255f);
        tickAnims();
        RenderHelper.drawText(g, textRenderer, title, width / 2 - textRenderer.getWidth(title) / 2, 14, c().configTitle(), false);

        String tooltipKey = null;

        // 左侧标签树
        RenderHelper.enableScissor(g, CAT_X, START_Y, dividerX, viewBottom());
        int ly = START_Y - treeScroll;
        for (int i = 0; i < cats.size(); i++) {
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= CAT_X && mouseX <= CAT_X + CAT_W && mouseY >= ly && mouseY < ly + CAT_ROW_H;
            if (sel || hover)
                RenderHelper.fill(g, CAT_X, ly, CAT_X + CAT_W, ly + CAT_ROW_H, c().iconHover());
            if (sel)
                RenderHelper.fill(g, CAT_X, ly, CAT_X + 2, ly + CAT_ROW_H, c().configTitle());
            drawTriangle(g, CAT_X + 6, ly + (CAT_ROW_H - 5) / 2, expanded[i],
                sel ? c().configTitle() : c().configLabel());
            RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable(cats.get(i).key()), CAT_X + 18, ly + (CAT_ROW_H - 8) / 2,
                sel ? c().configTitle() : c().configLabel(), false);
            ly += CAT_ROW_H;
            if (expanded[i]) {
                int sub = 0;
                for (Opt o : cats.get(i).opts()) {
                    if (!o.isHeader()) continue;
                    boolean selSub = i == selectedCat && sub == selectedSub;
                    boolean sh = mouseX >= CAT_X + 14 && mouseX <= CAT_X + CAT_W && mouseY >= ly && mouseY < ly + SUB_ROW_H;
                    if (selSub || sh)
                        RenderHelper.fill(g, CAT_X + 14, ly, CAT_X + CAT_W, ly + SUB_ROW_H, c().iconHover());
                    if (selSub)
                        RenderHelper.fill(g, CAT_X + 14, ly, CAT_X + 16, ly + SUB_ROW_H, c().configTitle());
                    RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable(o.key()), CAT_X + 24, ly + (SUB_ROW_H - 8) / 2,
                        (selSub || sh) ? c().configTitle() : c().configLabel(), false);
                    sub++;
                    ly += SUB_ROW_H;
                }
            }
        }
        RenderHelper.disableScissor(g);
        drawBar(g, tTrackX(), START_Y, viewBottom(), tTotalH(), treeScroll, calcTreeMaxScroll(), mouseX, mouseY, tBarDrag);

        RenderHelper.drawTexture(g, com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
            dividerX, START_Y - 6, 0f, 0f, 1, viewBottom() - (START_Y - 6), 1, 1);

        if (showPreview()) drawBubblePreview(g);

        RenderHelper.enableScissor(g, optLabelX - 4, viewTop(), width, viewBottom());
        int y = viewTop() - scrollOffset;
        for (Opt opt : visibleOpts()) {
            if (opt.isHeader()) {
                Text label = com.niuqu.chatbubble.Txt.translatable(opt.key());
                RenderHelper.drawText(g, textRenderer, label, optLabelX, y + 11, c().configLabel(), false);
                int lineX = optLabelX + textRenderer.getWidth(label) + 8;
                int lineEnd = optLabelX + optAreaW() + 4;
                if (lineX < lineEnd)
                    RenderHelper.drawTexture(g, com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
                        lineX, y + 15, 0f, 0f, lineEnd - lineX, 1, 1, 1);
                y += HEADER_H;
                continue;
            }
            RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable(opt.key()), optLabelX, y + 6, c().configLabel(), false);
            if (opt.previewColor() != null) {
                drawPreview(g, y + 3, opt.previewColor().get());
                int px = paletteX();
                for (int i = 0; i < PALETTE.length; i++) {
                    int bx = px + i * 10, by = y + 12;
                    RenderHelper.fill(g, bx, by, bx + 8, by + 8, c().iconHover());
                    RenderHelper.fill(g, bx + 1, by + 1, bx + 7, by + 7, ChatBubbleConfig.parseHexColor(PALETTE[i], 0xFF000000));
                }
            }
            if (y >= viewTop() && y + 20 <= viewBottom()
                && mouseX >= optLabelX - 4 && mouseX <= inputX - 10 && mouseY >= y && mouseY <= y + 20)
                tooltipKey = opt.key() + ".desc";
            y += ROW_H;
        }
        RenderHelper.disableScissor(g);
        drawBar(g, rTrackX(), viewTop(), viewBottom(), rTotalH(), scrollOffset, calcMaxScroll(), mouseX, mouseY, rBarDrag);

        int changed = changeCount();
        doneBtn.visible = changed == 0;
        exitBtn.visible = changed > 0;
        saveBtn.visible = changed > 0;

        super.render(g, mouseX, mouseY, partialTick);

        if (changed > 0)
            RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable("e33chat.config.changed", changed),
                width / 2 + 112, height - 26, c().configLabel(), false);

        if (tooltipKey != null)
            GuiCompat.renderTooltip(g, this, com.niuqu.chatbubble.Txt.translatable(tooltipKey), mouseX, mouseY);
    }

    private void drawBubblePreview(Object g) {
        int top = START_Y;
        int other = ChatBubbleConfig.parseHexColor(otherBubbleColor, 0xFF4A4A4A);
        int own = ChatBubbleConfig.parseHexColor(ownBubbleColor, ACCENT);
        int otherT = ChatBubbleConfig.parseHexColor(otherTextColor, 0xFFFFFFFF);
        int ownT = ChatBubbleConfig.parseHexColor(ownTextColor, 0xFFFFFFFF);
        float rad = bubbleCornerRadius;
        Text otherMsg = com.niuqu.chatbubble.Txt.translatable("e33chat.config.preview.sample_other");
        Text ownMsg = com.niuqu.chatbubble.Txt.translatable("e33chat.config.preview.sample_own");
        int maxW = (optAreaW() - 8) / 2;
        int ow = Math.min(textRenderer.getWidth(otherMsg) + 8, maxW);
        RoundRectRenderer.fill(g, optLabelX, top + 4, optLabelX + ow, top + 18, rad, other);
        RenderHelper.drawText(g, textRenderer, otherMsg, optLabelX + 4, top + 7, otherT, false);
        int mw = Math.min(textRenderer.getWidth(ownMsg) + 8, maxW);
        int mx = optLabelX + optAreaW() - mw;
        RoundRectRenderer.fill(g, mx, top + 22, mx + mw, top + 36, rad, own);
        RenderHelper.drawText(g, textRenderer, ownMsg, mx + 4, top + 25, ownT, false);
        RenderHelper.drawTexture(g, com.niuqu.chatbubble.texture.UiTextureManager.rl(com.niuqu.chatbubble.texture.UiElement.DIVIDER, ChatBubbleTheme.DARK),
            optLabelX - 4, top + PREVIEW_H - 1, 0f, 0f, optAreaW() + 8, 1, 1, 1);
    }

    private void drawPreview(Object g, int y, String hex) {
        int color = ChatBubbleConfig.parseHexColor(hex, 0xFF000000);
        RenderHelper.fill(g, previewX, y, previewX + 14, y + 14, c().iconHover());
        RenderHelper.fill(g, previewX + 1, y + 1, previewX + 13, y + 13, color);
    }

    private void drawTriangle(Object g, int x, int y, boolean down, int color) {
        if (down) {
            RenderHelper.fill(g, x, y, x + 5, y + 1, color);
            RenderHelper.fill(g, x + 1, y + 1, x + 4, y + 2, color);
            RenderHelper.fill(g, x + 2, y + 2, x + 3, y + 3, color);
        } else {
            RenderHelper.fill(g, x, y, x + 1, y + 1, color);
            RenderHelper.fill(g, x, y + 1, x + 2, y + 2, color);
            RenderHelper.fill(g, x, y + 2, x + 3, y + 3, color);
            RenderHelper.fill(g, x, y + 3, x + 2, y + 4, color);
            RenderHelper.fill(g, x, y + 4, x + 1, y + 5, color);
        }
    }

    //#if MC >= 12004
    //#if MC >= 26000
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float partialTick) {
    //#else
    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float partialTick) {
    //#endif
        // no-op：背景已在 render() 开头画，避免 1.21.1 batch 缓冲叠暗文字
    }
    //#else
    //#if MC >= 12000
    //$$ @Override
    //$$ public void renderBackground(DrawContext g) {
    //$$     // no-op：背景已在 render() 开头画
    //$$ }
    //#else
    //$$ @Override
    //$$ public void renderBackground(MatrixStack g) {
    //$$     // no-op：背景已在 render() 开头画
    //$$ }
    //#endif
    //#endif

    // ---- input ----

    @Override
    //#if MC >= 12109
    public boolean mouseClicked(Click click, boolean inside) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //#else
        //$$ public boolean mouseClicked(double mouseX, double mouseY, int button) {
        //#endif
        int w = SCROLLBAR_W;
        int rMax = calcMaxScroll();
        if (rMax > 0 && mouseX >= rTrackX() && mouseX < rTrackX() + w
                && mouseY >= viewTop() && mouseY < viewBottom()) {
            int th = sbThumbH(rTrackH(), rTotalH());
            int ty = sbThumbY(viewTop(), rTrackH(), th, scrollOffset, rMax);
            if (mouseY < ty) startR(scrollOffset - rTrackH(), 120);
            else if (mouseY > ty + th) startR(scrollOffset + rTrackH(), 120);
            else { rBarDrag = true; rBarDragY = (int) mouseY; rBarDragOff = scrollOffset; }
            return true;
        }
        int tMax = calcTreeMaxScroll();
        if (tMax > 0 && mouseX >= tTrackX() && mouseX < tTrackX() + w
                && mouseY >= START_Y && mouseY < viewBottom()) {
            int th = sbThumbH(tTrackH(), tTotalH());
            int ty = sbThumbY(START_Y, tTrackH(), th, treeScroll, tMax);
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
                int y = viewTop() - scrollOffset;
                int wi = 0;
                for (Opt opt : visibleOpts()) {
                    if (opt.isHeader()) { y += HEADER_H; continue; }
                    if (opt.previewColor() != null && mouseY >= y + 12 && mouseY < y + 20) {
                        int idx = MathHelper.clamp((int) (mouseX - px) / 10, 0, PALETTE.length - 1);
                        String hex = PALETTE[idx];
                        String key = opt.key();
                        switch (key) {
                            case "e33chat.config.own_bubble_color" -> ownBubbleColor = hex;
                            case "e33chat.config.other_bubble_color" -> otherBubbleColor = hex;
                            case "e33chat.config.own_text_color" -> ownTextColor = hex;
                            case "e33chat.config.other_text_color" -> otherTextColor = hex;
                        }
                        if (wi < scrollWidgets.size() && scrollWidgets.get(wi) instanceof TextFieldWidget eb)
                            eb.setText(hex);
                        return true;
                    }
                    wi++;
                    y += ROW_H;
                }
            }
        }
        //#if MC >= 12109
        return super.mouseClicked(click, inside);
        //#else
        //$$ return super.mouseClicked(mouseX, mouseY, button);
        //#endif
    }

    @Override
    //#if MC >= 12004
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    //#else
    //$$ public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
    //#endif
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
    //#if MC >= 12109
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        //#else
        //$$ public boolean mouseDragged(double mouseX, double mouseY, int button, double dx, double dy) {
        //#endif
        if (rBarDrag && calcMaxScroll() > 0) {
            int travel = rTrackH() - sbThumbH(rTrackH(), rTotalH());
            if (travel > 0) {
                int d = (int) mouseY - rBarDragY;
                startR(rBarDragOff + (float) d * calcMaxScroll() / travel, 80);
            }
            return true;
        }
        if (tBarDrag && calcTreeMaxScroll() > 0) {
            int travel = tTrackH() - sbThumbH(tTrackH(), tTotalH());
            if (travel > 0) {
                int d = (int) mouseY - tBarDragY;
                startT(tBarDragOff + (float) d * calcTreeMaxScroll() / travel, 80);
            }
            return true;
        }
        //#if MC >= 12109
        return super.mouseDragged(click, dx, dy);
        //#else
        //$$ return super.mouseDragged(mouseX, mouseY, button, dx, dy);
        //#endif
    }

    @Override
    //#if MC >= 12109
    public boolean mouseReleased(Click click) {
        rBarDrag = false;
        tBarDrag = false;
        return super.mouseReleased(click);
        //#else
        //$$ public boolean mouseReleased(double mouseX, double mouseY, int button) {
        //$$     rBarDrag = false;
        //$$     tBarDrag = false;
        //$$     return super.mouseReleased(mouseX, mouseY, button);
        //#endif
    }

    private void doClose() {
        saveAll();
        GuiCompat.setScreen(client, lastScreen);
    }

    private void doExit() {
        revertAll();
        GuiCompat.setScreen(client, lastScreen);
    }

    //#if MC >= 11700
    @Override
    public void close() {
    //#else
    //$$ @Override
    //$$ public void onClose() {
    //#endif
        int changed = changeCount();
        if (changed > 0) {
            GuiCompat.setScreen(client, new ConfirmScreen((BooleanConsumer) confirmed -> {
                if (confirmed) doExit();
                else GuiCompat.setScreen(client, this);
            },
                com.niuqu.chatbubble.Txt.translatable("e33chat.config.discard.title"),
                com.niuqu.chatbubble.Txt.translatable("e33chat.config.discard.message", changed)));
        } else {
            doClose();
        }
    }
}
