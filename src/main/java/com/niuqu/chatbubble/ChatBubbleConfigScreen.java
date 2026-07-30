package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.text.Text;
import net.minecraft.util.math.MathHelper;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class ChatBubbleConfigScreen extends Screen {
    private final Screen lastScreen;

    private ChatBubbleTheme.Colors c() { return ChatBubbleTheme.DARK.colors(); }

    private static final int ROW_H = 28;
    private static final int HEADER_H = 16;
    private static final int START_Y = 40;
    private static final int CAT_X = 24;
    private static final int CAT_W = 86;
    private static final int CAT_ROW_H = 22;
    private static final int INPUT_W = 90;

    private int dividerX, optLabelX, inputX, previewX;
    private int selectedCat;
    private int scrollOffset;
    private final List<ClickableWidget> scrollWidgets = new ArrayList<>();

    // Mutable copies of config values
    private boolean enabled, redDotEnabled, hideChatIcon, animationEnabled;
    private boolean strongHintEnabled, systemChatAsBubble;
    private boolean antiSpam, chatHistoryEnabled;
    private boolean previewEnabled, soundPublic, soundSystem, soundWhisper;
    private boolean debugLog, preserveInput, colorCodes;
    private boolean mentionBannerEnabled, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner;
    private int mentionBannerDuration;
    private int previewLines, previewWidth, timeSeparatorMinutes, panelWidth, bubbleCornerRadius;
    private String theme, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor;
    private List<String> sidebarHidePatterns;

    private interface WidgetFactory { ClickableWidget create(int y); }

    private record Opt(String key, WidgetFactory factory, Supplier<String> previewColor) {
        static Opt header(String key) { return new Opt(key, null, null); }
        boolean isHeader() { return factory == null; }
    }

    private record Cat(String key, List<Opt> opts) {}
    private List<Cat> cats;

    private void loadFromConfig() {
        var cfg = ChatBubbleClientSetup.config();
        enabled = cfg.enabled(); redDotEnabled = cfg.redDotEnabled();
        hideChatIcon = cfg.hideChatIcon(); animationEnabled = cfg.animationEnabled();
        strongHintEnabled = cfg.strongHintEnabled();
        systemChatAsBubble = cfg.systemChatAsBubble(); antiSpam = cfg.antiSpam();
        chatHistoryEnabled = cfg.chatHistoryEnabled();
        previewEnabled = cfg.previewEnabled(); soundPublic = cfg.soundPublic();
        soundSystem = cfg.soundSystem();
        soundWhisper = cfg.soundWhisper(); debugLog = cfg.debugLog();
        preserveInput = cfg.preserveInput();
        colorCodes = cfg.colorCodes();
        mentionBannerEnabled = cfg.mentionBannerEnabled();
        mentionBannerDuration = cfg.mentionBannerDuration();
        mentionSoundEnabled = cfg.mentionSoundEnabled();
        mentionRequireAt = cfg.mentionRequireAt();
        mentionWhisperBanner = cfg.mentionWhisperBanner();
        previewLines = cfg.previewLines(); previewWidth = cfg.previewWidth();
        timeSeparatorMinutes = cfg.timeSeparatorMinutes(); panelWidth = cfg.panelWidth();
        bubbleCornerRadius = cfg.bubbleCornerRadius();
        theme = cfg.theme(); ownBubbleColor = cfg.ownBubbleColor();
        otherBubbleColor = cfg.otherBubbleColor(); ownTextColor = cfg.ownTextColor();
        otherTextColor = cfg.otherTextColor();
        sidebarHidePatterns = new ArrayList<>(cfg.sidebarHidePatterns());
    }

    private void saveToConfig() {
        ChatBubbleClientSetup.saveConfig(new ChatBubbleConfig(
            enabled, theme, redDotEnabled, hideChatIcon, animationEnabled,
            strongHintEnabled, systemChatAsBubble, antiSpam,
            chatHistoryEnabled, previewEnabled, previewLines, previewWidth, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundWhisper, debugLog, preserveInput, colorCodes,
            sidebarHidePatterns,
            ChatBubbleClientSetup.config().quickChatPhrases(),
            mentionBannerEnabled, mentionBannerDuration, mentionSoundEnabled, mentionRequireAt, mentionWhisperBanner,
            ChatBubbleClientSetup.config().blurEnabled(), ChatBubbleClientSetup.config().panelOpacity(),
            ChatBubbleClientSetup.config().soundVolume(),
            ChatBubbleClientSetup.config().ownMentionNotify(), ChatBubbleClientSetup.config().ownQuoteNotify(),
            ChatBubbleClientSetup.config().bannerCornerRadius()));
    }

    private void buildCats() {
        if (cats != null) return;
        cats = new ArrayList<>();

        // Appearance: panel / bubbles / text
        List<Opt> appearance = new ArrayList<>();
        appearance.add(Opt.header("e33chat.config.section.panel"));
        appearance.add(new Opt("theme", y -> ButtonWidget.builder(Text.literal(theme.toUpperCase()), btn -> {
            theme = theme.equalsIgnoreCase("dark") ? "light" : "dark";
            btn.setMessage(Text.literal(theme.toUpperCase()));
        }).dimensions(inputX, y, INPUT_W, 20).build(), null));
        appearance.add(new Opt("animation", y -> mkBoolBtn(y, animationEnabled, v -> animationEnabled = v), null));
        appearance.add(new Opt("panel_width", y -> mkIntBox(y, String.valueOf(panelWidth), 4, 800, 1600, v -> panelWidth = v), null));
        appearance.add(Opt.header("e33chat.config.section.bubbles"));
        appearance.add(new Opt("bubble_corner_radius", y -> mkIntBox(y, String.valueOf(bubbleCornerRadius), 2, 0, 10, v -> bubbleCornerRadius = v), null));
        appearance.add(new Opt("own_bubble_color", y -> mkHexBox(y, ownBubbleColor, v -> ownBubbleColor = v), () -> ownBubbleColor));
        appearance.add(new Opt("other_bubble_color", y -> mkHexBox(y, otherBubbleColor, v -> otherBubbleColor = v), () -> otherBubbleColor));
        appearance.add(Opt.header("e33chat.config.section.text"));
        appearance.add(new Opt("own_text_color", y -> mkHexBox(y, ownTextColor, v -> ownTextColor = v), () -> ownTextColor));
        appearance.add(new Opt("other_text_color", y -> mkHexBox(y, otherTextColor, v -> otherTextColor = v), () -> otherTextColor));
        cats.add(new Cat("e33chat.config.cat.appearance", appearance));

        // Notifications: HUD / mention / sounds
        List<Opt> notifications = new ArrayList<>();
        notifications.add(Opt.header("e33chat.config.section.hud"));
        notifications.add(new Opt("red_dot", y -> mkBoolBtn(y, redDotEnabled, v -> redDotEnabled = v), null));
        notifications.add(new Opt("hide_chat_icon", y -> mkBoolBtn(y, hideChatIcon, v -> hideChatIcon = v), null));
        notifications.add(new Opt("preview_enabled", y -> mkBoolBtn(y, previewEnabled, v -> previewEnabled = v), null));
        notifications.add(new Opt("preview_lines", y -> ButtonWidget.builder(Text.literal(String.valueOf(previewLines)), btn -> {
            previewLines = previewLines >= 8 ? 1 : previewLines + 1;
            btn.setMessage(Text.literal(String.valueOf(previewLines)));
        }).dimensions(inputX, y, INPUT_W, 20).build(), null));
        notifications.add(new Opt("preview_width", y -> mkIntBox(y, String.valueOf(previewWidth), 3, 50, 400, v -> previewWidth = v), null));
        notifications.add(new Opt("strong_hint", y -> mkBoolBtn(y, strongHintEnabled, v -> strongHintEnabled = v), null));
        notifications.add(Opt.header("e33chat.config.section.mention"));
        notifications.add(new Opt("mention_banner_enabled", y -> mkBoolBtn(y, mentionBannerEnabled, v -> mentionBannerEnabled = v), null));
        notifications.add(new Opt("mention_banner_duration", y -> mkIntBox(y, String.valueOf(mentionBannerDuration), 2, 2, 10, v -> mentionBannerDuration = v), null));
        notifications.add(new Opt("mention_sound_enabled", y -> mkBoolBtn(y, mentionSoundEnabled, v -> mentionSoundEnabled = v), null));
        notifications.add(new Opt("mention_require_at", y -> mkBoolBtn(y, mentionRequireAt, v -> mentionRequireAt = v), null));
        notifications.add(new Opt("mention_whisper_banner", y -> mkBoolBtn(y, mentionWhisperBanner, v -> mentionWhisperBanner = v), null));
        notifications.add(Opt.header("e33chat.config.section.sounds"));
        notifications.add(new Opt("sound_whisper", y -> mkBoolBtn(y, soundWhisper, v -> soundWhisper = v), null));
        notifications.add(new Opt("sound_system", y -> mkBoolBtn(y, soundSystem, v -> soundSystem = v), null));
        notifications.add(new Opt("sound_public", y -> mkBoolBtn(y, soundPublic, v -> soundPublic = v), null));
        cats.add(new Cat("e33chat.config.cat.notifications", notifications));

        // Behavior: general / messages / history
        List<Opt> behavior = new ArrayList<>();
        behavior.add(Opt.header("e33chat.config.section.general"));
        behavior.add(new Opt("enabled", y -> mkBoolBtn(y, enabled, v -> enabled = v), null));
        behavior.add(new Opt("time_separator", y -> {
            String label = timeSeparatorMinutes == 0
                ? Text.translatable("e33chat.config.time_separator.disable").getString()
                : timeSeparatorMinutes + " " + Text.translatable("e33chat.config.time_separator.minute").getString();
            return ButtonWidget.builder(Text.literal(label), btn -> {
                int[] presets = {1, 5, 10, 15, 30, 0};
                int idx = -1;
                for (int i = 0; i < presets.length; i++) if (presets[i] == timeSeparatorMinutes) { idx = i; break; }
                timeSeparatorMinutes = presets[(idx + 1) % presets.length];
                String nl = timeSeparatorMinutes == 0
                    ? Text.translatable("e33chat.config.time_separator.disable").getString()
                    : timeSeparatorMinutes + " " + Text.translatable("e33chat.config.time_separator.minute").getString();
                btn.setMessage(Text.literal(nl));
            }).dimensions(inputX, y, INPUT_W, 20).build();
        }, null));
        behavior.add(Opt.header("e33chat.config.section.messages"));
        behavior.add(new Opt("anti_spam", y -> mkBoolBtn(y, antiSpam, v -> antiSpam = v), null));
        behavior.add(new Opt("system_chat_as_bubble", y -> mkBoolBtn(y, systemChatAsBubble, v -> systemChatAsBubble = v), null));
        behavior.add(new Opt("color_codes", y -> mkBoolBtn(y, colorCodes, v -> colorCodes = v), null));
        behavior.add(Opt.header("e33chat.config.section.history"));
        behavior.add(new Opt("chat_history", y -> mkBoolBtn(y, chatHistoryEnabled, v -> chatHistoryEnabled = v), null));
        behavior.add(new Opt("preserve_input", y -> mkBoolBtn(y, preserveInput, v -> preserveInput = v), null));
        behavior.add(new Opt("sidebar_hide_patterns", y -> mkPatternBox(y, sidebarHidePatterns, v -> sidebarHidePatterns = v), null));
        cats.add(new Cat("e33chat.config.cat.behavior", behavior));

        cats.add(new Cat("e33chat.config.cat.advanced", List.of(
            new Opt("debug_log", y -> mkBoolBtn(y, debugLog, v -> debugLog = v), null))));
    }

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(Text.translatable("e33chat.config.title"));
        this.lastScreen = lastScreen;
    }

    @Override
    protected void init() {
        buildCats();
        loadFromConfig();
        scrollWidgets.clear();
        clearChildren();

        dividerX = CAT_X + CAT_W + 12;
        optLabelX = dividerX + 14;
        previewX = width - 26;
        inputX = previewX - 8 - INPUT_W;

        scrollOffset = MathHelper.clamp(scrollOffset, 0, calcMaxScroll());

        int y = START_Y - scrollOffset;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (opt.isHeader()) { y += HEADER_H + 2; continue; }
            ClickableWidget w = opt.factory().create(y);
            addDrawableChild(w);
            scrollWidgets.add(w);
            y += ROW_H;
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), btn -> onClose())
            .dimensions(width / 2 - 100, height - 32, 200, 20).build());
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        super.render(g, mouseX, mouseY, delta);
        g.drawText(textRenderer, title.getString(), width / 2 - textRenderer.getWidth(title) / 2, 14, c().configTitle(), false);

        for (int i = 0; i < cats.size(); i++) {
            int cy = START_Y + i * CAT_ROW_H;
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= CAT_X && mouseX <= CAT_X + CAT_W && mouseY >= cy && mouseY <= cy + CAT_ROW_H;
            if (sel || hover) g.fill(CAT_X, cy, CAT_X + CAT_W, cy + CAT_ROW_H, c().iconHover());
            if (sel) g.fill(CAT_X, cy, CAT_X + 2, cy + CAT_ROW_H, c().configTitle());
            String label = Text.translatable(cats.get(i).key()).getString();
            g.drawText(textRenderer, label, CAT_X + 8, cy + (CAT_ROW_H - 8) / 2,
                sel ? c().configTitle() : c().configLabel(), false);
        }

        g.fill(dividerX, START_Y - 6, dividerX + 1, height - 44, c().divider());

        int y = START_Y - scrollOffset;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (opt.isHeader()) {
                if (y > -HEADER_H && y < height) {
                    g.fill(optLabelX - 4, y, optLabelX + optAreaW() + 4, y + HEADER_H, c().configBg());
                    g.drawCenteredTextWithShadow(textRenderer, Text.translatable(opt.key()),
                        optLabelX + optAreaW() / 2, y + (HEADER_H - 8) / 2, c().configSection());
                }
                y += HEADER_H + 2;
                continue;
            }
            if (y > -ROW_H && y < height) {
                g.drawText(textRenderer, Text.translatable("e33chat.config." + opt.key()).getString(),
                    optLabelX, y + 6, c().configLabel(), false);
                if (opt.previewColor() != null) {
                    String colorVal = opt.previewColor().get();
                    if (colorVal != null) drawPreview(g, y + 3, colorVal);
                }
                if (mouseX >= optLabelX && mouseX <= inputX - 4 && mouseY >= y && mouseY <= y + ROW_H) {
                    Text desc = Text.translatable("e33chat.config." + opt.key() + ".desc");
                    g.drawTooltip(textRenderer, desc, mouseX, mouseY);
                }
            }
            y += ROW_H;
        }
    }

    private int optAreaW() { return previewX - optLabelX - 4; }

    private void drawPreview(DrawContext g, int y, String hex) {
        int color = ChatBubbleConfig.parseHexColor(hex, 0xFF000000);
        g.fill(previewX, y, previewX + 14, y + 14, c().iconHover());
        g.fill(previewX + 1, y + 1, previewX + 13, y + 13, color);
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            for (int i = 0; i < cats.size(); i++) {
                int cy = START_Y + i * CAT_ROW_H;
                if (mouseX >= CAT_X && mouseX <= CAT_X + CAT_W && mouseY >= cy && mouseY <= cy + CAT_ROW_H) {
                    switchCategory(i); return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    public void onClose() {
        saveToConfig();
        if (client != null) client.setScreen(lastScreen);
    }

    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
        g.fill(0, 0, width, height, 0xC0101010);
    }

    private int calcMaxScroll() {
        int total = 0;
        for (Opt opt : cats.get(selectedCat).opts())
            total += opt.isHeader() ? HEADER_H + 2 : ROW_H;
        return Math.max(0, START_Y + total - (height - 42));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

        int y = START_Y - scrollOffset;
        int wi = 0;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (opt.isHeader()) { y += HEADER_H + 2; continue; }
            if (wi < scrollWidgets.size()) scrollWidgets.get(wi++).setY(y);
            y += ROW_H;
        }
        return true;
    }

    private void switchCategory(int idx) {
        if (idx == selectedCat) return;
        selectedCat = idx;
        scrollOffset = 0;
        setFocused(null);
        init();
    }

    private ButtonWidget mkBoolBtn(int y, boolean val, java.util.function.Consumer<Boolean> setter) {
        return ButtonWidget.builder(val ? Text.translatable("options.on") : Text.translatable("options.off"), btn -> {
            boolean nv = btn.getMessage().equals(Text.translatable("options.on"));
            setter.accept(!nv);
            btn.setMessage(!nv ? Text.translatable("options.on") : Text.translatable("options.off"));
        }).dimensions(inputX, y, INPUT_W, 20).build();
    }

    private TextFieldWidget mkHexBox(int y, String initial, java.util.function.Consumer<String> onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
        box.setText(initial);
        box.setMaxLength(7);
        box.setChangedListener(s -> {
            if (!s.matches("#?[0-9a-fA-F]{0,6}")) return;
            if (s.length() == 6 && !s.startsWith("#")) { box.setText("#" + s); onChange.accept("#" + s); }
            else if (s.length() == 7) onChange.accept(s);
        });
        return box;
    }

    private TextFieldWidget mkIntBox(int y, String initial, int maxLen, int min, int max, java.util.function.Consumer<Integer> onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
        box.setText(initial);
        box.setMaxLength(maxLen);
        box.setChangedListener(s -> {
            if (!s.matches("\\d*")) return;
            try { onChange.accept(MathHelper.clamp(Integer.parseInt(s), min, max)); } catch (NumberFormatException ignored) {}
        });
        return box;
    }

    private TextFieldWidget mkPatternBox(int y, List<String> patterns, java.util.function.Consumer<List<String>> onChange) {
        TextFieldWidget box = new TextFieldWidget(textRenderer, inputX, y, INPUT_W, 20, Text.literal(""));
        box.setText(String.join(", ", patterns));
        box.setMaxLength(200);
        box.setChangedListener(s -> {
            List<String> newPatterns = new ArrayList<>();
            if (!s.isBlank()) {
                for (String part : s.split(",")) {
                    String trimmed = part.trim();
                    if (!trimmed.isEmpty()) newPatterns.add(trimmed);
                }
            }
            onChange.accept(newPatterns);
        });
        return box;
    }
}
