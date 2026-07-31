package com.niuqu.chatbubble;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.TextColor;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;
import com.niuqu.chatbubble.packets.QuoteSyncPayload;
import com.niuqu.chatbubble.render.ChatBars;
import com.niuqu.chatbubble.render.ChatContextMenus;
import com.niuqu.chatbubble.render.ChatLayout;
import com.niuqu.chatbubble.render.ChatMessageRenderer;
import com.niuqu.chatbubble.render.ChatScrollbar;
import com.niuqu.chatbubble.render.ChatSidebar;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

public class ChatBubbleScreen extends Screen {

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
    private static ChatBubbleTheme loadedTheme;

    static ResourceLocation iconTex(String name) {
        String theme = ChatBubbleConfig.THEME.get().name().toLowerCase();
        return ResourceLocation.fromNamespaceAndPath("e33chat", "textures/gui/" + theme + "/" + name);
    }

    private static void ensureIconsLoaded() {
        var theme = ChatBubbleConfig.THEME.get();
        if (loadedTheme == theme) return;
        loadIconTextures();
        loadedTheme = theme;
    }




    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static String timeKey(long t) {
        return ChatMessageRenderer.timeKey(t, ChatBubbleConfig.TIME_SEPARATOR_MINUTES.get());
    }

    private EditBox input;
    private CommandSuggestions commandSuggestions;
    private static int inputX, inputY;
    // Caches resolved head skins per player uuid so the SkinManager isn't hit every frame
    private static final Map<UUID, ResourceLocation> skinCache = new HashMap<>();
    private final String initialText;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollToBottom = true;
    private boolean firstRender = true;
    private static String savedInput = "";
    private String historyBuffer = "";
    private int historyPos = -1;

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
    private final List<String> mentionCandidates = new ArrayList<>();
    private int mentionIdx;
    private String mentionFilter = "";

    // Right-click menu
    private int contextMsgIndex = -1;
    private int contextX, contextY;
    private int contextAvatarIndex = -1;
    private int contextAvatarX, contextAvatarY;

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
        super(Component.translatable("e33chat.screen.title"));
        this.initialText = initialText;
    }

    @Override
    protected void init() {
        historyPos = minecraft.gui.getChat().getRecentChat().size();
        ChatMessageStore.setScreenOpen(true);
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
        input.setResponder(this::onEdited);
        addRenderableWidget(input);

        int cmdBgAlpha = ChatBubbleConfig.THEME.get() == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
        commandSuggestions = new CommandSuggestions(minecraft, this, input, font,
            false, false, 0, 8, true, ChatBubbleTheme.alphaBlend(c().panelBg(), cmdBgAlpha));
        commandSuggestions.updateCommandInfo();

        ensureIconsLoaded();

        sidebarSearchBox = new EditBox(font, 2, 5, SIDEBAR_W - 5, 14, Component.literal(""));
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
        input.moveCursorToEnd(false);
        showMentions = false;
    }

    private void onEdited(String text) {
        showMentions = false;
        int atIdx = text.lastIndexOf('@');
        if (atIdx >= 0 && minecraft.player != null && minecraft.player.connection != null) {
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
        if (commandSuggestions != null) {
            commandSuggestions.setAllowSuggestions(!text.equals(initialText));
            commandSuggestions.updateCommandInfo();
        }
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
        if (closing && net.minecraft.Util.getMillis() - animStart >= ANIM_MS)
            minecraft.setScreen(null);
    }

    private float getAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) return 1.0f;
        return Animation.progress(animStart, ANIM_MS, closing);
    }

    private float getSidebarAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) return sidebarOpen ? 1f : 0f;
        if (sidebarAnimating) {
            float progress = Animation.progress(sidebarAnimStart, ANIM_MS, false);
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
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // No-op: prevents vanilla 1.21.1 renderBlurredBackground()
        // (processBlurEffect) from blurring the entire screen.
        // Our panel blur is applied manually in render() before the panel background.
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
                return true;
            }
            if (keyCode == 265) { // Up
                mentionIdx = mentionIdx > 0 ? mentionIdx - 1 : mentionCandidates.size() - 1;
                return true;
            }
            if (keyCode == 264) { // Down
                mentionIdx = mentionIdx < mentionCandidates.size() - 1 ? mentionIdx + 1 : 0;
                return true;
            }
            if (keyCode == 257 || keyCode == 335) { // Enter
                insertMention(mentionCandidates.get(mentionIdx));
                return true;
            }
        }

        if (commandSuggestions != null && commandSuggestions.keyPressed(keyCode, scanCode, modifiers))
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
            if (commandSuggestions != null) commandSuggestions.hide();
            sendMessage();
            return true;
        }
        if (keyCode == 265) { moveInHistory(-1); return true; }
        if (keyCode == 264) { moveInHistory(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (emojiPanel.visible) {
            emojiPanel.handleScroll(scrollY);
            return true;
        }
        if (quickChatPanel.visible) {
            quickChatPanel.handleScroll(scrollY);
            return true;
        }
        if (searchPanel.visible && !searchMatches.isEmpty()) {
            searchMatchIdx = Mth.clamp(searchMatchIdx - (int) scrollY, 0, searchMatches.size() - 1);
            searchHighlightIndex = searchMatches.get(searchMatchIdx);
            jumpToMessage(searchHighlightIndex);
            return true;
        }
        if (showMentions && !mentionCandidates.isEmpty()) {
            mentionIdx = Mth.clamp(mentionIdx - (int) scrollY, 0, mentionCandidates.size() - 1);
            return true;
        }
        int sidebarX = (int) getSidebarScreenX();
        if ((sidebarOpen || sidebarAnimating)
            && mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_W) {
            sidebarScrollOffset = Mth.clamp(sidebarScrollOffset - (int)(scrollY * 20), 0, sidebarMaxScroll);
            return true;
        }
        if (commandSuggestions != null && commandSuggestions.mouseScrolled(scrollY))
            return true;
        scrollToBottom = false;
        lastScrollTime = net.minecraft.Util.getMillis();
        float newTarget = Mth.clamp(scrollOffset - (int)(scrollY * 40), 0, maxScroll);
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
        int sidebarX = (int) getSidebarScreenX();
        if (ChatSidebar.handleMouseClicked(mouseX, mouseY, whisperPartner, font,
                sidebarX, sidebarOpen || sidebarAnimating, sidebarSearchBox, sidebarScrollOffset)) {
            // Search box
            int searchY = 2;
            int searchH = 14;
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

        if (commandSuggestions != null && commandSuggestions.mouseClicked((int) mouseX, (int) mouseY, button))
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
                    input.moveCursorToEnd(false);
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
        return super.mouseClicked(mouseX, mouseY, button);
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
            settingsMenu.visible = !settingsMenu.visible;
            return true;
        }
        // Emoji icon — toggle emoji panel
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;
        if (mx >= emojiX && mx <= emojiX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (settingsMenu.visible) settingsMenu.visible = false;
            if (searchPanel.visible) closeSearchPanel();
            emojiPanel.visible = !emojiPanel.visible;
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
                copyToastTicks = 20;
            }
        } else if (ChatContextMenus.isOverItem(mx, my, menuX,
            menuY + ChatContextMenus.CTX_ITEM_H + 1, ChatContextMenus.CTX_ITEM_H)) {
            replyTargetIndex = contextMsgIndex;
        }
        contextMsgIndex = -1;
    }

    private void handleAvatarContextClick(int mx, int my) {
        int menuH = ChatContextMenus.CTX_ITEM_H * 2 + 3;
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
        }
        contextAvatarIndex = -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tickSidebarAnimation();

        float anim = getAnimProgress();
        int fillLeft = (!sidebarAnimating && sidebarOpen)
            ? (int)(anim * SIDEBAR_W) : panelX;

        int moveDist;
        if (sidebarOpen) {
            moveDist = closing ? panelW : SIDEBAR_W;
        } else {
            moveDist = panelW;
        }
        int panelOffset = (int) ((anim - 1.0f) * moveDist);

        if (ChatBubbleConfig.BLUR_ENABLED.get()) {
            BlurRenderer.blurPanel(fillLeft, 0, panelX + panelW - fillLeft, height);
        }

        g.pose().pushPose();
        g.pose().translate(panelOffset, 0, 0);

        float opacity = ChatBubbleConfig.PANEL_OPACITY.get() / 100f * anim;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.PANEL_BG),
            fillLeft, 0, panelX + panelW - fillLeft, height, opacity);

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
        settingsMenu.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, ChatBubbleScreen::iconTex);
        emojiPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, ICON_S, PAD);
        quickChatPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, quickChatInput);
        searchPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, searchInput, searchMatches, searchMatchIdx);
        renderBottomBar(g, mouseX, mouseY);
        renderMentionPopup(g, mouseX, mouseY);

        g.enableScissor(panelX, 0, panelX + panelW, height);
        if (commandSuggestions != null) commandSuggestions.render(g, mouseX, mouseY);
        g.disableScissor();

        g.pose().popPose();

        // Sidebar on top of chat panel, with its own slide animation
        if (sidebarOpen || sidebarAnimating) {
            g.pose().pushPose();
            int sidebarOffset = closing
                ? (int)((getAnimProgress() - 1.0f) * SIDEBAR_W)
                : (int) getSidebarScreenX();
            g.pose().translate(sidebarOffset, 0, 50);
            renderSidebar(g, mouseX - sidebarOffset, mouseY);
            g.pose().popPose();
            if (closing) sidebarSearchBox.setX(2 + sidebarOffset);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        // EditBox is a widget drawn by super.render() at its real coords — it doesn't
        // follow the panel's pose translate, so slide it with the open/close animation
        input.setX(inputX + panelOffset);
        super.render(g, mouseX, mouseY, partialTick);

        // Banner rendered here after super.render() so it's always on top of the panel
        // (the HUD-layer render draws behind the screen batch on Forge/NeoForge).
        MentionNotificationBanner.INSTANCE.render(g,
            Minecraft.getInstance().getWindow().getGuiScaledWidth(),
            Minecraft.getInstance().getWindow().getGuiScaledHeight());

        g.pose().popPose();

    }

    private void renderTitleBar(GuiGraphics g, int mouseX, int mouseY) {
        ChatBars.renderTitleBar(g, font, mouseX, mouseY, c(), panelX, panelW,
            getDisplayTitle(), LocalTime.now().format(TIME_FMT), iconTex("menu"));
    }

    private boolean isMouseOverHamburger(double mx, double my) {
        int menuX = panelX + 3;
        int menuY = titleY + (TITLE_H - ICON_S) / 2;
        return mx >= menuX && mx <= menuX + ICON_S && my >= menuY && my <= menuY + ICON_S;
    }

    private void renderMessages(GuiGraphics g, int mouseX, int mouseY) {
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
            g.fill(panelX, indY, panelX + panelW, indY + indicatorH, c().whisperBar());
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
            renderBubble(g, msg, fullIdx, screenY, mouseX, mouseY);
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

    private int getMsgHeight(ChatMessageStore.ChatMessage msg) {
        int bubbleMaxW = panelW - ChatMessageRenderer.AVATAR - ChatLayout.PAD * 2
            - ChatMessageRenderer.BUBBLE_PAD_X * 2 - 16;
        return ChatMessageRenderer.msgHeight(msg, font, bubbleMaxW);
    }


    private void renderBubble(GuiGraphics g, ChatMessageStore.ChatMessage msg,
                               int index, int baseY, int mouseX, int mouseY) {
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
            searchHighlightIndex, bubbleMaxW, bubbleRects, clickableSpans);
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
        ChatContextMenus.renderAvatarMenu(g, font, mouseX, mouseY, c(), panelX, panelW,
            msgTop, iconTex("tp"), iconTex("whisper"), contextAvatarX, contextAvatarY,
            ChatMessageStore.useTpa());
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
        int xBg = hoverX ? c().closeHoverBg() : c().sidebarItemSelected();
        g.fill(cx, cy, cx + 12, cy + 12, xBg);
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

        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, c().popupBg());
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
                g.fill(popupX + 1, ly, popupX + popupW - 1, ly + font.lineHeight, c().popupHover());
            g.drawString(font, Component.literal(mentionCandidates.get(i)),
                popupX + 4, ly, c().textPrimary(), false);
        }
    }

    private void renderToast(GuiGraphics g) {
        if (copyToastTicks <= 0) return;
        int alpha = Animation.fadeIn(copyToastTicks, 5) << 24;
        int color = alpha | (c().toastText() & 0x00FFFFFF);
        String text = Component.translatable("e33chat.toast.copied").getString();
        int tw = font.width(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        int ty = msgBottom - 24;
        g.fill(tx - 6, ty - 2, tx + tw + 6, ty + font.lineHeight + 2, c().toastBg());
        g.drawString(font, Component.literal(text), tx, ty, color, false);
    }

    private void executeMenuAction(int action) {
        switch (action) {
            case 0: // 搜索
                if (quickChatPanel.visible) { quickChatPanel.visible = false; quickChatInput.setVisible(false); }
                if (emojiPanel.visible) emojiPanel.visible = false;
                searchPanel.visible = true;
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
                quickChatPanel.scrollOffset = 0;
                quickChatInput.setValue("");
                setFocused(input);
                break;
            case 2: { // 主题
                ChatBubbleTheme next = ChatBubbleConfig.THEME.get() == ChatBubbleTheme.DARK
                    ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
                ChatBubbleConfig.THEME.set(next);
                ensureIconsLoaded();
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
                commandSuggestions = new CommandSuggestions(minecraft, this, input, font,
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
        ChatBars.renderBottomBar(g, font, mouseX, mouseY, c(), panelX, panelW, barTop, height,
            inputX, inputY, input.getWidth(), input.isFocused(), emojiPanel.visible,
            iconTex("settings"), iconTex("emoji"), iconTex("send"));
    }

    // HUD 未读角标复用 private_tip 等图标纹理，需跨类调用（package-private）
    static void loadIconTextures() {
        String theme = ChatBubbleConfig.THEME.get().name().toLowerCase();
        String base = "assets/e33chat/textures/gui/" + theme + "/";
        loadIconTexture(iconTex("settings"), base + "settings.png");
        loadIconTexture(iconTex("send"), base + "send.png");
        loadIconTexture(iconTex("emoji"), base + "emoji.png");
        loadIconTexture(iconTex("menu"), base + "menu.png");
        loadIconTexture(iconTex("public_icon"), base + "public_icon.png");
        loadIconTexture(iconTex("private_tip"), base + "private_tip.png");
        loadIconTexture(iconTex("no_online"), base + "no_online.png");
        loadIconTexture(iconTex("theme"), base + "theme.png");
        loadIconTexture(iconTex("quick_chat"), base + "quick_chat.png");
        loadIconTexture(iconTex("copy"), base + "copy.png");
        loadIconTexture(iconTex("quote"), base + "quote.png");
        loadIconTexture(iconTex("tp"), base + "tp.png");
        loadIconTexture(iconTex("whisper"), base + "whisper.png");
        loadIconTexture(iconTex("search"), base + "search.png");
    }

    private static void loadIconTexture(ResourceLocation loc, String classpath) {
        try (InputStream in = ChatBubbleScreen.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                DynamicTexture tex = new DynamicTexture(img);
                Minecraft.getInstance().getTextureManager().register(loc, tex);
            }
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().error("[e33chat] Failed to load icon texture", e);
        }
    }

    static void drawTextureIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        var tm = Minecraft.getInstance().getTextureManager();
        AbstractTexture abstractTex;
        try {
            abstractTex = tm.getTexture(tex);
        } catch (Exception e) {
            loadIconTextures();
            abstractTex = tm.getTexture(tex);
        }
        RenderSystem.setShaderTexture(0, abstractTex.getId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        g.blit(tex, x, y, 0, 0, size, size, size, size);
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
        // Online players: read PlayerInfo fresh every frame. getSkin().texture() returns
        // the default skin and kicks off an async download on first call, then updates
        // in place once done. Caching that first (default) result froze the head on
        // Steve/Alex forever even after the real skin loaded — the entity model reads
        // this fresh each frame, which is why the body showed the skin but the head didn't.
        // CSL intercepts the underlying SkinManager lookup, so CSL skins flow through too.
        if (minecraft.getConnection() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getSkin().texture();
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
            return DefaultPlayerSkin.get(uuid != null ? uuid : NIL_UUID).texture();
        try {
            GameProfile profile = new GameProfile(
                uuid != null && !uuid.equals(NIL_UUID) ? uuid : NIL_UUID, name);
            return minecraft.getSkinManager().getInsecureSkin(profile).texture();
        } catch (Exception e) {
            return DefaultPlayerSkin.get(uuid != null ? uuid : NIL_UUID).texture();
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
                    QuoteSyncPayload.send(quoteSender, quoted, displayText);
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
            ChatMessageStore.addMessage(ChatBubbleConfig.COLOR_CODES.get() ? parseColorCodes(displayText) : Component.literal(displayText),
                minecraft.player.getUUID(),
                Component.literal(minecraft.player.getName().getString()),
                false,
                minecraft.player.getName().getString(),
                whisperTarget != null, whisperTarget);
            ChatMessageStore.incrementPendingEcho(text);
        }
        if (whisperTarget != null) ChatMessageStore.markPendingWhisperEcho(whisperTarget);

        input.setValue("");
        savedInput = "";
        scrollToBottom = true;
    }

    private void moveInHistory(int delta) {
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
