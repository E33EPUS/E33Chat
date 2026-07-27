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
import com.niuqu.chatbubble.packets.QuoteSyncPacket;
import com.niuqu.chatbubble.render.ChatLayout;
import com.niuqu.chatbubble.render.ChatScrollbar;
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

    private static String timeKey(LocalTime t) {
        int interval = ChatBubbleConfig.TIME_SEPARATOR_MINUTES.get();
        if (interval <= 0) return "";
        if (interval == 1) return t.format(TIME_FMT);
        int m = (t.getMinute() / interval) * interval;
        return String.format("%02d:%02d", t.getHour(), m);
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
    private static boolean sidebarOpen;
    private static String whisperPartner;
    private int sidebarScrollOffset;
    private int sidebarMaxScroll;
    private EditBox sidebarSearchBox;

    // Sidebar animation
    private long sidebarAnimStart;
    private boolean sidebarTargetOpen;
    private boolean sidebarAnimating;

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
    private static final int CTX_W = 80;
    private static final int CTX_ITEM_H = 18;
    private int contextAvatarIndex = -1;
    private int contextAvatarX, contextAvatarY;

    // Bubble hit tracking
    private final List<int[]> bubbleRects = new ArrayList<>();

    // Clickable text span tracking (for ClickEvent support)
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();

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

    private float getSidebarAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) return sidebarOpen ? 1f : 0f;
        // Independent sidebar toggle while chat is already open
        if (sidebarAnimating) {
            float progress = Animation.progress(sidebarAnimStart, ANIM_MS, false);
            return sidebarTargetOpen ? progress : 1.0f - progress;
        }
        if (!sidebarOpen) return 0f;
        // Sidebar open: sync to main chat panel open/close animation
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

    private static final int SIDEBAR_SEARCH_H = 14;

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(0, 0, SIDEBAR_W, height, c().sidebarBg());
        g.fill(SIDEBAR_W - 1, 0, SIDEBAR_W, height, c().sidebarDivider());

        int y = 2;
        int itemH = SIDEBAR_ITEM_H;

        // Search box background (anchor at y=2, EditBox shifted down 3px = (14-8)/2 to center text)
        int sbx = 2;
        int sby = 2;
        int sbw = SIDEBAR_W - 5;
        int sbh = SIDEBAR_SEARCH_H;
        g.fill(sbx - 1, sby, sbx + sbw, sby + sbh, c().inputBg());
        boolean hoverSearch = mouseX >= sbx - 1 && mouseX <= sbx + sbw && mouseY >= sby && mouseY <= sby + sbh;
        if (hoverSearch || sidebarSearchBox.isFocused())
            g.renderOutline(sbx - 1, sby, sbw + 1, sbh, c().textMuted());
        if (sidebarSearchBox.getValue().isEmpty() && !sidebarSearchBox.isFocused()) {
            g.drawString(font, Component.translatable("e33chat.sidebar.search"), sbx, sby + 3, c().textMuted(), false);
        }
        y = sby + sbh + 3;

        // Public
        boolean isPublic = whisperPartner == null;
        int pubBg = isPublic ? c().sidebarItemSelected()
            : (mouseX >= 0 && mouseX <= SIDEBAR_W && mouseY >= y && mouseY <= y + itemH ? c().sidebarItemHover() : 0);
        if (pubBg != 0) g.fill(0, y, SIDEBAR_W, y + itemH, pubBg);
        drawTextureIcon(g, iconTex("public_icon"), 2, y + 1, SIDEBAR_ICON_S);
        int nameX = 2 + SIDEBAR_ICON_S + 3;
        String publicLabel = Component.translatable("e33chat.sidebar.public").getString();
        g.drawString(font, Component.literal(publicLabel), nameX, y + 1, c().textPrimary(), false);
        ChatMessageStore.ChatMessage latestPub = ChatMessageStore.getLatestPublicMessage();
        if (latestPub != null) {
            int previewMaxW = SIDEBAR_W - nameX - 4;
            String preview = ChatMessageStore.singleLine(latestPub.content().getString());
            String previewDisplay = font.plainSubstrByWidth(preview, previewMaxW - font.width("..."));
            if (!previewDisplay.equals(preview)) previewDisplay += "...";
            g.drawString(font, Component.literal(previewDisplay), nameX, y + 1 + font.lineHeight, c().textMuted(), false);
        }
        y += itemH + 2;

        if (minecraft.player != null && minecraft.player.connection != null) {
            var players = new ArrayList<>(minecraft.player.connection.getOnlinePlayers());
            String selfName = minecraft.player.getName().getString();
            String filter = sidebarSearchBox.getValue().toLowerCase().trim();

            int startY = y;
            int visibleBottom = msgBottom > 0 ? msgBottom : height - BAR_H;
            int totalH = 0;
            for (var info : players) {
                String name = info.getProfile().getName();
                if (name.equals(selfName)) continue;
                if (ChatBubbleConfig.isSidebarHidden(name)) continue;
                if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                totalH += itemH + 2;
            }

            if (totalH == 0) {
                int iconS = 32;
                drawTextureIcon(g, iconTex("no_online"), (SIDEBAR_W - iconS) / 2, startY + 8, iconS);
                String noPlayers = Component.translatable("e33chat.sidebar.no_players").getString();
                int textW = font.width(noPlayers);
                g.drawString(font, Component.literal(noPlayers),
                    (SIDEBAR_W - textW) / 2, startY + 8 + iconS + 4, c().textMuted(), false);
            } else {
            sidebarMaxScroll = Math.max(0, totalH - (visibleBottom - startY));
            if (sidebarScrollOffset > sidebarMaxScroll) sidebarScrollOffset = sidebarMaxScroll;

            g.enableScissor(0, startY, SIDEBAR_W, visibleBottom); // sidebar is unscaled, no s-multiply needed
            int scrollY = startY - sidebarScrollOffset;
            for (var info : players) {
                String name = info.getProfile().getName();
                if (name.equals(selfName)) continue;
                if (ChatBubbleConfig.isSidebarHidden(name)) continue;
                if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;

                if (scrollY + itemH > startY && scrollY < visibleBottom) {
                    boolean sel = name.equals(whisperPartner);
                    int itemBg = sel ? c().sidebarItemSelected()
                        : (mouseX >= 0 && mouseX <= SIDEBAR_W && mouseY >= scrollY && mouseY <= scrollY + itemH ? c().sidebarItemHover() : 0);
                    if (itemBg != 0) g.fill(0, scrollY, SIDEBAR_W, scrollY + itemH, itemBg);

                    ResourceLocation skin = getSkin(info.getProfile().getId(), info.getProfile().getName());
                    drawPlayerHead(g, skin, 4, scrollY + 3, 16, 18);

                    int tipW = ChatMessageStore.hasUnreadWhisper(name) ? 16 : 0;
                    int maxNameW = SIDEBAR_W - nameX - 4 - tipW - 2;
                    String displayName = font.plainSubstrByWidth(name, maxNameW - font.width("..."));
                    if (!displayName.equals(name)) displayName += "...";
                    g.drawString(font, Component.literal(displayName), nameX, scrollY + 1, c().textPrimary(), false);

                    ChatMessageStore.ChatMessage latest = ChatMessageStore.getLatestWhisperWith(name);
                    if (latest != null) {
                        String preview = ChatMessageStore.singleLine(latest.content().getString());
                        String previewDisplay = font.plainSubstrByWidth(preview, maxNameW - font.width("..."));
                        if (!previewDisplay.equals(preview)) previewDisplay += "...";
                        g.drawString(font, Component.literal(previewDisplay), nameX, scrollY + 1 + font.lineHeight, c().textMuted(), false);
                    }

                    if (ChatMessageStore.hasUnreadWhisper(name)) {
                        int tipX = SIDEBAR_W - 16 - 2;
                        int tipY = scrollY + 3 + (int)(Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 3);
                        drawTextureIcon(g, iconTex("private_tip"), tipX, tipY, 16);
                    }
                }
                scrollY += itemH + 2;
            }
            g.disableScissor();
            }
        }
    }

    private void insertMention(String name) {
        String text = input.getValue();
        int atIdx = text.lastIndexOf('@');
        input.setValue(text.substring(0, atIdx) + "@" + name + " ");
        input.moveCursorToEnd();
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
            if (msg.content().getString().toLowerCase().contains(lower))
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
        return Animation.progress(animStart, ANIM_MS, closing);
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
            return true;
        }
        int sidebarX = getSidebarScreenX();
        if ((sidebarOpen || sidebarAnimating) && mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_W) {
            sidebarScrollOffset = Mth.clamp(sidebarScrollOffset - (int)(delta * 20), 0, sidebarMaxScroll);
            return true;
        }
        if (commandSuggestions != null && commandSuggestions.mouseScrolled(delta))
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

        // Sidebar clicks
        int sidebarX = getSidebarScreenX();
        if ((sidebarOpen || sidebarAnimating) && button == 0 && mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_W) {
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
            if (mouseY >= y && mouseY <= y + SIDEBAR_ITEM_H) {
                whisperPartner = null;
                sidebarSearchBox.setValue("");
                setFocused(input);
                scrollToBottom = true;
                return true;
            }
            y += SIDEBAR_ITEM_H + 2;
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
                    if (mouseY >= scrollY && mouseY <= scrollY + SIDEBAR_ITEM_H) {
                        whisperPartner = name;
                        ChatMessageStore.clearUnreadWhisper(name);
                        sidebarSearchBox.setValue("");
                        setFocused(input);
                        scrollToBottom = true;
                        return true;
                    }
                    scrollY += SIDEBAR_ITEM_H + 2;
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
                    input.setValue(input.getValue() + emojiText);
                    input.moveCursorToEnd();
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
                int avatarX = r[0] - AVATAR - 4;
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
        int menuH = CTX_ITEM_H * 2 + 2;
        int menuX = Math.min(contextX, panelX + panelW - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < msgTop) menuY = contextY + 4;

        if (mx >= menuX && mx <= menuX + CTX_W) {
            if (my >= menuY && my <= menuY + CTX_ITEM_H) {
                ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextMsgIndex);
                if (msg != null) {
                    minecraft.keyboardHandler.setClipboard(msg.content().getString());
                    copyToastTicks = 20;
                }
            } else if (my >= menuY + CTX_ITEM_H + 1 && my <= menuY + CTX_ITEM_H * 2 + 1) {
                replyTargetIndex = contextMsgIndex;
            }
        }
        contextMsgIndex = -1;
    }

    private void handleAvatarContextClick(int mx, int my) {
        int menuH = CTX_ITEM_H * 2 + 3;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;

        if (mx >= menuX && mx <= menuX + CTX_W) {
            ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
            String name = msg != null ? msg.rawPlayerName() : null;
            if (name == null || name.isEmpty()) { contextAvatarIndex = -1; return; }

            if (my >= menuY && my <= menuY + CTX_ITEM_H) {
                minecraft.player.connection.sendCommand((ChatMessageStore.useTpa() ? "tpa " : "tp ") + name);
            } else if (my >= menuY + CTX_ITEM_H + 2 && my <= menuY + CTX_ITEM_H * 2 + 2) {
                whisperPartner = name;
                ChatMessageStore.clearUnreadWhisper(name);
                if (sidebarSearchBox != null) sidebarSearchBox.setValue("");
                setFocused(input);
                scrollToBottom = true;
            }
        }
        contextAvatarIndex = -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        tickSidebarAnimation();

        float anim = getAnimProgress();
        int moveDist;
        if (sidebarOpen) {
            moveDist = closing ? panelW : SIDEBAR_W;
        } else {
            moveDist = panelW;
        }
        int panelOffset = (int) ((anim - 1.0f) * moveDist);

        // Panel contents slide in from left
        g.pose().pushPose();
        g.pose().translate(panelOffset, 0, 0);

        int panelBg = c().panelBg();
        int panelBgAlpha = (panelBg >> 24) & 0xFF;
        int fadedBg = ((int)(panelBgAlpha * anim) << 24) | (panelBg & 0x00FFFFFF);
        // When sidebar is synced to main animation, extend panel bg to
        // sidebar's right edge so there's no gap between them.
        int fillLeft = (!sidebarAnimating && sidebarOpen)
            ? (int)(anim * SIDEBAR_W) : panelX;
        g.fill(fillLeft, 0, panelX + panelW, height, fadedBg);

        renderTitleBar(g, mouseX, mouseY);
        renderMessages(g, mouseX, mouseY);
        net.minecraft.network.chat.Style hovered = getHoveredStyle(mouseX, mouseY);
        if (hovered != null && hovered.getHoverEvent() != null) {
            g.renderComponentHoverEffect(font, hovered, mouseX, mouseY);
        }
        // Everything past the message list renders 50 z-units up so vanilla/ModernUI
        // text effects (underline/strikethrough at z+0.01) can never pierce overlays
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
        // When closing, sidebar follows the chat panel's close animation
        if (sidebarOpen || sidebarAnimating) {
            g.pose().pushPose();
            int sidebarOffset = closing
                ? (int)((getAnimProgress() - 1.0f) * SIDEBAR_W)
                : getSidebarScreenX();
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
        g.pose().popPose();

    }

    private void renderTitleBar(GuiGraphics g, int mouseX, int mouseY) {
        int ty = titleY;
        g.fill(panelX, ty, panelX + panelW, ty + TITLE_H, c().titleBg());
        g.fill(panelX, ty + TITLE_H, panelX + panelW, ty + TITLE_H + 1, c().divider());

        int menuX = panelX + 3;
        int menuY = ty + (TITLE_H - ICON_S) / 2;
        boolean hoverMenu = mouseX >= menuX && mouseX <= menuX + ICON_S && mouseY >= menuY && mouseY <= menuY + ICON_S;
        if (hoverMenu) g.fill(menuX - 1, menuY - 1, menuX + ICON_S + 1, menuY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("menu"), menuX, menuY, ICON_S);

        String title = getDisplayTitle();
        int titleW = font.width(title);
        int titleX = UiLayout.centerX(panelX, panelW, titleW);
        int titleTextY = ty + (TITLE_H - font.lineHeight) / 2;
        g.drawString(font, Component.literal(title), titleX, titleTextY, c().textPrimary(), false);

        // Time
        String time = LocalTime.now().format(TIME_FMT);
        int timeW = font.width(time);
        g.drawString(font, Component.literal(time),
            panelX + panelW - PAD - 20 - timeW, ty + (TITLE_H - font.lineHeight) / 2, c().timeColor(), false);

        // Close button
        int closeX = panelX + panelW - 18;
        int closeY = ty + 6;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        int closeBg = hoverClose ? c().closeHoverBg() : c().closeBg();
        g.fill(closeX, closeY, closeX + 12, closeY + 12, closeBg);
        g.drawString(font, Component.literal("✕"), closeX + 6 - font.width("✕") / 2, closeY + 2, c().closeText(), false);
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
            scrollbarDragging, scrollbarAlpha, c().scrollbar(), effectiveMsgBottom);
    }

    private void renderTimeSeparator(GuiGraphics g, LocalTime time, int y) {
        String text = time.format(TIME_FMT);
        int tw = font.width(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        g.fill(tx - 6, y + 2, tx + tw + 6, y + TIME_SEP_H - 2, ChatBubbleTheme.alphaBlend(c().toastBg(), 0x44));
        g.drawString(font, Component.literal(text), tx, y + 3, c().timeColor(), false);
    }

    // Wrap a message honoring '\n' as real line breaks: split the styled component into
    // paragraphs on newlines (keeping each run's style), wrap each with the font, then
    // concatenate. Stray leading/trailing newlines are trimmed so they don't leave a
    // dangling blank line. The chat list thus shows a multi-line announcement on several
    // lines instead of "LF" boxes; pure chat-clear messages are dropped at ingest.
    private List<FormattedCharSequence> wrapContent(Component c, int width) {
        List<Component> paras = new ArrayList<>();
        MutableComponent[] cur = { Component.empty() };
        c.visit((style, text) -> {
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    if (i > start) cur[0].append(Component.literal(text.substring(start, i)).withStyle(style));
                    paras.add(cur[0]);
                    cur[0] = Component.empty();
                    start = i + 1;
                }
            }
            if (start < text.length()) cur[0].append(Component.literal(text.substring(start)).withStyle(style));
            return Optional.<Object>empty();
        }, Style.EMPTY);
        paras.add(cur[0]);
        while (!paras.isEmpty() && paras.get(0).getString().isEmpty()) paras.remove(0);
        while (!paras.isEmpty() && paras.get(paras.size() - 1).getString().isEmpty()) paras.remove(paras.size() - 1);
        List<FormattedCharSequence> out = new ArrayList<>();
        for (Component p : paras) out.addAll(font.split(p, width));
        if (out.isEmpty()) out.addAll(font.split(c, width));
        return out;
    }

    private int getMsgHeight(ChatMessageStore.ChatMessage msg) {
        if (msg.isSystem()) {
            List<FormattedCharSequence> lines = wrapContent(msg.content(), panelW - PAD * 2 - 20);
            return lines.size() * font.lineHeight + 4;
        }
        int bubbleMaxW = panelW - AVATAR - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
        List<FormattedCharSequence> lines = wrapContent(msg.content(), bubbleMaxW);
        int h = lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2 + NAME_H;
        if (msg.replyContent() != null) h += font.lineHeight + 7;
        return h;
    }


    private void renderBubble(GuiGraphics g, ChatMessageStore.ChatMessage msg,
                               int index, int baseY, int mouseX, int mouseY) {
        if (msg.isSystem()) {
            List<FormattedCharSequence> lines = wrapContent(msg.content(), panelW - PAD * 2 - 20);
            int yy = baseY + 2;
            net.minecraft.network.chat.Style fb = findClickStyle(msg.content());
            int sysColor = c().textMuted();
            for (var line : lines) {
                int lw = font.width(line);
                renderLineWithClicks(g, line, panelX + (panelW - lw) / 2, yy, sysColor, fb);
                yy += font.lineHeight;
            }
            return;
        }

        boolean own = msg.isOwn();
        int bubbleMaxW = panelW - AVATAR - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
        List<FormattedCharSequence> lines = wrapContent(msg.content(), bubbleMaxW);

        int textW = 0;
        for (var line : lines) textW = Math.max(textW, font.width(line));
        int bubbleW = textW + BUBBLE_PAD_X * 2;
        int bubbleH = lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2;

        int avatarX, bubbleX;
        if (own) {
            avatarX = panelX + panelW - PAD - AVATAR;
            bubbleX = avatarX - 4 - bubbleW;
        } else {
            avatarX = panelX + PAD;
            bubbleX = avatarX + AVATAR + 4;
        }

        int nameY = baseY;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - AVATAR - PAD * 2 - 20;
            Component sn = msg.senderName();
            net.minecraft.util.FormattedCharSequence nameSeq;
            if (font.width(sn) > maxNameW) {
                var cut = font.substrByWidth(sn, maxNameW - font.width("..."));
                nameSeq = net.minecraft.locale.Language.getInstance().getVisualOrder(
                    net.minecraft.network.chat.FormattedText.composite(cut, net.minecraft.network.chat.FormattedText.of("...")));
            } else {
                nameSeq = sn.getVisualOrderText();
            }
            int nameW = font.width(nameSeq);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            g.drawString(font, nameSeq, startX, nameY, c().nameColor(), false);
        }

        int bubbleY = baseY + NAME_H;
        int avatarY = baseY;

        int bg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_BUBBLE_COLOR.get(), 0xFF1E90FF)
            : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_BUBBLE_COLOR.get(), c().contextHover());
        int fg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR.get(), 0xFFFFFFFF)
            : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR.get(), c().textPrimary());

        RoundRectRenderer.fill(g, bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH,
            ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get(), bg);

        net.minecraft.network.chat.Style fbP = findClickStyle(msg.content());
        for (int li = 0; li < lines.size(); li++)
            renderLineWithClicks(g, lines.get(li), bubbleX + BUBBLE_PAD_X,
                bubbleY + BUBBLE_PAD_Y + li * font.lineHeight, fg, fbP);

        String skinName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
            ? msg.rawPlayerName() : msg.senderName().getString();
        ResourceLocation skin = getSkin(msg.senderUUID(), skinName);
        drawPlayerHead(g, skin, avatarX, avatarY, 20, 22);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = font.width(label);
            int labelX, labelY = bubbleY + (bubbleH - font.lineHeight) / 2;
            if (own) {
                labelX = bubbleX - labelW - 3;
            } else {
                labelX = bubbleX + bubbleW + 3;
            }
            g.drawString(font, Component.literal(label), labelX, labelY, c().duplicateLabel(), false);
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - PAD * 2 - AVATAR - 24;
            String quoteText = "↳ " + msg.replySender() + ": " + msg.replyContent();
            String quoteDisplay = font.plainSubstrByWidth(quoteText, quoteMaxW - 10);
            if (!quoteDisplay.equals(quoteText)) quoteDisplay += "...";
            int quoteTextW = font.width(quoteDisplay);
            int quoteW = Math.min(quoteTextW + 8, quoteMaxW);
            int quoteH = font.lineHeight + 4;
            int quoteY = bubbleY + bubbleH + 3;
            int quoteX;
            if (own) {
                quoteX = bubbleX + bubbleW - quoteW;
            } else {
                quoteX = bubbleX;
            }
            if (quoteX < panelX + PAD) quoteX = panelX + PAD;
            if (quoteX + quoteW > panelX + panelW - PAD) quoteW = panelX + panelW - PAD - quoteX;
            RoundRectRenderer.fill(g, quoteX, quoteY, quoteX + quoteW, quoteY + quoteH, 3, c().contextHover());
            g.drawString(font, Component.literal(quoteDisplay), quoteX + 4, quoteY + 2, c().textSecondary(), false);
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});

        if (index == searchHighlightIndex)
            g.renderOutline(bubbleX - 1, bubbleY - 1, bubbleW + 2, bubbleH + 2, ChatSearchPanel.HIGHLIGHT);
    }

    private void renderLineWithClicks(GuiGraphics g, FormattedCharSequence line,
                                       int x, int y, int color) {
        renderLineWithClicks(g, line, x, y, color, null);
    }

    private void renderLineWithClicks(GuiGraphics g, FormattedCharSequence line,
                                       int x, int y, int color,
                                       net.minecraft.network.chat.Style fallback) {
        // Let the font renderer draw underlines itself: re-measuring glyphs per char
        // drifts under font mods with sub-pixel advances (ModernUI), producing
        // offset/oversized self-painted lines. Overlay piercing by the z+0.01 text
        // effect layer is handled by the overlay z-lift in render() instead
        FormattedCharSequence decorated = sink -> line.accept((i, st, cp) ->
            sink.accept(i, st.getClickEvent() != null && !st.isUnderlined() ? st.withUnderlined(true) : st, cp));
        g.drawString(font, decorated, x, y, color, false);

        final java.util.List<net.minecraft.network.chat.Style> styles = new java.util.ArrayList<>();
        line.accept((i, st, cp) -> { styles.add(st); return true; });

        final int beforeCount = clickableSpans.size();
        int runStart = -1;
        net.minecraft.network.chat.Style runStyle = null;
        for (int idx = 0; idx <= styles.size(); idx++) {
            net.minecraft.network.chat.Style st = idx < styles.size() ? styles.get(idx) : null;
            // Track segments with a click OR hover event so hover tooltips (e.g.
            // advancement descriptions) work, not just clickable links
            boolean clickable = st != null && (st.getClickEvent() != null || st.getHoverEvent() != null);
            if (runStyle == null) {
                if (clickable) { runStart = idx; runStyle = st; }
            } else if (!clickable || !st.equals(runStyle)) {
                int x0 = prefixWidth(line, runStart);
                int x1 = prefixWidth(line, idx);
                clickableSpans.add(new ClickableSpan(x + x0, y, x1 - x0, font.lineHeight, runStyle));
                runStart = clickable ? idx : -1;
                runStyle = clickable ? st : null;
            }
        }
        if (clickableSpans.size() == beforeCount && fallback != null && fallback.getClickEvent() != null) {
            clickableSpans.add(new ClickableSpan(x, y, font.width(line), font.lineHeight, fallback.withUnderlined(true)));
        }
    }

    // Width of the first count codepoints, measured through the renderer's own
    // metrics so positions match what it actually draws (per-char accumulation
    // does not — see renderLineWithClicks)
    private int prefixWidth(FormattedCharSequence line, int count) {
        if (count <= 0) return 0;
        return font.width((FormattedCharSequence) sink -> {
            int[] left = {count};
            line.accept((i, st, cp) -> left[0]-- > 0 && sink.accept(i, st, cp));
            return true;
        });
    }

    private net.minecraft.network.chat.Style findClickStyle(net.minecraft.network.chat.Component c) {
        net.minecraft.network.chat.Style s = c.getStyle();
        if (s != null && s.getClickEvent() != null) return s;
        for (net.minecraft.network.chat.Component child : c.getSiblings()) {
            s = findClickStyle(child);
            if (s != null) return s;
        }
        return null;
    }

    private net.minecraft.network.chat.Style getHoveredStyle(double mouseX, double mouseY) {
        for (ClickableSpan s : clickableSpans) {
            if (mouseX >= s.x && mouseX <= s.x + s.w
                && mouseY >= s.y && mouseY <= s.y + s.h)
                return s.style;
        }
        return null;
    }

    private void renderNotificationBar(GuiGraphics g, int mouseX, int mouseY) {
        if (newMessageCount <= 0) return;
        int notifY = barTop - NOTIF_H;
        g.fill(panelX, notifY - 1, panelX + panelW, notifY, c().divider());
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
        int menuH = CTX_ITEM_H * 2 + 2;
        int menuX = Math.min(contextX, panelX + panelW - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < msgTop) menuY = contextY + 4;

        g.fill(menuX, menuY, menuX + CTX_W, menuY + menuH, c().contextBg());
        g.fill(menuX, menuY, menuX + CTX_W, menuY + 1, c().divider());
        g.fill(menuX, menuY + menuH - 1, menuX + CTX_W, menuY + menuH, c().divider());
        g.fill(menuX, menuY, menuX + 1, menuY + menuH, c().divider());
        g.fill(menuX + CTX_W - 1, menuY, menuX + CTX_W, menuY + menuH, c().divider());

        boolean hoverCopy = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        int copyBg = hoverCopy ? c().contextHover() : c().sidebarItemSelected();
        g.fill(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, copyBg);
        drawTextureIcon(g, iconTex("copy"), menuX + 5, menuY + 3, 12);
        g.drawString(font, Component.translatable("e33chat.context.copy"), menuX + 22, menuY + 4, c().textPrimary(), false);

        g.fill(menuX + 4, menuY + CTX_ITEM_H, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 1, c().closeHoverBg());

        boolean hoverQuote = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 1 && mouseY <= menuY + menuH;
        int quoteBg = hoverQuote ? c().contextHover() : c().sidebarItemSelected();
        g.fill(menuX + 1, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 1, menuY + menuH - 1, quoteBg);
        drawTextureIcon(g, iconTex("quote"), menuX + 5, menuY + CTX_ITEM_H + 3, 12);
        g.drawString(font, Component.translatable("e33chat.context.quote"), menuX + 22, menuY + CTX_ITEM_H + 5, c().textPrimary(), false);
    }

    private void renderAvatarContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        if (contextAvatarIndex < 0) return;
        int menuH = CTX_ITEM_H * 2 + 3;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;

        g.fill(menuX, menuY, menuX + CTX_W, menuY + menuH, c().contextBg());
        g.fill(menuX, menuY, menuX + CTX_W, menuY + 1, c().divider());
        g.fill(menuX, menuY + menuH - 1, menuX + CTX_W, menuY + menuH, c().divider());
        g.fill(menuX, menuY, menuX + 1, menuY + menuH, c().divider());
        g.fill(menuX + CTX_W - 1, menuY, menuX + CTX_W, menuY + menuH, c().divider());

        boolean hoverTp = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        int tpBg = hoverTp ? c().contextHover() : c().sidebarItemSelected();
        g.fill(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, tpBg);
        drawTextureIcon(g, iconTex("tp"), menuX + 5, menuY + 3, 12);
        g.drawString(font, Component.translatable(ChatMessageStore.useTpa() ? "e33chat.context.tpa" : "e33chat.context.tp"), menuX + 22, menuY + 4, c().textPrimary(), false);

        g.fill(menuX + 4, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 2, c().closeHoverBg());

        boolean hoverWhisper = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 2 && mouseY <= menuY + menuH;
        int whBg = hoverWhisper ? c().contextHover() : c().sidebarItemSelected();
        g.fill(menuX + 1, menuY + CTX_ITEM_H + 2, menuX + CTX_W - 1, menuY + menuH - 1, whBg);
        drawTextureIcon(g, iconTex("whisper"), menuX + 5, menuY + CTX_ITEM_H + 4, 12);
        g.drawString(font, Component.translatable("e33chat.context.whisper"), menuX + 22, menuY + CTX_ITEM_H + 6, c().textPrimary(), false);
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

        g.fill(barX, barY, barX + barW, barTop - notifOffset, c().panelBg());
        g.fill(barX, barTop - notifOffset - 1, barX + barW, barTop - notifOffset, c().divider());

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
        g.fill(panelX, barTop, panelX + panelW, height, c().barBg());
        g.fill(panelX, barTop, panelX + panelW, barTop + 1, c().divider());

        int iconY = barTop + (BAR_H - ICON_S) / 2;

        // Input background (anchor at layout position, EditBox shifted down 3px = (14-8)/2 to center text)
        int ibX = inputX;
        int ibY = inputY;
        int ibW = input.getWidth();
        int ibH = INPUT_H;
        g.fill(ibX - 1, ibY - 1, ibX + ibW, ibY, c().divider());
        g.fill(ibX - 1, ibY, ibX + ibW, ibY + ibH, c().inputBg());

        boolean hoverInput = mouseX >= ibX - 1 && mouseX <= ibX + ibW && mouseY >= ibY && mouseY <= ibY + ibH;
        if (hoverInput || input.isFocused())
            g.renderOutline(ibX - 1, ibY, ibW + 1, ibH, c().textMuted());

        // Icons
        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;

        // Gear icon (left)
        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverGear) g.fill(gearX - 1, iconY - 1, gearX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("settings"), gearX, iconY, ICON_S);

        // Emoji icon (between input and send)
        boolean hoverEmoji = mouseX >= emojiX && mouseX <= emojiX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverEmoji || emojiPanel.visible) g.fill(emojiX - 1, iconY - 1, emojiX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("emoji"), emojiX, iconY, ICON_S);

        // Send icon (right)
        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverSend) g.fill(sendX - 1, iconY - 1, sendX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("send"), sendX, iconY, ICON_S);
    }

    private static void loadIconTextures() {
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
                String k = m.time().format(TIME_FMT);
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
        minecraft.gui.getChat().addRecentChat(text);

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

    private static class ClickableSpan {
        final int x, y, w, h;
        final net.minecraft.network.chat.Style style;
        ClickableSpan(int x, int y, int w, int h, net.minecraft.network.chat.Style style) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.style = style;
        }
    }
}
