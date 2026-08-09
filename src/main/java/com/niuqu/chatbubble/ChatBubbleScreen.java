package com.niuqu.chatbubble;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import com.niuqu.chatbubble.packets.QuoteSyncPacket;
import com.niuqu.chatbubble.render.ChatBars;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import com.niuqu.chatbubble.render.ChatContextMenus;
import com.niuqu.chatbubble.render.ChatLayout;
import com.niuqu.chatbubble.render.ChatMessageRenderer;
import com.niuqu.chatbubble.render.ChatScrollbar;
import com.niuqu.chatbubble.render.ChatSidebar;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ChatBubbleScreen extends ChatScreen {

    // Layout
    private int panelX, panelW;
    private static final int TITLE_H = 24;
    private int titleY, msgTop, msgBottom, barTop;
    private static final int PAD = 8;
    private static final int AVATAR = 20;
    private static final int BUBBLE_PAD_X = 6;
    private static final int BUBBLE_PAD_Y = 4;
    private static final int GAP = 6;
    private static final int NAME_H = 10;
    private static final int TIME_SEP_H = 14;
    static final int BAR_H = 26;
    private static final int SIDEBAR_W = 90;
    private static final int SIDEBAR_ITEM_H = 22;
    private static final int SIDEBAR_ICON_S = 20;

    private ChatBubbleTheme.Colors c() {
        return ChatBubbleConfig.THEME.get().colors();
    }

    private static final int INPUT_H = 14;
    private static final int ICON_S = 14;

    static ResourceLocation iconTex(String name) {
        String theme = ChatBubbleConfig.THEME.get().name().toLowerCase();
        return ResourceLocation.fromNamespaceAndPath(ChatBubbleMod.MODID, "textures/gui/" + theme + "/" + name + ".png");
    }




    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static String timeKey(long timeMillis) {
        return ChatMessageRenderer.timeKey(timeMillis, ChatBubbleConfig.TIME_SEPARATOR_MINUTES.get());
    }

    private static int inputX, inputY;
    // Caches resolved head skins per player uuid so the SkinManager isn't hit every frame
    private static final Map<UUID, ResourceLocation> skinCache = new HashMap<>();
    private CommandSuggestions suggestions;
    private final String initialText;
    private String historyBuffer = "";
    private int historyPos = -1;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollToBottom = true;
    private boolean firstRender = true;
    private static String savedInput = "";

    // Emoji panel
    final ChatEmojiPanel emojiPanel = new ChatEmojiPanel();
    final ChatSettingsMenu settingsMenu = new ChatSettingsMenu();
    final ChatSearchPanel searchPanel = new ChatSearchPanel();
    private EditBox searchInput;
    private final List<Integer> searchMatches = new ArrayList<>();
    private int searchMatchIdx;
    private int searchHighlightIndex = -1;
    final ChatQuickChatPanel quickChatPanel = new ChatQuickChatPanel();
    private EditBox quickChatInput;
    private static final int QUICK_CHAT_W = 140;
    private static String whisperPartner;

    // Popup open animation timestamps (opening only; closing stays instant)
    private long settingsAnimStart, emojiAnimStart, quickAnimStart, searchAnimStart;

    // Sidebar — animation state owned by ChatBubbleScreen, rendering delegated to ChatSidebar
    private static boolean sidebarOpen;
    private boolean sidebarAnimating;
    private boolean sidebarTargetOpen;
    private long sidebarAnimStart;
    private int sidebarScrollOffset;
    private int sidebarMaxScroll;
    private EditBox sidebarSearchBox;

    // Scrollbar
    private boolean scrollbarDragging;
    private int scrollbarDragStartY;
    private int scrollbarDragStartOffset;
    private int messageTotalH;
    private float scrollbarAlpha;
    private boolean scrollAnimActive;
    private long scrollAnimStart;
    private float scrollAnimFrom;
    private float scrollAnimTo;
    private int scrollAnimDuration;
    private long lastScrollTime;

    // @mention autocomplete
    private boolean showMentions;
    private boolean mentionNavigated;
    private final List<String> mentionCandidates = new ArrayList<>();
    private int mentionIdx;
    private String mentionFilter = "";

    // Right-click menu
    private int contextMsgIndex = -1;
    private int contextX, contextY;
    private int contextAvatarIndex = -1;
    private int contextAvatarX, contextAvatarY;

    // Per-frame wrap cache: every message is measured once (layout pass) and
    // rendered once (bubble pass), and both call msgHeight -> wrapContent.
    // Without the cache each message gets re-wrapped 3x per frame.
    private final Map<ChatMessageStore.ChatMessage, Integer> msgHeightCache =
        new IdentityHashMap<>();

    // Bubble hit tracking
    private final List<int[]> bubbleRects = new ArrayList<>();

    // Clickable text span tracking (for ClickEvent support)
    private final List<ChatMessageRenderer.ClickableSpan> clickableSpans = new ArrayList<>();

    // Reply / quote
    private int replyTargetIndex = -1;

    // Copy toast
    private int copyToastTicks;

    // Animations
    private long animStart;
    private boolean closing;
    private static final int ANIM_MS = 150;
    private static final int NOTIF_H = 14;
    private int newMessageCount;
    private boolean hasNewMentionOrQuote;
    private int latestMentionIndex = -1;
    private int lastSeenMessageCount;
    private int notifCountLeft, notifCountRight;
    private int notifMentionLeft = -1, notifMentionRight = -1;
    private int notifBarTextY;

    public ChatBubbleScreen(String initialText) {
        super("");
        this.initialText = initialText;
    }

    @Override
    protected void init() {
        ChatMessageStore.setScreenOpen(true);
        historyPos = minecraft.gui.getChat().getRecentChat().size();
        animStart = net.minecraft.Util.getMillis();
        closing = false;
        firstRender = true;

        int physicalW = ChatBubbleConfig.PANEL_WIDTH.get();
        int guiScale = (int)Math.round(minecraft.getWindow().getGuiScale());
        panelW = Math.max(100, Math.min(physicalW / guiScale, width));
        if (sidebarOpen) {
            panelX = SIDEBAR_W;
            sidebarAnimating = false;
        } else {
            panelX = 0;
            sidebarAnimating = false;
            sidebarTargetOpen = false;
        }
        if (panelX + panelW > width) panelW = width - panelX;
        titleY = 0;
        msgTop = titleY + TITLE_H + 1;
        barTop = height - BAR_H;
        msgBottom = barTop - 1;

        // Input box: gear (left) → input → emoji → send (right)
        int ibY = barTop + (BAR_H - INPUT_H) / 2;
        inputY = ibY;
        inputX = panelX + 4 + ICON_S + 3;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int inputW = sendX - ICON_S - 8 - inputX;

        input = new EditBox(font, inputX, ibY + 3, inputW, INPUT_H, Component.literal(""));
        input.setMaxLength(256);
        input.setBordered(false);
        int editColor = ChatBubbleConfig.THEME.get() == ChatBubbleTheme.LIGHT
            ? c().textSecondary() : c().textPrimary();
        input.setTextColor(editColor);
        input.setTextColorUneditable(c().textMuted());
        input.setValue(initialText.isEmpty() && ChatBubbleConfig.PRESERVE_INPUT.get() && !savedInput.isEmpty() ? savedInput : initialText);
        input.setCanLoseFocus(false);
        input.setResponder(this::onInputEdited);
        addRenderableWidget(input);

        int cmdBgAlpha = ChatBubbleConfig.THEME.get() == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
        suggestions = new CommandSuggestions(minecraft, this, input, font,
            false, false, 0, 8, true, ChatBubbleTheme.alphaBlend(c().panelBg(), cmdBgAlpha));
        suggestions.updateCommandInfo();

        sidebarSearchBox = new EditBox(font, 2, 5, SIDEBAR_W - 5, SIDEBAR_SEARCH_H, Component.literal(""));
        sidebarSearchBox.setMaxLength(20);
        sidebarSearchBox.setBordered(false);
        sidebarSearchBox.setTextColor(editColor);
        sidebarSearchBox.setTextColorUneditable(editColor);
        sidebarSearchBox.setVisible(sidebarOpen);
        sidebarSearchBox.setCanLoseFocus(true);
        sidebarSearchBox.setResponder(s -> sidebarScrollOffset = 0);
        if (sidebarOpen) sidebarSearchBox.setX(2 - SIDEBAR_W);
        addRenderableWidget(sidebarSearchBox);

        quickChatInput = new EditBox(font, 0, 0, QUICK_CHAT_W - 8, 12, Component.translatable("e33chat.menu.quick_chat"));
        quickChatInput.setMaxLength(256);
        quickChatInput.setBordered(false);
        quickChatInput.setTextColor(editColor);
        quickChatInput.setTextColorUneditable(c().textMuted());
        quickChatInput.setVisible(false);
        quickChatInput.setCanLoseFocus(true);
        addRenderableWidget(quickChatInput);

        searchInput = new EditBox(font, 0, 0, 160, 12, Component.translatable("e33chat.menu.search"));
        searchInput.setMaxLength(128);
        searchInput.setBordered(false);
        searchInput.setTextColor(editColor);
        searchInput.setTextColorUneditable(c().textMuted());
        searchInput.setVisible(false);
        searchInput.setCanLoseFocus(true);
        searchInput.setResponder(this::onSearchEdited);
        addRenderableWidget(searchInput);

        setInitialFocus(input);
        // The chat field's initial text is set before setResponder binds,
        // so the open-time value (e.g. "/" from the chat key) never flows
        // through onInputEdited — sync it once so the IMBlocker IME state
        // is correct.
        onInputEdited(input.getValue());
    }

    private void rebuildLayout() {
        int physicalW = ChatBubbleConfig.PANEL_WIDTH.get();
        int guiScale = (int)Math.round(minecraft.getWindow().getGuiScale());
        panelW = Math.max(100, Math.min(physicalW / guiScale, width));
        if (panelX + panelW > width) panelW = width - panelX;
        titleY = 0;
        msgTop = titleY + TITLE_H + 1;
        barTop = height - BAR_H;
        msgBottom = barTop - 1;

        int ibY = barTop + (BAR_H - INPUT_H) / 2;
        inputY = ibY;
        inputX = panelX + 4 + ICON_S + 3;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int inputW = sendX - ICON_S - 8 - inputX;

        if (input != null) {
            input.setX(inputX);
            input.setWidth(inputW);
            input.setY(ibY + 3);
        }

    }

    private String getDisplayTitle() {
        if (whisperPartner != null) return whisperPartner;
        return Component.translatable("e33chat.sidebar.public").getString();
    }


    private static final int SIDEBAR_SEARCH_H = 14;

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        sidebarMaxScroll = ChatSidebar.render(g, font, mouseX, mouseY, c(), panelW,
            msgBottom > 0 ? msgBottom : height - BAR_H, whisperPartner,
            iconTex("public_icon"), iconTex("no_online"), iconTex("private_tip"),
            sidebarSearchBox, sidebarScrollOffset, sidebarMaxScroll);
        if (sidebarScrollOffset > sidebarMaxScroll) sidebarScrollOffset = sidebarMaxScroll;
    }

    private void insertMention(String name) {
        String text = input.getValue();
        int atIdx = text.lastIndexOf('@');
        input.setValue(text.substring(0, atIdx) + "@" + name + " ");
        input.moveCursorToEnd();
        showMentions = false;
        mentionNavigated = false;
    }

    private void onInputEdited(String text) {
        showMentions = false;
        mentionNavigated = false;
        int atIdx = text.lastIndexOf('@');
        // Commands use vanilla selectors (@s/@p/...) instead of player names:
        // do not offer player-name completion inside a command.
        if (atIdx >= 0 && !text.startsWith("/") && minecraft.player != null && minecraft.player.connection != null) {
            String after = text.substring(atIdx + 1);
            if (!after.contains(" ")) {
                mentionFilter = after.toLowerCase();
                mentionCandidates.clear();
                for (var info : minecraft.player.connection.getOnlinePlayers()) {
                    String name = info.getProfile().getName();
                    if (name.toLowerCase().contains(mentionFilter))
                        mentionCandidates.add(name);
                }
                mentionCandidates.sort(String::compareToIgnoreCase);
                mentionIdx = 0;
                showMentions = !mentionCandidates.isEmpty();
            }
        }
        if (suggestions != null) {
            suggestions.setAllowSuggestions(!text.equals(initialText));
            suggestions.updateCommandInfo();
        }
        // IMBlocker listens to vanilla ChatScreen.onChatFieldUpdate, which we
        // bypass; mirror its command-detection hook so the IME still switches
        // to English while typing a command. No-op when IMBlocker is absent.
        IMBlockerCompat.setCommandMode(input, text.startsWith("/"));
    }

    private void onSearchEdited(String text) {
        searchMatches.clear();
        searchMatchIdx = -1;
        searchHighlightIndex = -1;
        if (text.isEmpty()) return;
        String lower = text.toLowerCase();
        var msgs = ChatMessageStore.getMessages();
        for (int i = 0; i < msgs.size(); i++) {
            var msg = msgs.get(i);
            if (msg == null) continue;
            if (msg.content().getString().toLowerCase().contains(lower)
                || msg.senderName().getString().toLowerCase().contains(lower))
                searchMatches.add(i);
        }
        if (!searchMatches.isEmpty()) {
            searchMatchIdx = 0;
            searchHighlightIndex = searchMatches.get(0);
            jumpToMessage(searchHighlightIndex);
        }
    }

    @Override
    public void tick() {
        if (copyToastTicks > 0) copyToastTicks--;
        input.tick();
        if (searchPanel.visible) searchInput.tick();
        if (closing && net.minecraft.Util.getMillis() - animStart >= ANIM_MS)
            minecraft.setScreen(null);
    }

    private float getAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) return 1.0f;
        AnimationStyle style = ChatBubbleConfig.PANEL_ANIM_STYLE.get();
        if (style == AnimationStyle.NONE) return 1.0f;
        long elapsed = net.minecraft.Util.getMillis() - animStart;
        float t = Mth.clamp((float) elapsed / ANIM_MS, 0f, 1f);
        if (closing) return 1.0f - (t * t);
        return Animation.styleCurve(style, t);
    }

    // Popup open animation (opening only — closing stays instant). FADE fades the
    // whole panel in; ZOOM scales it in around the screen center with overshoot.
    private void renderPopupWithAnim(GuiGraphics g, long startMs, Runnable render) {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) { render.run(); return; }
        AnimationStyle style = ChatBubbleConfig.POPUP_ANIM_STYLE.get();
        float t = Mth.clamp((float) (net.minecraft.Util.getMillis() - startMs) / 150f, 0f, 1f);
        float curve = Animation.styleCurve(style, t);
        if (curve >= 1f) { render.run(); return; }
        if (style == AnimationStyle.ZOOM) {
            g.pose().pushPose();
            float s = 0.85f + 0.15f * Animation.easeOutBack(curve);
            g.pose().translate(width / 2f, height / 2f, 0);
            g.pose().scale(s, s, 1f);
            g.pose().translate(-width / 2f, -height / 2f, 0);
        }
        RenderSystem.setShaderColor(1f, 1f, 1f, curve);
        render.run();
        RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
        if (style == AnimationStyle.ZOOM) g.pose().popPose();
    }

    private float getSidebarAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) return sidebarOpen ? 1f : 0f;
        AnimationStyle style = ChatBubbleConfig.PANEL_ANIM_STYLE.get();
        // FADE/NONE have no horizontal displacement: the sidebar fades in place.
        if (style == AnimationStyle.FADE || style == AnimationStyle.NONE) return sidebarOpen ? 1f : 0f;
        if (sidebarAnimating) {
            long elapsed = net.minecraft.Util.getMillis() - sidebarAnimStart;
            float t = Mth.clamp((float) elapsed / ANIM_MS, 0f, 1f);
            float progress = Animation.styleCurve(style, t);
            return sidebarTargetOpen ? progress : 1.0f - progress;
        }
        if (!sidebarOpen) return 0f;
        return getAnimProgress();
    }

    private int getSidebarScreenX() {
        return (int)((getSidebarAnimProgress() - 1.0f) * SIDEBAR_W);
    }

    private void tickSidebarAnimation() {
        if (!sidebarAnimating) return;
        long elapsed = net.minecraft.Util.getMillis() - sidebarAnimStart;
        float t = Mth.clamp((float) elapsed / ANIM_MS, 0f, 1f);
        if (t >= 1f) {
            sidebarAnimating = false;
            sidebarOpen = sidebarTargetOpen;
            panelX = sidebarOpen ? SIDEBAR_W : 0;
            sidebarSearchBox.setX(2);
            sidebarSearchBox.setVisible(sidebarOpen);
            if (!sidebarOpen && sidebarSearchBox.isFocused()) setFocused(input);
            rebuildLayout();
            return;
        }
        float progress = getSidebarAnimProgress();
        panelX = (int)(SIDEBAR_W * progress);
        sidebarSearchBox.setX(2 + getSidebarScreenX());
        sidebarSearchBox.setVisible(progress > 0.01f);
        rebuildLayout();
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Settings menu / emoji panel gets ESC first
        if (settingsMenu.visible && keyCode == 256) {
            settingsMenu.visible = false;
            return true;
        }
        if (emojiPanel.visible && keyCode == 256) {
            emojiPanel.visible = false;
            return true;
        }
        if (quickChatPanel.visible && keyCode == 256) {
            quickChatPanel.visible = false;
            quickChatInput.setVisible(false);
            setFocused(input);
            return true;
        }
        if (searchPanel.visible && keyCode == 256) {
            closeSearchPanel();
            return true;
        }

        // Search navigation
        if (searchPanel.visible && !searchMatches.isEmpty()) {
            if (keyCode == 265) { // Up
                searchMatchIdx = searchMatchIdx > 0 ? searchMatchIdx - 1 : searchMatches.size() - 1;
                searchHighlightIndex = searchMatches.get(searchMatchIdx);
                jumpToMessage(searchHighlightIndex);
                return true;
            }
            if (keyCode == 264) { // Down
                searchMatchIdx = searchMatchIdx < searchMatches.size() - 1 ? searchMatchIdx + 1 : 0;
                searchHighlightIndex = searchMatches.get(searchMatchIdx);
                jumpToMessage(searchHighlightIndex);
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                closeSearchPanel();
                return true;
            }
        }

        if (sidebarSearchBox.isFocused()) {
            if (keyCode == 256 || keyCode == 257 || keyCode == 335) {
                sidebarSearchBox.setFocused(false);
                setFocused(input);
                return true;
            }
        }

        // @mention autocomplete keys
        if (showMentions) {
            if (keyCode == 258) { // Tab
                insertMention(mentionCandidates.get(mentionIdx));
                return true;
            }
            if (keyCode == 256) { // Esc
                showMentions = false;
                mentionNavigated = false;
                return true;
            }
            if (keyCode == 265) { // Up
                mentionIdx = mentionIdx > 0 ? mentionIdx - 1 : mentionCandidates.size() - 1;
                mentionNavigated = true;
                return true;
            }
            if (keyCode == 264) { // Down
                mentionIdx = mentionIdx < mentionCandidates.size() - 1 ? mentionIdx + 1 : 0;
                mentionNavigated = true;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                // Only apply the highlighted candidate when the player actually
                // navigated it (arrow keys); otherwise Enter just sends the text.
                if (mentionNavigated) {
                    insertMention(mentionCandidates.get(mentionIdx));
                    return true;
                }
            }
        }

        if (suggestions != null && suggestions.keyPressed(keyCode, scanCode, modifiers))
            return true;
        if (keyCode == 256) { onClose(); return true; }
        if (quickChatInput.isFocused() && (keyCode == 257 || keyCode == 335)) {
            String text = quickChatInput.getValue().trim();
            if (!text.isEmpty()) {
                java.util.ArrayList<String> phrases = new java.util.ArrayList<>();
                phrases.addAll(ChatBubbleConfig.QUICK_CHAT_PHRASES.get());
                phrases.add(text);
                ChatBubbleConfig.QUICK_CHAT_PHRASES.set(phrases);
                quickChatInput.setValue("");
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            if (suggestions != null) suggestions.hide();
            sendMessage();
            return true;
        }
        if (keyCode == 265 && this.getFocused() == input) { moveInHistory(-1); return true; }
        if (keyCode == 264 && this.getFocused() == input) { moveInHistory(1); return true; }

        // 不调 super.keyPressed（= ChatScreen，内部访问 package-private commandSuggestions = null → NPE）。
        // self 实现 Screen.keyPressed 等价分发：先给 focused widget（input EditBox 处理
        // backspace/删除/左右/Home/End/Ctrl+A/C/V/X），再 Tab/箭头焦点导航。
        if (this.getFocused() != null && this.getFocused().keyPressed(keyCode, scanCode, modifiers))
            return true;
        net.minecraft.client.gui.navigation.FocusNavigationEvent nav = switch (keyCode) {
            case 258 -> new net.minecraft.client.gui.navigation.FocusNavigationEvent.TabNavigation(!Screen.hasShiftDown());
            case 262 -> new net.minecraft.client.gui.navigation.FocusNavigationEvent.ArrowNavigation(net.minecraft.client.gui.navigation.ScreenDirection.RIGHT);
            case 263 -> new net.minecraft.client.gui.navigation.FocusNavigationEvent.ArrowNavigation(net.minecraft.client.gui.navigation.ScreenDirection.LEFT);
            case 264 -> new net.minecraft.client.gui.navigation.FocusNavigationEvent.ArrowNavigation(net.minecraft.client.gui.navigation.ScreenDirection.DOWN);
            case 265 -> new net.minecraft.client.gui.navigation.FocusNavigationEvent.ArrowNavigation(net.minecraft.client.gui.navigation.ScreenDirection.UP);
            default -> null;
        };
        if (nav != null) {
            net.minecraft.client.gui.ComponentPath path = super.nextFocusPath(nav);
            if (path == null && nav instanceof net.minecraft.client.gui.navigation.FocusNavigationEvent.TabNavigation) {
                // 1.20.1 的 Screen.clearFocus 是 private，self 等价：让当前焦点路径失焦
                if (this.getCurrentFocusPath() != null) this.getCurrentFocusPath().applyFocus(false);
                path = super.nextFocusPath(nav);
            }
            if (path != null) this.changeFocus(path);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double delta) {
        if (emojiPanel.visible) {
            emojiPanel.handleScroll(delta);
            return true;
        }
        if (quickChatPanel.visible) {
            quickChatPanel.handleScroll(delta);
            return true;
        }
        if (searchPanel.visible && !searchMatches.isEmpty()) {
            searchMatchIdx = Mth.clamp(searchMatchIdx - (int) delta, 0, searchMatches.size() - 1);
            searchHighlightIndex = searchMatches.get(searchMatchIdx);
            jumpToMessage(searchHighlightIndex);
            return true;
        }
        if (showMentions && !mentionCandidates.isEmpty()) {
            mentionIdx = Mth.clamp(mentionIdx - (int) delta, 0, mentionCandidates.size() - 1);
            mentionNavigated = true;
            return true;
        }
        int sidebarX = getSidebarScreenX();
        if ((sidebarOpen || sidebarAnimating)
            && mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_W) {
            sidebarScrollOffset = Mth.clamp(sidebarScrollOffset - (int)(delta * 20), 0, sidebarMaxScroll);
            return true;
        }
        if (suggestions != null && suggestions.mouseScrolled(delta))
            return true;
        scrollToBottom = false;
        lastScrollTime = net.minecraft.Util.getMillis();
        float newTarget = Mth.clamp(scrollOffset - (int)(delta * 40), 0, maxScroll);
        scrollAnimFrom = scrollOffset;
        scrollAnimTo = newTarget;
        scrollAnimStart = net.minecraft.Util.getMillis();
        if (!scrollAnimActive) {
            scrollAnimDuration = 120;
            scrollAnimActive = true;
        }
        return true;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        // Panel contents are translated by panelOffset during the open/close slide;
        // undo the shift here so hit-testing matches what is drawn. The sidebar and
        // EditBox render outside that translate (they set their own x), so they keep
        // the original coordinate.
        double origX = mouseX;
        if (isPanelSliding()) mouseX -= currentPanelOffset();

        // @mention popup click
        if (showMentions && button == 0) {
            int popupX = input.getX();
            int popupH = Math.min(mentionCandidates.size(), 8) * font.lineHeight + 4;
            int popupY = input.getY() - popupH - 2;
            if (popupY < msgTop) popupY = input.getY() + input.getHeight() + 2;
            int maxW = 60;
            for (String name : mentionCandidates)
                maxW = Math.max(maxW, font.width(name));
            int popupW = maxW + 12;
            if (mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= popupY && mouseY <= popupY + popupH) {
                int relY = (int) mouseY - popupY - 2;
                int idx = relY / font.lineHeight;
                int startIdx = Math.max(0, mentionIdx - Math.min(mentionCandidates.size(), 8) + 1);
                idx += startIdx;
                if (idx >= 0 && idx < mentionCandidates.size()) {
                    insertMention(mentionCandidates.get(idx));
                    return true;
                }
            }
        }

        // Sidebar clicks — route to sidebar for hit detection, Screen handles side effects
        int sidebarX = getSidebarScreenX();
        if (ChatSidebar.handleMouseClicked(origX, mouseY, whisperPartner, font,
                sidebarX, sidebarOpen || sidebarAnimating, sidebarSearchBox, sidebarScrollOffset)) {
            // Search box
            int searchY = 2;
            int searchH = SIDEBAR_SEARCH_H;
            if (mouseY >= searchY && mouseY <= searchY + searchH) {
                setFocused(sidebarSearchBox);
                input.setFocused(false);
                return true;
            }
            if (sidebarSearchBox.isFocused()) {
                setFocused(input);
            }

            int y = searchY + searchH + 3;
            if (mouseY >= y && mouseY <= y + 22) {
                whisperPartner = null;
                sidebarSearchBox.setValue("");
                setFocused(input);
                scrollToBottom = true;
                return true;
            }
            y += 22 + 2;
            if (minecraft.player != null && minecraft.player.connection != null) {
                var players = new ArrayList<>(minecraft.player.connection.getOnlinePlayers());
                String selfName = minecraft.player.getName().getString();
                String filter = sidebarSearchBox.getValue().toLowerCase().trim();
                int scrollY = y - sidebarScrollOffset;
                for (var info : players) {
                    String name = info.getProfile().getName();
                    if (name.equals(selfName)) continue;
                    if (ChatBubbleConfig.isSidebarHidden(name)) continue;
                    if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                    if (mouseY >= scrollY && mouseY <= scrollY + 22) {
                        whisperPartner = name;
                        ChatMessageStore.clearUnreadWhisper(name);
                        sidebarSearchBox.setValue("");
                        setFocused(input);
                        scrollToBottom = true;
                        return true;
                    }
                    scrollY += 22 + 2;
                }
            }
        }

        // Context menu clicks must be handled before dismiss
        if (button == 0 && contextAvatarIndex >= 0) {
            handleAvatarContextClick((int) mouseX, (int) mouseY);
            return true;
        }
        if (contextAvatarIndex >= 0) { contextAvatarIndex = -1; return true; }

        if (button == 0 && contextMsgIndex >= 0) {
            handleContextClick((int) mouseX, (int) mouseY);
            return true;
        }
        if (contextMsgIndex >= 0) { contextMsgIndex = -1; return true; }

        // Notification bar clicks
        if (button == 0 && newMessageCount > 0) {
            if (mouseX >= notifCountLeft && mouseX <= notifCountRight
                && mouseY >= notifBarTextY && mouseY <= notifBarTextY + font.lineHeight) {
                scrollToBottom = true;
                newMessageCount = 0;
                hasNewMentionOrQuote = false;
                latestMentionIndex = -1;
                lastSeenMessageCount = ChatMessageStore.getMessages().size();
                return true;
            }
            if (hasNewMentionOrQuote && notifMentionLeft >= 0
                && mouseX >= notifMentionLeft && mouseX <= notifMentionRight
                && mouseY >= notifBarTextY && mouseY <= notifBarTextY + font.lineHeight) {
                jumpToMessage(latestMentionIndex);
                return true;
            }
        }

        // Reply bar cancel button
        if (button == 0 && replyTargetIndex >= 0 && isMouseOverReplyCancel(mouseX, mouseY)) {
            replyTargetIndex = -1;
            return true;
        }

        // Scrollbar interaction
        if (button == 0 && maxScroll > 0) {
            int trackX = panelX + panelW - ChatScrollbar.WIDTH;
            int effBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
            if (mouseX >= trackX && mouseX < trackX + ChatScrollbar.WIDTH
                && mouseY >= msgTop && mouseY < effBottom) {
                int trackH = effBottom - msgTop;
                int thumbH = ChatScrollbar.thumbHeight(trackH, messageTotalH);
                int thumbY = ChatScrollbar.thumbY(msgTop, trackH, thumbH, scrollOffset, maxScroll);

                if (mouseY < thumbY) {
                    scrollOffset = Math.max(0, scrollOffset - trackH);
                } else if (mouseY > thumbY + thumbH) {
                    scrollOffset = Math.min(maxScroll, scrollOffset + trackH);
                } else {
                    scrollbarDragging = true;
                    scrollbarDragStartY = (int) mouseY;
                    scrollbarDragStartOffset = scrollOffset;
                }
                scrollToBottom = false;
                return true;
            }
        }

        if (suggestions != null && suggestions.mouseClicked((int) mouseX, (int) mouseY, button))
            return true;

        if (button == 0) {
            if (isMouseOverHamburger(mouseX, mouseY)) {
                if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) {
                    sidebarOpen = !sidebarOpen;
                    sidebarAnimating = false;
                    panelX = sidebarOpen ? SIDEBAR_W : 0;
                    sidebarSearchBox.setX(2);
                    sidebarSearchBox.setVisible(sidebarOpen);
                    if (!sidebarOpen && sidebarSearchBox.isFocused()) setFocused(input);
                    rebuildLayout();
                } else if (sidebarAnimating) {
                    sidebarTargetOpen = !sidebarTargetOpen;
                    long elapsed = net.minecraft.Util.getMillis() - sidebarAnimStart;
                    float currentT = Mth.clamp((float) elapsed / ANIM_MS, 0f, 1f);
                    sidebarAnimStart = net.minecraft.Util.getMillis() - (long)((1.0f - currentT) * ANIM_MS);
                } else {
                    sidebarTargetOpen = !sidebarOpen;
                    sidebarAnimating = true;
                    sidebarAnimStart = net.minecraft.Util.getMillis();
                }
                return true;
            }
            if (mouseX >= panelX + panelW - 18 && mouseX <= panelX + panelW - 6
                && mouseY >= titleY + 6 && mouseY <= titleY + 18) {
                onClose();
                return true;
            }
            if (settingsMenu.visible) {
                int action = settingsMenu.handleClick((int) mouseX, (int) mouseY, panelX, panelW, barTop, ICON_S);
                if (action >= 0) executeMenuAction(action);
                return true;
            }
            if (emojiPanel.visible) {
                String emojiText = emojiPanel.handleClick((int) mouseX, (int) mouseY, font, c(), panelX, panelW, barTop, ICON_S, PAD);
                if (emojiText != null && !emojiText.isEmpty()) {
                    // insertText honors the cursor position and replaces any selection
                    input.insertText(emojiText);
                }
                return true;
            }
            if (quickChatPanel.visible) {
                // 输入框聚焦不依赖 widget 点击命中链路（1.21.1/yarn TextFieldWidget 点击不自动聚焦）：
                // 直接几何判定命中就聚焦，覆盖所有情况
                if (ChatQuickChatPanel.isInsideInput((int) mouseX, (int) mouseY, panelX, panelW, barTop,
                        ChatBubbleConfig.QUICK_CHAT_PHRASES.get().size())) {
                    // 与 sidebar 搜索框聚焦同款（Fabric 实测需显式失焦主输入框，否则焦点链被 chatField 占用）
                    quickChatInput.setVisible(true);
                    setFocused(quickChatInput);
                    input.setFocused(false);
                    return true;
                }
                int result = quickChatPanel.handleClick((int) mouseX, (int) mouseY, font, c(), panelX, panelW, barTop, quickChatInput);
                if (result >= 0) {
                    input.setValue(ChatBubbleConfig.QUICK_CHAT_PHRASES.get().get(result));
                    setFocused(input);
                } else if (result == -2) {
                    setFocused(quickChatInput);
                }
                return true;
            }
            if (searchPanel.visible) {
                if (searchPanel.isClickOnPanel((int) mouseX, (int) mouseY, panelX, panelW, barTop)) {
                    setFocused(searchInput);
                    return true;
                }
                closeSearchPanel();
                return true;
            }
            if (mouseY >= barTop) {
                if (handleIconClick((int) mouseX, (int) mouseY))
                    return true;
            }
        }

        if (button == 0) {
            for (int[] r : bubbleRects) {
                ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(r[4]);
                if (msg == null || msg.isSystem()) continue;
                int avatarX = msg.isOwn() ? r[0] + r[2] + 4 : r[0] - AVATAR - 4;
                int avatarY = msg.replyContent() != null ? r[1] - font.lineHeight - 2 : r[1] - NAME_H;
                if (mouseX >= avatarX && mouseX <= avatarX + AVATAR
                    && mouseY >= avatarY && mouseY <= avatarY + AVATAR) {
                    String mentionName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
                        ? msg.rawPlayerName() : msg.senderName().getString();
                    String mention = "@" + mentionName + " ";
                    input.setValue(input.getValue() + mention);
                    input.moveCursorToEnd();
                    return true;
                }
            }
        }

        if (button == 1) {
            for (int[] r : bubbleRects) {
                ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(r[4]);
                if (msg == null || msg.isSystem() || msg.isOwn()) continue;
                if (msg.rawPlayerName() == null || msg.rawPlayerName().isEmpty()) continue;
                int avatarX = r[0] - AVATAR - 4;
                int avatarY = msg.replyContent() != null ? r[1] - font.lineHeight - 2 : r[1] - NAME_H;
                if (mouseX >= avatarX && mouseX <= avatarX + AVATAR
                    && mouseY >= avatarY && mouseY <= avatarY + AVATAR) {
                    contextAvatarIndex = r[4];
                    contextAvatarX = (int) mouseX;
                    contextAvatarY = (int) mouseY;
                    return true;
                }
            }
        }

        if (button == 1) {
            for (int[] r : bubbleRects) {
                if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                    && mouseY >= r[1] && mouseY <= r[1] + r[3]) {
                    contextMsgIndex = r[4];
                    contextX = (int) mouseX;
                    contextY = (int) mouseY;
                    return true;
                }
            }
        }
        if (button == 0) {
            net.minecraft.network.chat.Style style = getHoveredStyle(mouseX, mouseY);
            if (style != null && style.getClickEvent() != null) {
                net.minecraft.network.chat.ClickEvent click = style.getClickEvent();
                if (click.getAction() == net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND) {
                    input.setValue(click.getValue());
                    return true;
                }
                if (click.getAction() == net.minecraft.network.chat.ClickEvent.Action.OPEN_FILE) {
                    java.io.File file = new java.io.File(click.getValue());
                    net.minecraft.Util.getPlatform().openFile(file);
                    return true;
                }
                if (click.getAction() == net.minecraft.network.chat.ClickEvent.Action.OPEN_URL) {
                    handleComponentClicked(style);
                    return true;
                }
                handleComponentClicked(style);
                return true;
            }
        }
        return this.input.mouseClicked(origX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbarDragging && maxScroll > 0) {
            lastScrollTime = net.minecraft.Util.getMillis();
            int effBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
            int trackH = effBottom - msgTop;
            int thumbH = ChatScrollbar.thumbHeight(trackH, messageTotalH);
            int travelRange = trackH - thumbH;
            if (travelRange > 0) {
                int dy = (int) mouseY - scrollbarDragStartY;
                float newTarget = Mth.clamp(scrollbarDragStartOffset + (int)((long)dy * maxScroll / travelRange), 0, maxScroll);
                scrollAnimFrom = scrollOffset;
                scrollAnimTo = newTarget;
                scrollAnimStart = net.minecraft.Util.getMillis();
                if (!scrollAnimActive) {
                    scrollAnimDuration = 80;
                    scrollAnimActive = true;
                }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbarDragging) {
            scrollbarDragging = false;
            return true;
        }
        return super.mouseReleased(mouseX, mouseY, button);
    }


    private boolean handleIconClick(int mx, int my) {
        int iconY = barTop + (BAR_H - ICON_S) / 2;
        // Gear icon (left) — toggle settings menu
        int gearX = panelX + 4;
        if (mx >= gearX && mx <= gearX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (emojiPanel.visible) emojiPanel.visible = false;
            if (searchPanel.visible) closeSearchPanel();
            boolean opening = !settingsMenu.visible;
            settingsMenu.visible = opening;
            if (opening) settingsAnimStart = net.minecraft.Util.getMillis();
            return true;
        }
        // Emoji icon — toggle emoji panel
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;
        if (mx >= emojiX && mx <= emojiX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (settingsMenu.visible) settingsMenu.visible = false;
            if (searchPanel.visible) closeSearchPanel();
            boolean opening = !emojiPanel.visible;
            emojiPanel.visible = opening;
            if (opening) emojiAnimStart = net.minecraft.Util.getMillis();
            showMentions = false;
            if (emojiPanel.visible) emojiPanel.scroll = 0;
            return true;
        }
        // Send icon (right)
        if (mx >= sendX && mx <= sendX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            sendMessage();
            return true;
        }
        return false;
    }

    private void handleContextClick(int mx, int my) {
        int menuH = ChatContextMenus.CTX_ITEM_H * 2 + 2;
        int menuX = ChatContextMenus.menuX(contextX, panelX, panelW);
        int menuY = ChatContextMenus.menuY(contextY, menuH, msgTop, true);

        if (ChatContextMenus.isOverItem(mx, my, menuX, menuY, ChatContextMenus.CTX_ITEM_H)) {
            ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextMsgIndex);
            if (msg != null) {
                minecraft.keyboardHandler.setClipboard(msg.content().getString());
                copyToastTicks = 30;
            }
        } else if (ChatContextMenus.isOverItem(mx, my, menuX,
            menuY + ChatContextMenus.CTX_ITEM_H + 1, ChatContextMenus.CTX_ITEM_H)) {
            replyTargetIndex = contextMsgIndex;
        }
        contextMsgIndex = -1;
    }

    private void handleAvatarContextClick(int mx, int my) {
        int menuH = ChatContextMenus.CTX_ITEM_H * 3 + 4;
        int menuX = ChatContextMenus.menuX(contextAvatarX, panelX, panelW);
        int menuY = ChatContextMenus.menuY(contextAvatarY, menuH, msgTop, true);

        ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
        String name = msg != null ? msg.rawPlayerName() : null;
        if (name == null || name.isEmpty()) { contextAvatarIndex = -1; return; }

        if (ChatContextMenus.isOverItem(mx, my, menuX, menuY, ChatContextMenus.CTX_ITEM_H)) {
            minecraft.player.connection.sendCommand((ChatMessageStore.useTpa() ? "tpa " : "tp ") + name);
        } else if (ChatContextMenus.isOverItem(mx, my, menuX,
            menuY + ChatContextMenus.CTX_ITEM_H + 2, ChatContextMenus.CTX_ITEM_H)) {
            whisperPartner = name;
            ChatMessageStore.clearUnreadWhisper(name);
            if (sidebarSearchBox != null) sidebarSearchBox.setValue("");
            setFocused(input);
            scrollToBottom = true;
        } else if (ChatContextMenus.isOverItem(mx, my, menuX,
            menuY + ChatContextMenus.CTX_ITEM_H * 2 + 4, ChatContextMenus.CTX_ITEM_H)) {
            toggleBlockedPlayer();
        }
        contextAvatarIndex = -1;
    }

    // 屏蔽/取消屏蔽右键菜单目标玩家：名单即时生效 + 从消息列表清掉历史 +
    // 立即写盘（set() 只改内存，参考 doClose 的 saveClientConfig 约定）
    private void toggleBlockedPlayer() {
        ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
        if (msg == null) return;
        String name = msg.rawPlayerName();
        if (name == null || name.isEmpty()) {
            name = msg.senderName() != null ? msg.senderName().getString() : null;
        }
        if (name == null || name.isEmpty()) return;
        final String target = name;

        List<String> blocked = new ArrayList<>(ChatBubbleConfig.BLOCKED_PLAYERS.get());
        boolean nowBlocked = ChatMessageStore.isPlayerBlocked(
            msg.rawPlayerName(), msg.senderName(), blocked);
        if (nowBlocked) {
            blocked.removeIf(b -> b != null && b.trim().equalsIgnoreCase(target));
        } else {
            blocked.add(target.trim());
        }
        ChatBubbleConfig.BLOCKED_PLAYERS.set(blocked);
        ChatMessageStore.purgeBlocked(blocked);
        com.niuqu.chatbubble.ChatBubbleMod.saveClientConfig();
        ChatMessageStore.debugLog(() -> "[e33chat] Block list updated | name='" + target + "' | blocked=" + nowBlocked);
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tickSidebarAnimation();

        float anim = getAnimProgress();
        AnimationStyle pstyle = ChatBubbleConfig.PANEL_ANIM_STYLE.get();
        float panelOpacity = ChatBubbleConfig.PANEL_OPACITY.get() / 100f * anim;
        int fillLeft = (!sidebarAnimating && sidebarOpen)
            ? (int)(anim * SIDEBAR_W) : panelX;
        int panelOffset = (pstyle == AnimationStyle.SLIDE) ? currentPanelOffset() : 0;
        boolean zoom = (pstyle == AnimationStyle.ZOOM) && anim < 1f;
        float panelScale = 1f;
        if (zoom) panelScale = 0.8f + 0.2f * Animation.easeOutBack(anim);

        // Panel background blur — copy region from main FB, multi-pass downscale blur,
        // write result back to panel region. No shader → Oculus/Embeddium compatible.
        // 区域坐标必须与 PANEL_BG 完全一致（含 panelOffset），否则：
        // 1) 面板滑入/滑出动画中 blur 区不随面板移动（原地生成/消失）
        // 2) blur 左缘与内容（汉堡图标等，在 translate 内）错位 → 边缘被切开
        if (ChatBubbleConfig.BLUR_ENABLED.get() && panelOpacity < 0.999f && !zoom) {
            BlurRenderer.blurPanel(panelOffset + fillLeft, 0, panelX + panelW - fillLeft, height);
        }

        // Panel contents slide in from left (or zoom in around center)
        g.pose().pushPose();
        g.pose().translate(panelOffset, 0, 0);
        if (zoom) {
            float cx = panelX + panelW / 2f;
            g.pose().translate(cx, height / 2f, 0);
            g.pose().scale(panelScale, panelScale, 1f);
            g.pose().translate(-cx, -height / 2f, 0);
        }

        // Panel background overlay — semi-transparent tint on top of blurred/clear world.
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.PANEL_BG),
            fillLeft, 0, panelX + panelW - fillLeft, height, panelOpacity);

        renderTitleBar(g, mouseX, mouseY);
        renderMessages(g, mouseX, mouseY);
        net.minecraft.network.chat.Style hovered = getHoveredStyle(mouseX, mouseY);
        if (hovered != null && hovered.getHoverEvent() != null) {
            g.renderComponentHoverEffect(font, hovered, mouseX, mouseY);
        }
        g.pose().translate(0, 0, 50);
        renderNotificationBar(g, mouseX, mouseY);
        renderReplyBar(g, mouseX, mouseY);
        renderContextMenu(g, mouseX, mouseY);
        renderAvatarContextMenu(g, mouseX, mouseY);
        renderToast(g);
        renderBottomBar(g, mouseX, mouseY);
        renderMentionPopup(g, mouseX, mouseY);
        // 弹层面板（设置/表情/快捷/搜索）画在底栏之上，z 高一层——侧边栏同 z 后画
        // 会盖住它们，提升弹层 z 到侧边栏之上避免遮挡
        g.pose().pushPose();
        g.pose().translate(0, 0, 100);
        renderPopupWithAnim(g, settingsAnimStart, () -> settingsMenu.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, ChatBubbleScreen::iconTex));
        renderPopupWithAnim(g, emojiAnimStart, () -> emojiPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, ICON_S, PAD));
        renderPopupWithAnim(g, quickAnimStart, () -> quickChatPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, quickChatInput));
        renderPopupWithAnim(g, searchAnimStart, () -> searchPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, searchInput, searchMatches, searchMatchIdx));
        // 输入框 widget 在 z=50 的 renderables 循环渲染，会被这里 z=100 的不透明面板背景盖住
        // （5bb740e 弹层 z 提升引入）——面板打开时在同 z 重画一次，文字/光标才可见。
        // widget 无背景（setBordered(false)），只画文字/光标，不遮挡面板内容
        if (quickChatPanel.visible && quickChatInput != null) quickChatInput.render(g, mouseX, mouseY, partialTick);
        if (searchPanel.visible && searchInput != null) searchInput.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();

        g.pose().popPose();

        // Sidebar on top of chat panel, with its own slide animation.
        // When closing, sidebar follows the chat panel's close animation.
        if (sidebarOpen || sidebarAnimating) {
            g.pose().pushPose();
            boolean fadeSidebar = pstyle == AnimationStyle.FADE && closing;
            int sidebarOffset = closing
                ? (int)((getAnimProgress() - 1.0f) * SIDEBAR_W)
                : getSidebarScreenX();
            g.pose().translate(sidebarOffset, 0, 50);
            if (fadeSidebar) RenderSystem.setShaderColor(1f, 1f, 1f, getAnimProgress());
            renderSidebar(g, mouseX - sidebarOffset, mouseY);
            if (fadeSidebar) RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            g.pose().popPose();
            if (closing) sidebarSearchBox.setX(2 + sidebarOffset);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        // EditBox is a widget drawn by super.render() at its real coords — it doesn't
        // follow the panel's pose translate, so slide it with the open/close animation
        input.setX(inputX + panelOffset);
        // 不调 super.render（ChatScreen.render 访问 package-private commandSuggestions，
        // 跨包无法初始化）；复制 Screen.render 的 widgets 遍历渲染
        for (net.minecraft.client.gui.components.Renderable w : this.renderables) {
            w.render(g, mouseX, mouseY, partialTick);
        }

        // 建议框定位基于 input.getScreenX()（屏幕坐标），与 input 同坐标空间渲染
        g.enableScissor(panelX, 0, panelX + panelW, height);
        if (suggestions != null) suggestions.render(g, mouseX, mouseY);
        g.disableScissor();

        com.niuqu.chatbubble.chat.notification.MentionNotificationBanner.INSTANCE.render(g,
            Minecraft.getInstance().getWindow().getGuiScaledWidth(),
            Minecraft.getInstance().getWindow().getGuiScaledHeight());

        g.pose().popPose();
    }

    private void renderTitleBar(GuiGraphics g, int mouseX, int mouseY) {
        float panelAlpha = ChatBubbleConfig.PANEL_OPACITY.get() / 100f * getAnimProgress();
        ChatBars.renderTitleBar(g, font, mouseX, mouseY, c(), panelX, panelW,
            getDisplayTitle(), LocalTime.now().format(TIME_FMT), iconTex("menu"), panelAlpha);
    }

    private boolean isMouseOverHamburger(double mx, double my) {
        int menuX = panelX + 3;
        int menuY = titleY + (TITLE_H - ICON_S) / 2;
        return mx >= menuX && mx <= menuX + ICON_S && my >= menuY && my <= menuY + ICON_S;
    }

    private void renderMessages(GuiGraphics g, int mouseX, int mouseY) {
        msgHeightCache.clear();
        bubbleRects.clear();
        clickableSpans.clear();
        List<ChatMessageStore.ChatMessage> messages;
        if (whisperPartner != null) {
            messages = ChatMessageStore.getWhisperMessages(whisperPartner);
        } else {
            messages = ChatMessageStore.getPublicMessages();
        }
        if (messages.isEmpty()) return;

        int indicatorH = 0;
        if (whisperPartner != null) {
            indicatorH = 14;
            int indY = msgTop;
            g.blit(UiTextureManager.rl(UiElement.WHISPER_BAR), panelX, indY, panelW, indicatorH, 0f, 0f, 1, 1, 1, 1);
            String modeText = Component.translatable("e33chat.whisper.mode").getString() + ": " + whisperPartner;
            int modeTW = font.width(modeText);
            g.drawString(font, Component.literal(modeText), panelX + (panelW - modeTW) / 2, indY + 2, c().textPrimary(), false);
        }

        int effectiveMsgTop = msgTop + indicatorH;
        int effectiveMsgBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
        int areaH = effectiveMsgBottom - effectiveMsgTop;

        int timeSeps = 0;
        String lastKey = null;
        int totalH = 0;
        for (var msg : messages) {
            totalH += getMsgHeight(msg) + GAP;
            if (!msg.isSystem()) {
                String key = timeKey(msg.time());
                if (lastKey == null || !key.equals(lastKey)) { timeSeps++; lastKey = key; }
            }
        }
        totalH += timeSeps * (TIME_SEP_H + GAP);
        int prevMaxScroll = maxScroll;
        maxScroll = Math.max(0, totalH - areaH);
        this.messageTotalH = totalH;

        boolean wasAtBottom = scrollOffset >= prevMaxScroll - 2;

        String playerName = minecraft.player != null ? minecraft.player.getName().getString() : "";
        int currentMsgCount = messages.size();
        if (wasAtBottom) {
            newMessageCount = 0;
            hasNewMentionOrQuote = false;
            latestMentionIndex = -1;
            lastSeenMessageCount = currentMsgCount;
        } else if (currentMsgCount > lastSeenMessageCount) {
            for (int i = lastSeenMessageCount; i < currentMsgCount; i++) {
                var msg = messages.get(i);
                if (msg == null) continue;
                newMessageCount++;
                if (msg.content().getString().contains("@" + playerName)) {
                    hasNewMentionOrQuote = true;
                    latestMentionIndex = i;
                }
                if (msg.replySender() != null && msg.replySender().equals(playerName)) {
                    hasNewMentionOrQuote = true;
                    if (i > latestMentionIndex) latestMentionIndex = i;
                }
            }
            lastSeenMessageCount = currentMsgCount;
        }

        if (firstRender) {
            scrollOffset = maxScroll;
            scrollToBottom = false;
            firstRender = false;
            scrollAnimActive = false;
        } else if (scrollAnimActive) {
            float t = Animation.progress(scrollAnimStart, scrollAnimDuration, false);
            scrollOffset = Math.round(scrollAnimFrom + (scrollAnimTo - scrollAnimFrom) * t);
            if (t >= 1.0f) {
                scrollOffset = Math.round(scrollAnimTo);
                scrollAnimActive = false;
            }
        } else if (scrollToBottom || wasAtBottom) {
            float newTarget = maxScroll;
            if (Math.abs(scrollOffset - newTarget) <= 3) {
                scrollOffset = Math.round(newTarget);
                scrollToBottom = false;
            } else {
                lastScrollTime = net.minecraft.Util.getMillis();
                scrollAnimFrom = scrollOffset;
                scrollAnimTo = newTarget;
                scrollAnimStart = net.minecraft.Util.getMillis();
                scrollAnimDuration = 150;
                scrollAnimActive = true;
            }
        }
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        g.enableScissor(panelX, effectiveMsgTop, panelX + panelW, effectiveMsgBottom);

        List<ChatMessageStore.ChatMessage> fullList = ChatMessageStore.getMessages();
        int fullIdx = 0;
        while (fullIdx < fullList.size() && fullList.get(fullIdx) != messages.get(0)) fullIdx++;

        int contentY = 0;
        lastKey = null;
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            while (fullIdx < fullList.size() && fullList.get(fullIdx) != msg) fullIdx++;

            // Time separator
            if (!msg.isSystem()) {
                String key = timeKey(msg.time());
                if (lastKey == null || !key.equals(lastKey)) {
                    lastKey = key;
                    int ssy = effectiveMsgTop + contentY - scrollOffset;
                    if (ssy + TIME_SEP_H > effectiveMsgTop && ssy < effectiveMsgBottom)
                        renderTimeSeparator(g, msg.time(), ssy);
                    contentY += TIME_SEP_H + GAP;
                }
            }

            int h = getMsgHeight(msg);
            int screenY = effectiveMsgTop + contentY - scrollOffset;
            contentY += h + GAP;

            if (screenY + h <= effectiveMsgTop || screenY >= effectiveMsgBottom) { fullIdx++; continue; }

            // New-message enter animation: fade in + slide up 8px, staggered 40ms
            // per message from the tail (250ms window, keyed on msg.time()).
            float mAlpha = 1f;
            int mDy = 0;
            if (ChatBubbleConfig.ANIMATION_ENABLED.get()) {
                AnimationStyle mstyle = ChatBubbleConfig.MESSAGE_ANIM_STYLE.get();
                if (mstyle != AnimationStyle.NONE) {
                    int tailIdx = messages.size() - 1 - i;
                    // msg.time() is epoch millis (System.currentTimeMillis), so the
                    // "now" side must use the same clock — the MC render clock is
                    // nanoTime-based and subtracting it yields a huge negative raw.
                    float raw = (float) (System.currentTimeMillis() - msg.time() - tailIdx * 40L) / 250f;
                    if (raw < 1f) {
                        float curve = Animation.styleCurve(mstyle, raw);
                        mAlpha = curve;
                        mDy = Math.round((1f - curve) * 8f);
                    }
                }
            }
            g.pose().pushPose();
            g.pose().translate(0, mDy, 0);
            renderBubble(g, msg, fullIdx, screenY, mouseX, mouseY, mAlpha);
            g.pose().popPose();
            fullIdx++;
        }
        renderScrollbar(g, mouseX, mouseY, effectiveMsgBottom);
        g.disableScissor();
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY, int effectiveMsgBottom) {
        boolean inZone = ChatScrollbar.isInZone(mouseX, panelX, panelW, mouseY, msgTop, effectiveMsgBottom);
        boolean recentlyScrolled = net.minecraft.Util.getMillis() - lastScrollTime < 1000;
        float target = ChatScrollbar.alphaTarget(inZone, scrollbarDragging, lastScrollTime);
        scrollbarAlpha = Animation.lerpTo(scrollbarAlpha, target, 0.15f, 0.005f);

        ChatLayout layout = new ChatLayout(panelX, panelW, titleY, msgTop, msgBottom, barTop, width, height);
        ChatScrollbar.render(g, layout, mouseX, mouseY, maxScroll, messageTotalH, scrollOffset,
            scrollbarDragging, scrollbarAlpha, effectiveMsgBottom);
    }

    private void renderTimeSeparator(GuiGraphics g, long timeMillis, int y) {
        ChatMessageRenderer.renderTimeSeparator(g, font, timeMillis, y, panelX, panelW, c());
    }

    private List<FormattedCharSequence> wrapContent(Component c, int width) {
        return ChatMessageRenderer.wrapContent(c, font, width);
    }

    private boolean isPanelSliding() {
        return ChatBubbleConfig.ANIMATION_ENABLED.get() && getAnimProgress() < 1.0f;
    }

    private int currentPanelOffset() {
        if (ChatBubbleConfig.PANEL_ANIM_STYLE.get() != AnimationStyle.SLIDE)
            return 0; // FADE/ZOOM/NONE have no horizontal displacement
        float anim = getAnimProgress();
        int moveDist;
        if (sidebarOpen) {
            moveDist = closing ? panelW : SIDEBAR_W;
        } else {
            moveDist = panelW;
        }
        return (int) ((anim - 1.0f) * moveDist);
    }

    private int getMsgHeight(ChatMessageStore.ChatMessage msg) {
        Integer cached = msgHeightCache.get(msg);
        if (cached != null) return cached;
        int bubbleMaxW = panelW - ChatMessageRenderer.AVATAR - ChatLayout.PAD * 2
            - ChatMessageRenderer.BUBBLE_PAD_X * 2 - 16;
        int h = ChatMessageRenderer.msgHeight(msg, font, bubbleMaxW);
        msgHeightCache.put(msg, h);
        return h;
    }


    private void renderBubble(GuiGraphics g, ChatMessageStore.ChatMessage msg,
                               int index, int baseY, int mouseX, int mouseY, float alpha) {
        boolean own = msg.isOwn();
        int bubbleMaxW = panelW - ChatMessageRenderer.AVATAR - ChatLayout.PAD * 2
            - ChatMessageRenderer.BUBBLE_PAD_X * 2 - 16;
        int ownBg = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_BUBBLE_COLOR.get(), 0xFF1E90FF);
        int otherBg = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_BUBBLE_COLOR.get(), c().contextHover());
        int ownFg = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR.get(), 0xFFFFFFFF);
        int otherFg = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR.get(), c().textPrimary());
        String skinName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
            ? msg.rawPlayerName() : msg.senderName().getString();
        ResourceLocation skin = getSkin(msg.senderUUID(), skinName);

        ChatMessageRenderer.renderBubble(g, font, msg, index, baseY, mouseX, mouseY,
            panelX, panelW, ownBg, otherBg, ownFg, otherFg, own,
            ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get(), c(), skin,
            searchHighlightIndex, bubbleMaxW, bubbleRects, clickableSpans, alpha);
    }

    private void renderLineWithClicks(GuiGraphics g, FormattedCharSequence line,
                                       int x, int y, int color) {
        ChatMessageRenderer.renderLineWithClicks(g, font, line, x, y, color, null, clickableSpans);
    }

    private void renderLineWithClicks(GuiGraphics g, FormattedCharSequence line,
                                       int x, int y, int color,
                                       net.minecraft.network.chat.Style fallback) {
        ChatMessageRenderer.renderLineWithClicks(g, font, line, x, y, color, fallback, clickableSpans);
    }

    private int prefixWidth(FormattedCharSequence line, int count) {
        return ChatMessageRenderer.prefixWidth(line, count, font);
    }

    private net.minecraft.network.chat.Style findClickStyle(net.minecraft.network.chat.Component c) {
        return ChatMessageRenderer.findClickStyle(c);
    }

    private net.minecraft.network.chat.Style getHoveredStyle(double mouseX, double mouseY) {
        for (ChatMessageRenderer.ClickableSpan s : clickableSpans) {
            if (mouseX >= s.x() && mouseX <= s.x() + s.w()
                && mouseY >= s.y() && mouseY <= s.y() + s.h())
                return s.style();
        }
        return null;
    }

    private void renderNotificationBar(GuiGraphics g, int mouseX, int mouseY) {
        if (newMessageCount <= 0) return;
        int notifY = barTop - NOTIF_H;
        g.blit(UiTextureManager.rl(UiElement.DIVIDER), panelX, notifY - 1, panelW, 1, 0f, 0f, 1, 1, 1, 1);
        int yellow = c().notificationText();
        int textY = notifY + (NOTIF_H - font.lineHeight) / 2;
        String ct = newMessageCount + Component.translatable("e33chat.notif.new_messages").getString() + " ▽";
        notifCountLeft = panelX + PAD;
        notifCountRight = notifCountLeft + font.width(ct);
        notifBarTextY = textY;
        boolean h = mouseX >= notifCountLeft && mouseX <= notifCountRight
            && mouseY >= textY && mouseY <= textY + font.lineHeight;
        g.drawString(font, Component.literal(ct), notifCountLeft, textY, h ? c().notificationText() : yellow, false);
        if (hasNewMentionOrQuote) {
            String mt = Component.translatable("e33chat.notif.mention").getString() + " ▽";
            notifMentionLeft = panelX + panelW - PAD - font.width(mt);
            notifMentionRight = notifMentionLeft + font.width(mt);
            h = mouseX >= notifMentionLeft && mouseX <= notifMentionRight
                && mouseY >= textY && mouseY <= textY + font.lineHeight;
            g.drawString(font, Component.literal(mt), notifMentionLeft, textY, h ? c().notificationText() : yellow, false);
        } else {
            notifMentionLeft = -1;
            notifMentionRight = -1;
        }
    }

    private void renderContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        if (contextMsgIndex < 0) return;
        ChatContextMenus.renderMessageMenu(g, font, mouseX, mouseY, c(), panelX, panelW,
            msgTop, iconTex("copy"), iconTex("quote"), contextX, contextY);
    }

    private void renderAvatarContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        if (contextAvatarIndex < 0) return;
        ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
        boolean isBlocked = msg != null
            && ChatMessageStore.isPlayerBlocked(msg.rawPlayerName(), msg.senderName(),
                ChatBubbleConfig.BLOCKED_PLAYERS.get());
        ChatContextMenus.renderAvatarMenu(g, font, mouseX, mouseY, c(), panelX, panelW,
            msgTop, iconTex("tp"), iconTex("whisper"), iconTex("block"), isBlocked,
            contextAvatarX, contextAvatarY, ChatMessageStore.useTpa());
    }

    private static final int REPLY_BAR_H = 18;

    private void renderReplyBar(GuiGraphics g, int mouseX, int mouseY) {
        if (replyTargetIndex < 0) return;
        ChatMessageStore.ChatMessage target = ChatMessageStore.getMessageAt(replyTargetIndex);
        if (target == null) { replyTargetIndex = -1; return; }

        int notifOffset = (newMessageCount > 0) ? NOTIF_H : 0;
        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int barX = gearX + ICON_S + 4;
        int barW = sendX - 6 - barX;
        int barY = barTop - REPLY_BAR_H - notifOffset;

        float panelBgAlpha = (c().panelBg() >>> 24) / 255f;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.PANEL_BG),
            barX, barY, barW, barTop - notifOffset - barY, panelBgAlpha);
        g.blit(UiTextureManager.rl(UiElement.DIVIDER), barX, barTop - notifOffset - 1, barW, 1, 0f, 0f, 1, 1, 1, 1);

        String sender = target.senderName().getString();
        if (sender.isEmpty()) sender = Component.translatable("e33chat.sender.system").getString();
        String preview = sender + ": " + ChatMessageStore.singleLine(target.content().getString());
        int maxW = barW - 24;
        String display = font.plainSubstrByWidth(preview, maxW - font.width("..."));
        if (!display.equals(preview)) display += "...";
        g.drawString(font, Component.literal(display), barX + 6, barY + 4, c().textSecondary(), false);

        int cx = barX + barW - 16;
        int cy = barY + 3;
        boolean hoverX = mouseX >= cx && mouseX <= cx + 12 && mouseY >= cy && mouseY <= cy + 12;
        g.blit(UiTextureManager.rl(hoverX ? UiElement.CLOSE_HOVER : UiElement.SIDEBAR_SELECTED),
            cx, cy, 12, 12, 0f, 0f, 1, 1, 1, 1);
        g.drawString(font, Component.literal("✕"), cx + 6 - font.width("✕") / 2, cy + 2, c().closeText(), false);
    }

    private boolean isMouseOverReplyCancel(double mx, double my) {
        if (replyTargetIndex < 0) return false;
        int notifOffset = (newMessageCount > 0) ? NOTIF_H : 0;
        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int barX = gearX + ICON_S + 4;
        int barW = sendX - 6 - barX;
        int barY = barTop - REPLY_BAR_H - notifOffset;
        int cx = barX + barW - 16;
        int cy = barY + 3;
        return mx >= cx && mx <= cx + 12 && my >= cy && my <= cy + 12;
    }

    private void renderMentionPopup(GuiGraphics g, int mouseX, int mouseY) {
        if (!showMentions || mentionCandidates.isEmpty()) return;

        int maxW = 60;
        for (String name : mentionCandidates)
            maxW = Math.max(maxW, font.width(name));
        int popupW = maxW + 12;
        int visible = Math.min(mentionCandidates.size(), 8);
        int popupH = visible * font.lineHeight + 4;
        int popupX = input.getX();
        int popupY = input.getY() - popupH - 2;
        if (popupY < msgTop) popupY = input.getY() + input.getHeight() + 2;

        g.blit(UiTextureManager.rl(UiElement.POPUP_BG), popupX, popupY, popupW, popupH, 0f, 0f, 1, 1, 1, 1);
        g.renderOutline(popupX, popupY, popupW, popupH, c().divider());

        int startIdx = Math.max(0, mentionIdx - visible + 1);
        int endIdx = Math.min(mentionCandidates.size(), startIdx + visible);
        if (endIdx - startIdx < visible)
            startIdx = Math.max(0, endIdx - visible);
        for (int i = startIdx; i < endIdx; i++) {
            int ly = popupY + 2 + (i - startIdx) * font.lineHeight;
            boolean hover = mouseX >= popupX && mouseX <= popupX + popupW
                && mouseY >= ly && mouseY <= ly + font.lineHeight;
            if (i == mentionIdx)
                g.fill(popupX + 1, ly, popupX + popupW - 1, ly + font.lineHeight, c().popupHover());            g.drawString(font, Component.literal(mentionCandidates.get(i)),
                popupX + 4, ly, c().textPrimary(), false);
        }
    }

    private void renderToast(GuiGraphics g) {
        if (copyToastTicks <= 0) return;
        int alpha = Animation.fadeInOut(copyToastTicks, 5, 20, 5);
        int color = (alpha << 24) | (c().toastText() & 0x00FFFFFF);
        String text = Component.translatable("e33chat.toast.copied").getString();
        int tw = font.width(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        int ty = msgBottom - 24;
        // Background fades with the text, at half opacity like the strong-hint bar
        // TOAST_BG 烘焙不透明 toastBg；纹理 × 动态 alpha = 半透明淡入淡出。2.2.4 黑块根因：
        // 当时 blit 无 alpha 通道渲染不透明纯黑 → drawWithAlpha 后纹理可覆盖 + 透明度可控
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.TOAST_BG),
            tx - 6, ty - 2, tw + 12, font.lineHeight + 4, (alpha / 2) / 255f);
        g.drawString(font, Component.literal(text), tx, ty, color, false);
    }

    private void executeMenuAction(int action) {
        switch (action) {
            case 0: // 搜索
                if (quickChatPanel.visible) { quickChatPanel.visible = false; quickChatInput.setVisible(false); }
                if (emojiPanel.visible) emojiPanel.visible = false;
                searchPanel.visible = true;
                searchAnimStart = net.minecraft.Util.getMillis();
                searchInput.setValue("");
                searchMatches.clear();
                searchMatchIdx = -1;
                searchHighlightIndex = -1;
                setFocused(searchInput);
                break;
            case 1: // 常用语
                if (searchPanel.visible) closeSearchPanel();
                if (emojiPanel.visible) emojiPanel.visible = false;
                quickChatPanel.visible = true;
                quickAnimStart = net.minecraft.Util.getMillis();
                quickChatPanel.scrollOffset = 0;
                quickChatInput.setValue("");
                setFocused(input);
                break;
            case 2: { // 主题
                ChatBubbleTheme next = ChatBubbleConfig.THEME.get() == ChatBubbleTheme.DARK
                    ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
                ChatBubbleConfig.THEME.set(next);
                int editColor = ChatBubbleConfig.THEME.get() == ChatBubbleTheme.LIGHT
                    ? c().textSecondary() : c().textPrimary();
                input.setTextColor(editColor);
                input.setTextColorUneditable(c().textMuted());
                sidebarSearchBox.setTextColor(editColor);
                sidebarSearchBox.setTextColorUneditable(editColor);
                quickChatInput.setTextColor(editColor);
                quickChatInput.setTextColorUneditable(c().textMuted());
                searchInput.setTextColor(editColor);
                searchInput.setTextColorUneditable(c().textMuted());
                int cmdAlpha = ChatBubbleConfig.THEME.get() == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
                suggestions = new CommandSuggestions(minecraft, this, input, font,
                    false, false, 0, 8, true, ChatBubbleTheme.alphaBlend(c().panelBg(), cmdAlpha));
                break;
            }
            case 3: // 设置
                minecraft.setScreen(new ChatBubbleConfigScreen(this));
                break;
        }
    }

    private void closeSearchPanel() {
        searchPanel.visible = false;
        searchInput.setVisible(false);
        searchMatches.clear();
        searchMatchIdx = -1;
        searchHighlightIndex = -1;
        setFocused(input);
    }




    private void renderBottomBar(GuiGraphics g, int mouseX, int mouseY) {
        float panelAlpha = ChatBubbleConfig.PANEL_OPACITY.get() / 100f * getAnimProgress();
        ChatBars.renderBottomBar(g, font, mouseX, mouseY, c(), panelX, panelW, barTop, height,
            inputX, inputY, input.getWidth(), input.isFocused(), emojiPanel.visible,
            iconTex("settings"), iconTex("emoji"), iconTex("send"), panelAlpha);
    }

    static void drawTextureIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        // getTexture 无缓存时自动 new SimpleTexture 懒加载（资源包可覆盖，F3+T 即时生效）
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        if (size < 16) {
            // 图标 16x16 内容居中占 14x14（1px 透明边）——全幅采样压缩会切出边缘切割
            g.blit(tex, x, y, size, size, 1f, 1f, 14, 14, 16, 16);
        } else {
            g.blit(tex, x, y, 0, 0, size, size, size, size);
        }
    }

    private static final UUID NIL_UUID = new UUID(0, 0);

    private void drawPlayerHead(GuiGraphics g, ResourceLocation skin, int x, int y, int baseSize, int hatSize) {
        RenderSystem.enableBlend();
        g.blit(skin, x, y, baseSize, baseSize, 8.0F, 8.0F, 8, 8, 64, 64);
        int hatOff = (hatSize - baseSize) / 2;
        g.blit(skin, x - hatOff, y - hatOff, hatSize, hatSize, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private ResourceLocation getSkin(UUID uuid, String name) {
        // Online players: read PlayerInfo fresh every frame. getSkinLocation() returns
        // the default skin and kicks off an async download on first call, then updates
        // in place once done. Caching that first (default) result froze the head on
        // Steve/Alex forever even after the real skin loaded — the entity model reads
        // this fresh each frame, which is why the body showed the skin but the head didn't.
        // CSL intercepts the underlying SkinManager lookup, so CSL skins flow through too.
        if (minecraft.getConnection() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getSkinLocation();
        }
        // Not in the tab list (offline player / history mention): route through the
        // SkinManager with a GameProfile carrying the name. CSL keys off the name, so
        // offline players with an imported skin resolve; otherwise vanilla (real skin
        // for paid accounts carrying textures, Steve/Alex otherwise). The first result
        // is final here, so cache it to avoid repeating the lookup every frame.
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            ResourceLocation cached = skinCache.get(uuid);
            if (cached != null) return cached;
        }
        ResourceLocation resolved = resolveSkin(uuid, name);
        if (uuid != null && !uuid.equals(NIL_UUID)) skinCache.put(uuid, resolved);
        return resolved;
    }

    private ResourceLocation resolveSkin(UUID uuid, String name) {
        if (name == null || name.isEmpty())
            return DefaultPlayerSkin.getDefaultSkin(uuid != null ? uuid : NIL_UUID);
        try {
            GameProfile profile = new GameProfile(
                uuid != null && !uuid.equals(NIL_UUID) ? uuid : NIL_UUID, name);
            return minecraft.getSkinManager().getInsecureSkinLocation(profile);
        } catch (Exception e) {
            return DefaultPlayerSkin.getDefaultSkin(uuid != null ? uuid : NIL_UUID);
        }
    }

    private void jumpToMessage(int msgIndex) {
        var msgs = ChatMessageStore.getMessages();
        if (msgIndex < 0 || msgIndex >= msgs.size()) return;
        int cy = 0;
        String lk = null;
        for (int i = 0; i < msgIndex && i < msgs.size(); i++) {
            var m = msgs.get(i);
            if (!m.isSystem()) {
                String k = timeKey(m.time());
                if (lk == null || !k.equals(lk)) {
                    lk = k;
                    cy += TIME_SEP_H + GAP;
                }
            }
            cy += getMsgHeight(m) + GAP;
        }
        scrollOffset = Math.max(0, cy - 20);
        newMessageCount = 0;
        hasNewMentionOrQuote = false;
        latestMentionIndex = -1;
        lastSeenMessageCount = msgs.size();
    }

    // Parse legacy & color/format codes into a real styled Component for LOCAL display
    // only — the raw text is what gets sent. '&'+code becomes formatting (not visible
    // text), exactly like § renders; a bare & stays literal. Lets the player see their
    // own colored bubble without ever putting § on the wire (which vanilla rejects).
    private static Component parseColorCodes(String s) {
        if (s.indexOf('&') < 0) return Component.literal(s);
        MutableComponent out = Component.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && isFormatCode(s.charAt(i + 1))) {
                if (run.length() > 0) {
                    out.append(Component.literal(run.toString()).withStyle(style));
                    run.setLength(0);
                }
                style = applyCode(style, s.charAt(i + 1));
                i++;
            } else {
                run.append(c);
            }
        }
        if (run.length() > 0) out.append(Component.literal(run.toString()).withStyle(style));
        return out;
    }

    private static Style applyCode(Style st, char c) {
        switch (Character.toLowerCase(c)) {
            case '0': return st.withColor(TextColor.fromRgb(ChatFormatting.BLACK.getColor()));
            case '1': return st.withColor(TextColor.fromRgb(ChatFormatting.DARK_BLUE.getColor()));
            case '2': return st.withColor(TextColor.fromRgb(ChatFormatting.DARK_GREEN.getColor()));
            case '3': return st.withColor(TextColor.fromRgb(ChatFormatting.DARK_AQUA.getColor()));
            case '4': return st.withColor(TextColor.fromRgb(ChatFormatting.DARK_RED.getColor()));
            case '5': return st.withColor(TextColor.fromRgb(ChatFormatting.DARK_PURPLE.getColor()));
            case '6': return st.withColor(TextColor.fromRgb(ChatFormatting.GOLD.getColor()));
            case '7': return st.withColor(TextColor.fromRgb(ChatFormatting.GRAY.getColor()));
            case '8': return st.withColor(TextColor.fromRgb(ChatFormatting.DARK_GRAY.getColor()));
            case '9': return st.withColor(TextColor.fromRgb(ChatFormatting.BLUE.getColor()));
            case 'a': return st.withColor(TextColor.fromRgb(ChatFormatting.GREEN.getColor()));
            case 'b': return st.withColor(TextColor.fromRgb(ChatFormatting.AQUA.getColor()));
            case 'c': return st.withColor(TextColor.fromRgb(ChatFormatting.RED.getColor()));
            case 'd': return st.withColor(TextColor.fromRgb(ChatFormatting.LIGHT_PURPLE.getColor()));
            case 'e': return st.withColor(TextColor.fromRgb(ChatFormatting.YELLOW.getColor()));
            case 'f': return st.withColor(TextColor.fromRgb(ChatFormatting.WHITE.getColor()));
            case 'k': return st.withObfuscated(true);
            case 'l': return st.withBold(true);
            case 'm': return st.withStrikethrough(true);
            case 'n': return st.withUnderlined(true);
            case 'o': return st.withItalic(true);
            case 'r': return Style.EMPTY;
            default: return st;
        }
    }

    private static boolean isFormatCode(char c) {
        return (c >= '0' && c <= '9') || (c >= 'a' && c <= 'f')
            || (c >= 'k' && c <= 'o') || (c >= 'A' && c <= 'F')
            || (c >= 'K' && c <= 'O');
    }

    private void sendMessage() {
        String raw = input.getValue().trim();
        if (raw.isEmpty()) return;
        // Send the text UNCHANGED (raw '&', never '§'): vanilla servers reject '§' in
        // player chat and kick, so converting client-side is a dead end. Server color
        // plugins (Essentials etc.) translate '&' for everyone; on plain servers others
        // see the literal '&'. Local coloring of our own bubble is done at addMessage.
        String text = raw;

        String whisperTarget = null;
        String displayText = text;

        // In whisper mode, auto-prepend /msg behind the scenes
        if (whisperPartner != null && !text.startsWith("/")) {
            text = "/msg " + whisperPartner + " " + text;
            whisperTarget = whisperPartner;
            displayText = raw;
        }

        if (whisperTarget == null && (text.startsWith("/msg ") || text.startsWith("/tell ") || text.startsWith("/w ") || text.startsWith("/whisper "))) {
            String[] parts = text.split(" ", 3);
            if (parts.length >= 3) {
                whisperTarget = parts[1];
                displayText = parts[2];
            }
        }

        // Vanilla doesn't echo commands into chat — only real chat and whispers get a local bubble
        boolean localBubble = !text.startsWith("/") || whisperTarget != null;

        if (replyTargetIndex >= 0) {
            if (localBubble) {
                ChatMessageStore.ChatMessage target = ChatMessageStore.getMessageAt(replyTargetIndex);
                if (target != null) {
                    String quoteSender = (target.rawPlayerName() != null && !target.rawPlayerName().isEmpty())
                        ? target.rawPlayerName() : target.senderName().getString();
                    String quoted = ChatMessageStore.singleLine(target.content().getString());
                    ChatMessageStore.setPendingReply(quoted, quoteSender);
                    QuoteSyncPacket.send(quoteSender, quoted, displayText);
                }
            }
            replyTargetIndex = -1;
        }

        if (text.startsWith("/"))
            minecraft.player.connection.sendCommand(text.substring(1));
        else
            minecraft.player.connection.sendChat(text);
        // Record what the user typed — never the behind-the-scenes /msg splice,
        // or up-arrow history would leak the hidden command (v1.4 promise)
        minecraft.gui.getChat().addRecentChat(raw);

        String logCmd = text, logDisp = displayText, logTarget = whisperTarget;
        boolean logBub = localBubble;
        ChatMessageStore.debugLog(() -> "[e33chat] Send | cmd='" + logCmd + "' | display='" + logDisp + "' | whisperTarget=" + logTarget + " | localBubble=" + logBub);
        if (localBubble) {
            Component bubbleContent = ChatBubbleConfig.COLOR_CODES.get()
                ? parseColorCodes(displayText) : Component.literal(displayText);
            // Convert embedded image codes so the outgoing bubble previews the
            // image like the vanilla chat does (ChatImage may be absent — then
            // convert passes through unchanged)
            ChatMessageStore.addMessage(ChatImageCompat.convert(bubbleContent),
                minecraft.player.getUUID(),
                ChatMessageStore.ownDisplayName(),
                false,
                minecraft.player.getName().getString(),
                whisperTarget != null, whisperTarget, true);
            ChatMessageStore.incrementPendingEcho(text);
        }
        if (whisperTarget != null) ChatMessageStore.markPendingWhisperEcho(whisperTarget);

        input.setValue("");
        savedInput = "";
        scrollToBottom = true;
    }

    // 父类 moveInHistory 访问 package-private commandSuggestions（跨包 null），
    // override 用自己的实现（history 字段也私有化到本类）
    @Override
    public void moveInHistory(int delta) {
        int size = minecraft.gui.getChat().getRecentChat().size();
        int newPos = Mth.clamp(historyPos + delta, 0, size);
        if (newPos != historyPos) {
            if (newPos == size) {
                historyPos = size;
                input.setValue(historyBuffer);
            } else {
                if (historyPos == size) historyBuffer = input.getValue();
                input.setValue(minecraft.gui.getChat().getRecentChat().get(newPos));
                historyPos = newPos;
            }
        }
    }

    // 父类 resize 访问 package-private commandSuggestions（跨包 null）→ 自实现
    @Override
    public void resize(Minecraft mc, int w, int h) {
        String cur = input.getValue();
        this.init(mc, w, h);
        input.setValue(cur);
        suggestions.updateCommandInfo();
    }

    @Override
    public void removed() {
        if (ChatBubbleConfig.PRESERVE_INPUT.get()) savedInput = input.getValue();
        ChatMessageStore.setScreenOpen(false);
        minecraft.gui.getChat().resetChatScroll();
    }

    @Override
    public void onClose() {
        if (ChatBubbleConfig.PRESERVE_INPUT.get()) savedInput = input.getValue();
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) {
            minecraft.setScreen(null);
            return;
        }
        if (closing) return;
        closing = true;
        animStart = net.minecraft.Util.getMillis();
    }

    public static int getInputX() { return inputX; }
    public static int getInputY() { return inputY; }

    @Override
    public boolean isPauseScreen() { return false; }
}
