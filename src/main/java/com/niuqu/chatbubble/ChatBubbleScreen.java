package com.niuqu.chatbubble;

import com.mojang.authlib.GameProfile;
import com.mojang.blaze3d.systems.RenderSystem;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.compat.IMBlockerCompat;
import com.niuqu.chatbubble.compat.ModernUIEmojiCompat;
import com.niuqu.chatbubble.compat.NativeFileDialog;
import com.niuqu.chatbubble.config.ChatBubbleConfigScreen;
import com.niuqu.chatbubble.render.Animation;
import com.niuqu.chatbubble.render.AnimationStyle;
import com.niuqu.chatbubble.render.BlurRenderer;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.render.UiLayout;
import com.niuqu.chatbubble.render.MessageGrouping;
import com.niuqu.chatbubble.render.UiTokens;
import com.niuqu.chatbubble.store.BlockList;
import com.niuqu.chatbubble.ui.EmoteStore;
import com.niuqu.chatbubble.image.BracketCodec;
import com.niuqu.chatbubble.image.ImageEntry;
import com.niuqu.chatbubble.image.ImageLoader;
import com.niuqu.chatbubble.image.ImageUploader;
import com.niuqu.chatbubble.image.LocalImageSource;
import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.ChatTextSelection;
import com.niuqu.chatbubble.render.TextSpan;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.network.QuoteSyncPayload;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import com.niuqu.chatbubble.ui.ChatEmojiPanel;
import com.niuqu.chatbubble.ui.ChatQuickChatPanel;
import com.niuqu.chatbubble.ui.ChatSearchPanel;
import com.niuqu.chatbubble.ui.ChatSettingsMenu;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.render.GameRenderer;
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

public class ChatBubbleScreen extends ChatScreen {

    // Layout
    private int panelX, panelW;
    private static final int TITLE_H = 24;
    private int titleY, msgTop, msgBottom, barTop;
    private static final int PAD = UiTokens.PAD;
    private static final int BUBBLE_PAD_X = UiTokens.BUBBLE_PAD_X;
    private static final int BUBBLE_PAD_Y = UiTokens.BUBBLE_PAD_Y;
    private static final int NAME_H = 10;
    private static final int TIME_SEP_H = 14;
    public static final int BAR_H = 26;
    private static final int SIDEBAR_W = 90;

    /**
     * Windowed-mode cap: the panel never exceeds this fraction of the window
     * width (40% keeps a 1000px panel intact on a 2560px fullscreen display
     * while shrinking it on smaller windows). Fullscreen panel mode opts out.
     */
    private static final double MAX_WINDOW_FRACTION = 0.40;
    private static final int SIDEBAR_ITEM_H = 22;
    private static final int SIDEBAR_ICON_S = 20;

    private ChatBubbleTheme.Colors c() {
        return Appearance.snapshot();
    }

    private ChatBubbleTheme theme() {
        return "light".equalsIgnoreCase(ChatBubbleClientSetup.config().theme())
            ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
    }

    private static final int INPUT_H = 14;
    private static final int ICON_S = 14;

    public static Identifier iconTex(String name) {
        String theme = ChatBubbleClientSetup.config().theme().toLowerCase();
        return Identifier.of("e33chat", "textures/gui/" + theme + "/" + name + ".png");
    }


    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static String timeKey(long t) {
        return ChatMessageStore.timeKey(t, ChatBubbleClientSetup.config().timeSeparatorMinutes());
    }

    private ChatInputSuggestor commandSuggestions;
    private static int inputX, inputY;

    public static int getInputX() { return inputX; }
    public static int getInputY() { return inputY; }
    private final String initialText;
    private String historyBuffer = "";
    private int historyPos = -1;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollToBottom = true;
    private boolean firstRender = true;
    private static String savedInput = "";
    private boolean emojiReplacing;


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

    // Popup open animation timestamps (opening only; closing stays instant)
    private long settingsAnimStart, emojiAnimStart, quickAnimStart, searchAnimStart;
    // Popup close animation timestamps (0 = not closing; D07-6)
    private long settingsCloseStart, emojiCloseStart, quickCloseStart, searchCloseStart;
    private String whisperPartner;
    private int sidebarScrollOffset;
    private int sidebarMaxScroll;
    private TextFieldWidget sidebarSearchBox;

    // Real drag selection for TextFieldWidget inputs (vanilla doesn't support mouse-drag selection)
    private net.minecraft.client.gui.widget.TextFieldWidget inputDragTarget;
    private int inputDragAnchor = -1;
    private boolean suppressInputChange;

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
    private boolean mentionNavigated;
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

    // Image cards: parsed once per message (bracket strip + refs), invalidated
    // when ImageLoader flips any entry's state (VERSION bumps).
    private final Map<ChatMessageStore.ChatMessage, BracketCodec.ParseResult> imageParseCache =
        new IdentityHashMap<>();
    private int lastImageVersion = -1;
    private int uploadToastTicks = 0;
    /** Upload-in-progress hint; set while a job is running, cleared on completion. */
    private int uploadBusyTicks = 0;

    private final com.niuqu.chatbubble.image.UploadQueue uploadQueue =
        new com.niuqu.chatbubble.image.UploadQueue(new com.niuqu.chatbubble.image.UploadQueue.Callbacks() {
            @Override public void onBusyStart() { uploadBusyTicks = 60; }
            @Override public void onIdle() { uploadBusyTicks = 0; }
            @Override public void onFailure() { uploadBusyTicks = 0; uploadToastTicks = 60; }
            @Override public void onEmoteSent(String url) { sendMessageText(url); }
            @Override public void onSendText(String text) { sendMessageText(text); }
            @Override public void onInputImage(String code) {
                String cur = chatField.getText();
                if (cur.contains("[[CICode,url=file://")) {
                    cur = cur.replaceFirst("\\[\\[CICode,url=file://[^]]*]]", code);
                } else {
                    cur = cur.isEmpty() ? code : cur + " " + code;
                }
                chatField.setText(cur);
                chatField.setCursorToEnd(false);
            }
            @Override public void onRestoreInput(String text) { chatField.setText(text); }
        });
    private static final int EMOTE_MAX_SIZE = 32;


    private final List<int[]> bubbleRects = new ArrayList<>();
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();
    private final List<TextSpan> textSpans = new ArrayList<>();
    private final ChatTextSelection textSelection = new ChatTextSelection();

    private double selectionStartX;
    private double selectionStartY;

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
        super("");
        this.initialText = initialText;
    }

    @Override
    protected void init() {
        historyPos = client.inGameHud.getChatHud().getMessageHistory().size();
        ChatMessageStore.setScreenOpen(true);
        historyPos = client.inGameHud.getChatHud().getMessageHistory().size();
        animStart = Util.getMeasuringTimeMs();
        closing = false;
        firstRender = true;

        int physicalW = ChatBubbleClientSetup.config().panelWidth();
        double guiScale = client.getWindow().getScaleFactor();
        if (sidebarOpen) {
            panelX = SIDEBAR_W;
            sidebarAnimating = false; // sidebar is already in place; the panel's
            // own open animation (by style) handles its entrance — don't let the
            // sidebar state machine re-drive panelX (slides the whole panel)
        } else {
            panelX = 0;
            sidebarAnimating = false;
            sidebarTargetOpen = false;
        }
        panelW = computePanelWidth(physicalW, guiScale, width, panelX, ChatBubbleClientSetup.config().panelFullscreen());
        titleY = 0;
        msgTop = titleY + TITLE_H + 1;
        barTop = height - BAR_H;
        msgBottom = Math.max(0, barTop - 1);

        int ibY = barTop + (BAR_H - INPUT_H) / 2;
        inputY = ibY;
        inputX = panelX + 4 + ICON_S + 3;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int inputW = sendX - ICON_S - 8 - inputX;

        chatField = new TextFieldWidget(textRenderer, inputX, ibY + 3, inputW, INPUT_H, Text.literal(""));
        chatField.setMaxLength(256);
        chatField.setDrawsBackground(false);
        int editColor = theme() == ChatBubbleTheme.LIGHT ? c().textSecondary() : c().textPrimary();
        chatField.setEditableColor(editColor);
        chatField.setUneditableColor(c().textMuted());
        chatField.setText(initialText.isEmpty() && ChatBubbleClientSetup.config().preserveInput() && !savedInput.isEmpty() ? savedInput : initialText);
        chatField.setChangedListener(this::onInputEdited);
        chatField.setFocusUnlocked(false);
        addDrawableChild(chatField);

        int cmdBgAlpha = theme() == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
        commandSuggestions = new ChatInputSuggestor(client, this, chatField, textRenderer,
            false, false, 0, 8, true, ChatBubbleTheme.alphaBlend(c().panelBg(), cmdBgAlpha));
        commandSuggestions.setWindowActive(true);
        commandSuggestions.refresh();


        sidebarSearchBox = new TextFieldWidget(textRenderer, 2, 5, SIDEBAR_W - 5, SIDEBAR_SEARCH_H, Text.literal(""));
        sidebarSearchBox.setMaxLength(20);
        sidebarSearchBox.setDrawsBackground(false);
        sidebarSearchBox.setEditableColor(editColor);
        sidebarSearchBox.setUneditableColor(editColor);
        sidebarSearchBox.setVisible(sidebarOpen);
        sidebarSearchBox.setChangedListener(s -> sidebarScrollOffset = 0);
        sidebarSearchBox.setFocusUnlocked(true);
        if (sidebarOpen) sidebarSearchBox.setX(2);
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

        setFocused(chatField);
        // The chat field's initial text is set before setChangedListener binds,
        // so the open-time value (e.g. "/" from the chat key) never flows through
        // onInputEdited — sync it once so the IMBlocker IME state is correct.
        onInputEdited(chatField.getText());

        // D07-6: 弹层关闭动画钩子——visible 延迟置 false，先播 150ms 关闭动画
        settingsMenu.closeRequest = () -> beginPopupClose(s -> settingsCloseStart = s,
            () -> settingsMenu.visible = false);
        emojiPanel.closeRequest = () -> beginPopupClose(s -> emojiCloseStart = s,
            () -> emojiPanel.visible = false);
        quickChatPanel.closeRequest = () -> beginPopupClose(s -> quickCloseStart = s, () -> {
            quickChatPanel.visible = false;
            quickChatInput.setVisible(false);
        });
    }

    /**
     * Panel width in logical GUI pixels.
     *
     * <p>{@code panel_width} is a PHYSICAL-pixel size (see config comment): to get
     * the logical width we divide by the exact — possibly fractional — GUI scale.
     * Rounding the scale to an int was the 2.4.3 bug that made the panel's real
     * width drift with the window and misalign on resize. Fullscreen mode ignores
     * {@code panel_width} and fills the remaining width instead. The result is
     * clamped to the remaining width and to a 100px safety floor.
     *
     * <p>2.4.5: in windowed mode the panel is additionally capped at
     * {@link #MAX_WINDOW_FRACTION} of the window width. A fixed physical width
     * otherwise dominates smaller windows (a 1000px panel covered 66% of a
     * 2184px window at GUI scale 5); the fraction is scale-invariant because
     * both sides are logical pixels.
     */
    static int computePanelWidth(int physicalW, double guiScale, int width, int panelX, boolean fullscreen) {
        if (fullscreen) {
            return Math.max(100, width - panelX);
        }
        double s = guiScale > 0.01 ? guiScale : 1.0;
        int w = (int) Math.round(physicalW / s);
        w = Math.min(w, (int) (width * MAX_WINDOW_FRACTION));
        return Math.max(100, Math.min(w, width - panelX));
    }

    private void rebuildLayout() {
        int physicalW = ChatBubbleClientSetup.config().panelWidth();
        double guiScale = client.getWindow().getScaleFactor();
        panelW = computePanelWidth(physicalW, guiScale, width, panelX, ChatBubbleClientSetup.config().panelFullscreen());
        titleY = 0;
        msgTop = titleY + TITLE_H + 1;
        barTop = height - BAR_H;
        msgBottom = Math.max(0, barTop - 1);

        int ibY = barTop + (BAR_H - INPUT_H) / 2;
        inputY = ibY;
        inputX = panelX + 4 + ICON_S + 3;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int inputW = sendX - ICON_S - 8 - inputX;

        if (chatField != null) {
            chatField.setX(inputX);
            chatField.setWidth(inputW);
            chatField.setY(ibY + 3);
        }
    }

    private String getDisplayTitle() {
        if (whisperPartner != null) return whisperPartner;
        return Text.translatable("e33chat.sidebar.public").getString();
    }

    private float getSidebarAnimProgress() {
        if (!ChatBubbleClientSetup.config().animationEnabled()) return sidebarOpen ? 1f : 0f;
        AnimationStyle style = AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle());
        // Hamburger toggle always slides, regardless of the panel animation style
        if (sidebarAnimating) {
            long elapsed = Util.getMeasuringTimeMs() - sidebarAnimStart;
            float t = MathHelper.clamp((float) elapsed / ANIM_MS, 0f, 1f);
            float progress = Animation.styleCurve(AnimationStyle.SLIDE, t);
            return sidebarTargetOpen ? progress : 1.0f - progress;
        }
        // FADE/NONE have no horizontal displacement: the sidebar fades in place.
        if (style == AnimationStyle.FADE || style == AnimationStyle.NONE) return sidebarOpen ? 1f : 0f;
        if (!sidebarOpen) return 0f;
        return getAnimProgress(); // follow the panel's open animation
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
            if (!sidebarOpen && sidebarSearchBox.isFocused()) setFocused(chatField);
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

    private void renderSidebar(DrawContext g, int mouseX, int mouseY, float alpha) {
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_BG), 0, 0, SIDEBAR_W, height, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), SIDEBAR_W - 1, 0, 1, height, alpha);

        int y = 2;
        int itemH = SIDEBAR_ITEM_H;

        int sbx = 2;
        int sby = 2;
        int sbw = SIDEBAR_W - 5;
        int sbh = SIDEBAR_SEARCH_H;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.INPUT_BG), sbx - 1, sby, sbw + 1, sbh, alpha);
        boolean hoverSearch = mouseX >= sbx - 1 && mouseX <= sbx + sbw && mouseY >= sby && mouseY <= sby + sbh;
        if (hoverSearch || sidebarSearchBox.isFocused())
            g.drawBorder(sbx - 1, sby, sbw + 1, sbh, c().textMuted());
        if (sidebarSearchBox.getText().isEmpty() && !sidebarSearchBox.isFocused()) {
            g.drawText(textRenderer, Text.translatable("e33chat.sidebar.search").getString(), sbx, sby + 3, c().textMuted(), false);
        }
        y = sby + sbh + 3;

        boolean isPublic = whisperPartner == null;
        boolean hoverTab = mouseX >= 0 && mouseX <= SIDEBAR_W && mouseY >= y && mouseY <= y + itemH;
        if (isPublic)
            ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_SELECTED), 0, y, SIDEBAR_W, itemH, alpha);
        else if (hoverTab)
            ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_HOVER), 0, y, SIDEBAR_W, itemH, alpha);
        drawTextureIconAlpha(g, iconTex("public_icon"), 2, y + 1, SIDEBAR_ICON_S, alpha);
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
                drawTextureIconAlpha(g, iconTex("no_online"), (SIDEBAR_W - iconS) / 2, startY + 8, iconS, alpha);
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
                        boolean hoverRow = mouseX >= 0 && mouseX <= SIDEBAR_W && mouseY >= scrollY && mouseY <= scrollY + itemH;
                        if (sel)
                            ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_SELECTED), 0, scrollY, SIDEBAR_W, itemH, alpha);
                        else if (hoverRow)
                            ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_HOVER), 0, scrollY, SIDEBAR_W, itemH, alpha);

                        Identifier skin = com.niuqu.chatbubble.render.SkinResolver.getSkin(info.getProfile().getId(), info.getProfile().getName());
                        drawPlayerHead(g, skin, 4, scrollY + 3, 16, 18, alpha);

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
                            drawTextureIconAlpha(g, iconTex("private_tip"), tipX, tipY, 16, alpha);
                        }
                    }
                    scrollY += itemH + 2;
                }
                g.disableScissor();
            }
        }
    }

    private void insertMention(String name) {
        String text = chatField.getText();
        int atIdx = text.lastIndexOf('@');
        chatField.setText(text.substring(0, atIdx) + "@" + name + " ");
        chatField.setCursorToEnd(false);
        showMentions = false;
        mentionNavigated = false;
    }

    private void onInputEdited(String text) {
        if (suppressInputChange) return;
        // ModernUI hooks vanilla ChatScreen.onEdited; E33Chat installs its own
        // responder, so mirror the shortcode transformation here when ModernUI
        // is installed and has the feature enabled.
        if (!emojiReplacing && ModernUIEmojiCompat.isEnabled() && !text.startsWith("/")) {
            emojiReplacing = true;
            try {
                if (ModernUIEmojiCompat.replaceIn(chatField)) {
                    return; // reentrant onInputEdited already did post-processing
                }
            } finally {
                emojiReplacing = false;
            }
        }
        showMentions = false;
        mentionNavigated = false;
        int atIdx = text.lastIndexOf('@');
        // Commands use vanilla selectors (@s/@p/...) instead of player names:
        // do not offer player-name completion inside a command.
        if (atIdx >= 0 && !text.startsWith("/") && client.player != null && client.player.networkHandler != null) {
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
        // IMBlocker listens to vanilla ChatScreen.onChatFieldUpdate, which we
        // bypass; mirror its command-detection hook so the IME still switches
        // to English while typing a command. No-op when IMBlocker is absent.
        IMBlockerCompat.setCommandMode(chatField, text.startsWith("/"));
    }

    private void onSearchEdited(String text) {
        if (suppressInputChange) return;
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
        if (uploadToastTicks > 0) uploadToastTicks--;
        finishPopupClose(settingsCloseStart, () -> { settingsCloseStart = 0; settingsMenu.visible = false; });
        finishPopupClose(emojiCloseStart, () -> { emojiCloseStart = 0; emojiPanel.visible = false; });
        finishPopupClose(quickCloseStart, () -> {
            quickCloseStart = 0;
            quickChatPanel.visible = false;
            quickChatInput.setVisible(false);
        });
        finishPopupClose(searchCloseStart, () -> {
            searchCloseStart = 0;
            searchPanel.visible = false;
            searchInput.setVisible(false);
        });
        if (closing && Util.getMeasuringTimeMs() - animStart >= ANIM_MS)
            client.setScreen(null);
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
        // no-op: disable vanilla blur
    }

    private float getAnimProgress() {
        if (!ChatBubbleClientSetup.config().animationEnabled()) return 1.0f;
        AnimationStyle style = AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle());
        if (style == AnimationStyle.NONE) return 1.0f;
        long elapsed = Util.getMeasuringTimeMs() - animStart;
        float t = MathHelper.clamp((float) elapsed / ANIM_MS, 0f, 1f);
        if (closing) return 1.0f - (t * t);
        return Animation.styleCurve(style, t);
    }

    // 上下栏背景透明度：跟面板开合动画同步（150ms），但用线性曲线——
    // easeOutCubic 前 75ms 就到 87% 不透明，观感=瞬间出现（2.3.13 用户反馈）。
    private float getBarAlpha() {
        if (!ChatBubbleClientSetup.config().animationEnabled()) return 1.0f;
        AnimationStyle style = AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle());
        if (style == AnimationStyle.NONE) return 1.0f;
        long elapsed = Util.getMeasuringTimeMs() - animStart;
        float t = MathHelper.clamp((float) elapsed / ANIM_MS, 0f, 1f);
        if (closing) return 1.0f - t;
        return t;
    }

    // Popup open animation (opening only — closing stays instant). The panel
    // renders itself with the given alpha (per-element fade); ZOOM additionally
    // scales it in around the screen center with overshoot.
    // Popup open/close animation (D07-6: closing is no longer instant).
    // Popup open/close animation (D07-6: closing is no longer instant; 2.4.5:
    // close replays the open curve in reverse). Open 200ms, close 150ms.
    private void renderPopupWithAnim(DrawContext g, long openStartMs, long closeStartMs,
                                     java.util.function.Function<Float, Runnable> renderer) {
        AnimationStyle style = AnimationStyle.parse(ChatBubbleClientSetup.config().popupAnimStyle());
        float alpha;
        boolean animating;
        if (closeStartMs > 0) {
            float tc = MathHelper.clamp((float) (Util.getMeasuringTimeMs() - closeStartMs) / UiTokens.POPUP_CLOSE_MS, 0f, 1f);
            alpha = Animation.styleCurve(style, 1f - tc);
            animating = tc < 1f;
        } else if (ChatBubbleClientSetup.config().animationEnabled() && style != AnimationStyle.NONE) {
            float t = MathHelper.clamp((float) (Util.getMeasuringTimeMs() - openStartMs) / UiTokens.POPUP_OPEN_MS, 0f, 1f);
            alpha = Animation.styleCurve(style, t);
            animating = t < 1f;
        } else {
            alpha = 1f;
            animating = false;
        }
        // Vanilla Font.adjustColor snaps any color with alpha <= 3 back to fully
        // opaque — below this threshold text/emoji flashed back on the last fade
        // frame while the panel (SDF shader, float alpha) faded correctly.
        if (alpha <= 0.02f) return;
        Runnable render = renderer.apply(alpha);
        if (!animating) { render.run(); return; }
        if (style == AnimationStyle.ZOOM) {
            g.getMatrices().push();
            float s = 0.85f + 0.15f * Animation.easeOutBack(alpha);
            g.getMatrices().translate(width / 2f, height / 2f, 0);
            g.getMatrices().scale(s, s, 1f);
            g.getMatrices().translate(-width / 2f, -height / 2f, 0);
            render.run();
            g.getMatrices().pop();
        } else if (style == AnimationStyle.SLIDE) {
            // SLIDE: rise up from below while fading in; close sinks back down
            g.getMatrices().push();
            g.getMatrices().translate(0, (1f - alpha) * 10f, 0);
            render.run();
            g.getMatrices().pop();
        } else {
            render.run();
        }
    }

    /** 开始弹层关闭动画（D07-6）：动画关/风格 NONE 时立即隐藏，否则 150ms 后由 tick 隐藏。 */
    private void beginPopupClose(java.util.function.LongConsumer setCloseStart, Runnable hide) {
        if (!ChatBubbleClientSetup.config().animationEnabled()
                || AnimationStyle.parse(ChatBubbleClientSetup.config().popupAnimStyle()) == AnimationStyle.NONE) {
            setCloseStart.accept(0);
            hide.run();
            return;
        }
        setCloseStart.accept(Util.getMeasuringTimeMs());
    }

    /** tick 调用：关闭动画到期后真正隐藏（D07-6）。 */
    private void finishPopupClose(long closeStart, Runnable hide) {
        if (closeStart > 0 && Util.getMeasuringTimeMs() - closeStart >= UiTokens.POPUP_CLOSE_MS) {
            hide.run();
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_C && (modifiers & 0x2) != 0
            && textSelection.hasSelection()) {
            String copied = textSelection.copyText(textSpans);
            if (!copied.isEmpty()) {
                client.keyboard.setClipboard(copied);
                copyToastTicks = 30;
            }
            return true;
        }
        // Ctrl+V with an image in the clipboard uploads it and inserts the code;
        // on the custom-emote tab it adds the image to the emote pack instead.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V && (modifiers & 0x2) != 0) {
            // Text paste must stay on the text field path. Only try the AWT image
            // path when the GLFW clipboard has no text, so Ctrl+V text paste is
            // never delayed or disturbed by the background clipboard probe.
            boolean hasText = client.keyboard.getClipboard() != null
                && !client.keyboard.getClipboard().isEmpty();
            if (!hasText && emojiPanel.visible && emojiPanel.tab == 2) {
                addClipboardEmote();
            } else if (!hasText) {
                startUploadFromClipboard();
            }
        }
        if (settingsMenu.visible && keyCode == 256) {
            beginPopupClose(s -> settingsCloseStart = s, () -> settingsMenu.visible = false);
            return true;
        }
        if (emojiPanel.visible && keyCode == 256) {
            beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
            return true;
        }
        if (quickChatPanel.visible && keyCode == 256) {
            beginPopupClose(s -> quickCloseStart = s, () -> {
                quickChatPanel.visible = false;
                quickChatInput.setVisible(false);
            });
            setFocused(chatField);
            return true;
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
                sidebarSearchBox.setFocused(false); setFocused(chatField); return true;
            }
        }

        if (showMentions) {
            if (keyCode == 258) { insertMention(mentionCandidates.get(mentionIdx)); return true; }
            if (keyCode == 256) { showMentions = false; mentionNavigated = false; return true; }
            if (keyCode == 265) { mentionIdx = mentionIdx > 0 ? mentionIdx - 1 : mentionCandidates.size() - 1; mentionNavigated = true; return true; }
            if (keyCode == 264) { mentionIdx = mentionIdx < mentionCandidates.size() - 1 ? mentionIdx + 1 : 0; mentionNavigated = true; return true; }
            if (keyCode == 257 || keyCode == 335) {
                // Only apply the highlighted candidate when the player actually
                // navigated it (arrow keys); otherwise Enter just sends the text.
                if (mentionNavigated) { insertMention(mentionCandidates.get(mentionIdx)); return true; }
            }
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
        if (keyCode == 265 && this.getFocused() == chatField) { setChatFromHistory(-1); return true; }
        if (keyCode == 264 && this.getFocused() == chatField) { setChatFromHistory(1); return true; }

        // 不调 super.keyPressed（= ChatScreen，内部访问 package-private chatInputSuggestor = null → NPE）。
        // self 实现 Screen.keyPressed 等价分发：先给 focused widget（chatField TextFieldWidget 处理
        // backspace/删除/左右/Home/End/Ctrl+A/C/V/X），再 Tab/箭头焦点导航。
        if (this.getFocused() != null && this.getFocused().keyPressed(keyCode, scanCode, modifiers))
            return true;
        net.minecraft.client.gui.navigation.GuiNavigation nav = switch (keyCode) {
            case 258 -> new net.minecraft.client.gui.navigation.GuiNavigation.Tab(!Screen.hasShiftDown());
            case 262 -> new net.minecraft.client.gui.navigation.GuiNavigation.Arrow(net.minecraft.client.gui.navigation.NavigationDirection.RIGHT);
            case 263 -> new net.minecraft.client.gui.navigation.GuiNavigation.Arrow(net.minecraft.client.gui.navigation.NavigationDirection.LEFT);
            case 264 -> new net.minecraft.client.gui.navigation.GuiNavigation.Arrow(net.minecraft.client.gui.navigation.NavigationDirection.DOWN);
            case 265 -> new net.minecraft.client.gui.navigation.GuiNavigation.Arrow(net.minecraft.client.gui.navigation.NavigationDirection.UP);
            default -> null;
        };
        if (nav != null) {
            net.minecraft.client.gui.navigation.GuiNavigationPath path = super.getNavigationPath(nav);
            if (path == null && nav instanceof net.minecraft.client.gui.navigation.GuiNavigation.Tab) {
                this.blur();
                path = super.getNavigationPath(nav);
            }
            if (path != null) this.switchFocus(path);
        }
        return false;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
        if (scrollY != 0 && textSelection.hasSelection()) {
            textSelection.clear();
        }
        if (emojiPanel.visible) { emojiPanel.handleScroll(scrollY); return true; }
        if (quickChatPanel.visible) { quickChatPanel.handleScroll(scrollY); return true; }
        if (searchPanel.visible && !searchMatches.isEmpty()) {
            searchMatchIdx = MathHelper.clamp(searchMatchIdx - (int) scrollY, 0, searchMatches.size() - 1);
            searchHighlightIndex = searchMatches.get(searchMatchIdx);
            jumpToMessage(searchHighlightIndex); return true;
        }
        if (showMentions && !mentionCandidates.isEmpty()) {
            mentionIdx = MathHelper.clamp(mentionIdx - (int) scrollY, 0, mentionCandidates.size() - 1);
            mentionNavigated = true;
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
            int popupX = chatField.getX();
            int popupH = Math.min(mentionCandidates.size(), 8) * textRenderer.fontHeight + 4;
            int popupY = chatField.getY() - popupH - 2;
            if (popupY < msgTop) popupY = chatField.getY() + chatField.getHeight() + 2;
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
                boolean handled = sidebarSearchBox.mouseClicked(origX, mouseY, button);
                setFocused(sidebarSearchBox); chatField.setFocused(false);
                if (handled && button == 0) {
                    setDragging(true);
                    inputDragTarget = sidebarSearchBox;
                    inputDragAnchor = inputDragTarget.getCursor();
                }
                return true;
            }
            if (sidebarSearchBox.isFocused()) setFocused(chatField);

            int y2 = searchY + searchH + 3;
            if (mouseY >= y2 && mouseY <= y2 + SIDEBAR_ITEM_H) {
                whisperPartner = null; sidebarSearchBox.setText(""); setFocused(chatField); scrollToBottom = true; return true;
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
                        sidebarSearchBox.setText(""); setFocused(chatField); scrollToBottom = true; return true;
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
            if (textSelection.hasSelection()) textSelection.clear();
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
                    if (!sidebarOpen && sidebarSearchBox.isFocused()) setFocused(chatField);
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
                    if (emojiText.startsWith("@EMOTE:")) {
                        java.io.File f = new java.io.File(emojiText.substring(7));
                        if (f.isFile()) {
                            beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
                            uploadQueue.enqueue(new com.niuqu.chatbubble.image.UploadQueue.UploadJob(f, null, null, true, null));
                        }
                    } else if (emojiText.startsWith("@EMOTE_DEL:")) {
                        java.io.File f = new java.io.File(emojiText.substring(11));
                        if (f.isFile()) EmoteStore.remove(f);
                    } else if (emojiText.equals("@EMOTE_ADD")) {
                        NativeFileDialog.pickImage(f -> {
                            if (f == null || !f.isFile()) return;
                            if (EmoteStore.isFull()) return;
                            EmoteStore.add(f);
                        });
                    } else {
                        chatField.write(emojiText);
                    }
                }
                return true;
            }
            if (quickChatPanel.visible) {
                // 输入框聚焦不依赖 widget 点击命中链路（1.21.1/yarn TextFieldWidget 点击不自动聚焦）：
                // 直接几何判定命中就聚焦，覆盖所有情况
                if (ChatQuickChatPanel.isInsideInput((int) mouseX, (int) mouseY, panelX, panelW, barTop,
                        ChatBubbleClientSetup.config().quickChatPhrases().size())) {
                    // 与 sidebar 搜索框聚焦同款（Fabric 实测需显式失焦主输入框，否则焦点链被 chatField 占用）
                    quickChatInput.setVisible(true);
                    setFocused(quickChatInput);
                    chatField.setFocused(false);
                    boolean handled = quickChatInput.mouseClicked(mouseX, mouseY, button);
                    if (handled && button == 0) {
                        setDragging(true);
                        inputDragTarget = quickChatInput;
                        inputDragAnchor = inputDragTarget.getCursor();
                    }
                    return true;
                }
                int result = quickChatPanel.handleClick((int) mouseX, (int) mouseY, textRenderer, c(), panelX, panelW, barTop, quickChatInput);
                if (result >= 0) {
                    chatField.setText(ChatBubbleClientSetup.config().quickChatPhrases().get(result));
                    setFocused(chatField);
                } else if (result == -2) {
                    setFocused(quickChatInput);
                }
                return true;
            }
            if (searchPanel.visible) {
                if (searchPanel.isClickOnPanel((int) mouseX, (int) mouseY, panelX, panelW, barTop)) {
                    boolean handled = searchInput.mouseClicked(mouseX, mouseY, button);
                    setFocused(searchInput);
                    if (handled && button == 0) {
                        setDragging(true);
                        inputDragTarget = searchInput;
                        inputDragAnchor = inputDragTarget.getCursor();
                    }
                    return true;
                }
                closeSearchPanel(); return true;
            }
            if (mouseY >= barTop) {
                if (handleIconClick((int) mouseX, (int) mouseY)) return true;
            }
        }

        // Text selection: a drag selects text; a simple click on text starts a
        // selection and is deferred to mouseReleased so the old immediate
        // clickable-style handling only remains for non-text spans (images/emotes).
        if (button == 0) {
            TextSpan hit = findTextSpanAt(mouseX, mouseY);
            if (hit != null) {
                if (textSelection.hasSelection()) textSelection.clear();
                textSelection.begin(hit.messageIndex(), hit.lineIndex(), hit.kind(),
                    charAt(hit, mouseX));
                selectionStartX = mouseX;
                selectionStartY = mouseY;
                return true;
            }
            if (textSelection.hasSelection() || textSelection.isDragActive()) {
                textSelection.clear();
            }
        }

        // Clickable text
        if (button == 0) {
            Style style = getHoveredStyle(mouseX, mouseY);
            if (style != null && style.getClickEvent() != null) {
                ClickEvent click = style.getClickEvent();
                if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                    chatField.setText(click.getValue()); return true;
                }
                if (click.getAction() == ClickEvent.Action.OPEN_FILE) {
                    java.io.File file = new java.io.File(click.getValue());
                    Util.getOperatingSystem().open(file); return true;
                }
                if (click.getAction() == ClickEvent.Action.OPEN_URL) {
                    // Local file:// links (e.g. legacy chatimage messages) are not
                    // browser URLs; opening them throws URISyntaxException. Only
                    // hand http(s) to the vanilla handler.
                    String clickUrl = click.getValue();
                    if (clickUrl != null && (clickUrl.startsWith("http://") || clickUrl.startsWith("https://"))) {
                        handleTextClick(style);
                    }
                    return true;
                }
                handleTextClick(style); return true;
            }
        }

        // Avatar click for @mention
        if (button == 0) {
            for (int[] r : bubbleRects) {
                ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(r[4]);
                if (msg == null || msg.isSystem()) continue;
                int avatarX = msg.isOwn() ? r[0] + r[2] + 4 : r[0] - Appearance.avatarSize() - 4;
                int avatarY = msg.replyContent() != null ? r[1] - textRenderer.fontHeight - 2 : r[1] - NAME_H;
                if (mouseX >= avatarX && mouseX <= avatarX + Appearance.avatarSize()
                    && mouseY >= avatarY && mouseY <= avatarY + Appearance.avatarSize()) {
                    String mentionName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
                        ? msg.rawPlayerName() : msg.senderName().getString();
                    chatField.setText(chatField.getText() + "@" + mentionName + " ");
                    chatField.setCursorToEnd(false);
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
                int avatarX = r[0] - Appearance.avatarSize() - 4;
                int avatarY = msg.replyContent() != null ? r[1] - textRenderer.fontHeight - 2 : r[1] - NAME_H;
                if (mouseX >= avatarX && mouseX <= avatarX + Appearance.avatarSize()
                    && mouseY >= avatarY && mouseY <= avatarY + Appearance.avatarSize()) {
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

        boolean chatHandled = this.chatField.mouseClicked(origX, mouseY, button);
        if (chatHandled) {
            setFocused(this.chatField);
            // We bypass Screen.mouseClicked -> super.mouseClicked, so the container
            // drag state is never set automatically. Without it, mouseDragged won't
            // reach the text field and selection (needed for Ctrl+C) is broken.
            if (button == 0) {
                setDragging(true);
                inputDragTarget = this.chatField;
                inputDragAnchor = inputDragTarget.getCursor();
            }
        }
        return chatHandled;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
        if (textSelection.isDragActive()) {
            double mx = mouseX;
            if (isPanelSliding()) mx -= currentPanelOffset();
            TextSpan hit = findTextSpanAt(mx, mouseY);
            if (hit == null) {
                textSelection.markMoved();
                hit = findNearestTextSpan(mx, mouseY);
            } else if (Math.abs(mx - selectionStartX) + Math.abs(mouseY - selectionStartY) > 3.0) {
                textSelection.markMoved();
            }
            if (hit != null) {
                textSelection.update(hit.messageIndex(), hit.lineIndex(), hit.kind(), charAt(hit, mx));
            }
            autoScrollSelection(mouseY);
            return true;
        }
        if (scrollbarDragging && maxScroll > 0) {
            if (textSelection.hasSelection()) textSelection.clear();
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
        if (inputDragTarget != null && button == 0) {
            double mx = mouseX;
            if ((inputDragTarget == quickChatInput || inputDragTarget == searchInput)
                && isPanelSliding()) {
                mx -= currentPanelOffset();
            }
            suppressInputChange = true;
            try {
                inputDragTarget.onClick(mx, mouseY);
                inputDragTarget.setSelectionEnd(inputDragAnchor);
            } finally {
                suppressInputChange = false;
            }
            return true;
        }
        return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        if (textSelection.isDragActive()) {
            textSelection.endDrag();
            if (!textSelection.didMove()) {
                double mx = mouseX;
                if (isPanelSliding()) mx -= currentPanelOffset();
                executeClickAction(mx, mouseY);
                textSelection.clear();
            }
            return true;
        }
        if (inputDragTarget != null) {
            inputDragTarget = null;
            inputDragAnchor = -1;
        }
        if (scrollbarDragging) { scrollbarDragging = false; return true; }
        return super.mouseReleased(mouseX, mouseY, button);
    }

    private boolean handleIconClick(int mx, int my) {
        int iconY = barTop + (BAR_H - ICON_S) / 2;
        int gearX = panelX + 4;
        if (mx >= gearX && mx <= gearX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (emojiPanel.visible) beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
            if (searchPanel.visible) closeSearchPanel();
            boolean opening = !settingsMenu.visible;
            if (opening) {
                settingsMenu.visible = true;
                settingsAnimStart = Util.getMeasuringTimeMs();
            } else {
                beginPopupClose(s -> settingsCloseStart = s, () -> settingsMenu.visible = false);
            }
            return true;
        }
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;
        if (mx >= emojiX && mx <= emojiX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (settingsMenu.visible) beginPopupClose(s -> settingsCloseStart = s, () -> settingsMenu.visible = false);
            if (searchPanel.visible) closeSearchPanel();
            boolean opening = !emojiPanel.visible;
            if (opening) {
                emojiPanel.visible = true;
                EmoteStore.refresh();
                emojiAnimStart = Util.getMeasuringTimeMs();
                showMentions = false;
                emojiPanel.scroll = 0;
            } else {
                beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
            }
            return true;
        }
        if (mx >= sendX && mx <= sendX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            sendMessage(); return true;
        }
        return false;
    }


    // ---- Local image upload (2.3.11) ----

    /** OS file drag onto the window (vanilla drop hook): upload the first image dropped. */
    @Override
    public void filesDragged(List<java.nio.file.Path> paths) {
        com.mojang.logging.LogUtils.getLogger().info("[e33chat] filesDrop {} paths | emojiTab={}",
            paths.size(), emojiPanel.visible && emojiPanel.tab == 2);
        // Emote tab open: dropping adds to the pack instead of uploading.
        if (emojiPanel.visible && emojiPanel.tab == 2) {
            for (java.nio.file.Path p : paths) {
                java.io.File f = p.toFile();
                if (f.isFile() && EmoteStore.isImageFile(f)) {
                    EmoteStore.add(f);
                    break;
                }
            }
            return;
        }
        for (java.nio.file.Path p : paths) {
            String l = p.getFileName().toString().toLowerCase();
            if (l.endsWith(".png") || l.endsWith(".jpg") || l.endsWith(".jpeg")
                    || l.endsWith(".gif") || l.endsWith(".bmp")) {
                uploadQueue.enqueue(new com.niuqu.chatbubble.image.UploadQueue.UploadJob(p.toFile(), null, null, false, null));
                // The OS drop can steal window focus; give it back to the chat input
                // so typing keeps working right after a drag.
                client.execute(() -> setFocused(chatField));
                return;
            }
        }
    }

    private void addClipboardEmote() {
        ImageLoader.executor().execute(() -> {
            LocalImageSource.PreparedImage prep = readClipboard();
            if (prep == null) return; // no image in clipboard
            client.execute(() -> EmoteStore.addBytes(prep.bytes(), "paste_" + System.currentTimeMillis() + ".png"));
        });
    }

    private void startUploadFromClipboard() {
        ImageLoader.executor().execute(() -> {
            LocalImageSource.PreparedImage prep = readClipboard();
            if (prep == null) return; // no image in clipboard — let vanilla paste text
            client.execute(() -> uploadQueue.enqueue(new com.niuqu.chatbubble.image.UploadQueue.UploadJob(null, prep.bytes(), "clipboard", false, null)));
        });
    }

    private static LocalImageSource.PreparedImage readClipboard() {
        try {
            return LocalImageSource.fromClipboard();
        } catch (Throwable t) {
            return null;
        }
    }



    /** Runs queued uploads one at a time; the completion callback in
     * finishUpload calls this again for the next job. */




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
        int menuH = CTX_ITEM_H * 3 + 4;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;
        if (mx >= menuX && mx <= menuX + CTX_W) {
            ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
            String name = msg != null ? msg.rawPlayerName() : null;
            if (name == null || name.isEmpty()) { contextAvatarIndex = -1; return; }
            if (my >= menuY && my <= menuY + CTX_ITEM_H) {
                client.player.networkHandler.sendChatCommand((ChatMessageStore.useTpa() ? "tpa " : "tp ") + name);
            } else if (my >= menuY + CTX_ITEM_H + 2 && my <= menuY + CTX_ITEM_H * 2 + 2) {
                whisperPartner = name;
                ChatMessageStore.clearUnreadWhisper(name);
                if (sidebarSearchBox != null) sidebarSearchBox.setText("");
                setFocused(chatField); scrollToBottom = true;
            } else if (my >= menuY + CTX_ITEM_H * 2 + 4 && my <= menuY + menuH) {
                toggleBlockedPlayer();
            }
        }
        contextAvatarIndex = -1;
    }

    // 屏蔽/取消屏蔽右键菜单目标玩家：名单即时生效 + 从消息列表清掉历史 + 立即写盘
    private void toggleBlockedPlayer() {
        ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
        if (msg == null) return;
        String name = msg.rawPlayerName();
        if (name == null || name.isEmpty()) {
            name = msg.senderName() != null ? msg.senderName().getString() : null;
        }
        if (name == null || name.isEmpty()) return;
        final String target = name;

        List<String> blocked = new ArrayList<>(ChatBubbleClientSetup.config().blockedPlayers());
        boolean nowBlocked = BlockList.isPlayerBlocked(
            msg.rawPlayerName(), msg.senderName(), blocked);
        if (nowBlocked) {
            blocked.removeIf(b -> b != null && b.trim().equalsIgnoreCase(target));
        } else {
            blocked.add(target.trim());
        }
        ChatBubbleClientSetup.saveConfig(ChatBubbleClientSetup.config().withBlockedPlayers(blocked));
        ChatMessageStore.purgeBlocked(blocked);
        ChatMessageStore.debugLog(() -> "[e33chat] Block list updated | name='" + target + "' | blocked=" + nowBlocked);
    }

    @Override
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
        tickSidebarAnimation();

        float anim = getAnimProgress();
        AnimationStyle pstyle = AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle());
        int panelOffset = (pstyle == AnimationStyle.SLIDE) ? currentPanelOffset() : 0;
        boolean zoom = (pstyle == AnimationStyle.ZOOM) && anim < 1f;
        float panelScale = 1f;
        if (zoom) panelScale = 0.8f + 0.2f * Animation.easeOutBack(anim);

        g.getMatrices().push();
        g.getMatrices().translate(panelOffset, 0, 0);
        if (zoom) {
            float cx = panelX + panelW / 2f;
            g.getMatrices().translate(cx, height / 2f, 0);
            g.getMatrices().scale(panelScale, panelScale, 1f);
            g.getMatrices().translate(-cx, -height / 2f, 0);
        }

        float panelOpacity = ChatBubbleClientSetup.config().panelOpacity() / 100f * anim;
        // When sidebar is synced to main animation, extend panel bg to
        // sidebar's right edge so there's no gap between them. Only SLIDE
        // moves horizontally (the panel slides in); FADE/ZOOM keep the bg
        // in place and fade/scale it in place instead.
        int fillLeft = (!sidebarAnimating && sidebarOpen && pstyle == AnimationStyle.SLIDE)
            ? (int)(anim * SIDEBAR_W) : panelX;
        if (ChatBubbleClientSetup.config().blurEnabled() && panelOpacity < 0.999f && !zoom) {
            g.draw();
            BlurRenderer.blurPanel(panelOffset + fillLeft, 0, panelX + panelW - fillLeft, height);
        }
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.PANEL_BG),
            fillLeft, 0, panelX + panelW - fillLeft, height, panelOpacity);

        // 上下栏背景只跟开合动画（fade 终点 1.0 不透明），不乘 PANEL_OPACITY（2.3.7 起永久半透明回归）
        renderTitleBar(g, mouseX, mouseY, getBarAlpha());
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
        renderBottomBar(g, mouseX, mouseY, getBarAlpha());
        renderMentionPopup(g, mouseX, mouseY);
        // 弹层面板（设置/表情/快捷/搜索）画在底栏之上，z 高一层——侧边栏同 z 后画
        // 会盖住它们，提升弹层 z 到侧边栏之上避免遮挡
        g.getMatrices().push();
        g.getMatrices().translate(0, 0, 100);
        renderPopupWithAnim(g, settingsAnimStart, settingsCloseStart, a -> () -> settingsMenu.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, ChatBubbleScreen::iconTex, a));
        renderPopupWithAnim(g, emojiAnimStart, emojiCloseStart, a -> () -> emojiPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, ICON_S, PAD, a));
        renderPopupWithAnim(g, quickAnimStart, quickCloseStart, a -> () -> quickChatPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, quickChatInput, a));
        renderPopupWithAnim(g, searchAnimStart, searchCloseStart, a -> () -> searchPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, searchInput, searchMatches, searchMatchIdx, a));
        // 输入框 widget 在 z=50 的 children 循环渲染，会被这里 z=100 的不透明面板背景盖住
        // （5bb740e 弹层 z 提升引入）——面板打开时在同 z 重画一次，文字/光标才可见。
        // widget 无背景（drawsBackground=false），只画文字/光标，不遮挡面板内容
        if (quickChatPanel.visible && quickChatInput != null) quickChatInput.render(g, mouseX, mouseY, delta);
        if (searchPanel.visible && searchInput != null) searchInput.render(g, mouseX, mouseY, delta);
        g.getMatrices().pop();

        g.getMatrices().pop();

        if (sidebarOpen || sidebarAnimating) {
            g.getMatrices().push();
            // ZOOM: the sidebar scales with the panel around the panel center
            if (zoom) {
                float cx = panelX + panelW / 2f;
                g.getMatrices().translate(cx, height / 2f, 0);
                g.getMatrices().scale(panelScale, panelScale, 1f);
                g.getMatrices().translate(-cx, -height / 2f, 0);
            }
            // Fade/zoom-in-place applies only to the panel's own open/close
            // animation; the hamburger toggle always slides.
            boolean fadeSidebar = !sidebarAnimating && (pstyle == AnimationStyle.FADE || zoom);
            int sidebarOffset = (closing && !fadeSidebar)
                ? (int) ((getAnimProgress() - 1.0f) * SIDEBAR_W)
                : (fadeSidebar ? 0 : getSidebarScreenX());
            g.getMatrices().translate(sidebarOffset, 0, 50);
            // Per-element alpha (vanilla drawTexture ignores setShaderColor; the
            // sidebar fades its own textures through the alpha path)
            renderSidebar(g, mouseX - sidebarOffset, mouseY, fadeSidebar ? getAnimProgress() : 1f);
            g.getMatrices().pop();
            if (closing) sidebarSearchBox.setX(2 + sidebarOffset);
        }

        g.getMatrices().push();
        g.getMatrices().translate(0, 0, 50);
        chatField.setX(inputX + panelOffset);
        // 不调 super.render（ChatScreen.render 访问 package-private chatInputSuggestor，
        // 跨包无法初始化）；复制 Screen.render 的 widgets 遍历渲染
        for (net.minecraft.client.gui.Element w : this.children()) {
            if (w instanceof net.minecraft.client.gui.Drawable d) d.render(g, mouseX, mouseY, delta);
        }
        // 建议框定位基于 chatField.getScreenX()（屏幕坐标），与 input 同坐标空间渲染
        g.enableScissor(panelX, 0, panelX + panelW, height);
        if (commandSuggestions != null) commandSuggestions.render(g, mouseX, mouseY);
        g.disableScissor();
        g.getMatrices().pop();

        // Notification banner is rendered by ChatBubbleHudOverlay at z=300
    }

    private void renderTitleBar(DrawContext g, int mouseX, int mouseY, float panelAlpha) {
        int ty = titleY;
        int a255 = (int) (255 * panelAlpha);
        // Content (icons/text) alpha follows only the open/close animation —
        // panelOpacity must not tint it (2.3.7 regression: permanent 80%
        // opacity made icons/text lighter on light themes).
        int c255 = (int) (255 * getAnimProgress());
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.TITLE_BAR), panelX, ty, panelW, TITLE_H, panelAlpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), panelX, ty + TITLE_H, panelW, 1, panelAlpha);

        int menuX = panelX + 3;
        int menuY = ty + (TITLE_H - ICON_S) / 2;
        boolean hoverMenu = mouseX >= menuX && mouseX <= menuX + ICON_S && mouseY >= menuY && mouseY <= menuY + ICON_S;
        if (hoverMenu) ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.HOVER_BG), menuX - 1, menuY - 1, ICON_S + 2, ICON_S + 2, panelAlpha);
        drawTextureIconAlpha(g, iconTex("menu"), menuX, menuY, ICON_S, getAnimProgress());

        String title = getDisplayTitle();
        int titleW = textRenderer.getWidth(title);
        int titleX = UiLayout.centerX(panelX, panelW, titleW);
        int titleTextY = ty + (TITLE_H - textRenderer.fontHeight) / 2;
        g.drawText(textRenderer, title, titleX, titleTextY, ChatBubbleTheme.alphaBlend(c().textPrimary(), c255), false);

        String time = LocalTime.now().format(TIME_FMT);
        int timeW = textRenderer.getWidth(time);
        g.drawText(textRenderer, time,
            panelX + panelW - PAD - 20 - timeW, ty + (TITLE_H - textRenderer.fontHeight) / 2, ChatBubbleTheme.alphaBlend(c().timeColor(), c255), false);

        int closeX = panelX + panelW - 18;
        int closeY = ty + 6;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverClose ? UiElement.CLOSE_HOVER : UiElement.CLOSE_BG),
            closeX, closeY, 12, 12, panelAlpha);
        g.drawText(textRenderer, "✕", closeX + 6 - textRenderer.getWidth("✕") / 2, closeY + 2, ChatBubbleTheme.alphaBlend(c().closeText(), c255), false);
    }

    private boolean isMouseOverHamburger(double mx, double my) {
        int menuX = panelX + 3;
        int menuY = titleY + (TITLE_H - ICON_S) / 2;
        return mx >= menuX && mx <= menuX + ICON_S && my >= menuY && my <= menuY + ICON_S;
    }

    private void renderMessages(DrawContext g, int mouseX, int mouseY) {
        msgHeightCache.clear();
        int imgVersion = ImageLoader.version();
        if (imgVersion != lastImageVersion) {
            lastImageVersion = imgVersion;
            imageParseCache.clear();
        }
        bubbleRects.clear();
        clickableSpans.clear();
        textSpans.clear();
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
            ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.WHISPER_BAR), panelX, indY, panelW, indicatorH, getAnimProgress());
            String modeText = Text.translatable("e33chat.whisper.mode").getString() + ": " + whisperPartner;
            int modeTW = textRenderer.getWidth(modeText);
            g.drawText(textRenderer, modeText, panelX + (panelW - modeTW) / 2, indY + 2, c().textPrimary(), false);
        }

        int effectiveMsgTop = msgTop + indicatorH;
        int effectiveMsgBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
        int areaH = effectiveMsgBottom - effectiveMsgTop;

        String lastKey = null;
        ChatMessageStore.ChatMessage prevMsg = null;
        int totalH = 0;
        for (var msg : messages) {
            if (!msg.isSystem()) {
                String key = timeKey(msg.time());
                if (lastKey == null || !key.equals(lastKey)) {
                    lastKey = key;
                    totalH += TIME_SEP_H + Appearance.messageGap();
                    prevMsg = null;
                }
            }
            if (prevMsg != null) totalH += Appearance.messageGap();
            totalH += getMsgHeight(msg);
            prevMsg = msg;
        }
        totalH += Appearance.messageGap();
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
        ChatMessageStore.ChatMessage prevRenderMsg = null;
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
                    contentY += TIME_SEP_H + Appearance.messageGap();
                    prevRenderMsg = null;
                }
            }

            int h = getMsgHeight(msg);
            if (prevRenderMsg != null) contentY += Appearance.messageGap();
            int screenY = effectiveMsgTop + contentY - scrollOffset;
            // showAvatar 必须用“上一条消息”比较；先赋值 prevRenderMsg 再比会恒自比（2.3.16 回归）
            boolean showAvatar = !(ChatBubbleClientSetup.config().hideRepeatedAvatars() != null
                && ChatBubbleClientSetup.config().hideRepeatedAvatars()
                && MessageGrouping.isSameGroup(prevRenderMsg, msg));
            contentY += h;
            prevRenderMsg = msg;

            if (screenY + h <= effectiveMsgTop || screenY >= effectiveMsgBottom) { fullIdx++; continue; }

            // New-message enter animation, staggered 40ms per message from the
            // tail (250ms window, keyed on msg.time()).
            // SLIDE: slide in horizontally — own bubbles from right to left,
            // others from left to right — plus fade. FADE: pure fade, no
            // displacement. ZOOM: scale in around the bubble center with overshoot.
            float mAlpha = 1f;
            int mDx = 0;
            int mDy = 0;
            float mScale = 1f;
            if (ChatBubbleClientSetup.config().animationEnabled()) {
                AnimationStyle mstyle = AnimationStyle.parse(ChatBubbleClientSetup.config().messageAnimStyle());
                if (mstyle != AnimationStyle.NONE) {
                    int tailIdx = messages.size() - 1 - i;
                    // msg.time() is epoch millis (System.currentTimeMillis), so the
                    // "now" side must use the same clock — the MC render clock is
                    // nanoTime-based and subtracting it yields a huge negative raw.
                    float raw = (float) (System.currentTimeMillis() - msg.time() - tailIdx * 40L) / 250f;
                    if (raw < 1f) {
                        float curve = Animation.styleCurve(mstyle, raw);
                        mAlpha = curve;
                        switch (mstyle) {
                            case SLIDE -> mDx = Math.round((1f - curve) * 40f) * (msg.isOwn() ? 1 : -1);
                            case FADE -> { /* pure fade, no displacement */ }
                            case ZOOM -> mScale = 0.8f + 0.2f * Animation.easeOutBack(curve);
                            default -> { }
                        }
                    }
                }
            }
            g.getMatrices().push();
            g.getMatrices().translate(mDx, mDy, 0);
            if (mScale != 1f) {
                // Bubble top-left for the ZOOM pivot (mirrors renderBubble's layout incl. bubble_size)
                float bs = Appearance.bubbleScale(textRenderer.fontHeight);
                int zMaxW = panelW - Appearance.avatarSize() - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
                int zW = 0;
                for (var zl : wrapContent(msg.content(), Appearance.bubbleWrapWidth(zMaxW, textRenderer.fontHeight)))
                    zW = Math.max(zW, textRenderer.getWidth(zl));
                int zBubbleW = (int)((zW + BUBBLE_PAD_X * 2) * bs);
                int zBubbleX = msg.isOwn()
                    ? panelX + panelW - PAD - Appearance.avatarSize() - 4 - zBubbleW
                    : panelX + PAD + Appearance.avatarSize() + 4;
                int zBubbleY = screenY + NAME_H;
                g.getMatrices().translate(zBubbleX + zBubbleW / 2f, zBubbleY, 0);
                g.getMatrices().scale(mScale, mScale, 1f);
                g.getMatrices().translate(-(zBubbleX + zBubbleW / 2f), -zBubbleY, 0);
            }
            renderBubble(g, msg, fullIdx, screenY, mouseX, mouseY, mAlpha, showAvatar);
            g.getMatrices().pop();
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

        // 纯色填充替代 drawWithAlpha：绕开消息路径 blend/flush 污染
        // （4844270a 同机制：上游 drawWithAlpha → scrollbar 渐显只显最后一帧）
        g.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom,
            ChatBubbleTheme.alphaBlend(c().scrollbar(), (int) (0x1A * scrollbarAlpha)));

        int thumbH = Math.max(MIN_THUMB_H, (int) ((long) trackH * trackH / messageTotalH));
        thumbH = Math.min(thumbH, trackH);
        int travelRange = trackH - thumbH;
        int thumbY = trackTop + (int) ((long) scrollOffset * travelRange / maxScroll);

        boolean hovering = !scrollbarDragging
            && mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
            && mouseY >= thumbY && mouseY < thumbY + thumbH;
        scrollbarHovered = hovering || scrollbarDragging;

        float thumbBase = scrollbarDragging ? 0xAA : scrollbarHovered ? 0x88 : 0x66;
        g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH,
            ChatBubbleTheme.alphaBlend(c().scrollbar(), (int) (thumbBase * scrollbarAlpha)));
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

    private TextSpan findTextSpanAt(double mouseX, double mouseY) {
        for (int i = textSpans.size() - 1; i >= 0; i--) {
            TextSpan s = textSpans.get(i);
            if (mouseX >= s.x() && mouseX <= s.x() + s.w()
                && mouseY >= s.y() && mouseY <= s.y() + s.h()) {
                return s;
            }
        }
        return null;
    }


    private TextSpan findNearestTextSpan(double mouseX, double mouseY) {
        TextSpan best = null;
        double bestDist = Double.MAX_VALUE;
        for (TextSpan s : textSpans) {
            double cx = Math.max(s.x(), Math.min(mouseX, s.x() + s.w()));
            double cy = Math.max(s.y(), Math.min(mouseY, s.y() + s.h()));
            double dx = mouseX - cx;
            double dy = mouseY - cy;
            double dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = s;
            }
        }
        return best;
    }

    private void autoScrollSelection(double mouseY) {
        boolean changed = false;
        if (mouseY < msgTop + 16 && scrollOffset > 0) {
            scrollOffset = Math.max(0, scrollOffset - 4);
            changed = true;
        } else if (mouseY > msgBottom - 16 && scrollOffset < maxScroll) {
            scrollOffset = Math.min(maxScroll, scrollOffset + 4);
            changed = true;
        }
        if (changed) {
            textSelection.markMoved();
            scrollToBottom = false;
            scrollAnimActive = false;
            lastScrollTime = Util.getMeasuringTimeMs();
        }
    }

    private int charAt(TextSpan span, double mouseX) {
        String text = span.text();
        if (text.isEmpty()) return 0;
        double localX = (mouseX - span.x()) / span.scale();
        int lo = 0;
        int hi = text.codePointCount(0, text.length());
        Object visual = span.visualLine();
        while (lo < hi) {
            int mid = (lo + hi + 1) >>> 1;
            double w;
            if (visual instanceof net.minecraft.text.OrderedText ot) {
                w = prefixWidth(ot, mid);
            } else {
                w = textRenderer.getWidth(text.substring(0, text.offsetByCodePoints(0, mid)));
            }
            if (w <= localX) {
                lo = mid;
            } else {
                hi = mid - 1;
            }
        }
        return lo;
    }

    private void executeClickAction(double mouseX, double mouseY) {
        Style style = getHoveredStyle(mouseX, mouseY);
        if (style != null && style.getClickEvent() != null) {
            ClickEvent click = style.getClickEvent();
            if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
                chatField.setText(click.getValue());
            } else if (click.getAction() == ClickEvent.Action.OPEN_FILE) {
                java.io.File file = new java.io.File(click.getValue());
                Util.getOperatingSystem().open(file);
            } else if (click.getAction() == ClickEvent.Action.OPEN_URL) {
                String clickUrl = click.getValue();
                if (clickUrl != null && (clickUrl.startsWith("http://") || clickUrl.startsWith("https://"))) {
                    handleTextClick(style);
                }
            } else {
                handleTextClick(style);
            }
        }
    }

    private boolean isPanelSliding() {
        return ChatBubbleClientSetup.config().animationEnabled() && getAnimProgress() < 1.0f;
    }

    private int currentPanelOffset() {
        if (AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle()) != AnimationStyle.SLIDE)
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
        int h;
        if (msg.isSystem()) {
            List<OrderedText> lines = wrapContent(msg.content(), panelW - PAD * 2 - 20);
            h = lines.size() * textRenderer.fontHeight + 4;
        } else {
            int bubbleMaxW = panelW - Appearance.avatarSize() - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
            BracketCodec.ParseResult parsed = parseImages(msg);
            if (!parsed.images().isEmpty()
                    && parsed.images().stream().allMatch(BracketCodec.ImageRef::emote)
                    && parsed.textWithoutImages().getString().isBlank()) {
                h = NAME_H + textRenderer.fontHeight + 2 + EMOTE_MAX_SIZE + 2;
                msgHeightCache.put(msg, h);
                return h;
            }
            if (!parsed.images().isEmpty()) {
                List<OrderedText> imgLines = wrapContent(parsed.textWithoutImages(), bubbleMaxW);
                int textH = imgLines.size() * textRenderer.fontHeight;
                int imgH = 0;
                for (var ref : parsed.images()) imgH += imageEdgeHeight(ref.url()) + 2;
                h = NAME_H + textH + imgH;
                if (msg.replyContent() != null) h += textRenderer.fontHeight + 7;
                msgHeightCache.put(msg, h);
                return h;
            }
            float s = Appearance.bubbleScale(textRenderer.fontHeight);
            List<OrderedText> lines = wrapContent(parsed.textWithoutImages(), Appearance.bubbleWrapWidth(bubbleMaxW, textRenderer.fontHeight));
            double contentH = lines.size() * textRenderer.fontHeight + BUBBLE_PAD_Y * 2;
            if (msg.replyContent() != null) contentH += textRenderer.fontHeight + 7;
            h = NAME_H + (int) (contentH * s);
        }
        msgHeightCache.put(msg, h);
        return h;
    }

    private BracketCodec.ParseResult parseImages(ChatMessageStore.ChatMessage msg) {
        if (!ChatBubbleClientSetup.config().receiveImages()) {
            // Receiving disabled: bracket codes render as a plain-text
            // placeholder, never downloaded (the flood limiter stays untouched).
            return new BracketCodec.ParseResult(
                BracketCodec.toPlaceholderText(msg.content()), java.util.List.of());
        }
        BracketCodec.ParseResult cached = imageParseCache.get(msg);
        if (cached != null) return cached;
        cached = BracketCodec.parseOrExtract(msg.content());
        imageParseCache.put(msg, cached);
        return cached;
    }

    /** Height in px for one bubble-less image (state-dependent, panel-clamped, never upscaled). */
    private int imageEdgeHeight(String url) {
        int maxW = Math.max(80, panelW - Appearance.avatarSize() - PAD * 2 - 16);
        ImageEntry entry = ImageLoader.getOrLoad(url);
        if (entry != null && entry.state() == ImageEntry.State.LOADED
                && entry.width() > 0 && entry.height() > 0) {
            float ratio = Math.min((float) maxW / entry.width(),
                (float) maxW / entry.height());
            ratio = Math.min(1f, ratio);
            return Math.max(1, (int) (entry.height() * ratio));
        }
        return maxW;
    }

    private void renderBubble(DrawContext g, ChatMessageStore.ChatMessage msg, int index, int baseY, int mouseX, int mouseY, float alpha, boolean showAvatar) {
        if (msg.isSystem()) {
            List<OrderedText> lines = wrapContent(msg.content(), panelW - PAD * 2 - 20);
            int yy = baseY + 2;
            Style fb = findRootClickStyle(msg.content());
            int sysColor = ChatBubbleTheme.alphaBlend(c().textMuted(), (int)(255 * alpha));
            for (int li = 0; li < lines.size(); li++) {
                OrderedText line = lines.get(li);
                int lw = textRenderer.getWidth(line);
                renderLineWithClicks(g, line, panelX + (panelW - lw) / 2, yy, sysColor, fb,
                    index, li, TextSpan.KIND_CONTENT, 1f, c().panelBg(), textSelection);
                yy += textRenderer.fontHeight;
            }
            return;
        }

        boolean own = msg.isOwn();
        int bubbleMaxW = panelW - Appearance.avatarSize() - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
        BracketCodec.ParseResult parsed = parseImages(msg);

        // E33Emote-only messages render bubble-less: max 64px, aligned by direction (QQ style).
        if (!parsed.images().isEmpty()
                && parsed.images().stream().allMatch(BracketCodec.ImageRef::emote)
                && parsed.textWithoutImages().getString().isBlank()) {
            renderEmoteMessage(g, msg, index, baseY, own, alpha, showAvatar);
            return;
        }

        List<OrderedText> lines = wrapContent(parsed.textWithoutImages(), bubbleMaxW);

        // Any message carrying images renders bubble-less too (320px long-edge,
        // aspect preserved, stacked vertically, direction-aligned).
        if (!parsed.images().isEmpty()) {
            renderNoBubbleMessage(g, msg, index, baseY, own, alpha, parsed, lines, showAvatar);
            return;
        }

        // Bubble path only: re-wrap at the scaled width so bigger bubbles fit fewer
        // characters per line (bubble-less emote/image paths above keep the unscaled lines).
        float s = Appearance.bubbleScale(textRenderer.fontHeight);
        lines = wrapContent(parsed.textWithoutImages(), Appearance.bubbleWrapWidth(bubbleMaxW, textRenderer.fontHeight));
        int textW = 0;
        for (var line : lines) textW = Math.max(textW, textRenderer.getWidth(line));
        int bubbleW = (int) ((textW + BUBBLE_PAD_X * 2) * s);
        int bubbleH = (int) ((lines.size() * textRenderer.fontHeight + BUBBLE_PAD_Y * 2) * s);

        int avatarX, bubbleX;
        if (own) {
            avatarX = panelX + panelW - PAD - Appearance.avatarSize();
            bubbleX = avatarX - UiTokens.AVATAR_GAP - bubbleW;
        } else {
            avatarX = panelX + PAD;
            bubbleX = avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP;
        }

        int nameY = baseY;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - PAD * 2 - 20;
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
            renderLineWithClicks(g, nameSeq, startX, nameY,
                ChatBubbleTheme.alphaBlend(c().nameColor(), (int) (255 * alpha)), null,
                index, 0, TextSpan.KIND_NAME, 1f, c().panelBg(), textSelection);
        }

        int bubbleY = baseY + NAME_H;
        int avatarY = baseY;

        int bg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().ownBubbleColor(), 0xFF1E90FF)
            : ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().otherBubbleColor(), c().contextHover());
        int fg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().ownTextColor(), 0xFFFFFFFF)
            : ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().otherTextColor(), c().textPrimary());

        // 气泡背景：SDF 圆角（shader 数学，任何半径平滑；配置实时生效，不可被资源包覆盖）
        // 坐标已含 bubble_size 缩放，圆角半径同样按比例缩放，否则放大后圆角相对变小
        RoundRectRenderer.fill(g, bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH,
            ChatBubbleClientSetup.config().bubbleCornerRadius() * s, ChatBubbleTheme.alphaBlend(bg, (int)(255 * alpha)));

        Style fbP = findRootClickStyle(msg.content());
        int fgA = ChatBubbleTheme.alphaBlend(fg, (int)(255 * alpha));
        for (int li = 0; li < lines.size(); li++) {
            // Bubble text is drawn at the matrix origin; the translate must be unconditional
            // (s == 1 still needs the offset, only the scale is skipped), and clickable spans
            // are recorded in origin space then transformed back to screen space so
            // hit-testing and the visual position stay in sync at every bubble size.
            int textSX = bubbleX + (int)(BUBBLE_PAD_X * s);
            int textSY = bubbleY + (int)(BUBBLE_PAD_Y * s) + (int)(li * textRenderer.fontHeight * s);
            int beforeText = textSpans.size();
            int beforeLine = clickableSpans.size();
            g.getMatrices().push();
            g.getMatrices().translate(textSX, textSY, 0);
            if (s != 1f) g.getMatrices().scale(s, s, 1f);
            renderLineWithClicks(g, lines.get(li), 0, 0, fgA, fbP,
                index, li, TextSpan.KIND_CONTENT, s, bg, textSelection);
            g.getMatrices().pop();
            for (int i = beforeLine; i < clickableSpans.size(); i++) {
                ClickableSpan sp = clickableSpans.get(i);
                clickableSpans.set(i, new ClickableSpan(
                    textSX + (int)(sp.x * s),
                    textSY + (int)(sp.y * s),
                    Math.max(1, (int)(sp.w * s)),
                    Math.max(1, (int)(sp.h * s)),
                    sp.style));
            }
            for (int i = beforeText; i < textSpans.size(); i++) {
                TextSpan sp = textSpans.get(i);
                textSpans.set(i, sp.withPosition(
                    textSX + (int)(sp.x() * s),
                    textSY + (int)(sp.y() * s),
                    Math.max(1, (int)(sp.w() * s)),
                    Math.max(1, (int)(sp.h() * s))));
            }
        }

        String skinName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
            ? msg.rawPlayerName() : msg.senderName().getString();
        Identifier skin = com.niuqu.chatbubble.render.SkinResolver.getSkin(msg.senderUUID(), skinName);
        // Draw avatar (per-element alpha); D07: hidden on repeated same-sender messages
        if (showAvatar) drawPlayerHead(g, skin, avatarX, avatarY, Appearance.avatarSize(), Appearance.avatarSize() + 2, alpha);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = (int)(textRenderer.getWidth(label) * s);
            int labelX, labelY = bubbleY + (bubbleH - (int)(textRenderer.fontHeight * s)) / 2;
            if (own) { labelX = bubbleX - labelW - 3; } else { labelX = bubbleX + bubbleW + 3; }
            g.getMatrices().push();
            g.getMatrices().translate(labelX, labelY, 0);
            if (s != 1f) g.getMatrices().scale(s, s, 1f);
            g.drawText(textRenderer, label, 0, 0, ChatBubbleTheme.alphaBlend(c().duplicateLabel(), (int)(255 * alpha)), false);
            g.getMatrices().pop();
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - PAD * 2 - Appearance.avatarSize() - 24;
            String quoteText = "↳ " + msg.replySender() + ": " + msg.replyContent();
            String quoteDisplay = textRenderer.trimToWidth(quoteText, Math.max(8, (int)((quoteMaxW - 10) / s)));
            if (!quoteDisplay.equals(quoteText)) quoteDisplay += "...";
            int quoteTextW = (int)(textRenderer.getWidth(quoteDisplay) * s);
            int quoteW = Math.min(quoteTextW + (int)(8 * s), quoteMaxW);
            int quoteH = Math.max(1, (int)((textRenderer.fontHeight + 4) * s));
            int quoteY = bubbleY + bubbleH + 3;
            int quoteX;
            if (own) { quoteX = bubbleX + bubbleW - quoteW; } else { quoteX = bubbleX; }
            if (quoteX < panelX + PAD) quoteX = panelX + PAD;
            if (quoteX + quoteW > panelX + panelW - PAD) quoteW = panelX + panelW - PAD - quoteX;
            // 引用块：SDF 圆角（随 bubble_size 缩放）
            RoundRectRenderer.fill(g, quoteX, quoteY, quoteX + quoteW, quoteY + quoteH, ChatBubbleClientSetup.config().bubbleCornerRadius() * s, ChatBubbleTheme.alphaBlend(c().contextHover(), (int)(255 * alpha)));
            int beforeText = textSpans.size();
            g.getMatrices().push();
            g.getMatrices().translate(quoteX + (int)(4 * s), quoteY + (int)(2 * s), 0);
            if (s != 1f) g.getMatrices().scale(s, s, 1f);
            renderLineWithClicks(g, Text.literal(quoteDisplay).asOrderedText(), 0, 0,
                ChatBubbleTheme.alphaBlend(c().textSecondary(), (int) (255 * alpha)), null,
                index, 0, TextSpan.KIND_QUOTE, s, c().contextHover(), textSelection);
            g.getMatrices().pop();
            for (int i = beforeText; i < textSpans.size(); i++) {
                TextSpan sp = textSpans.get(i);
                textSpans.set(i, sp.withPosition(
                    quoteX + (int)(4 * s) + (int)(sp.x() * s),
                    quoteY + (int)(2 * s) + (int)(sp.y() * s),
                    Math.max(1, (int)(sp.w() * s)),
                    Math.max(1, (int)(sp.h() * s))));
            }
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});

        if (index == searchHighlightIndex)
            g.drawBorder(bubbleX - 1, bubbleY - 1, bubbleW + 2, bubbleH + 2, ChatSearchPanel.HIGHLIGHT);
    }

    /** Bubble-less image message: name + avatar + optional text + images
     * (320px long-edge, aspect preserved, stacked vertically, direction-aligned). */
    private void renderNoBubbleMessage(DrawContext g, ChatMessageStore.ChatMessage msg, int index, int baseY,
            boolean own, float alpha, BracketCodec.ParseResult parsed, List<OrderedText> lines, boolean showAvatar) {
        int avatarX = own ? panelX + panelW - PAD - Appearance.avatarSize() : panelX + PAD;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - PAD * 2 - 20;
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
            int startX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - nameW) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
            int nameY = baseY;
            renderLineWithClicks(g, nameSeq, startX, nameY,
                ChatBubbleTheme.alphaBlend(c().nameColor(), (int) (255 * alpha)), null,
                index, 0, TextSpan.KIND_NAME, 1f, c().panelBg(), textSelection);
        }

        Identifier skin = com.niuqu.chatbubble.render.SkinResolver.getSkin(msg.senderUUID(), msg.rawPlayerName());
        // 头像顶与名字行顶对齐（2.3.16 曾改内容顶对齐，实测回退老锚点）
        if (showAvatar) drawPlayerHead(g, skin, avatarX, baseY, Appearance.avatarSize(), Appearance.avatarSize() + 2, alpha);

        int maxTextW = 0;
        for (var line : lines) maxTextW = Math.max(maxTextW, textRenderer.getWidth(line));
        int textX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - maxTextW) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);

        int y = baseY + NAME_H;
        if (!lines.isEmpty()) {
            int fg = own
                ? ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().ownTextColor(), 0xFFFFFFFF)
                : ChatBubbleConfig.parseHexColor(ChatBubbleClientSetup.config().otherTextColor(), c().textPrimary());
            Style fb = findRootClickStyle(msg.content());
            int fgA = ChatBubbleTheme.alphaBlend(fg, (int) (255 * alpha));
            for (int li = 0; li < lines.size(); li++)
                renderLineWithClicks(g, lines.get(li), textX, y + li * textRenderer.fontHeight, fgA, fb,
                    index, li, TextSpan.KIND_CONTENT, 1f, c().panelBg(), textSelection);
            y += lines.size() * textRenderer.fontHeight;
        }

        // Long-edge clamped to the panel's usable width so a narrow window/guiScale
        // can never push the image off-screen; small images keep their real size.
        int maxImgW = Math.max(80, panelW - Appearance.avatarSize() - PAD * 2 - 16);

        for (var ref : parsed.images()) {
            int w = maxImgW, h = maxImgW;
            ImageEntry entry = ImageLoader.getOrLoad(ref.url());
            if (entry != null && entry.state() == ImageEntry.State.LOADED
                    && entry.width() > 0 && entry.height() > 0) {
                float ratio = Math.min((float) maxImgW / entry.width(),
                    (float) maxImgW / entry.height());
                ratio = Math.min(1f, ratio); // never upscale
                w = Math.max(1, (int) (entry.width() * ratio));
                h = Math.max(1, (int) (entry.height() * ratio));
            }
            int imgX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - w) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
            if (entry != null && entry.state() == ImageEntry.State.LOADED && entry.textureId() != null) {
                g.drawTexture(entry.textureId(), imgX, y, w, h,
                    0, 0, entry.width(), entry.height(), entry.width(), entry.height());
            } else {
                boolean limited = entry != null && entry.state() == ImageEntry.State.FAILED
                    && entry.failure() != null && entry.failure().contains("rate limited");
                String txt = limited
                    ? Text.translatable("e33chat.image.ratelimited").getString()
                    : entry != null && entry.state() == ImageEntry.State.FAILED
                        ? Text.translatable("e33chat.image.failed").getString()
                        : Text.translatable("e33chat.image.loading").getString();
                g.drawText(textRenderer, txt, imgX, y,
                    ChatBubbleTheme.alphaBlend(limited ? 0xFFFF5555 : c().textSecondary(), (int) (255 * alpha)), false);
            }
            // Open the URL in the system browser on click; hover shows the URL
            Style st = Style.EMPTY
                .withClickEvent(new ClickEvent(ClickEvent.Action.OPEN_URL, ref.url()))
                .withHoverEvent(new HoverEvent(HoverEvent.Action.SHOW_TEXT, Text.literal(ref.url())));
            clickableSpans.add(new ClickableSpan(imgX, y, w, h, st));
            y += h + 2;
        }

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int lx = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - textRenderer.getWidth(label) - 3) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP + 3);
            g.drawText(textRenderer, label, lx, baseY + NAME_H + 2,
                ChatBubbleTheme.alphaBlend(c().duplicateLabel(), (int) (255 * alpha)), false);
        }

        if (msg.replyContent() != null) {
            int quoteMaxW = panelW - PAD * 2 - Appearance.avatarSize() - 24;
            String quoteText = "↳ " + msg.replySender() + ": " + msg.replyContent();
            String quoteDisplay = textRenderer.trimToWidth(quoteText, quoteMaxW - 10);
            if (!quoteDisplay.equals(quoteText)) quoteDisplay += "...";
            int quoteW = Math.min(textRenderer.getWidth(quoteDisplay) + 8, quoteMaxW);
            int quoteX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - quoteW) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
            if (quoteX < panelX + PAD) quoteX = panelX + PAD;
            if (quoteX + quoteW > panelX + panelW - PAD) quoteW = panelX + panelW - PAD - quoteX;
            RoundRectRenderer.fill(g, quoteX, y, quoteX + quoteW, y + textRenderer.fontHeight + 4, ChatBubbleClientSetup.config().bubbleCornerRadius(),
                ChatBubbleTheme.alphaBlend(c().contextHover(), (int) (255 * alpha)));
            renderLineWithClicks(g, Text.literal(quoteDisplay).asOrderedText(),
                quoteX + 4, y + 2, ChatBubbleTheme.alphaBlend(c().textSecondary(), (int) (255 * alpha)),
                null, index, 0, TextSpan.KIND_QUOTE, 1f, c().contextHover(), textSelection);
        }

        // Hit-test region for avatar clicks / context menus: the message span.
        bubbleRects.add(new int[]{own ? avatarX - 8 - maxTextW : avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP,
            baseY, Math.max(maxTextW, maxImgW), y - baseY, index});
    }

    /** QQ-style emote: bubble-less image, max 64px, aligned by direction. */
    private void renderEmoteMessage(DrawContext g, ChatMessageStore.ChatMessage msg, int index, int baseY, boolean own, float alpha, boolean showAvatar) {
        BracketCodec.ParseResult parsed = parseImages(msg);
        if (parsed.images().isEmpty()) return;
        BracketCodec.ImageRef ref = parsed.images().get(0);

        int avatarX = own ? panelX + panelW - PAD - Appearance.avatarSize() : panelX + PAD;
        int nameY = baseY;

        if (!msg.senderName().getString().isEmpty()) {
            int maxNameW = panelW - Appearance.avatarSize() - PAD * 2 - 20;
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
            int startX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - nameW) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
            renderLineWithClicks(g, nameSeq, startX, nameY,
                ChatBubbleTheme.alphaBlend(c().nameColor(), (int) (255 * alpha)), null,
                index, 0, TextSpan.KIND_NAME, 1f, c().panelBg(), textSelection);
        }

        Identifier skin = com.niuqu.chatbubble.render.SkinResolver.getSkin(msg.senderUUID(), msg.rawPlayerName());
        if (showAvatar) drawPlayerHead(g, skin, avatarX, baseY, Appearance.avatarSize(), Appearance.avatarSize() + 2, alpha);

        int emoteY = baseY + NAME_H + 2;
        int maxE = Math.max(16, Math.min(EMOTE_MAX_SIZE, panelW - Appearance.avatarSize() - PAD * 2 - 16));
        int w = maxE, h = maxE;
        ImageEntry entry = ImageLoader.getOrLoad(ref.url());
        if (entry != null && entry.state() == ImageEntry.State.LOADED
                && entry.width() > 0 && entry.height() > 0) {
            float ratio = Math.min((float) maxE / entry.width(),
                (float) maxE / entry.height());
            ratio = Math.min(1f, ratio); // never upscale
            w = Math.max(1, (int) (entry.width() * ratio));
            h = Math.max(1, (int) (entry.height() * ratio));
        }
        int emoteX = own ? (avatarX - UiTokens.AVATAR_NAME_GAP - w) : (avatarX + Appearance.avatarSize() + UiTokens.AVATAR_GAP);
        if (entry != null && entry.state() == ImageEntry.State.LOADED && entry.textureId() != null) {
            g.drawTexture(entry.textureId(), emoteX, emoteY, w, h,
                0, 0, entry.width(), entry.height(), entry.width(), entry.height());
        } else {
            boolean limited = entry != null && entry.state() == ImageEntry.State.FAILED
                && entry.failure() != null && entry.failure().contains("rate limited");
            String txt = limited
                ? Text.translatable("e33chat.image.ratelimited").getString()
                : entry != null && entry.state() == ImageEntry.State.FAILED
                    ? Text.translatable("e33chat.image.failed").getString()
                    : Text.translatable("e33chat.image.loading").getString();
            g.drawText(textRenderer, txt, emoteX, emoteY,
                ChatBubbleTheme.alphaBlend(limited ? 0xFFFF5555 : c().textSecondary(), (int) (255 * alpha)), false);
        }
    }

    private void renderLineWithClicks(DrawContext g, OrderedText line, int x, int y, int color) {
        renderLineWithClicks(g, line, x, y, color, null);
    }

    private void renderLineWithClicks(DrawContext g, OrderedText line, int x, int y, int color, Style fallback) {
        renderLineWithClicks(g, line, x, y, color, fallback, -1, -1,
            TextSpan.KIND_CONTENT, 1f, 0, null);
    }

    private void renderLineWithClicks(DrawContext g, OrderedText line, int x, int y, int color,
                                      Style fallback, int messageIndex, int lineIndex,
                                      int kind, float scale, int backgroundRgb,
                                      ChatTextSelection selection) {
        final List<Style> styles = new ArrayList<>();
        StringBuilder textBuilder = new StringBuilder();
        line.accept((i, st, cp) -> {
            styles.add(st);
            textBuilder.appendCodePoint(cp);
            return true;
        });
        String text = textBuilder.toString();

        int[] range = null;
        int selBg = 0;
        int selFg = 0;
        if (textSpans != null && messageIndex >= 0) {
            int w = textRenderer.getWidth(line);
            selBg = ChatTextSelection.selectionBg();
            selFg = ChatTextSelection.selectionFg();
            textSpans.add(new TextSpan(messageIndex, lineIndex, kind,
                x, y, w, textRenderer.fontHeight, text, scale, line));
            if (selection != null) {
                range = selection.rangeFor(textSpans.get(textSpans.size() - 1));
                if (range != null) {
                    int hx = x + prefixWidth(line, range[0]);
                    int hw = Math.max(1, prefixWidth(line, range[1]) - prefixWidth(line, range[0]));
                    g.fill(hx, y, hx + hw, y + textRenderer.fontHeight, selBg);
                }
            }
        }

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

        int[] idx = {0};
        int[] selectionRange = range;
        int selectionFg = selFg;
        OrderedText decorated = sink -> line.accept((i, st, cp) -> {
            int pos = Math.min(idx[0]++, styleLen);
            boolean underline = pos < styleLen ? hasClickEvent[pos] : st.getClickEvent() != null;
            Style out = underline && !st.isUnderlined() ? st.withUnderline(true) : st;
            if (selectionRange != null && pos >= selectionRange[0] && pos < selectionRange[1]) {
                out = out.withColor(selectionFg);
            }
            return sink.accept(i, out, cp);
        });
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

    private Style findRootClickStyle(Text c) {
        // Only a click event on the root/wrapper style is a true "parent-level"
        // fallback. A click event buried in one sibling must NOT underline or
        // make clickable unrelated lines/segments; per-character styles already
        // carry inherited parent styles, so the recursive search is unnecessary
        // and caused whole-line underlines on system messages.
        Style s = c.getStyle();
        return s != null && s.getClickEvent() != null ? s : null;
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
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), panelX, notifY - 1, panelW, 1, getAnimProgress());
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
        float alpha = getAnimProgress();

        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.CONTEXT_MENU_BG), menuX, menuY, CTX_W, menuH, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, CTX_W, 1, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY + menuH - 1, CTX_W, 1, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 1, menuH, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), menuX + CTX_W - 1, menuY, 1, menuH, alpha);

        boolean hoverCopy = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverCopy ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            menuX + 1, menuY + 1, CTX_W - 2, CTX_ITEM_H - 1, alpha);
        drawTextureIconAlpha(g, iconTex("copy"), menuX + 5, menuY + 3, 12, alpha);
        g.drawText(textRenderer, Text.translatable("e33chat.context.copy").getString(), menuX + 22, menuY + 4, c().textPrimary(), false);

        g.fill(menuX + 4, menuY + CTX_ITEM_H, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 1, c().closeHoverBg());

        boolean hoverQuote = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 1 && mouseY <= menuY + menuH;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverQuote ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            menuX + 1, menuY + CTX_ITEM_H + 1, CTX_W - 2, CTX_ITEM_H, alpha);
        drawTextureIconAlpha(g, iconTex("quote"), menuX + 5, menuY + CTX_ITEM_H + 3, 12, alpha);
        g.drawText(textRenderer, Text.translatable("e33chat.context.quote").getString(), menuX + 22, menuY + CTX_ITEM_H + 5, c().textPrimary(), false);
    }

    private void renderAvatarContextMenu(DrawContext g, int mouseX, int mouseY) {
        if (contextAvatarIndex < 0) return;
        int menuH = CTX_ITEM_H * 3 + 4;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;
        float alpha = getAnimProgress();

        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.CONTEXT_MENU_BG), menuX, menuY, CTX_W, menuH, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, CTX_W, 1, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY + menuH - 1, CTX_W, 1, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 1, menuH, alpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), menuX + CTX_W - 1, menuY, 1, menuH, alpha);

        boolean hoverTp = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverTp ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            menuX + 1, menuY + 1, CTX_W - 2, CTX_ITEM_H - 1, alpha);
        drawTextureIconAlpha(g, iconTex("tp"), menuX + 5, menuY + 3, 12, alpha);
        g.drawText(textRenderer, Text.translatable(ChatMessageStore.useTpa() ? "e33chat.context.tpa" : "e33chat.context.tp").getString(), menuX + 22, menuY + 4, c().textPrimary(), false);

        g.fill(menuX + 4, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 2, c().closeHoverBg());

        boolean hoverWhisper = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 2 && mouseY <= menuY + CTX_ITEM_H * 2 + 2;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverWhisper ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            menuX + 1, menuY + CTX_ITEM_H + 2, CTX_W - 2, CTX_ITEM_H, alpha);
        drawTextureIconAlpha(g, iconTex("whisper"), menuX + 5, menuY + CTX_ITEM_H + 4, 12, alpha);
        g.drawText(textRenderer, Text.translatable("e33chat.context.whisper").getString(), menuX + 22, menuY + CTX_ITEM_H + 6, c().textPrimary(), false);

        g.fill(menuX + 4, menuY + CTX_ITEM_H * 2 + 3, menuX + CTX_W - 4, menuY + CTX_ITEM_H * 2 + 4, c().closeHoverBg());

        boolean hoverBlock = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H * 2 + 4 && mouseY <= menuY + menuH;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverBlock ? UiElement.CONTEXT_HOVER : UiElement.SIDEBAR_SELECTED),
            menuX + 1, menuY + CTX_ITEM_H * 2 + 4, CTX_W - 2, CTX_ITEM_H, alpha);
        drawTextureIconAlpha(g, iconTex("block"), menuX + 5, menuY + CTX_ITEM_H * 2 + 6, 12, alpha);
        ChatMessageStore.ChatMessage avaMsg = ChatMessageStore.getMessageAt(contextAvatarIndex);
        boolean isBlocked = avaMsg != null
            && BlockList.isPlayerBlocked(avaMsg.rawPlayerName(), avaMsg.senderName(),
                ChatBubbleClientSetup.config().blockedPlayers());
        g.drawText(textRenderer, Text.translatable(isBlocked ? "e33chat.context.unblock" : "e33chat.context.block").getString(),
            menuX + 22, menuY + CTX_ITEM_H * 2 + 8, c().textPrimary(), false);
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
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), barX, barTop - notifOffset - 1, barW, 1, getAnimProgress());

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
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverX ? UiElement.CLOSE_HOVER : UiElement.SIDEBAR_SELECTED),
            cx, cy, 12, 12, getAnimProgress());
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
        int popupX = chatField.getX();
        int popupY = chatField.getY() - popupH - 2;
        if (popupY < msgTop) popupY = chatField.getY() + chatField.getHeight() + 2;

        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.POPUP_BG), popupX, popupY, popupW, popupH, getAnimProgress());
        g.drawBorder(popupX, popupY, popupW, popupH, ChatBubbleTheme.alphaBlend(c().divider(), (int) (255 * getAnimProgress())));

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
        int alpha;
        String text;
        int color;
        if (uploadToastTicks > 0) {
            alpha = Animation.fadeInOut(uploadToastTicks, 5, 20, 5);
            color = (alpha << 24) | 0x00FF5555;
            text = Text.translatable("e33chat.upload.failed").getString();
        } else if (uploadBusyTicks > 0) {
            // Upload-in-progress hint; cleared by the worker when the job finishes.
            // Same look as the copy toast (TOAST_BG texture + toastText color).
            alpha = 200;
            color = (alpha << 24) | (c().toastText() & 0x00FFFFFF);
            text = Text.translatable("e33chat.upload.start").getString();
        } else {
            if (copyToastTicks <= 0) return;
            alpha = Animation.fadeInOut(copyToastTicks, 5, 20, 5);
            color = (alpha << 24) | (c().toastText() & 0x00FFFFFF);
            text = Text.translatable("e33chat.toast.copied").getString();
        }
        int tw = textRenderer.getWidth(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        int ty = msgBottom - 24;
        // Background fades with the text, at half opacity like the strong-hint bar
        // TOAST_BG 烘焙不透明 toastBg；纹理 × 动态 alpha = 半透明淡入淡出。2.2.4 黑块根因：
        // 当时 blit 无 alpha 通道渲染不透明纯黑 → drawWithAlpha 后纹理可覆盖 + 透明度可控
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.TOAST_BG),
            tx - 6, ty - 2, tw + 12, textRenderer.fontHeight + 4, (alpha / 2) / 255f);
        g.drawText(textRenderer, text, tx, ty, color, false);
    }

    private void executeMenuAction(int action) {
        switch (action) {
            case 0: // search
                if (quickChatPanel.visible) beginPopupClose(s -> quickCloseStart = s, () -> {
                    quickChatPanel.visible = false;
                    quickChatInput.setVisible(false);
                });
                if (emojiPanel.visible) beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
                searchPanel.visible = true;
                searchAnimStart = Util.getMeasuringTimeMs();
                searchInput.setText("");
                searchMatches.clear(); searchMatchIdx = -1; searchHighlightIndex = -1;
                setFocused(searchInput);
                break;
            case 1: // quick_chat
                if (searchPanel.visible) closeSearchPanel();
                if (emojiPanel.visible) beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
                quickChatPanel.visible = true;
                quickAnimStart = Util.getMeasuringTimeMs();
                quickChatPanel.scrollOffset = 0;
                quickChatInput.setText("");
                setFocused(chatField);
                break;
            case 2: { // theme
                ChatBubbleTheme next = theme() == ChatBubbleTheme.DARK ? ChatBubbleTheme.LIGHT : ChatBubbleTheme.DARK;
                ChatBubbleClientSetup.saveConfig(ChatBubbleClientSetup.config().withTheme(next.name().toLowerCase()));
                        int editColor = next == ChatBubbleTheme.LIGHT ? c().textSecondary() : c().textPrimary();
                chatField.setEditableColor(editColor);
                chatField.setUneditableColor(c().textMuted());
                sidebarSearchBox.setEditableColor(editColor);
                sidebarSearchBox.setUneditableColor(editColor);
                quickChatInput.setEditableColor(editColor);
                quickChatInput.setUneditableColor(c().textMuted());
                searchInput.setEditableColor(editColor);
                searchInput.setUneditableColor(c().textMuted());
                int cmdAlpha = next == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
                commandSuggestions = new ChatInputSuggestor(client, this, chatField, textRenderer,
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
        beginPopupClose(s -> searchCloseStart = s, () -> {
            searchPanel.visible = false;
            searchInput.setVisible(false);
        });
        searchInput.setVisible(false);
        searchMatches.clear(); searchMatchIdx = -1; searchHighlightIndex = -1;
        setFocused(chatField);
    }

    private void renderBottomBar(DrawContext g, int mouseX, int mouseY, float panelAlpha) {
        int a255 = (int) (255 * panelAlpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.BOTTOM_BAR), panelX, barTop, panelW, height - barTop, panelAlpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), panelX, barTop, panelW, 1, panelAlpha);

        int iconY = barTop + (BAR_H - ICON_S) / 2;

        int ibX = inputX;
        int ibY = inputY;
        int ibW = chatField.getWidth();
        int ibH = INPUT_H;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), ibX - 1, ibY - 1, ibW + 1, 1, panelAlpha);
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.INPUT_BG), ibX - 1, ibY, ibW + 1, ibH, panelAlpha);

        boolean hoverInput = mouseX >= ibX - 1 && mouseX <= ibX + ibW && mouseY >= ibY && mouseY <= ibY + ibH;
        if (hoverInput || chatField.isFocused())
            g.drawBorder(ibX - 1, ibY, ibW + 1, ibH, ChatBubbleTheme.alphaBlend(c().textMuted(), a255));

        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;

        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverGear) ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.HOVER_BG), gearX - 1, iconY - 1, ICON_S + 2, ICON_S + 2, panelAlpha);
        drawTextureIconAlpha(g, iconTex("settings"), gearX, iconY, ICON_S, getAnimProgress());

        boolean hoverEmoji = mouseX >= emojiX && mouseX <= emojiX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverEmoji || emojiPanel.visible) ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.HOVER_BG), emojiX - 1, iconY - 1, ICON_S + 2, ICON_S + 2, panelAlpha);
        drawTextureIconAlpha(g, iconTex("emoji"), emojiX, iconY, ICON_S, getAnimProgress());

        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverSend) ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.HOVER_BG), sendX - 1, iconY - 1, ICON_S + 2, ICON_S + 2, panelAlpha);
        drawTextureIconAlpha(g, iconTex("send"), sendX, iconY, ICON_S, getAnimProgress());
    }



    static void drawTextureIcon(DrawContext g, Identifier tex, int x, int y, int size) {
        // getTexture 无缓存时自动 new ResourceTexture 懒加载（资源包可覆盖，F3+T 即时生效）
        RenderSystem.setShaderTexture(0, tex);
        RenderSystem.setShader(GameRenderer::getPositionTexProgram);
        RenderSystem.enableBlend();
        if (size < 16) {
            // 图标纹理约定 16x16（内容居中，四周 1px 透明边，内容占 14x14）。采样内容区
            // (偏移1,1) 完整 14x14 绘制——窗口取 size 会切掉内容右/下 2px（copy 右页被切）。
            g.drawTexture(tex, x, y, size, size, 1.0F, 1.0F, 14, 14, 16, 16);
        } else {
            g.drawTexture(tex, x, y, 0, 0, size, size, size, size);
        }
    }

    /** 带透明度图标的绘制：与 drawTextureIcon 同采样语义，但走带 alpha 的渲染路径（弹层淡入用）。 */
    public static void drawTextureIconAlpha(DrawContext g, Identifier tex, int x, int y, int size, float alpha) {
        if (alpha <= 0.003f) return;
        if (size < 16) {
            ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size, 1f, 1f, 14, 14, 16, 16, alpha);
        } else {
            ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size, 0f, 0f, size, size, size, size, alpha);
        }
    }

    private void drawPlayerHead(DrawContext g, Identifier skin, int x, int y, int baseSize, int hatSize, float alpha) {
        if (alpha <= 0.003f) return;
        ColoredTextureRenderer.drawWithAlpha(g, skin, x, y, baseSize, baseSize, 8.0F, 8.0F, 8, 8, 64, 64, alpha);
        int hatOff = (hatSize - baseSize) / 2;
        ColoredTextureRenderer.drawWithAlpha(g, skin, x - hatOff, y - hatOff, hatSize, hatSize, 40.0F, 8.0F, 8, 8, 64, 64, alpha);
    }





    private void jumpToMessage(int msgIndex) {
        var msgs = ChatMessageStore.getMessages();
        if (msgIndex < 0 || msgIndex >= msgs.size()) return;
        int cy = 0;
        String lk = null;
        ChatMessageStore.ChatMessage prevMsg = null;
        for (int i = 0; i < msgIndex && i < msgs.size(); i++) {
            var m = msgs.get(i);
            if (!m.isSystem()) {
                String k = timeKey(m.time());
                if (lk == null || !k.equals(lk)) { lk = k; cy += TIME_SEP_H + Appearance.messageGap(); prevMsg = null; }
            }
            if (prevMsg != null) cy += Appearance.messageGap();
            cy += getMsgHeight(m);
            prevMsg = m;
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

    /** Extracts the local path from [[CICode,url=file:///...]] (chatimage appends Windows backslash paths). */
    private static String extractLocalPath(String cicode) {
        int start = cicode.indexOf("url=file:///");
        if (start < 0) return null;
        start += "url=file:///".length();
        int end = cicode.indexOf("]]", start);
        if (end < 0) end = cicode.length();
        String path = cicode.substring(start, end);
        // file:///C:\... → C:\... (drop the leading slash before the drive letter)
        if (path.startsWith("/") && path.length() > 1 && path.charAt(1) == ':') return path.substring(1);
        return path;
    }

    private void sendMessage() {
        String raw = chatField.getText().trim();
        if (raw.isEmpty()) return;
        if (raw.contains("[[CICode,url=file://")) {
            // A local file:// CICode (chatimage's drag/paste handler inserts
            // these) is a local-only broken link. Queue our own upload and
            // finish the send automatically once the real URL is up — one
            // enter, no second press. The input is cleared so the enter can't
            // double-fire; the text is restored if the upload fails.
            //
            // Strict validity check: only a real, existing local file is an
            // upload candidate. Edited remnants (deleted brackets, stale paths)
            // fall through and are sent as plain text — never block the user
            // on a string prefix alone.
            String localPath = extractLocalPath(raw);
            if (localPath == null || !new java.io.File(localPath).isFile()) {
                ChatMessageStore.debugLog("[e33chat] upload skip | not a live file | raw=" + raw);
                sendMessageText(raw);
                return;
            }
            if (uploadQueue.enqueue(new com.niuqu.chatbubble.image.UploadQueue.UploadJob(new java.io.File(localPath), null, null, false, raw))) {
                chatField.setText("");
                savedInput = "";
                client.player.sendMessage(Text.translatable("e33chat.upload.wait"), false);
                ChatMessageStore.debugLog("[e33chat] upload block | queued=" + uploadQueue.pending() + " | raw=" + raw);
            } else {
                client.player.sendMessage(Text.translatable("e33chat.upload.queue_full"), false);
            }
            return;
        }
        sendMessageText(raw);
    }

    private void sendMessageText(String text) {
        String raw = text;
        var cfg = ChatBubbleClientSetup.config();
        // Send the text UNCHANGED (raw '&', never '§'): vanilla servers reject '§' in
        // player chat and kick, so converting client-side is a dead end. Server color
        // plugins (Essentials etc.) translate '&' for everyone; on plain servers others
        // see the literal '&'. Local coloring of our own bubble is done at addMessage.

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
                    // Optional payload: only send when the server registered e33chat:quote_sync.
                    // Without this guard, right-click quote on servers without the E33Chat
                    // server mod still works locally but can fail on the network layer.
                    if (ClientPlayNetworking.canSend(QuoteSyncPayload.ID)) {
                        try {
                            ClientPlayNetworking.send(new QuoteSyncPayload(quoteSender, quoted, displayText));
                        } catch (Exception e) {
                            ChatMessageStore.debugLog(() -> "[e33chat] quote_sync skipped | " + e.getMessage());
                        }
                    } else {
                        ChatMessageStore.debugLog(() -> "[e33chat] quote_sync skipped | server has no e33chat:quote_sync channel");
                    }
                }
            }
            replyTargetIndex = -1;
        }

        if (text.startsWith("/"))
            // yarn: sendChatCommand is the full signed path (vanilla ChatScreen
            // uses it); sendCommand silently drops commands with signable
            // arguments (/msg /tell /w) — regression from the 1.20 port
            client.player.networkHandler.sendChatCommand(text.substring(1));
        else
            client.player.networkHandler.sendChatMessage(text);
        client.inGameHud.getChatHud().addToMessageHistory(text);
        // Keep the history cursor at the newest end: this screen stays open after
        // send (vanilla closes), so init()'s one-time historyPos snapshot goes
        // stale and up-arrow would skip the freshly sent entries.
        historyPos = client.inGameHud.getChatHud().getMessageHistory().size();

        ChatMessageStore.debugLog("[e33chat] Send | cmd='" + text + "' | display='" + displayText + "' | whisperTarget=" + whisperTarget + " | localBubble=" + localBubble);
        if (localBubble) {
            Text contentForSend = cfg != null && cfg.colorCodes() ? parseColorCodes(displayText) : Text.literal(displayText);
            // 2.3.10+: keep image bracket codes raw so the local bubble renders
            // the picture natively (BracketCodec + ImageLoader); the vanilla chat
            // echo is converted by ChatImage's own mixins when installed.
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

        chatField.setText("");
        savedInput = "";
        scrollToBottom = true;
        // Optional vanilla-style behaviour: close the chat screen right after the
        // message goes out (off by default — this screen supports multi-send).
        if (cfg != null && cfg.closeChatOnSend()) onClose();
    }


    // 父类 setChatFromHistory 访问 package-private chatInputSuggestor（跨包 null），
    // override 用自己的实现（history 字段也私有化到本类）
    @Override
    public void setChatFromHistory(int offset) {
        int size = client.inGameHud.getChatHud().getMessageHistory().size();
        int newPos = MathHelper.clamp(historyPos + offset, 0, size);
        if (newPos != historyPos) {
            if (newPos == size) {
                historyPos = size;
                chatField.setText(historyBuffer);
            } else {
                if (historyPos == size) historyBuffer = chatField.getText();
                chatField.setText(client.inGameHud.getChatHud().getMessageHistory().get(newPos));
                historyPos = newPos;
            }
        }
    }

    // 父类 resize 访问 package-private chatInputSuggestor（跨包 null）→ 自实现
    @Override
    public void resize(MinecraftClient client, int width, int height) {
        String cur = chatField.getText();
        this.init(client, width, height);
        chatField.setText(cur);
    }

    @Override
    public void removed() {
        if (ChatBubbleClientSetup.config().preserveInput()) savedInput = chatField.getText();
        ChatMessageStore.setScreenOpen(false);
        client.inGameHud.getChatHud().reset();
    }

    public void onClose() {
        if (ChatBubbleClientSetup.config().preserveInput()) savedInput = chatField.getText();
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
