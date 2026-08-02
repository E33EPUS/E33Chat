package com.niuqu.chatbubble;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.network.QuoteSyncPayload;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.texture.AbstractTexture;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.text.*;
import net.minecraft.util.Formatting;
import net.minecraft.util.Identifier;
import net.minecraft.util.Language;
import net.minecraft.util.Util;
import net.minecraft.util.math.MathHelper;

import java.io.InputStream;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.IdentityHashMap;
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
        return theme().colors();
    }

    private ChatBubbleTheme theme() {
        return "light".equalsIgnoreCase(ChatBubbleClientSetup.config().theme())
            ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
    }

    private static final int INPUT_H = 14;
    private static final int ICON_S = 14;
    private ChatBubbleTheme loadedTheme;

    static Identifier iconTex(String name) {
        String theme = ChatBubbleClientSetup.config().theme().toLowerCase();
        return Identifier.of("e33chat", "textures/gui/" + theme + "/" + name);
    }

    private void ensureIconsLoaded() {
        var t = theme();
        if (loadedTheme == t) return;
        loadIconTextures();
        loadedTheme = t;
    }

    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static String timeKey(long t) {
        return ChatMessageStore.timeKey(t, ChatBubbleClientSetup.config().timeSeparatorMinutes());
    }

    private TextFieldWidget input;
    private ChatInputSuggestor commandSuggestions;
    private static int inputX, inputY;

    public static int getInputX() { return inputX; }
    public static int getInputY() { return inputY; }
    private final String initialText;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollToBottom = true;
    private boolean firstRender = true;
    private static String savedInput = "";
    private static final java.util.Map<UUID, Identifier> skinCache = new java.util.HashMap<>();
    private String historyBuffer = "";
    private int historyPos = -1;

    final ChatEmojiPanel emojiPanel = new ChatEmojiPanel();
    final ChatSettingsMenu settingsMenu = new ChatSettingsMenu();
    final ChatSearchPanel searchPanel = new ChatSearchPanel();
    private TextFieldWidget searchInput;
    private final List<Integer> searchMatches = new ArrayList<>();
    private int searchMatchIdx;
    private int searchHighlightIndex = -1;
    final ChatQuickChatPanel quickChatPanel = new ChatQuickChatPanel();
    private TextFieldWidget quickChatInput;
    private static final int QUICK_CHAT_W = 140;
    private static boolean sidebarOpen;
    private String whisperPartner;
    private int sidebarScrollOffset;
    private int sidebarMaxScroll;
    private TextFieldWidget sidebarSearchBox;

    private long sidebarAnimStart;
    private boolean sidebarTargetOpen;
    private boolean sidebarAnimating;

    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_THUMB_H = 8;
    private boolean scrollbarDragging;
    private int scrollbarDragStartY;
    private int scrollbarDragStartOffset;
    private int messageTotalH;
    private boolean scrollbarHovered;
    private float scrollbarAlpha;
    private static final int SCROLLBAR_HOVER_ZONE = 20;
    private boolean scrollAnimActive;
    private long scrollAnimStart;
    private float scrollAnimFrom;
    private float scrollAnimTo;
    private int scrollAnimDuration;
    private long lastScrollTime;

    private boolean showMentions;
    private final List<String> mentionCandidates = new ArrayList<>();
    private int mentionIdx;
    private String mentionFilter = "";

    private int contextMsgIndex = -1;
    private int contextX, contextY;
    private static final int CTX_W = 80;
    private static final int CTX_ITEM_H = 18;
    private int contextAvatarIndex = -1;
    private int contextAvatarX, contextAvatarY;

    // Per-frame wrap cache: every message is measured once (layout pass) and
    // rendered once (bubble pass), and both call getMsgHeight -> wrapContent.
    // Without the cache each message gets re-wrapped 3x per frame.
    private final Map<ChatMessageStore.ChatMessage, Integer> msgHeightCache =
        new IdentityHashMap<>();

    private final List<int[]> bubbleRects = new ArrayList<>();
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();

    private int replyTargetIndex = -1;
    private int copyToastTicks;

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
        super(Text.translatable("e33chat.screen.title"));
        this.initialText = initialText;
    }

    @Override
    protected void init() {
        historyPos = client.inGameHud.getChatHud().getMessageHistory().size();
        ChatMessageStore.setScreenOpen(true);
        animStart = Util.getMeasuringTimeMs();
        closing = false;
        firstRender = true;

        int physicalW = ChatBubbleClientSetup.config().panelWidth();
        int guiScale = (int) Math.round(client.getWindow().getScaleFactor());
        panelW = Math.max(100, Math.min(physicalW / Math.max(1, guiScale), width));
        if (sidebarOpen) {
            panelX = SIDEBAR_W;
            sidebarTargetOpen = true;
            sidebarAnimating = true;
            sidebarAnimStart = Util.getMeasuringTimeMs();
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

        int ibY = barTop + (BAR_H - INPUT_H) / 2;
        inputY = ibY;
        inputX = panelX + 4 + ICON_S + 3;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int inputW = sendX - ICON_S - 8 - inputX;

        input = new TextFieldWidget(textRenderer, inputX, ibY + 3, inputW, INPUT_H, Text.literal(""));
        input.setMaxLength(256);
        input.setDrawsBackground(false);
        int editColor = theme() == ChatBubbleTheme.LIGHT ? c().textSecondary() : c().textPrimary();
        input.setEditableColor(editColor);
        input.setUneditableColor(c().textMuted());
        input.setText(initialText.isEmpty() && ChatBubbleClientSetup.config().preserveInput() && !savedInput.isEmpty() ? savedInput : initialText);
        input.setChangedListener(this::onEdited);
        input.setFocusUnlocked(false);
        addDrawableChild(input);

        int cmdBgAlpha = theme() == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
        commandSuggestions = new ChatInputSuggestor(client, this, input, textRenderer,
            false, false, 0, 8, true, ChatBubbleTheme.alphaBlend(c().panelBg(), cmdBgAlpha));
        commandSuggestions.setWindowActive(true);
        commandSuggestions.refresh();

        ensureIconsLoaded();

        sidebarSearchBox = new TextFieldWidget(textRenderer, 2, 5, SIDEBAR_W - 5, SIDEBAR_SEARCH_H, Text.literal(""));
        sidebarSearchBox.setMaxLength(20);
        sidebarSearchBox.setDrawsBackground(false);
        sidebarSearchBox.setEditableColor(editColor);
        sidebarSearchBox.setUneditableColor(editColor);
        sidebarSearchBox.setVisible(sidebarOpen);
        sidebarSearchBox.setChangedListener(s -> sidebarScrollOffset = 0);
        sidebarSearchBox.setFocusUnlocked(true);
        if (sidebarOpen) sidebarSearchBox.setX(2 - SIDEBAR_W);
        addDrawableChild(sidebarSearchBox);

        quickChatInput = new TextFieldWidget(textRenderer, 0, 0, QUICK_CHAT_W - 8, 12, Text.translatable("e33chat.menu.quick_chat"));
        quickChatInput.setMaxLength(256);
        quickChatInput.setDrawsBackground(false);
        quickChatInput.setEditableColor(editColor);
        quickChatInput.setUneditableColor(c().textMuted());
        quickChatInput.setVisible(false);
        quickChatInput.setFocusUnlocked(true);
        addDrawableChild(quickChatInput);

        searchInput = new TextFieldWidget(textRenderer, 0, 0, 160, 12, Text.translatable("e33chat.menu.search"));
        searchInput.setMaxLength(128);
        searchInput.setDrawsBackground(false);
        searchInput.setEditableColor(editColor);
        searchInput.setUneditableColor(c().textMuted());
        searchInput.setVisible(false);
        searchInput.setChangedListener(this::onSearchEdited);
        searchInput.setFocusUnlocked(true);
        addDrawableChild(searchInput);

        setFocused(input);
    }

    private void rebuildLayout() {
        int physicalW = ChatBubbleClientSetup.config().panelWidth();
        int guiScale = (int) Math.round(client.getWindow().getScaleFactor());
        panelW = Math.max(100, Math.min(physicalW / Math.max(1, guiScale), width));
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
        return Text.translatable("e33chat.sidebar.public").getString();
    }

    private float getSidebarAnimProgress() {
        if (!ChatBubbleClientSetup.config().animationEnabled()) return sidebarOpen ? 1f : 0f;
        // Independent sidebar toggle while chat is already open
        if (sidebarAnimating) {
            float progress = Animation.progress(sidebarAnimStart, ANIM_MS, false);
            return sidebarTargetOpen ? progress : 1.0f - progress;
        }
        return sidebarOpen ? 1f : 0f;
    }

    private int getSidebarScreenX() {
        return (int) ((getSidebarAnimProgress() - 1.0f) * SIDEBAR_W);
    }

    private void tickSidebarAnimation() {
        if (!sidebarAnimating) return;
        long elapsed = Util.getMeasuringTimeMs() - sidebarAnimStart;
        float t = MathHelper.clamp((float) elapsed / ANIM_MS, 0f, 1f);
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
        panelX = (int) (SIDEBAR_W * progress);
        sidebarSearchBox.setX(2 + getSidebarScreenX());
        sidebarSearchBox.setVisible(progress > 0.01f);
        rebuildLayout();
    }

    private static final int SIDEBAR_SEARCH_H = 14;

    private void renderSidebar(DrawContext g, int mouseX, int mouseY) {
        g.drawTexture(UiTextureManager.rl(UiElement.SIDEBAR_BG), 0, 0, 0f, 0f, SIDEBAR_W, height, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), SIDEBAR_W - 1, 0, 0f, 0f, 1, height, 1, 1);

        int y = 2;
        int itemH = SIDEBAR_ITEM_H;

        int sbx = 2;
        int sby = 2;
        int sbw = SIDEBAR_W - 5;
        int sbh = SIDEBAR_SEARCH_H;
        g.drawTexture(UiTextureManager.rl(UiElement.INPUT_BG), sbx - 1, sby, 0f, 0f, sbw + 1, sbh, 1, 1);
        boolean hoverSearch = mouseX >= sbx - 1 && mouseX <= sbx + sbw && mouseY >= sby && mouseY <= sby + sbh;
        if (hoverSearch || sidebarSearchBox.isFocused())
            g.drawBorder(sbx - 1, sby, sbw + 1, sbh, c().textMuted());
        if (sidebarSearchBox.getText().isEmpty() && !sidebarSearchBox.isFocused()) {
            g.drawText(textRenderer, Text.translatable("e33chat.sidebar.search").getString(), sbx, sby + 3, c().textMuted(), false);
        }
        y = sby + sbh + 3;

        boolean isPublic = whisperPartner == null;
        int pubBg = isPublic ? c().sidebarItemSelected()
            : (mouseX >= 0 && mouseX <= SIDEBAR_W && mouseY >= y && mouseY <= y + itemH ? c().sidebarItemHover() : 0);
        if (pubBg != 0) g.fill(0, y, SIDEBAR_W, y + itemH, pubBg);
        drawTextureIcon(g, iconTex("public_icon"), 2, y + 1, SIDEBAR_ICON_S);
        int nameX = 2 + SIDEBAR_ICON_S + 3;
        String publicLabel = Text.translatable("e33chat.sidebar.public").getString();
        g.drawText(textRenderer, publicLabel, nameX, y + 1, c().textPrimary(), false);
        ChatMessageStore.ChatMessage latestPub = ChatMessageStore.getLatestPublicMessage();
        if (latestPub != null) {
            int previewMaxW = SIDEBAR_W - nameX - 4;
            String preview = ChatMessageStore.singleLine(latestPub.content().getString());
            String previewDisplay = textRenderer.trimToWidth(preview, previewMaxW - textRenderer.getWidth("..."));
            if (!previewDisplay.equals(preview)) previewDisplay += "...";
            g.drawText(textRenderer, previewDisplay, nameX, y + 1 + textRenderer.fontHeight, c().textMuted(), false);
        }
        y += itemH + 2;

        if (client.player != null && client.player.networkHandler != null) {
            var players = new ArrayList<>(client.player.networkHandler.getPlayerList());
            String selfName = client.player.getName().getString();
            String filter = sidebarSearchBox.getText().toLowerCase().trim();

            int startY = y;
            int visibleBottom = msgBottom > 0 ? msgBottom : height - BAR_H;
            int totalH = 0;
            for (var info : players) {
                String name = info.getProfile().getName();
                if (name.equals(selfName)) continue;
                if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                if (ChatBubbleClientSetup.config().isSidebarHidden(name)) continue;
                totalH += itemH + 2;
            }

            if (totalH == 0) {
                int iconS = 32;
                drawTextureIcon(g, iconTex("no_online"), (SIDEBAR_W - iconS) / 2, startY + 8, iconS);
                String noPlayers = Text.translatable("e33chat.sidebar.no_players").getString();
                int textW = textRenderer.getWidth(noPlayers);
                g.drawText(textRenderer, noPlayers,
                    (SIDEBAR_W - textW) / 2, startY + 8 + iconS + 4, c().textMuted(), false);
            } else {
                int maxSideScroll = Math.max(0, totalH - (visibleBottom - startY));
                sidebarMaxScroll = maxSideScroll;
                if (sidebarScrollOffset > maxSideScroll) sidebarScrollOffset = maxSideScroll;

                g.enableScissor(0, startY, SIDEBAR_W, visibleBottom);
                int scrollY = startY - sidebarScrollOffset;
                for (var info : players) {
                    String name = info.getProfile().getName();
                    if (name.equals(selfName)) continue;
                    if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                    if (ChatBubbleClientSetup.config().isSidebarHidden(name)) continue;

                    if (scrollY + itemH > startY && scrollY < visibleBottom) {
                        boolean sel = name.equals(whisperPartner);
                        int itemBg = sel ? c().sidebarItemSelected()
                            : (mouseX >= 0 && mouseX <= SIDEBAR_W && mouseY >= scrollY && mouseY <= scrollY + itemH ? c().sidebarItemHover() : 0);
                        if (itemBg != 0) g.fill(0, scrollY, SIDEBAR_W, scrollY + itemH, itemBg);

                        Identifier skin = getSkin(info.getProfile().getId(), info.getProfile().getName());
                        drawPlayerHead(g, skin, 4, scrollY + 3, 16, 18);

                        int tipW = ChatMessageStore.hasUnreadWhisper(name) ? 16 : 0;
                        int maxNameW = SIDEBAR_W - nameX - 4 - tipW - 2;
                        String displayName = textRenderer.trimToWidth(name, maxNameW - textRenderer.getWidth("..."));
                        if (!displayName.equals(name)) displayName += "...";
                        g.drawText(textRenderer, displayName, nameX, scrollY + 1, c().textPrimary(), false);

                        ChatMessageStore.ChatMessage latest = ChatMessageStore.getLatestWhisperWith(name);
                        if (latest != null) {
                            String preview = ChatMessageStore.singleLine(latest.content().getString());
                            String previewDisplay = textRenderer.trimToWidth(preview, maxNameW - textRenderer.getWidth("..."));
                            if (!previewDisplay.equals(preview)) previewDisplay += "...";
                            g.drawText(textRenderer, previewDisplay, nameX, scrollY + 1 + textRenderer.fontHeight, c().textMuted(), false);
                        }

                        if (ChatMessageStore.hasUnreadWhisper(name)) {
                            int tipX = SIDEBAR_W - 16 - 2;
                            int tipY = scrollY + 3 + (int) (Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 3);
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
        String text = input.getText();
        int atIdx = text.lastIndexOf('@');
        input.setText(text.substring(0, atIdx) + "@" + name + " ");
        input.setCursorToEnd(false);
        showMentions = false;
    }

    private void onEdited(String text) {
        showMentions = false;
        int atIdx = text.lastIndexOf('@');
        if (atIdx >= 0 && client.player != null && client.player.networkHandler != null) {
            String after = text.substring(atIdx + 1);
            if (!after.contains(" ")) {
                mentionFilter = after.toLowerCase();
                mentionCandidates.clear();
                for (var info : client.player.networkHandler.getPlayerList()) {
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
            commandSuggestions.refresh();
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
                || (msg.senderName() != null && msg.senderName().getString().toLowerCase().contains(lower)))
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
        if (closing && Util.getMeasuringTimeMs() - animStart >= ANIM_MS)
            client.setScreen(null);
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
        // no-op: disable vanilla blur
    }

    private float getAnimProgress() {
        if (!ChatBubbleClientSetup.config().animationEnabled()) return 1.0f;
        return Animation.progress(animStart, ANIM_MS, closing);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (settingsMenu.visible && keyCode == 256) { settingsMenu.visible = false; return true; }
        if (emojiPanel.visible && keyCode == 256) { emojiPanel.visible = false; return true; }
        if (quickChatPanel.visible && keyCode == 256) {
            quickChatPanel.visible = false; quickChatInput.setVisible(false); setFocused(input); return true;
        }
        if (searchPanel.visible && keyCode == 256) { closeSearchPanel(); return true; }

        if (searchPanel.visible && !searchMatches.isEmpty()) {
            if (keyCode == 265) {
                searchMatchIdx = searchMatchIdx > 0 ? searchMatchIdx - 1 : searchMatches.size() - 1;
                searchHighlightIndex = searchMatches.get(searchMatchIdx);
                jumpToMessage(searchHighlightIndex); return true;
            }
            if (keyCode == 264) {
                searchMatchIdx = searchMatchIdx < searchMatches.size() - 1 ? searchMatchIdx + 1 : 0;
                searchHighlightIndex = searchMatches.get(searchMatchIdx);
                jumpToMessage(searchHighlightIndex); return true;
            }
            if (keyCode == 257 || keyCode == 335) { closeSearchPanel(); return true; }
        }

        if (sidebarSearchBox.isFocused()) {
            if (keyCode == 256 || keyCode == 257 || keyCode == 335) {
                sidebarSearchBox.setFocused(false); setFocused(input); return true;
            }
        }

        if (showMentions) {
            if (keyCode == 258) { insertMention(mentionCandidates.get(mentionIdx)); return true; }
            if (keyCode == 256) { showMentions = false; return true; }
            if (keyCode == 265) { mentionIdx = mentionIdx > 0 ? mentionIdx - 1 : mentionCandidates.size() - 1; return true; }
            if (keyCode == 264) { mentionIdx = mentionIdx < mentionCandidates.size() - 1 ? mentionIdx + 1 : 0; return true; }
            if (keyCode == 257 || keyCode == 335) { insertMention(mentionCandidates.get(mentionIdx)); return true; }
        }

        if (commandSuggestions != null && commandSuggestions.keyPressed(keyCode, scanCode, modifiers))
            return true;
        if (keyCode == 256) { onClose(); return true; }
        if (quickChatInput.isFocused() && (keyCode == 257 || keyCode == 335)) {
            String text = quickChatInput.getText().trim();
            if (!text.isEmpty()) {
                var phrases = new ArrayList<>(ChatBubbleClientSetup.config().quickChatPhrases());
                phrases.add(text);
                ChatBubbleClientSetup.saveConfig(ChatBubbleClientSetup.config().withQuickChatPhrases(phrases));
                quickChatInput.setText("");
            }
            return true;
        }
        if (keyCode == 257 || keyCode == 335) {
            sendMessage(); return true;
        }
        if (keyCode == 265) { moveInHistory(-1); return true; }
        if (keyCode == 264) { moveInHistory(1); return true; }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (emojiPanel.visible) { emojiPanel.handleScroll(scrollY); return true; }
        if (quickChatPanel.visible) { quickChatPanel.handleScroll(scrollY); return true; }
        if (searchPanel.visible && !searchMatches.isEmpty()) {
            searchMatchIdx = MathHelper.clamp(searchMatchIdx - (int) scrollY, 0, searchMatches.size() - 1);
            searchHighlightIndex = searchMatches.get(searchMatchIdx);
            jumpToMessage(searchHighlightIndex); return true;
        }
        if (showMentions && !mentionCandidates.isEmpty()) {
            mentionIdx = MathHelper.clamp(mentionIdx - (int) scrollY, 0, mentionCandidates.size() - 1);
            return true;
        }
        int sidebarX = getSidebarScreenX();
        if ((sidebarOpen || sidebarAnimating) && mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_W) {
            sidebarScrollOffset = MathHelper.clamp(sidebarScrollOffset - (int) (scrollY * 20), 0, sidebarMaxScroll);
            return true;
        }
        if (commandSuggestions != null && commandSuggestions.mouseScrolled(scrollY)) return true;
        scrollToBottom = false;
        lastScrollTime = Util.getMeasuringTimeMs();
        float newTarget = MathHelper.clamp(scrollOffset - (int) (scrollY * 40), 0, maxScroll);
        scrollAnimFrom = scrollOffset;
        scrollAnimTo = newTarget;
        scrollAnimStart = Util.getMeasuringTimeMs();
        if (!scrollAnimActive) { scrollAnimDuration = 120; scrollAnimActive = true; }
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
            int popupH = Math.min(mentionCandidates.size(), 8) * textRenderer.fontHeight + 4;
            int popupY = input.getY() - popupH - 2;
            if (popupY < msgTop) popupY = input.getY() + input.getHeight() + 2;
            int maxW = 60;
            for (String name : mentionCandidates) maxW = Math.max(maxW, textRenderer.getWidth(name));
            int popupW = maxW + 12;
            if (mouseX >= popupX && mouseX <= popupX + popupW && mouseY >= popupY && mouseY <= popupY + popupH) {
                int relY = (int) mouseY - popupY - 2;
                int idx = relY / textRenderer.fontHeight;
                int startIdx = Math.max(0, mentionIdx - Math.min(mentionCandidates.size(), 8) + 1);
                idx += startIdx;
                if (idx >= 0 && idx < mentionCandidates.size()) {
                    insertMention(mentionCandidates.get(idx)); return true;
                }
            }
        }

        // Sidebar clicks
        int sidebarX = getSidebarScreenX();
        if ((sidebarOpen || sidebarAnimating) && button == 0 && origX >= sidebarX && origX <= sidebarX + SIDEBAR_W) {
            int searchY = 2;
            int searchH = SIDEBAR_SEARCH_H;
            if (mouseY >= searchY && mouseY <= searchY + searchH) {
                setFocused(sidebarSearchBox); input.setFocused(false); return true;
            }
            if (sidebarSearchBox.isFocused()) setFocused(input);

            int y2 = searchY + searchH + 3;
            if (mouseY >= y2 && mouseY <= y2 + SIDEBAR_ITEM_H) {
                whisperPartner = null; sidebarSearchBox.setText(""); setFocused(input); scrollToBottom = true; return true;
            }
            y2 += SIDEBAR_ITEM_H + 2;
            if (client.player != null && client.player.networkHandler != null) {
                var players = new ArrayList<>(client.player.networkHandler.getPlayerList());
                String selfName = client.player.getName().getString();
                String filter = sidebarSearchBox.getText().toLowerCase().trim();
                int scrollY = y2 - sidebarScrollOffset;
                for (var info : players) {
                    String name = info.getProfile().getName();
                    if (name.equals(selfName)) continue;
                    if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                    if (mouseY >= scrollY && mouseY <= scrollY + SIDEBAR_ITEM_H) {
                        whisperPartner = name;
                        ChatMessageStore.clearUnreadWhisper(name);
                        sidebarSearchBox.setText(""); setFocused(input); scrollToBottom = true; return true;
                    }
                    scrollY += SIDEBAR_ITEM_H + 2;
                }
            }
        }

        if (button == 0 && contextAvatarIndex >= 0) { handleAvatarContextClick((int) mouseX, (int) mouseY); return true; }
        if (contextAvatarIndex >= 0) { contextAvatarIndex = -1; return true; }
        if (button == 0 && contextMsgIndex >= 0) { handleContextClick((int) mouseX, (int) mouseY); return true; }
        if (contextMsgIndex >= 0) { contextMsgIndex = -1; return true; }

        // Notification bar clicks
        if (button == 0 && newMessageCount > 0) {
            if (mouseX >= notifCountLeft && mouseX <= notifCountRight
                && mouseY >= notifBarTextY && mouseY <= notifBarTextY + textRenderer.fontHeight) {
                scrollToBottom = true; newMessageCount = 0; hasNewMentionOrQuote = false;
                latestMentionIndex = -1; lastSeenMessageCount = ChatMessageStore.getMessages().size(); return true;
            }
            if (hasNewMentionOrQuote && notifMentionLeft >= 0
                && mouseX >= notifMentionLeft && mouseX <= notifMentionRight
                && mouseY >= notifBarTextY && mouseY <= notifBarTextY + textRenderer.fontHeight) {
                jumpToMessage(latestMentionIndex); return true;
            }
        }

        if (button == 0 && replyTargetIndex >= 0 && isMouseOverReplyCancel(mouseX, mouseY)) {
            replyTargetIndex = -1; return true;
        }

        // Scrollbar interaction
        if (button == 0 && maxScroll > 0) {
            int trackX = panelX + panelW - SCROLLBAR_WIDTH;
            int effBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
            if (mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                && mouseY >= msgTop && mouseY < effBottom) {
                int trackH = effBottom - msgTop;
                int thumbH = Math.max(MIN_THUMB_H, (int) ((long) trackH * trackH / messageTotalH));
                thumbH = Math.min(thumbH, trackH);
                int travelRange = trackH - thumbH;
                int thumbY = msgTop + (int) ((long) scrollOffset * travelRange / maxScroll);
                if (mouseY < thumbY) { scrollOffset = Math.max(0, scrollOffset - trackH); }
                else if (mouseY > thumbY + thumbH) { scrollOffset = Math.min(maxScroll, scrollOffset + trackH); }
                else { scrollbarDragging = true; scrollbarDragStartY = (int) mouseY; scrollbarDragStartOffset = scrollOffset; }
                scrollToBottom = false; return true;
            }
        }

        if (commandSuggestions != null && commandSuggestions.mouseClicked((int) mouseX, (int) mouseY, button))
            return true;

        if (button == 0) {
            if (isMouseOverHamburger(mouseX, mouseY)) {
                if (!ChatBubbleClientSetup.config().animationEnabled()) {
                    sidebarOpen = !sidebarOpen; sidebarAnimating = false;
                    panelX = sidebarOpen ? SIDEBAR_W : 0;
                    sidebarSearchBox.setX(2); sidebarSearchBox.setVisible(sidebarOpen);
                    if (!sidebarOpen && sidebarSearchBox.isFocused()) setFocused(input);
                    rebuildLayout();
                } else if (sidebarAnimating) {
                    sidebarTargetOpen = !sidebarTargetOpen;
                    long elapsed = Util.getMeasuringTimeMs() - sidebarAnimStart;
                    float currentT = MathHelper.clamp((float) elapsed / ANIM_MS, 0f, 1f);
                    sidebarAnimStart = Util.getMeasuringTimeMs() - (long) ((1.0f - currentT) * ANIM_MS);
                } else {
                    sidebarTargetOpen = !sidebarOpen; sidebarAnimating = true;
                    sidebarAnimStart = Util.getMeasuringTimeMs();
                }
                return true;
            }
            if (mouseX >= panelX + panelW - 18 && mouseX <= panelX + panelW - 6
                && mouseY >= titleY + 6 && mouseY <= titleY + 18) { onClose(); return true; }
            if (settingsMenu.visible) {
                int action = settingsMenu.handleClick((int) mouseX, (int) mouseY, panelX, panelW, barTop, ICON_S);
                if (action >= 0) executeMenuAction(action);
                return true;
            }
            if (emojiPanel.visible) {
                String emojiText = emojiPanel.handleClick((int) mouseX, (int) mouseY, textRenderer, c(), panelX, panelW, barTop, ICON_S, PAD);
                if (emojiText != null && !emojiText.isEmpty()) {
                    input.write(emojiText);
                }
                return true;
            }
            if (quickChatPanel.visible) {
                int result = quickChatPanel.handleClick((int) mouseX, (int) mouseY, textRenderer, c(), panelX, panelW, barTop, quickChatInput);
                if (result >= 0) {
                    input.setText(ChatBubbleClientSetup.config().quickChatPhrases().get(result));
                    setFocused(input);
                } else if (result == -2) {
                    setFocused(quickChatInput);
                }
                return true;
            }
            if (searchPanel.visible) {
                if (searchPanel.isClickOnPanel((int) mouseX, (int) mouseY, panelX, panelW, barTop)) {
                    setFocused(searchInput); return true;
                }
                closeSearchPanel(); return true;
            }
            if (mouseY >= barTop) {
                if (handleIconClick((int) mouseX, (int) mouseY)) return true;
            }
        }

        // Avatar click for @mention
        if (button == 0) {
            for (int[] r : bubbleRects) {
                ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(r[4]);
                if (msg == null || msg.isSystem()) continue;
                int avatarX = msg.isOwn() ? r[0] + r[2] + 4 : r[0] - AVATAR - 4;
                int avatarY = msg.replyContent() != null ? r[1] - textRenderer.fontHeight - 2 : r[1] - NAME_H;
                if (mouseX >= avatarX && mouseX <= avatarX + AVATAR
                    && mouseY >= avatarY && mouseY <= avatarY + AVATAR) {
                    String mentionName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
                        ? msg.rawPlayerName() : msg.senderName().getString();
                    input.setText(input.getText() + "@" + mentionName + " ");
                    input.setCursorToEnd(false);
                    return true;
                }
            }
        }

        // Avatar right-click context menu
        if (button == 1) {
            for (int[] r : bubbleRects) {
                ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(r[4]);
                if (msg == null || msg.isSystem() || msg.isOwn()) continue;
                if (msg.rawPlayerName() == null || msg.rawPlayerName().isEmpty()) continue;
                int avatarX = r[0] - AVATAR - 4;
                int avatarY = msg.replyContent() != null ? r[1] - textRenderer.fontHeight - 2 : r[1] - NAME_H;
                if (mouseX >= avatarX && mouseX <= avatarX + AVATAR
                    && mouseY >= avatarY && mouseY <= avatarY + AVATAR) {
                    contextAvatarIndex = r[4]; contextAvatarX = (int) mouseX; contextAvatarY = (int) mouseY;
                    return true;
                }
            }
        }

        // Bubble right-click
        if (button == 1) {
            for (int[] r : bubbleRects) {
                if (mouseX >= r[0] && mouseX <= r[0] + r[2]
                    && mouseY >= r[1] && mouseY <= r[1] + r[3]) {
                    contextMsgIndex = r[4]; contextX = (int) mouseX; contextY = (int) mouseY;
                    return true;
                }
            }
        }

        // Clickable text
        if (button == 0) {
            Style style = getHoveredStyle(mouseX, mouseY);
            if (style != null && style.getClickEvent() != null) {
                ClickEvent click = style.getClickEvent();
                if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                    input.setText(click.getValue()); return true;
                }
                if (click.getAction() == ClickEvent.Action.OPEN_FILE) {
                    java.io.File file = new java.io.File(click.getValue());
                    Util.getOperatingSystem().open(file); return true;
                }
                if (click.getAction() == ClickEvent.Action.OPEN_URL) {
                    handleTextClick(style); return true;
                }
                handleTextClick(style); return true;
            }
        }
        return super.mouseClicked(origX, mouseY, button);
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (scrollbarDragging && maxScroll > 0) {
            lastScrollTime = Util.getMeasuringTimeMs();
            int effBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
            int trackH = effBottom - msgTop;
            int thumbH = Math.max(MIN_THUMB_H, (int) ((long) trackH * trackH / messageTotalH));
            thumbH = Math.min(thumbH, trackH);
            int travelRange = trackH - thumbH;
            if (travelRange > 0) {
                int dy = (int) mouseY - scrollbarDragStartY;
                float newTarget = MathHelper.clamp(scrollbarDragStartOffset + (int) ((long) dy * maxScroll / travelRange), 0, maxScroll);
                scrollAnimFrom = scrollOffset; scrollAnimTo = newTarget;
                scrollAnimStart = Util.getMeasuringTimeMs();
                if (!scrollAnimActive) { scrollAnimDuration = 80; scrollAnimActive = true; }
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (scrollbarDragging) { scrollbarDragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleIconClick(int mx, int my) {
        int iconY = barTop + (BAR_H - ICON_S) / 2;
        int gearX = panelX + 4;
        if (mx >= gearX && mx <= gearX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (emojiPanel.visible) emojiPanel.visible = false;
            if (searchPanel.visible) closeSearchPanel();
            settingsMenu.visible = !settingsMenu.visible;
            return true;
        }
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
        if (mx >= sendX && mx <= sendX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            sendMessage(); return true;
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
                if (msg != null) { client.keyboard.setClipboard(msg.content().getString()); copyToastTicks = 30; }
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
                client.player.networkHandler.sendCommand((ChatMessageStore.useTpa() ? "tpa " : "tp ") + name);
            } else if (my >= menuY + CTX_ITEM_H + 2 && my <= menuY + CTX_ITEM_H * 2 + 2) {
                whisperPartner = name;
                ChatMessageStore.clearUnreadWhisper(name);
                if (sidebarSearchBox != null) sidebarSearchBox.setText("");
                setFocused(input); scrollToBottom = true;
            }
        }
        contextAvatarIndex = -1;
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        tickSidebarAnimation();

        float anim = getAnimProgress();
        int panelOffset = currentPanelOffset();

        g.getMatrices().push();
        g.getMatrices().translate(panelOffset, 0, 0);

        float panelOpacity = ChatBubbleClientSetup.config().panelOpacity() / 100f * anim;
        // When sidebar is synced to main animation, extend panel bg to
        // sidebar's right edge so there's no gap between them.
        int fillLeft = (!sidebarAnimating && sidebarOpen)
            ? (int)(anim * SIDEBAR_W) : panelX;
        if (ChatBubbleClientSetup.config().blurEnabled() && panelOpacity < 0.999f) {
            g.draw();
            BlurRenderer.blurPanel(fillLeft, 0, panelX + panelW - fillLeft, height);
        }
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.PANEL_BG),
            fillLeft, 0, panelX + panelW - fillLeft, height, panelOpacity);

        renderTitleBar(g, mouseX, mouseY);
        renderMessages(g, mouseX, mouseY);
        Style hovered = getHoveredStyle(mouseX, mouseY);
        if (hovered != null && hovered.getHoverEvent() != null) {
            g.drawHoverEvent(textRenderer, hovered, mouseX, mouseY);
        }

        g.getMatrices().translate(0, 0, 50);
        renderNotificationBar(g, mouseX, mouseY);
        renderReplyBar(g, mouseX, mouseY);
        renderContextMenu(g, mouseX, mouseY);
        renderAvatarContextMenu(g, mouseX, mouseY);
        renderToast(g);
        settingsMenu.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, ChatBubbleScreen::iconTex);
        emojiPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, ICON_S, PAD);
        quickChatPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, quickChatInput);
        searchPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, searchInput, searchMatches, searchMatchIdx);
        renderBottomBar(g, mouseX, mouseY);
        renderMentionPopup(g, mouseX, mouseY);

        g.enableScissor(panelX, 0, panelX + panelW, height);
        if (commandSuggestions != null) commandSuggestions.render(g, mouseX, mouseY);
        g.disableScissor();

        g.getMatrices().pop();

        if (sidebarOpen || sidebarAnimating) {
            g.getMatrices().push();
            int sidebarOffset = closing
                ? (int) ((getAnimProgress() - 1.0f) * SIDEBAR_W)
                : getSidebarScreenX();
            g.getMatrices().translate(sidebarOffset, 0, 50);
            renderSidebar(g, mouseX - sidebarOffset, mouseY);
            g.getMatrices().pop();
            if (closing) sidebarSearchBox.setX(2 + sidebarOffset);
        }

        g.getMatrices().push();
        g.getMatrices().translate(0, 0, 50);
        input.setX(inputX + panelOffset);
        super.render(g, mouseX, mouseY, delta);
        g.getMatrices().pop();

        // Notification banner is rendered by ChatBubbleHudOverlay at z=300
    }

    private void renderTitleBar(DrawContext g, int mouseX, int mouseY) {
        int ty = titleY;
        g.drawTexture(UiTextureManager.rl(UiElement.TITLE_BAR), panelX, ty, 0f, 0f, panelW, TITLE_H, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), panelX, ty + TITLE_H, 0f, 0f, panelW, 1, 1, 1);

        int menuX = panelX + 3;
        int menuY = ty + (TITLE_H - ICON_S) / 2;
        boolean hoverMenu = mouseX >= menuX && mouseX <= menuX + ICON_S && mouseY >= menuY && mouseY <= menuY + ICON_S;
        if (hoverMenu) g.fill(menuX - 1, menuY - 1, menuX + ICON_S + 1, menuY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("menu"), menuX, menuY, ICON_S);

        String title = getDisplayTitle();
        int titleW = textRenderer.getWidth(title);
        int titleX = UiLayout.centerX(panelX, panelW, titleW);
        int titleTextY = ty + (TITLE_H - textRenderer.fontHeight) / 2;
        g.drawText(textRenderer, title, titleX, titleTextY, c().textPrimary(), false);

        String time = LocalTime.now().format(TIME_FMT);
        int timeW = textRenderer.getWidth(time);
        g.drawText(textRenderer, time,
            panelX + panelW - PAD - 20 - timeW, ty + (TITLE_H - textRenderer.fontHeight) / 2, c().timeColor(), false);

        int closeX = panelX + panelW - 18;
        int closeY = ty + 6;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        int closeBg = hoverClose ? c().closeHoverBg() : c().closeBg();
        g.fill(closeX, closeY, closeX + 12, closeY + 12, closeBg);
        g.drawText(textRenderer, "✕", closeX + 6 - textRenderer.getWidth("✕") / 2, closeY + 2, c().closeText(), false);
    }

    private boolean isMouseOverHamburger(double mx, double my) {
        int menuX = panelX + 3;
        int menuY = titleY + (TITLE_H - ICON_S) / 2;
        return mx >= menuX && mx <= menuX + ICON_S && my >= menuY && my <= menuY + ICON_S;
    }

    private void renderMessages(DrawContext g, int mouseX, int mouseY) {
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
        g.drawTexture(UiTextureManager.rl(UiElement.WHISPER_BAR), panelX, indY, 0f, 0f, panelW, indicatorH, 1, 1);
            String modeText = Text.translatable("e33chat.whisper.mode").getString() + ": " + whisperPartner;
            int modeTW = textRenderer.getWidth(modeText);
            g.drawText(textRenderer, modeText, panelX + (panelW - modeTW) / 2, indY + 2, c().textPrimary(), false);
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

        String playerName = client.player != null ? client.player.getName().getString() : "";
        int currentMsgCount = messages.size();
        if (wasAtBottom) {
            newMessageCount = 0; hasNewMentionOrQuote = false;
            latestMentionIndex = -1; lastSeenMessageCount = currentMsgCount;
        } else if (currentMsgCount > lastSeenMessageCount) {
            for (int i = lastSeenMessageCount; i < currentMsgCount; i++) {
                var msg = messages.get(i);
                if (msg == null) continue;
                newMessageCount++;
                if (msg.content().getString().contains("@" + playerName)) {
                    hasNewMentionOrQuote = true; latestMentionIndex = i;
                }
                if (msg.replySender() != null && msg.replySender().equals(playerName)) {
                    hasNewMentionOrQuote = true;
                    if (i > latestMentionIndex) latestMentionIndex = i;
                }
            }
            lastSeenMessageCount = currentMsgCount;
        }

        if (firstRender) {
            scrollOffset = maxScroll; scrollToBottom = false; firstRender = false; scrollAnimActive = false;
        } else if (scrollAnimActive) {
            float t = Animation.progress(scrollAnimStart, scrollAnimDuration, false);
            scrollOffset = Math.round(scrollAnimFrom + (scrollAnimTo - scrollAnimFrom) * t);
            if (t >= 1.0f) { scrollOffset = Math.round(scrollAnimTo); scrollAnimActive = false; }
        } else if (scrollToBottom || wasAtBottom) {
            float newTarget = maxScroll;
            if (Math.abs(scrollOffset - newTarget) <= 3) {
                scrollOffset = Math.round(newTarget); scrollToBottom = false;
            } else {
                lastScrollTime = Util.getMeasuringTimeMs();
                scrollAnimFrom = scrollOffset; scrollAnimTo = newTarget;
                scrollAnimStart = Util.getMeasuringTimeMs(); scrollAnimDuration = 150; scrollAnimActive = true;
            }
        }
        scrollOffset = MathHelper.clamp(scrollOffset, 0, maxScroll);

        g.enableScissor(panelX, effectiveMsgTop, panelX + panelW, effectiveMsgBottom);

        List<ChatMessageStore.ChatMessage> fullList = ChatMessageStore.getMessages();
        int fullIdx = 0;
        while (fullIdx < fullList.size() && fullList.get(fullIdx) != messages.get(0)) fullIdx++;

        int contentY = 0;
        lastKey = null;
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);
            while (fullIdx < fullList.size() && fullList.get(fullIdx) != msg) fullIdx++;

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

    private void renderScrollbar(DrawContext g, int mouseX, int mouseY, int effectiveMsgBottom) {
        if (maxScroll <= 0) return;
        boolean inZone = mouseX >= panelX + panelW - SCROLLBAR_HOVER_ZONE
            && mouseX <= panelX + panelW && mouseY >= msgTop && mouseY < effectiveMsgBottom;
        boolean recentlyScrolled = Util.getMeasuringTimeMs() - lastScrollTime < 1000;
        float target = (inZone || scrollbarDragging || recentlyScrolled) ? 1f : 0f;
        scrollbarAlpha = Animation.lerpTo(scrollbarAlpha, target, 0.15f, 0.005f);
        if (scrollbarAlpha <= 0.005f && !scrollbarDragging) return;

        int trackX = panelX + panelW - SCROLLBAR_WIDTH;
        int trackTop = msgTop;
        int trackBottom = effectiveMsgBottom;
        int trackH = trackBottom - trackTop;

        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SCROLLBAR_TRACK),
            trackX, trackTop, SCROLLBAR_WIDTH, trackBottom - trackTop, 0x1A / 255f * scrollbarAlpha);

        int thumbH = Math.max(MIN_THUMB_H, (int) ((long) trackH * trackH / messageTotalH));
        thumbH = Math.min(thumbH, trackH);
        int travelRange = trackH - thumbH;
        int thumbY = trackTop + (int) ((long) scrollOffset * travelRange / maxScroll);

        boolean hovering = !scrollbarDragging
            && mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
            && mouseY >= thumbY && mouseY < thumbY + thumbH;
        scrollbarHovered = hovering || scrollbarDragging;

        float thumbBase = scrollbarDragging ? 0xAA : scrollbarHovered ? 0x88 : 0x66;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SCROLLBAR_THUMB),
            trackX, thumbY, SCROLLBAR_WIDTH, thumbH, thumbBase / 255f * scrollbarAlpha);
    }

    private void renderTimeSeparator(DrawContext g, long timeMillis, int y) {
        String text = ChatMessageStore.formatTime(timeMillis);
        int tw = textRenderer.getWidth(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        g.fill(tx - 6, y + 2, tx + tw + 6, y + TIME_SEP_H - 2, ChatBubbleTheme.alphaBlend(c().toastBg(), 0x44));
        g.drawText(textRenderer, text, tx, y + 3, c().timeColor(), false);
    }

    private List<OrderedText> wrapContent(Text c, int width) {
        List<Text> paras = new ArrayList<>();
        MutableText[] cur = { Text.empty() };
        c.visit((style, text) -> {
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    if (i > start) cur[0].append(Text.literal(text.substring(start, i)).fillStyle(style));
                    paras.add(cur[0]);
                    cur[0] = Text.empty();
                    start = i + 1;
                }
            }
            if (start < text.length()) cur[0].append(Text.literal(text.substring(start)).fillStyle(style));
            return Optional.empty();
        }, Style.EMPTY);
        paras.add(cur[0]);
        while (!paras.isEmpty() && paras.get(0).getString().isEmpty()) paras.remove(0);
        while (!paras.isEmpty() && paras.get(paras.size() - 1).getString().isEmpty()) paras.remove(paras.size() - 1);
        List<OrderedText> out = new ArrayList<>();
        for (Text p : paras) out.addAll(textRenderer.wrapLines(p, width));
        if (out.isEmpty()) out.addAll(textRenderer.wrapLines(c, width));
        return out;
    }

    private boolean isPanelSliding() {
        return ChatBubbleClientSetup.config().animationEnabled() && getAnimProgress() < 1.0f;
    }

    private int currentPanelOffset() {
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
        int h;
        if (msg.isSystem()) {
            List<OrderedText> lines = wrapContent(msg.content(), panelW - PAD * 2 - 20);
            h = lines.size() * textRenderer.fontHeight + 4;
        } else {
            int bubbleMaxW = panelW - AVATAR - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
            List<OrderedText> lines = wrapContent(msg.content(), bubbleMaxW);
            h = lines.size() * textRenderer.fontHeight + BUBBLE_PAD_Y * 2 + NAME_H;
            if (msg.replyContent() != null) h += textRenderer.fontHeight + 7;
        }
        msgHeightCache.put(msg, h);
        return h;
    }

    private void renderBubble(DrawContext g, ChatMessageStore.ChatMessage msg, int index, int baseY, int mouseX, int mouseY) {
        if (msg.isSystem()) {
            List<OrderedText> lines = wrapContent(msg.content(), panelW - PAD * 2 - 20);
            int yy = baseY + 2;
            Style fb = findClickStyle(msg.content());
            for (var line : lines) {
                int lw = textRenderer.getWidth(line);
                renderLineWithClicks(g, line, panelX + (panelW - lw) / 2, yy, c().textMuted(), fb);
                yy += textRenderer.fontHeight;
            }
            return;
        }

        boolean own = msg.isOwn();
        int bubbleMaxW = panelW - AVATAR - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
        List<OrderedText> lines = wrapContent(msg.content(), bubbleMaxW);

        int textW = 0;
        for (var line : lines) textW = Math.max(textW, textRenderer.getWidth(line));
        int bubbleW = textW + BUBBLE_PAD_X * 2;
        int bubbleH = lines.size() * textRenderer.fontHeight + BUBBLE_PAD_Y * 2;

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
            Text sn = msg.senderName();
            OrderedText nameSeq;
            if (textRenderer.getWidth(sn) > maxNameW) {
                var cut = textRenderer.trimToWidth(sn, maxNameW - textRenderer.getWidth("..."));
                nameSeq = Language.getInstance().reorder(
                    StringVisitable.concat(cut, StringVisitable.plain("...")));
            } else {
                nameSeq = sn.asOrderedText();
            }
            int nameW = textRenderer.getWidth(nameSeq);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            g.drawText(textRenderer, nameSeq, startX, nameY, c().nameColor(), false);
        }

        int bubbleY = baseY + NAME_H;
        int avatarY = baseY;

        int bg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().ownBubbleColor(), 0xFF1E90FF)
            : ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().otherBubbleColor(), c().contextHover());
        int fg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().ownTextColor(), 0xFFFFFFFF)
            : ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().otherTextColor(), c().textPrimary());

        RoundRectRenderer.fill(g, bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH,
            ChatBubbleClientSetup.config().bubbleCornerRadius(), bg);

        Style fbP = findClickStyle(msg.content());
        for (int li = 0; li < lines.size(); li++)
            renderLineWithClicks(g, lines.get(li), bubbleX + BUBBLE_PAD_X,
                bubbleY + BUBBLE_PAD_Y + li * textRenderer.fontHeight, fg, fbP);

        String skinName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
            ? msg.rawPlayerName() : msg.senderName().getString();
        Identifier skin = getSkin(msg.senderUUID(), skinName);
        drawPlayerHead(g, skin, avatarX, avatarY, 20, 22);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = textRenderer.getWidth(label);
            int labelX, labelY = bubbleY + (bubbleH - textRenderer.fontHeight) / 2;
            if (own) { labelX = bubbleX - labelW - 3; } else { labelX = bubbleX + bubbleW + 3; }
            g.drawText(textRenderer, label, labelX, labelY, c().duplicateLabel(), false);
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - PAD * 2 - AVATAR - 24;
            String quoteText = "↳ " + msg.replySender() + ": " + msg.replyContent();
            String quoteDisplay = textRenderer.trimToWidth(quoteText, quoteMaxW - 10);
            if (!quoteDisplay.equals(quoteText)) quoteDisplay += "...";
            int quoteTextW = textRenderer.getWidth(quoteDisplay);
            int quoteW = Math.min(quoteTextW + 8, quoteMaxW);
            int quoteH = textRenderer.fontHeight + 4;
            int quoteY = bubbleY + bubbleH + 3;
            int quoteX;
            if (own) { quoteX = bubbleX + bubbleW - quoteW; } else { quoteX = bubbleX; }
            if (quoteX < panelX + PAD) quoteX = panelX + PAD;
            if (quoteX + quoteW > panelX + panelW - PAD) quoteW = panelX + panelW - PAD - quoteX;
            RoundRectRenderer.fill(g, quoteX, quoteY, quoteX + quoteW, quoteY + quoteH, 3, c().contextHover());
            g.drawText(textRenderer, quoteDisplay, quoteX + 4, quoteY + 2, c().textSecondary(), false);
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});

        if (index == searchHighlightIndex)
            g.drawBorder(bubbleX - 1, bubbleY - 1, bubbleW + 2, bubbleH + 2, ChatSearchPanel.HIGHLIGHT);
    }

    private void renderLineWithClicks(DrawContext g, OrderedText line, int x, int y, int color) {
        renderLineWithClicks(g, line, x, y, color, null);
    }

    private void renderLineWithClicks(DrawContext g, OrderedText line, int x, int y, int color, Style fallback) {
        final List<Style> styles = new ArrayList<>();
        line.accept((i, st, cp) -> { styles.add(st); return true; });

        final int beforeCount = clickableSpans.size();
        int runStart = -1;
        Style runStyle = null;
        List<int[]> clickableCharRanges = new ArrayList<>();
        for (int idx = 0; idx <= styles.size(); idx++) {
            Style st = idx < styles.size() ? styles.get(idx) : null;
            boolean clickable = st != null && (st.getClickEvent() != null || st.getHoverEvent() != null);
            if (runStyle == null) {
                if (clickable) { runStart = idx; runStyle = st; }
            } else if (!clickable || !st.equals(runStyle)) {
                int x0 = prefixWidth(line, runStart);
                int x1 = prefixWidth(line, idx);
                clickableSpans.add(new ClickableSpan(x + x0, y, x1 - x0, textRenderer.fontHeight, runStyle));
                clickableCharRanges.add(new int[]{runStart, idx});
                runStart = clickable ? idx : -1;
                runStyle = clickable ? st : null;
            }
        }

        if (fallback != null && fallback.getClickEvent() != null) {
            if (clickableSpans.size() == beforeCount) {
                clickableSpans.add(new ClickableSpan(x, y, textRenderer.getWidth(line), textRenderer.fontHeight,
                    fallback.withUnderline(true)));
                clickableCharRanges.add(new int[]{0, styles.size()});
            } else {
                for (int i = beforeCount; i < clickableSpans.size(); i++) {
                    ClickableSpan s = clickableSpans.get(i);
                    if (s.style.getClickEvent() == null) {
                        clickableSpans.set(i, new ClickableSpan(s.x, s.y, s.w, s.h,
                            s.style.withClickEvent(fallback.getClickEvent())));
                    }
                }
            }
        }

        int styleLen = styles.size();
        boolean[] hasClickEvent = new boolean[styleLen];
        for (int ri = 0; ri < clickableCharRanges.size(); ri++) {
            int spanIdx = beforeCount + ri;
            if (spanIdx < clickableSpans.size()
                && clickableSpans.get(spanIdx).style.getClickEvent() != null) {
                int[] r = clickableCharRanges.get(ri);
                for (int i = r[0]; i < r[1]; i++) hasClickEvent[i] = true;
            }
        }

        OrderedText decorated = sink -> line.accept((i, st, cp) ->
            sink.accept(i, (i < styleLen ? hasClickEvent[i] : st.getClickEvent() != null)
                && !st.isUnderlined() ? st.withUnderline(true) : st, cp));
        g.drawText(textRenderer, decorated, x, y, color, false);
    }

    private int prefixWidth(OrderedText line, int count) {
        if (count <= 0) return 0;
        return textRenderer.getWidth((OrderedText) sink -> {
            int[] left = {count};
            line.accept((i, st, cp) -> left[0]-- > 0 && sink.accept(i, st, cp));
            return true;
        });
    }

    private Style findClickStyle(Text c) {
        Style s = c.getStyle();
        if (s != null && s.getClickEvent() != null) return s;
        for (Text child : c.getSiblings()) {
            s = findClickStyle(child);
            if (s != null) return s;
        }
        return null;
    }

    private Style getHoveredStyle(double mouseX, double mouseY) {
        for (ClickableSpan s : clickableSpans) {
            if (mouseX >= s.x && mouseX <= s.x + s.w
                && mouseY >= s.y && mouseY <= s.y + s.h)
                return s.style;
        }
        return null;
    }

    private void renderNotificationBar(DrawContext g, int mouseX, int mouseY) {
        if (newMessageCount <= 0) return;
        int notifY = barTop - NOTIF_H;
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), panelX, notifY - 1, 0f, 0f, panelW, 1, 1, 1);
        int yellow = c().notificationText();
        int textY = notifY + (NOTIF_H - textRenderer.fontHeight) / 2;
        String ct = Text.translatable("e33chat.notif.new_messages", newMessageCount).getString() + " ▽";
        notifCountLeft = panelX + PAD;
        notifCountRight = notifCountLeft + textRenderer.getWidth(ct);
        notifBarTextY = textY;
        boolean h = mouseX >= notifCountLeft && mouseX <= notifCountRight
            && mouseY >= textY && mouseY <= textY + textRenderer.fontHeight;
        g.drawText(textRenderer, ct, notifCountLeft, textY, h ? c().notificationText() : yellow, false);
        if (hasNewMentionOrQuote) {
            String mt = Text.translatable("e33chat.notif.mention").getString() + " ▽";
            notifMentionLeft = panelX + panelW - PAD - textRenderer.getWidth(mt);
            notifMentionRight = notifMentionLeft + textRenderer.getWidth(mt);
            h = mouseX >= notifMentionLeft && mouseX <= notifMentionRight
                && mouseY >= textY && mouseY <= textY + textRenderer.fontHeight;
            g.drawText(textRenderer, mt, notifMentionLeft, textY, h ? c().notificationText() : yellow, false);
        } else {
            notifMentionLeft = -1; notifMentionRight = -1;
        }
    }

    private void renderContextMenu(DrawContext g, int mouseX, int mouseY) {
        if (contextMsgIndex < 0) return;
        int menuH = CTX_ITEM_H * 2 + 2;
        int menuX = Math.min(contextX, panelX + panelW - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < msgTop) menuY = contextY + 4;

        g.drawTexture(UiTextureManager.rl(UiElement.CONTEXT_MENU_BG), menuX, menuY, 0f, 0f, CTX_W, menuH, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 0f, 0f, CTX_W, 1, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY + menuH - 1, 0f, 0f, CTX_W, 1, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 0f, 0f, 1, menuH, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), menuX + CTX_W - 1, menuY, 0f, 0f, 1, menuH, 1, 1);

        boolean hoverCopy = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        int copyBg = hoverCopy ? c().contextHover() : c().sidebarItemSelected();
        g.fill(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, copyBg);
        drawTextureIcon(g, iconTex("copy"), menuX + 5, menuY + 3, 12);
        g.drawText(textRenderer, Text.translatable("e33chat.context.copy").getString(), menuX + 22, menuY + 4, c().textPrimary(), false);

        g.fill(menuX + 4, menuY + CTX_ITEM_H, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 1, c().closeHoverBg());

        boolean hoverQuote = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 1 && mouseY <= menuY + menuH;
        int quoteBg = hoverQuote ? c().contextHover() : c().sidebarItemSelected();
        g.fill(menuX + 1, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 1, menuY + menuH - 1, quoteBg);
        drawTextureIcon(g, iconTex("quote"), menuX + 5, menuY + CTX_ITEM_H + 3, 12);
        g.drawText(textRenderer, Text.translatable("e33chat.context.quote").getString(), menuX + 22, menuY + CTX_ITEM_H + 5, c().textPrimary(), false);
    }

    private void renderAvatarContextMenu(DrawContext g, int mouseX, int mouseY) {
        if (contextAvatarIndex < 0) return;
        int menuH = CTX_ITEM_H * 2 + 3;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;

        g.drawTexture(UiTextureManager.rl(UiElement.CONTEXT_MENU_BG), menuX, menuY, 0f, 0f, CTX_W, menuH, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 0f, 0f, CTX_W, 1, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY + menuH - 1, 0f, 0f, CTX_W, 1, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 0f, 0f, 1, menuH, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), menuX + CTX_W - 1, menuY, 0f, 0f, 1, menuH, 1, 1);

        boolean hoverTp = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        int tpBg = hoverTp ? c().contextHover() : c().sidebarItemSelected();
        g.fill(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, tpBg);
        drawTextureIcon(g, iconTex("tp"), menuX + 5, menuY + 3, 12);
        g.drawText(textRenderer, Text.translatable(ChatMessageStore.useTpa() ? "e33chat.context.tpa" : "e33chat.context.tp").getString(), menuX + 22, menuY + 4, c().textPrimary(), false);

        g.fill(menuX + 4, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 2, c().closeHoverBg());

        boolean hoverWhisper = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 2 && mouseY <= menuY + menuH;
        int whBg = hoverWhisper ? c().contextHover() : c().sidebarItemSelected();
        g.fill(menuX + 1, menuY + CTX_ITEM_H + 2, menuX + CTX_W - 1, menuY + menuH - 1, whBg);
        drawTextureIcon(g, iconTex("whisper"), menuX + 5, menuY + CTX_ITEM_H + 4, 12);
        g.drawText(textRenderer, Text.translatable("e33chat.context.whisper").getString(), menuX + 22, menuY + CTX_ITEM_H + 6, c().textPrimary(), false);
    }

    private static final int REPLY_BAR_H = 18;

    private void renderReplyBar(DrawContext g, int mouseX, int mouseY) {
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
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), barX, barTop - notifOffset - 1, 0f, 0f, barW, 1, 1, 1);

        String sender = target.senderName().getString();
        if (sender.isEmpty()) sender = Text.translatable("e33chat.sender.system").getString();
        String preview = sender + ": " + target.content().getString();
        int maxW = barW - 24;
        String display = textRenderer.trimToWidth(preview, maxW - textRenderer.getWidth("..."));
        if (!display.equals(preview)) display += "...";
        g.drawText(textRenderer, display, barX + 6, barY + 4, c().textSecondary(), false);

        int cx = barX + barW - 16;
        int cy = barY + 3;
        boolean hoverX = mouseX >= cx && mouseX <= cx + 12 && mouseY >= cy && mouseY <= cy + 12;
        int xBg = hoverX ? c().closeHoverBg() : c().sidebarItemSelected();
        g.fill(cx, cy, cx + 12, cy + 12, xBg);
        g.drawText(textRenderer, "✕", cx + 6 - textRenderer.getWidth("✕") / 2, cy + 2, c().closeText(), false);
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

    private void renderMentionPopup(DrawContext g, int mouseX, int mouseY) {
        if (!showMentions || mentionCandidates.isEmpty()) return;
        int maxW = 60;
        for (String name : mentionCandidates) maxW = Math.max(maxW, textRenderer.getWidth(name));
        int popupW = maxW + 12;
        int visible = Math.min(mentionCandidates.size(), 8);
        int popupH = visible * textRenderer.fontHeight + 4;
        int popupX = input.getX();
        int popupY = input.getY() - popupH - 2;
        if (popupY < msgTop) popupY = input.getY() + input.getHeight() + 2;

        g.drawTexture(UiTextureManager.rl(UiElement.POPUP_BG), popupX, popupY, 0f, 0f, popupW, popupH, 1, 1);
        g.drawBorder(popupX, popupY, popupW, popupH, c().divider());

        int startIdx = Math.max(0, mentionIdx - visible + 1);
        int endIdx = Math.min(mentionCandidates.size(), startIdx + visible);
        if (endIdx - startIdx < visible) startIdx = Math.max(0, endIdx - visible);
        for (int i = startIdx; i < endIdx; i++) {
            int ly = popupY + 2 + (i - startIdx) * textRenderer.fontHeight;
            if (i == mentionIdx)
                g.fill(popupX + 1, ly, popupX + popupW - 1, ly + textRenderer.fontHeight, c().popupHover());
            g.drawText(textRenderer, mentionCandidates.get(i), popupX + 4, ly, c().textPrimary(), false);
        }
    }

    private void renderToast(DrawContext g) {
        if (copyToastTicks <= 0) return;
        int alpha = Animation.fadeInOut(copyToastTicks, 5, 20, 5);
        int color = (alpha << 24) | (c().toastText() & 0x00FFFFFF);
        String text = Text.translatable("e33chat.toast.copied").getString();
        int tw = textRenderer.getWidth(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        int ty = msgBottom - 24;
        // Background fades with the text, at half opacity like the strong-hint bar
        g.fill(tx - 6, ty - 2, tx + tw + 6, ty + textRenderer.fontHeight + 2, (alpha / 2) << 24);
        g.drawText(textRenderer, text, tx, ty, color, false);
    }

    private void executeMenuAction(int action) {
        switch (action) {
            case 0: // search
                if (quickChatPanel.visible) { quickChatPanel.visible = false; quickChatInput.setVisible(false); }
                if (emojiPanel.visible) emojiPanel.visible = false;
                searchPanel.visible = true;
                searchInput.setText("");
                searchMatches.clear(); searchMatchIdx = -1; searchHighlightIndex = -1;
                setFocused(searchInput);
                break;
            case 1: // quick_chat
                if (searchPanel.visible) closeSearchPanel();
                if (emojiPanel.visible) emojiPanel.visible = false;
                quickChatPanel.visible = true;
                quickChatPanel.scrollOffset = 0;
                quickChatInput.setText("");
                setFocused(input);
                break;
            case 2: { // theme
                ChatBubbleTheme next = theme() == ChatBubbleTheme.DARK ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
                ChatBubbleClientSetup.saveConfig(ChatBubbleClientSetup.config().withTheme(next.name().toLowerCase()));
                ensureIconsLoaded();
                int editColor = next == ChatBubbleTheme.LIGHT ? c().textSecondary() : c().textPrimary();
                input.setEditableColor(editColor);
                input.setUneditableColor(c().textMuted());
                sidebarSearchBox.setEditableColor(editColor);
                sidebarSearchBox.setUneditableColor(editColor);
                quickChatInput.setEditableColor(editColor);
                quickChatInput.setUneditableColor(c().textMuted());
                searchInput.setEditableColor(editColor);
                searchInput.setUneditableColor(c().textMuted());
                int cmdAlpha = next == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
                commandSuggestions = new ChatInputSuggestor(client, this, input, textRenderer,
                    false, false, 0, 8, true, ChatBubbleTheme.alphaBlend(c().panelBg(), cmdAlpha));
                commandSuggestions.setWindowActive(true);
                break;
            }
            case 3: // settings
                client.setScreen(new ChatBubbleConfigScreen(this));
                break;
        }
    }

    private void closeSearchPanel() {
        searchPanel.visible = false;
        searchInput.setVisible(false);
        searchMatches.clear(); searchMatchIdx = -1; searchHighlightIndex = -1;
        setFocused(input);
    }

    private void renderBottomBar(DrawContext g, int mouseX, int mouseY) {
        g.drawTexture(UiTextureManager.rl(UiElement.BOTTOM_BAR), panelX, barTop, 0f, 0f, panelW, height - barTop, 1, 1);
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), panelX, barTop, 0f, 0f, panelW, 1, 1, 1);

        int iconY = barTop + (BAR_H - ICON_S) / 2;

        int ibX = inputX;
        int ibY = inputY;
        int ibW = input.getWidth();
        int ibH = INPUT_H;
        g.drawTexture(UiTextureManager.rl(UiElement.DIVIDER), ibX - 1, ibY - 1, 0f, 0f, ibW + 1, 1, 1, 1);
        g.fill(ibX - 1, ibY, ibX + ibW, ibY + ibH, c().inputBg());

        boolean hoverInput = mouseX >= ibX - 1 && mouseX <= ibX + ibW && mouseY >= ibY && mouseY <= ibY + ibH;
        if (hoverInput || input.isFocused())
            g.drawBorder(ibX - 1, ibY, ibW + 1, ibH, c().textMuted());

        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;

        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverGear) g.fill(gearX - 1, iconY - 1, gearX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("settings"), gearX, iconY, ICON_S);

        boolean hoverEmoji = mouseX >= emojiX && mouseX <= emojiX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverEmoji || emojiPanel.visible) g.fill(emojiX - 1, iconY - 1, emojiX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("emoji"), emojiX, iconY, ICON_S);

        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverSend) g.fill(sendX - 1, iconY - 1, sendX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("send"), sendX, iconY, ICON_S);
    }

    static void loadIconTextures() {
        String theme = ChatBubbleClientSetup.config().theme().toLowerCase();
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

    private static void loadIconTexture(Identifier loc, String classpath) {
        try (InputStream in = ChatBubbleScreen.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                MinecraftClient.getInstance().getTextureManager().registerTexture(loc, tex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    static void drawTextureIcon(DrawContext g, Identifier tex, int x, int y, int size) {
        var tm = MinecraftClient.getInstance().getTextureManager();
        AbstractTexture abstractTex;
        try {
            abstractTex = tm.getTexture(tex);
        } catch (Exception e) {
            loadIconTextures();
            abstractTex = tm.getTexture(tex);
        }
        RenderSystem.setShaderTexture(0, abstractTex.getGlId());
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.enableBlend();
        if (size < 16) {
            // 图标纹理约定 16x16（内容居中，四周 1px 透明边）。size<16 时采样内容区
            // (偏移1,1) 1:1 绘制——全幅采样压缩会在 4x 缩放下把边缘半透明像素
            // 线性放大成"切割"痕迹；1:1 整数放大则干净。
            g.drawTexture(tex, x, y, 1.0F, 1.0F, size, size, 16, 16);
        } else {
            g.drawTexture(tex, x, y, 0, 0, size, size, size, size);
        }
    }

    private static final UUID NIL_UUID = new UUID(0, 0);

    private void drawPlayerHead(DrawContext g, Identifier skin, int x, int y, int baseSize, int hatSize) {
        RenderSystem.enableBlend();
        g.drawTexture(skin, x, y, baseSize, baseSize, 8.0F, 8.0F, 8, 8, 64, 64);
        int hatOff = (hatSize - baseSize) / 2;
        g.drawTexture(skin, x - hatOff, y - hatOff, hatSize, hatSize, 40.0F, 8.0F, 8, 8, 64, 64);
        RenderSystem.disableBlend();
    }

    private Identifier getSkin(UUID uuid, String name) {
        // Online players: read PlayerInfo fresh every frame — caching the first result
        // (default Steve/Alex while async download is in progress) would freeze the head
        // forever even after the real skin loaded. CSL intercepts the underlying lookup.
        if (client.getNetworkHandler() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            PlayerListEntry info = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (info != null) return info.getSkinTextures().texture();
        }
        // Offline player / history mention: route through SkinProvider with a name-bearing
        // GameProfile so CSL can match offline names to imported skins. Cache this result.
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            Identifier cached = skinCache.get(uuid);
            if (cached != null) return cached;
        }
        Identifier resolved = resolveSkin(uuid, name);
        if (uuid != null && !uuid.equals(NIL_UUID)) skinCache.put(uuid, resolved);
        return resolved;
    }

    private Identifier resolveSkin(UUID uuid, String name) {
        // Route through PlayerSkinProvider with a name-bearing GameProfile so CSL
        // can match offline players to imported skins. getSkinTextures(GameProfile)
        // is the Yarn equivalent of Mojang's SkinManager.getInsecureSkin().
        if (name != null && !name.isEmpty()) {
            try {
                GameProfile profile = new GameProfile(
                    uuid != null && !uuid.equals(NIL_UUID) ? uuid : NIL_UUID, name);
                return client.getSkinProvider().getSkinTextures(profile).texture();
            } catch (Exception ignored) {}
        }
        return DefaultSkinHelper.getTexture();
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
                if (lk == null || !k.equals(lk)) { lk = k; cy += TIME_SEP_H + GAP; }
            }
            cy += getMsgHeight(m) + GAP;
        }
        scrollOffset = Math.max(0, cy - 20);
        newMessageCount = 0; hasNewMentionOrQuote = false;
        latestMentionIndex = -1; lastSeenMessageCount = msgs.size();
    }

    private static Text parseColorCodes(String s) {
        if (s.indexOf('&') < 0) return Text.literal(s);
        MutableText out = Text.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && isFormatCode(s.charAt(i + 1))) {
                if (run.length() > 0) {
                    out.append(Text.literal(run.toString()).fillStyle(style));
                    run.setLength(0);
                }
                style = applyCode(style, s.charAt(i + 1));
                i++;
            } else {
                run.append(c);
            }
        }
        if (run.length() > 0) out.append(Text.literal(run.toString()).fillStyle(style));
        return out;
    }

    private static Style applyCode(Style st, char c) {
        switch (Character.toLowerCase(c)) {
            case '0': return st.withColor(Formatting.BLACK.getColorValue() != null ? Formatting.BLACK.getColorValue() : null);
            case '1': return st.withColor(Formatting.DARK_BLUE.getColorValue() != null ? Formatting.DARK_BLUE.getColorValue() : null);
            case '2': return st.withColor(Formatting.DARK_GREEN.getColorValue() != null ? Formatting.DARK_GREEN.getColorValue() : null);
            case '3': return st.withColor(Formatting.DARK_AQUA.getColorValue() != null ? Formatting.DARK_AQUA.getColorValue() : null);
            case '4': return st.withColor(Formatting.DARK_RED.getColorValue() != null ? Formatting.DARK_RED.getColorValue() : null);
            case '5': return st.withColor(Formatting.DARK_PURPLE.getColorValue() != null ? Formatting.DARK_PURPLE.getColorValue() : null);
            case '6': return st.withColor(Formatting.GOLD.getColorValue() != null ? Formatting.GOLD.getColorValue() : null);
            case '7': return st.withColor(Formatting.GRAY.getColorValue() != null ? Formatting.GRAY.getColorValue() : null);
            case '8': return st.withColor(Formatting.DARK_GRAY.getColorValue() != null ? Formatting.DARK_GRAY.getColorValue() : null);
            case '9': return st.withColor(Formatting.BLUE.getColorValue() != null ? Formatting.BLUE.getColorValue() : null);
            case 'a': return st.withColor(Formatting.GREEN.getColorValue() != null ? Formatting.GREEN.getColorValue() : null);
            case 'b': return st.withColor(Formatting.AQUA.getColorValue() != null ? Formatting.AQUA.getColorValue() : null);
            case 'c': return st.withColor(Formatting.RED.getColorValue() != null ? Formatting.RED.getColorValue() : null);
            case 'd': return st.withColor(Formatting.LIGHT_PURPLE.getColorValue() != null ? Formatting.LIGHT_PURPLE.getColorValue() : null);
            case 'e': return st.withColor(Formatting.YELLOW.getColorValue() != null ? Formatting.YELLOW.getColorValue() : null);
            case 'f': return st.withColor(Formatting.WHITE.getColorValue() != null ? Formatting.WHITE.getColorValue() : null);
            case 'k': return st.withObfuscated(true);
            case 'l': return st.withBold(true);
            case 'm': return st.withStrikethrough(true);
            case 'n': return st.withUnderline(true);
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
        String raw = input.getText().trim();
        if (raw.isEmpty()) return;
        var cfg = ChatBubbleClientSetup.config();
        // Send the text UNCHANGED (raw '&', never '§'): vanilla servers reject '§' in
        // player chat and kick, so converting client-side is a dead end. Server color
        // plugins (Essentials etc.) translate '&' for everyone; on plain servers others
        // see the literal '&'. Local coloring of our own bubble is done at addMessage.
        String text = raw;

        if (whisperPartner != null && !text.startsWith("/")) {
            text = "/msg " + whisperPartner + " " + text;
        }

        String whisperTarget = null;
        String displayText = text;
        if (text.startsWith("/msg ") || text.startsWith("/tell ") || text.startsWith("/w ")) {
            String[] parts = text.split(" ", 3);
            if (parts.length >= 3) { whisperTarget = parts[1]; displayText = parts[2]; }
        }

        boolean localBubble = !text.startsWith("/") || whisperTarget != null;

        if (replyTargetIndex >= 0) {
            if (localBubble) {
                ChatMessageStore.ChatMessage target = ChatMessageStore.getMessageAt(replyTargetIndex);
                if (target != null) {
                    String quoteSender = (target.rawPlayerName() != null && !target.rawPlayerName().isEmpty())
                        ? target.rawPlayerName() : target.senderName().getString();
                    String quoted = ChatMessageStore.singleLine(target.content().getString());
                    ChatMessageStore.setPendingReply(quoted, quoteSender);
                    ClientPlayNetworking.send(new QuoteSyncPayload(quoteSender, quoted, displayText));
                }
            }
            replyTargetIndex = -1;
        }

        if (text.startsWith("/"))
            client.player.networkHandler.sendCommand(text.substring(1));
        else
            client.player.networkHandler.sendChatMessage(text);
        client.inGameHud.getChatHud().addToMessageHistory(text);

        ChatMessageStore.debugLog("[e33chat] Send | cmd='" + text + "' | display='" + displayText + "' | whisperTarget=" + whisperTarget + " | localBubble=" + localBubble);
        if (localBubble) {
            Text contentForSend = cfg != null && cfg.colorCodes() ? parseColorCodes(displayText) : Text.literal(displayText);
            String playerName = client.player.getName().getString();
            String replySender = ChatMessageStore.getPendingReplySender();

            ChatMessageStore.addMessage(contentForSend,
                client.player.getUuid(),
                ChatMessageStore.ownDisplayName(),
                false,
                playerName,
                whisperTarget != null, whisperTarget, true);
            ChatMessageStore.incrementPendingEcho(text);

            // Trigger mention detection directly from send path.
            // The echo will be consumed (preventing duplicate bubbles),
            // so the controller must fire here for self-@ notifications.
            if (com.niuqu.chatbubble.chat.MentionDetector.isMentioned(
                    contentForSend.getString(), playerName,
                    cfg.mentionRequireAt(), replySender)) {
                com.niuqu.chatbubble.chat.notification.MentionNotificationController.INSTANCE.onMessageCaptured(
                    contentForSend,
                    new ChatMessageStore.SenderMeta(client.player.getUuid(),
                        Text.literal(playerName), contentForSend, false,
                        playerName, whisperTarget != null, whisperTarget),
                    ChatMessageStore.size(), replySender);
            }
        }
        if (whisperTarget != null) ChatMessageStore.markPendingWhisperEcho(whisperTarget);

        input.setText("");
        savedInput = "";
        scrollToBottom = true;
    }

    private void moveInHistory(int delta) {
        int size = client.inGameHud.getChatHud().getMessageHistory().size();
        int newPos = MathHelper.clamp(historyPos + delta, 0, size);
        if (newPos != historyPos) {
            if (newPos == size) {
                historyPos = size;
                input.setText(historyBuffer);
            } else {
                if (historyPos == size) historyBuffer = input.getText();
                input.setText(client.inGameHud.getChatHud().getMessageHistory().get(newPos));
                historyPos = newPos;
            }
        }
    }

    @Override
    public void removed() {
        if (ChatBubbleClientSetup.config().preserveInput()) savedInput = input.getText();
        ChatMessageStore.setScreenOpen(false);
        client.inGameHud.getChatHud().reset();
    }

    public void onClose() {
        if (ChatBubbleClientSetup.config().preserveInput()) savedInput = input.getText();
        if (!ChatBubbleClientSetup.config().animationEnabled()) {
            client.setScreen(null); return;
        }
        if (closing) return;
        closing = true;
        animStart = Util.getMeasuringTimeMs();
    }

    public boolean shouldPause() { return false; }

    private static class ClickableSpan {
        final int x, y, w, h;
        final Style style;
        ClickableSpan(int x, int y, int w, int h, Style style) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.style = style;
        }
    }
}
