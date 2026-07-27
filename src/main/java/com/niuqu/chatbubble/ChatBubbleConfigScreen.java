package com.niuqu.chatbubble;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.AbstractWidget;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.CommonComponents;
import net.minecraft.network.chat.Component;
import net.minecraft.util.Mth;
import net.minecraftforge.common.ForgeConfigSpec;

public class ChatBubbleConfigScreen extends Screen {
    private final Screen lastScreen;

    private ChatBubbleTheme.Colors c() {
        return ChatBubbleTheme.DARK.colors();
    }

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
    private final List<AbstractWidget> scrollWidgets = new ArrayList<>();

    private interface WidgetFactory {
        AbstractWidget create(int y);
    }

    private record Opt(String key, WidgetFactory factory, Supplier<String> previewColor) {
        static Opt header(String key) { return new Opt(key, null, null); }
        boolean isHeader() { return factory == null; }
    }

    private record Cat(String key, List<Opt> opts) {}

    private List<Cat> cats;

    private void buildCats() {
        if (cats != null) return;
        cats = new ArrayList<>();

        // Appearance: grouped by panel / bubbles / text
        List<Opt> appearance = new ArrayList<>();
        appearance.add(Opt.header("e33chat.config.section.panel"));
        appearance.add(new Opt("e33chat.config.theme", this::mkThemeButton, null));
        appearance.add(new Opt("e33chat.config.animation", y -> mkBoolButton(y, ChatBubbleConfig.ANIMATION_ENABLED), null));
        appearance.add(new Opt("e33chat.config.panel_width",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.PANEL_WIDTH.get()), 800, 1600, 4, ChatBubbleConfig.PANEL_WIDTH::set), null));
        appearance.add(Opt.header("e33chat.config.section.bubbles"));
        appearance.add(new Opt("e33chat.config.bubble_corner_radius",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get()), 0, 10, 2, ChatBubbleConfig.BUBBLE_CORNER_RADIUS::set), null));
        appearance.add(new Opt("e33chat.config.own_bubble_color",
            y -> mkHexBox(y, ChatBubbleConfig.OWN_BUBBLE_COLOR.get(), ChatBubbleConfig.OWN_BUBBLE_COLOR::set),
            ChatBubbleConfig.OWN_BUBBLE_COLOR::get));
        appearance.add(new Opt("e33chat.config.other_bubble_color",
            y -> mkHexBox(y, ChatBubbleConfig.OTHER_BUBBLE_COLOR.get(), ChatBubbleConfig.OTHER_BUBBLE_COLOR::set),
            ChatBubbleConfig.OTHER_BUBBLE_COLOR::get));
        appearance.add(Opt.header("e33chat.config.section.text"));
        appearance.add(new Opt("e33chat.config.own_text_color",
            y -> mkHexBox(y, ChatBubbleConfig.OWN_TEXT_COLOR.get(), ChatBubbleConfig.OWN_TEXT_COLOR::set),
            ChatBubbleConfig.OWN_TEXT_COLOR::get));
        appearance.add(new Opt("e33chat.config.other_text_color",
            y -> mkHexBox(y, ChatBubbleConfig.OTHER_TEXT_COLOR.get(), ChatBubbleConfig.OTHER_TEXT_COLOR::set),
            ChatBubbleConfig.OTHER_TEXT_COLOR::get));
        cats.add(new Cat("e33chat.config.cat.appearance", appearance));

        // Notifications: HUD elements / @notifications / sounds
        List<Opt> notifications = new ArrayList<>();
        notifications.add(Opt.header("e33chat.config.section.hud"));
        notifications.add(new Opt("e33chat.config.red_dot", y -> mkBoolButton(y, ChatBubbleConfig.RED_DOT_ENABLED), null));
        notifications.add(new Opt("e33chat.config.hide_chat_icon", y -> mkBoolButton(y, ChatBubbleConfig.HIDE_CHAT_ICON), null));
        notifications.add(new Opt("e33chat.config.preview_enabled", y -> mkBoolButton(y, ChatBubbleConfig.PREVIEW_ENABLED), null));
        notifications.add(new Opt("e33chat.config.preview_lines",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.PREVIEW_LINES.get()), 3, 10, 2,
                v -> ChatBubbleConfig.PREVIEW_LINES.set(v)), null));
        notifications.add(new Opt("e33chat.config.preview_width",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.PREVIEW_WIDTH.get()), 50, 400, 3, ChatBubbleConfig.PREVIEW_WIDTH::set), null));
        notifications.add(new Opt("e33chat.config.strong_hint", y -> mkBoolButton(y, ChatBubbleConfig.STRONG_HINT_ENABLED), null));
        notifications.add(Opt.header("e33chat.config.section.mention_quote"));
        notifications.add(new Opt("e33chat.config.mention_banner_enabled", y -> mkBoolButton(y, ChatBubbleConfig.MENTION_BANNER_ENABLED), null));
        notifications.add(new Opt("e33chat.config.mention_banner_duration",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.MENTION_BANNER_DURATION.get()), 2, 10, 2,
                v -> ChatBubbleConfig.MENTION_BANNER_DURATION.set(v)), null));
        notifications.add(new Opt("e33chat.config.banner_corner_radius",
            y -> mkIntBox(y, String.valueOf(ChatBubbleConfig.BANNER_CORNER_RADIUS.get()), 0, 10, 2,
                v -> ChatBubbleConfig.BANNER_CORNER_RADIUS.set(v)), null));
        notifications.add(new Opt("e33chat.config.mention_require_at", y -> mkBoolButton(y, ChatBubbleConfig.MENTION_REQUIRE_AT), null));
        notifications.add(new Opt("e33chat.config.mention_sound_enabled", y -> mkBoolButton(y, ChatBubbleConfig.MENTION_SOUND_ENABLED), null));
        notifications.add(Opt.header("e33chat.config.section.whisper"));
        notifications.add(new Opt("e33chat.config.mention_whisper_banner", y -> mkBoolButton(y, ChatBubbleConfig.MENTION_WHISPER_BANNER), null));
        notifications.add(Opt.header("e33chat.config.section.sounds"));
        notifications.add(new Opt("e33chat.config.sound_whisper",
            y -> mkBoolButton(y, ChatBubbleConfig.SOUND_WHISPER), null));
        notifications.add(new Opt("e33chat.config.sound_system",
            y -> mkBoolButton(y, ChatBubbleConfig.SOUND_SYSTEM), null));
        notifications.add(new Opt("e33chat.config.sound_public",
            y -> mkBoolButton(y, ChatBubbleConfig.SOUND_PUBLIC), null));
        cats.add(new Cat("e33chat.config.cat.notifications", notifications));

        // Behavior: message handling / chat history / advanced handling
        List<Opt> behavior = new ArrayList<>();
        behavior.add(Opt.header("e33chat.config.section.general"));
        behavior.add(new Opt("e33chat.config.enabled", y -> mkBoolButton(y, ChatBubbleConfig.ENABLED), null));
        behavior.add(new Opt("e33chat.config.time_separator", this::mkTimeSepButton, null));
        behavior.add(Opt.header("e33chat.config.section.messages"));
        behavior.add(new Opt("e33chat.config.anti_spam", y -> mkBoolButton(y, ChatBubbleConfig.ANTI_SPAM), null));
        behavior.add(new Opt("e33chat.config.system_chat_as_bubble", y -> mkBoolButton(y, ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE), null));
        behavior.add(new Opt("e33chat.config.color_codes", y -> mkBoolButton(y, ChatBubbleConfig.COLOR_CODES), null));
        behavior.add(Opt.header("e33chat.config.section.history"));
        behavior.add(new Opt("e33chat.config.chat_history", y -> mkBoolButton(y, ChatBubbleConfig.CHAT_HISTORY_ENABLED), null));
        behavior.add(new Opt("e33chat.config.preserve_input", y -> mkBoolButton(y, ChatBubbleConfig.PRESERVE_INPUT), null));
        behavior.add(new Opt("e33chat.config.sidebar_hide_patterns",
            y -> mkPatternBox(y,
                new ArrayList<>(ChatBubbleConfig.SIDEBAR_HIDE_PATTERNS.get()),
                parts -> ChatBubbleConfig.SIDEBAR_HIDE_PATTERNS.set(new ArrayList<>(parts))),
            null));
        cats.add(new Cat("e33chat.config.cat.behavior", behavior));

        List<Opt> advanced = new ArrayList<>();
        advanced.add(new Opt("e33chat.config.debug_log", y -> mkBoolButton(y, ChatBubbleConfig.DEBUG_LOG), null));
        cats.add(new Cat("e33chat.config.cat.advanced", advanced));
    }

    public ChatBubbleConfigScreen(Screen lastScreen) {
        super(Component.translatable("e33chat.config.title"));
        this.lastScreen = lastScreen;
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

        int y = START_Y - scrollOffset;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (opt.isHeader()) { y += HEADER_H + 2; continue; }
            scrollWidgets.add(addRenderableWidget(opt.factory().create(y)));
            y += ROW_H;
        }

        addRenderableWidget(Button.builder(CommonComponents.GUI_DONE, btn -> onClose())
            .bounds(width / 2 - 100, height - 32, 200, 20).build());
    }

    private void switchCategory(int idx) {
        if (idx == selectedCat) return;
        selectedCat = idx;
        scrollOffset = 0;
        setFocused(null);
        clearWidgets();
        init();
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

    private Button mkBoolButton(int y, ForgeConfigSpec.BooleanValue cfg) {
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
        renderBackground(g);
        g.drawString(font, title, width / 2 - font.width(title) / 2, 14, c().configTitle(), false);

        // Category column
        for (int i = 0; i < cats.size(); i++) {
            int cy = START_Y + i * CAT_ROW_H;
            boolean sel = i == selectedCat;
            boolean hover = mouseX >= CAT_X && mouseX <= CAT_X + CAT_W && mouseY >= cy && mouseY <= cy + CAT_ROW_H;
            if (sel || hover)
                g.fill(CAT_X, cy, CAT_X + CAT_W, cy + CAT_ROW_H, c().iconHover());
            if (sel)
                g.fill(CAT_X, cy, CAT_X + 2, cy + CAT_ROW_H, c().configTitle());
            Component label = Component.translatable(cats.get(i).key());
            g.drawString(font, label, CAT_X + 8, cy + (CAT_ROW_H - 8) / 2,
                sel ? c().configTitle() : c().configLabel(), false);
        }

        // Divider between categories and options
        g.fill(dividerX, START_Y - 6, dividerX + 1, height - 44, c().divider());

        // Option rows
        String tooltipKey = null;
        int y = START_Y - scrollOffset;
        boolean firstHeader = true;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (opt.isHeader()) {
                if (firstHeader) firstHeader = false;
                else if (y > -HEADER_H && y < height)
                    g.fill(optLabelX - 4, y - 3, optLabelX + optAreaW() + 4, y - 2, c().divider());
                if (y > -HEADER_H && y < height) {
                    g.fill(optLabelX - 4, y, optLabelX + optAreaW() + 4, y + HEADER_H,
                        c().configBg());
                    g.drawCenteredString(font, Component.translatable(opt.key()),
                        optLabelX + optAreaW() / 2, y + (HEADER_H - 8) / 2,
                        c().configSection());
                }
                y += HEADER_H + 2;
                continue;
            }
            if (y > -ROW_H && y < height) {
                g.drawString(font, Component.translatable(opt.key()), optLabelX, y + 6, c().configLabel(), false);
                if (opt.previewColor() != null)
                    drawPreview(g, y + 3, opt.previewColor().get());
                if (mouseX >= optLabelX - 4 && mouseX <= inputX - 10 && mouseY >= y && mouseY <= y + 20)
                    tooltipKey = opt.key() + ".desc";
            }
            y += ROW_H;
        }

        super.render(g, mouseX, mouseY, partialTick);

        if (tooltipKey != null) {
            g.renderTooltip(font, font.split(Component.translatable(tooltipKey), 190), mouseX, mouseY);
        }
    }

    private void drawPreview(GuiGraphics g, int y, String hex) {
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
                    switchCategory(i);
                    return true;
                }
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        minecraft.setScreen(lastScreen);
    }

    @Override
    public void renderBackground(GuiGraphics g) {
        g.fill(0, 0, width, height, 0xC0101010);
    }

    private int optAreaW() { return previewX - optLabelX - 4; }

    private int calcMaxScroll() {
        int total = 0;
        for (Opt opt : cats.get(selectedCat).opts())
            total += opt.isHeader() ? HEADER_H + 2 : ROW_H;
        return Math.max(0, START_Y + total - (height - 42));
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        int maxScroll = calcMaxScroll();
        if (maxScroll <= 0) return false;
        scrollOffset -= (int) (delta * 20);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        int y = START_Y - scrollOffset;
        int wi = 0;
        for (Opt opt : cats.get(selectedCat).opts()) {
            if (opt.isHeader()) { y += HEADER_H + 2; continue; }
            if (wi < scrollWidgets.size()) scrollWidgets.get(wi++).setY(y);
            y += ROW_H;
        }
        return true;
    }
}
