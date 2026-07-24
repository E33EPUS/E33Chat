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

public class ChatBubbleConfigScreen extends Screen {
    private final Screen lastScreen;

    private ChatBubbleTheme.Colors c() { return ChatBubbleTheme.DARK.colors(); }

    private static final int ROW_H = 28;
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
    private boolean strongHintEnabled, mentionStrongHintEnabled, systemChatAsBubble;
    private boolean antiSpam, chatHistoryEnabled;
    private boolean previewEnabled, soundPublic, soundSystem, soundMention, soundWhisper;
    private boolean debugLog, preserveInput, colorCodes;
    private int previewLines, previewWidth, timeSeparatorMinutes, panelWidth, bubbleCornerRadius;
    private String theme, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor;
    private List<String> sidebarHidePatterns;

    private record Cat(String key, List<String> optKeys) {}
    private List<Cat> cats;

    private void loadFromConfig() {
        var cfg = ChatBubbleClientSetup.config();
        enabled = cfg.enabled(); redDotEnabled = cfg.redDotEnabled();
        hideChatIcon = cfg.hideChatIcon(); animationEnabled = cfg.animationEnabled();
        strongHintEnabled = cfg.strongHintEnabled(); mentionStrongHintEnabled = cfg.mentionStrongHintEnabled();
        systemChatAsBubble = cfg.systemChatAsBubble(); antiSpam = cfg.antiSpam();
        chatHistoryEnabled = cfg.chatHistoryEnabled();
        previewEnabled = cfg.previewEnabled(); soundPublic = cfg.soundPublic();
        soundSystem = cfg.soundSystem(); soundMention = cfg.soundMention();
        soundWhisper = cfg.soundWhisper(); debugLog = cfg.debugLog();
        preserveInput = cfg.preserveInput();
        colorCodes = cfg.colorCodes();
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
            strongHintEnabled, mentionStrongHintEnabled, systemChatAsBubble, antiSpam,
            chatHistoryEnabled, previewEnabled, previewLines, previewWidth, timeSeparatorMinutes,
            panelWidth, bubbleCornerRadius, ownBubbleColor, otherBubbleColor, ownTextColor, otherTextColor,
            soundPublic, soundSystem, soundMention, soundWhisper, debugLog, preserveInput, colorCodes,
            sidebarHidePatterns,
            ChatBubbleClientSetup.config().quickChatPhrases()));
    }

    private void buildCats() {
        if (cats != null) return;
        cats = new ArrayList<>();
        // Appearance: overall look + colors grouped own/other
        cats.add(new Cat("e33chat.config.cat.appearance", List.of(
            "theme", "animation", "panel_width", "bubble_corner_radius",
            "own_bubble_color", "other_bubble_color", "own_text_color", "other_text_color")));
        // Notifications & Sound: HUD icon, preview (toggle→params), strong hints, then sounds
        cats.add(new Cat("e33chat.config.cat.notifications", List.of(
            "red_dot", "hide_chat_icon", "preview_enabled", "preview_lines", "preview_width",
            "strong_hint", "mention_strong_hint",
            "sound_mention", "sound_whisper", "sound_system", "sound_public")));
        // Behavior: master toggle first, then message handling
        cats.add(new Cat("e33chat.config.cat.behavior", List.of(
            "enabled", "anti_spam", "chat_history", "preserve_input", "color_codes",
            "system_chat_as_bubble", "time_separator", "sidebar_hide_patterns")));
        // Advanced: debug/dev-only
        cats.add(new Cat("e33chat.config.cat.advanced", List.of(
            "debug_log")));
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
        for (String key : cats.get(selectedCat).optKeys()) {
            ClickableWidget w = createWidget(key, y);
            if (w != null) { addDrawableChild(w); scrollWidgets.add(w); }
            y += ROW_H;
        }

        addDrawableChild(ButtonWidget.builder(Text.translatable("gui.done"), btn -> onClose())
            .dimensions(width / 2 - 100, height - 32, 200, 20).build());
    }

    private ClickableWidget createWidget(String key, int y) {
        return switch (key) {
            case "theme" -> ButtonWidget.builder(Text.literal(theme.toUpperCase()), btn -> {
                theme = theme.equalsIgnoreCase("dark") ? "light" : "dark";
                btn.setMessage(Text.literal(theme.toUpperCase()));
            }).dimensions(inputX, y, INPUT_W, 20).build();
            case "animation" -> mkBoolBtn(y, animationEnabled, v -> animationEnabled = v);
            case "red_dot" -> mkBoolBtn(y, redDotEnabled, v -> redDotEnabled = v);
            case "hide_chat_icon" -> mkBoolBtn(y, hideChatIcon, v -> hideChatIcon = v);
            case "preview_enabled" -> mkBoolBtn(y, previewEnabled, v -> previewEnabled = v);
            case "strong_hint" -> mkBoolBtn(y, strongHintEnabled, v -> strongHintEnabled = v);
            case "mention_strong_hint" -> mkBoolBtn(y, mentionStrongHintEnabled, v -> mentionStrongHintEnabled = v);
            case "enabled" -> mkBoolBtn(y, enabled, v -> enabled = v);
            case "anti_spam" -> mkBoolBtn(y, antiSpam, v -> antiSpam = v);
            case "chat_history" -> mkBoolBtn(y, chatHistoryEnabled, v -> chatHistoryEnabled = v);
            case "system_chat_as_bubble" -> mkBoolBtn(y, systemChatAsBubble, v -> systemChatAsBubble = v);
            case "sound_system" -> mkBoolBtn(y, soundSystem, v -> soundSystem = v);
            case "sound_mention" -> mkBoolBtn(y, soundMention, v -> soundMention = v);
            case "sound_whisper" -> mkBoolBtn(y, soundWhisper, v -> soundWhisper = v);
            case "sound_public" -> mkBoolBtn(y, soundPublic, v -> soundPublic = v);
            case "debug_log" -> mkBoolBtn(y, debugLog, v -> debugLog = v);
            case "preserve_input" -> mkBoolBtn(y, preserveInput, v -> preserveInput = v);
            case "color_codes" -> mkBoolBtn(y, colorCodes, v -> colorCodes = v);
            case "preview_lines" -> ButtonWidget.builder(Text.literal(String.valueOf(previewLines)), btn -> {
                previewLines = previewLines >= 8 ? 1 : previewLines + 1;
                btn.setMessage(Text.literal(String.valueOf(previewLines)));
            }).dimensions(inputX, y, INPUT_W, 20).build();
            case "time_separator" -> {
                String label = timeSeparatorMinutes == 0
                    ? Text.translatable("e33chat.config.time_separator.disable").getString()
                    : timeSeparatorMinutes + " " + Text.translatable("e33chat.config.time_separator.minute").getString();
                yield ButtonWidget.builder(Text.literal(label), btn -> {
                    int[] presets = {1, 5, 10, 15, 30, 0};
                    int idx = -1;
                    for (int i = 0; i < presets.length; i++) if (presets[i] == timeSeparatorMinutes) { idx = i; break; }
                    timeSeparatorMinutes = presets[(idx + 1) % presets.length];
                    String nl = timeSeparatorMinutes == 0
                        ? Text.translatable("e33chat.config.time_separator.disable").getString()
                        : timeSeparatorMinutes + " " + Text.translatable("e33chat.config.time_separator.minute").getString();
                    btn.setMessage(Text.literal(nl));
                }).dimensions(inputX, y, INPUT_W, 20).build();
            }
            case "preview_width" -> mkIntBox(y, String.valueOf(previewWidth), 3, 50, 400, v -> previewWidth = v);
            case "panel_width" -> mkIntBox(y, String.valueOf(panelWidth), 4, 800, 1600, v -> panelWidth = v);
            case "bubble_corner_radius" -> mkIntBox(y, String.valueOf(bubbleCornerRadius), 2, 0, 10, v -> bubbleCornerRadius = v);
            case "own_bubble_color" -> mkHexBox(y, ownBubbleColor, v -> ownBubbleColor = v);
            case "other_bubble_color" -> mkHexBox(y, otherBubbleColor, v -> otherBubbleColor = v);
            case "own_text_color" -> mkHexBox(y, ownTextColor, v -> ownTextColor = v);
            case "other_text_color" -> mkHexBox(y, otherTextColor, v -> otherTextColor = v);
            case "sidebar_hide_patterns" -> mkPatternBox(y, sidebarHidePatterns, v -> sidebarHidePatterns = v);
            default -> null;
        };
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

    private void switchCategory(int idx) {
        if (idx == selectedCat) return;
        selectedCat = idx;
        scrollOffset = 0;
        setFocused(null);
        init();
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
        for (String key : cats.get(selectedCat).optKeys()) {
            if (y > -ROW_H && y < height) {
                g.drawText(textRenderer, Text.translatable("e33chat.config." + key).getString(),
                    optLabelX, y + 6, c().configLabel(), false);
                String colorVal = getColorPreview(key);
                if (colorVal != null) drawPreview(g, y + 3, colorVal);
                if (mouseX >= optLabelX && mouseX <= inputX - 4 && mouseY >= y && mouseY <= y + ROW_H) {
                    Text desc = Text.translatable("e33chat.config." + key + ".desc");
                    g.drawTooltip(textRenderer, desc, mouseX, mouseY);
                }
            }
            y += ROW_H;
        }
    }

    private String getColorPreview(String key) {
        return switch (key) {
            case "own_bubble_color" -> ownBubbleColor;
            case "other_bubble_color" -> otherBubbleColor;
            case "own_text_color" -> ownTextColor;
            case "other_text_color" -> otherTextColor;
            default -> null;
        };
    }

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
        int total = cats.get(selectedCat).optKeys().size() * ROW_H;
        return Math.max(0, START_Y + total - (height - 42));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

        int y = START_Y - scrollOffset;
        List<String> keys = cats.get(selectedCat).optKeys();
        for (int i = 0; i < keys.size() && i < scrollWidgets.size(); i++) {
            scrollWidgets.get(i).setY(y);
            y += ROW_H;
        }
        return true;
    }
}
