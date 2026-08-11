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
//#if MC >= 12109
import net.minecraft.client.gui.Click;
//#endif
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
//#if MC >= 11900
import net.minecraft.client.gui.screen.ChatInputSuggestor;
//#endif
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.TextFieldWidget;
//#if MC >= 12004
import net.minecraft.client.gui.PlayerSkinDrawer;
//#endif
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.util.DefaultSkinHelper;
//#if MC >= 12004
//#if MC >= 12109
import net.minecraft.entity.player.SkinTextures;
//#else
//$$ import net.minecraft.client.util.SkinTextures;
//#endif
//#endif
import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;
import net.minecraft.text.*;
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
        return GuiCompat.id("e33chat", "textures/gui/" + theme + "/" + name);
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
    //#if MC >= 11900
    private ChatInputSuggestor commandSuggestions;
    //#endif
    private static int inputX, inputY;

    public static int getInputX() { return inputX; }
    public static int getInputY() { return inputY; }
    private final String initialText;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollToBottom = true;
    private boolean firstRender = true;
    private static String savedInput = "";
    //#if MC >= 12004
    private static final java.util.Map<UUID, SkinTextures> skinCache = new java.util.HashMap<>();
    private static final java.util.Map<String, SkinTextures> skinNameCache = new java.util.HashMap<>();
    //#endif
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
    // Popup open animation timestamps (opening only; closing stays instant)
    private long settingsAnimStart, emojiAnimStart, quickAnimStart, searchAnimStart;
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

    private final List<int[]> bubbleRects = new ArrayList<>();
    private final List<ClickableSpan> clickableSpans = new ArrayList<>();

    private int replyTargetIndex = -1;
    private int copyToastTicks;

    private long animStart;
    private boolean closing;
    private static final int ANIM_MS = 150;
    private static final int MSG_ANIM_MS = 200;
    private float currentPanelAlpha = 1f;
    private final java.util.Map<Integer, Long> messageAnimStart = new java.util.HashMap<>();
    private static final int NOTIF_H = 14;
    private int newMessageCount;
    private boolean hasNewMentionOrQuote;
    private int latestMentionIndex = -1;
    private int lastSeenMessageCount;
    private int notifCountLeft, notifCountRight;
    private int notifMentionLeft = -1, notifMentionRight = -1;
    private int notifBarTextY;

    public ChatBubbleScreen(String initialText) {
        super(com.niuqu.chatbubble.Txt.translatable("e33chat.screen.title"));
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
            sidebarAnimating = false; // sidebar is already in place; the panel's
            // own open animation (by style) handles its entrance — don't let the
            // sidebar state machine re-drive panelX (slides the whole panel)
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

        input = new TextFieldWidget(textRenderer, inputX, ibY + 3, inputW, INPUT_H, com.niuqu.chatbubble.Txt.literal(""));
        input.setMaxLength(256);
        input.setDrawsBackground(false);
        int editColor = theme() == ChatBubbleTheme.LIGHT ? c().textSecondary() : c().textPrimary();
        input.setEditableColor(editColor);
        input.setUneditableColor(c().textMuted());
        input.setText(initialText.isEmpty() && ChatBubbleClientSetup.config().preserveInput() && !savedInput.isEmpty() ? savedInput : initialText);
        input.setChangedListener(this::onEdited);
        input.setFocusUnlocked(false);
        GuiCompat.addDrawableChild(this, input);

        //#if MC >= 11900
        int cmdBgAlpha = theme() == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
                //#if MC >= 11900
                commandSuggestions = new ChatInputSuggestor(client, this, input, textRenderer,
                    false, false, 0, 8, true, ChatBubbleTheme.alphaBlend(c().panelBg(), cmdBgAlpha));
                commandSuggestions.setWindowActive(true);
                //#endif
        commandSuggestions.refresh();
        //#endif

        ensureIconsLoaded();

        sidebarSearchBox = new TextFieldWidget(textRenderer, 2, 5, SIDEBAR_W - 5, SIDEBAR_SEARCH_H, com.niuqu.chatbubble.Txt.literal(""));
        sidebarSearchBox.setMaxLength(20);
        sidebarSearchBox.setDrawsBackground(false);
        sidebarSearchBox.setEditableColor(editColor);
        sidebarSearchBox.setUneditableColor(editColor);
        sidebarSearchBox.setVisible(sidebarOpen);
        sidebarSearchBox.setChangedListener(s -> sidebarScrollOffset = 0);
        sidebarSearchBox.setFocusUnlocked(true);
        if (sidebarOpen) sidebarSearchBox.setX(2);
        GuiCompat.addDrawableChild(this, sidebarSearchBox);

        quickChatInput = new TextFieldWidget(textRenderer, 0, 0, QUICK_CHAT_W - 8, 12, com.niuqu.chatbubble.Txt.translatable("e33chat.menu.quick_chat"));
        quickChatInput.setMaxLength(256);
        quickChatInput.setDrawsBackground(false);
        quickChatInput.setEditableColor(editColor);
        quickChatInput.setUneditableColor(c().textMuted());
        quickChatInput.setVisible(false);
        quickChatInput.setFocusUnlocked(true);
        GuiCompat.addDrawableChild(this, quickChatInput);

        searchInput = new TextFieldWidget(textRenderer, 0, 0, 160, 12, com.niuqu.chatbubble.Txt.translatable("e33chat.menu.search"));
        searchInput.setMaxLength(128);
        searchInput.setDrawsBackground(false);
        searchInput.setEditableColor(editColor);
        searchInput.setUneditableColor(c().textMuted());
        searchInput.setVisible(false);
        searchInput.setChangedListener(this::onSearchEdited);
        searchInput.setFocusUnlocked(true);
        GuiCompat.addDrawableChild(this, searchInput);

        setFocused(input);
        // The chat field's initial text is set before setChangedListener binds,
        // so the open-time value (e.g. "/" from the chat key) never flows through
        // onEdited — sync it once so the IMBlocker IME state is correct.
        onEdited(input.getText());
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
            GuiCompat.setWidgetY(input, ibY + 3);
        }
    }

    private String getDisplayTitle() {
        if (whisperPartner != null) return whisperPartner;
        return com.niuqu.chatbubble.Txt.translatable("e33chat.sidebar.public").getString();
    }

    private float getSidebarAnimProgress() {
        if (!ChatBubbleClientSetup.config().animationEnabled()) return sidebarOpen ? 1f : 0f;
        AnimationStyle style = AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle());
        if (sidebarAnimating) {
            long elapsed = Util.getMeasuringTimeMs() - sidebarAnimStart;
            float t = MathHelper.clamp((float) elapsed / ANIM_MS, 0f, 1f);
            float progress = Animation.styleCurve(AnimationStyle.SLIDE, t);
            return sidebarTargetOpen ? progress : 1.0f - progress;
        }
        if (style == AnimationStyle.FADE || style == AnimationStyle.NONE) return sidebarOpen ? 1f : 0f;
        if (!sidebarOpen) return 0f;
        return getAnimProgress();
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

    private void renderSidebar(Object g, int mouseX, int mouseY) {
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.SIDEBAR_BG), 0, 0, 0f, 0f, SIDEBAR_W, height, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), SIDEBAR_W - 1, 0, 0f, 0f, 1, height, 1, 1);

        int y = 2;
        int itemH = SIDEBAR_ITEM_H;

        int sbx = 2;
        int sby = 2;
        int sbw = SIDEBAR_W - 5;
        int sbh = SIDEBAR_SEARCH_H;
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.INPUT_BG), sbx - 1, sby, 0f, 0f, sbw + 1, sbh, 1, 1);
        boolean hoverSearch = mouseX >= sbx - 1 && mouseX <= sbx + sbw && mouseY >= sby && mouseY <= sby + sbh;
        if (hoverSearch || sidebarSearchBox.isFocused())
            drawBorder(g, sbx - 1, sby, sbw + 1, sbh, c().textMuted());
        if (sidebarSearchBox.getText().isEmpty() && !sidebarSearchBox.isFocused()) {
            RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable("e33chat.sidebar.search").getString(), sbx, sby + 3, c().textMuted(), false);
        }
        y = sby + sbh + 3;

        boolean isPublic = whisperPartner == null;
        int pubBg = isPublic ? c().sidebarItemSelected()
            : (mouseX >= 0 && mouseX <= SIDEBAR_W && mouseY >= y && mouseY <= y + itemH ? c().sidebarItemHover() : 0);
        if (pubBg != 0) RenderHelper.fill(g, 0, y, SIDEBAR_W, y + itemH, pubBg);
        drawTextureIcon(g, iconTex("public_icon"), 2, y + 1, SIDEBAR_ICON_S);
        int nameX = 2 + SIDEBAR_ICON_S + 3;
        String publicLabel = com.niuqu.chatbubble.Txt.translatable("e33chat.sidebar.public").getString();
        RenderHelper.drawText(g, textRenderer, publicLabel, nameX, y + 1, c().textPrimary(), false);
        ChatMessageStore.ChatMessage latestPub = ChatMessageStore.getLatestPublicMessage();
        if (latestPub != null) {
            int previewMaxW = SIDEBAR_W - nameX - 4;
            String preview = ChatMessageStore.singleLine(latestPub.content().getString());
            String previewDisplay = textRenderer.trimToWidth(preview, previewMaxW - textRenderer.getWidth("..."));
            if (!previewDisplay.equals(preview)) previewDisplay += "...";
            RenderHelper.drawText(g, textRenderer, previewDisplay, nameX, y + 1 + textRenderer.fontHeight, c().textMuted(), false);
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
                //#if MC >= 12109
                String name = info.getProfile().name();
                //#else
                //$$ String name = info.getProfile().getName();
                //#endif
                if (name.equals(selfName)) continue;
                if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                if (ChatBubbleClientSetup.config().isSidebarHidden(name)) continue;
                totalH += itemH + 2;
            }

            if (totalH == 0) {
                int iconS = 32;
                drawTextureIcon(g, iconTex("no_online"), (SIDEBAR_W - iconS) / 2, startY + 8, iconS);
                String noPlayers = com.niuqu.chatbubble.Txt.translatable("e33chat.sidebar.no_players").getString();
                int textW = textRenderer.getWidth(noPlayers);
                RenderHelper.drawText(g, textRenderer, noPlayers,
                    (SIDEBAR_W - textW) / 2, startY + 8 + iconS + 4, c().textMuted(), false);
            } else {
                int maxSideScroll = Math.max(0, totalH - (visibleBottom - startY));
                sidebarMaxScroll = maxSideScroll;
                if (sidebarScrollOffset > maxSideScroll) sidebarScrollOffset = maxSideScroll;

                RenderHelper.enableScissor(g, 0, startY, SIDEBAR_W, visibleBottom);
                int scrollY = startY - sidebarScrollOffset;
                for (var info : players) {
                    //#if MC >= 12109
                    String name = info.getProfile().name();
                    //#else
                    //$$ String name = info.getProfile().getName();
                    //#endif
                    if (name.equals(selfName)) continue;
                    if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                    if (ChatBubbleClientSetup.config().isSidebarHidden(name)) continue;

                    if (scrollY + itemH > startY && scrollY < visibleBottom) {
                        boolean sel = name.equals(whisperPartner);
                        int itemBg = sel ? c().sidebarItemSelected()
                            : (mouseX >= 0 && mouseX <= SIDEBAR_W && mouseY >= scrollY && mouseY <= scrollY + itemH ? c().sidebarItemHover() : 0);
                        if (itemBg != 0) RenderHelper.fill(g, 0, scrollY, SIDEBAR_W, scrollY + itemH, itemBg);

                    //#if MC >= 12004
                    //#if MC >= 12109
        SkinTextures skin = getSkin(info.getProfile().id(), info.getProfile().name());
        //#else
        //$$ SkinTextures skin = getSkin(info.getProfile().getId(), info.getProfile().getName());
        //#endif
                        drawPlayerHead(g, skin, 4, scrollY + 3, 16, 18);
                    //#else
                        Identifier skinTex = getSkinIdentifier(
                            info.getProfile().getId(), info.getProfile().getName());
                        drawPlayerHead(g, skinTex, 4, scrollY + 3, 16);
                    //#endif

                        int tipW = ChatMessageStore.hasUnreadWhisper(name) ? 16 : 0;
                        int maxNameW = SIDEBAR_W - nameX - 4 - tipW - 2;
                        String displayName = textRenderer.trimToWidth(name, maxNameW - textRenderer.getWidth("..."));
                        if (!displayName.equals(name)) displayName += "...";
                        RenderHelper.drawText(g, textRenderer, displayName, nameX, scrollY + 1, c().textPrimary(), false);

                        ChatMessageStore.ChatMessage latest = ChatMessageStore.getLatestWhisperWith(name);
                        if (latest != null) {
                            String preview = ChatMessageStore.singleLine(latest.content().getString());
                            String previewDisplay = textRenderer.trimToWidth(preview, maxNameW - textRenderer.getWidth("..."));
                            if (!previewDisplay.equals(preview)) previewDisplay += "...";
                            RenderHelper.drawText(g, textRenderer, previewDisplay, nameX, scrollY + 1 + textRenderer.fontHeight, c().textMuted(), false);
                        }

                        if (ChatMessageStore.hasUnreadWhisper(name)) {
                            int tipX = SIDEBAR_W - 16 - 2;
                            int tipY = scrollY + 3 + (int) (Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 3);
                            drawTextureIcon(g, iconTex("private_tip"), tipX, tipY, 16);
                        }
                    }
                    scrollY += itemH + 2;
                }
                RenderHelper.disableScissor(g);
            }
        }
    }

    private void renderSidebar(Object g, int mouseX, int mouseY, float alpha) {
        renderSidebar(g, mouseX, mouseY);
    }

    private void insertMention(String name) {
        String text = input.getText();
        int atIdx = text.lastIndexOf('@');
        input.setText(text.substring(0, atIdx) + "@" + name + " ");
        //#if MC >= 12004
        input.setCursorToEnd(false);
        //#else
        //$$ input.setCursorToEnd();
        //#endif
        showMentions = false;
        mentionNavigated = false;
    }

    private void onEdited(String text) {
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
                    //#if MC >= 12109
                    String name = info.getProfile().name();
                    //#else
                    //$$ String name = info.getProfile().getName();
                    //#endif
                    if (name.toLowerCase().contains(mentionFilter))
                        mentionCandidates.add(name);
                }
                mentionCandidates.sort(String::compareToIgnoreCase);
                mentionIdx = 0;
                showMentions = !mentionCandidates.isEmpty();
            }
        }
        //#if MC >= 11900
        if (commandSuggestions != null) {
            commandSuggestions.refresh();
        }
        //#endif
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
            GuiCompat.setScreen(client, null);
    }

    //#if MC >= 12004
    //#if MC >= 26000
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    //#else
    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
    //#endif
        // no-op: disable vanilla blur
    }
    //#else
    //#if MC >= 12000
    //$$ @Override
    //$$ public void renderBackground(DrawContext g) {
    //$$     // no-op: disable vanilla blur
    //$$ }
    //#else
    //$$ @Override
    //$$ public void renderBackground(MatrixStack g) {
    //$$     // no-op: disable vanilla blur
    //$$ }
    //#endif
    //#endif

    private float getAnimProgress() {
        if (!ChatBubbleClientSetup.config().animationEnabled()) return 1.0f;
        AnimationStyle style = AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle());
        if (style == AnimationStyle.NONE) return 1.0f;
        long elapsed = Util.getMeasuringTimeMs() - animStart;
        float t = MathHelper.clamp((float) elapsed / ANIM_MS, 0f, 1f);
        if (closing) return 1.0f - (t * t);
        return Animation.styleCurve(style, t);
    }

    private void renderPopupWithAnim(Object g, long startMs, java.util.function.Function<Float, Runnable> renderer) {
        float alpha = 1f;
        float t = 1f;
        AnimationStyle style = AnimationStyle.parse(ChatBubbleClientSetup.config().popupAnimStyle());
        if (ChatBubbleClientSetup.config().animationEnabled() && style != AnimationStyle.NONE) {
            t = MathHelper.clamp((float) (Util.getMeasuringTimeMs() - startMs) / 150f, 0f, 1f);
            alpha = Animation.styleCurve(style, t);
        }
        Runnable render = renderer.apply(alpha);
        if (t >= 1f || style == AnimationStyle.NONE) { render.run(); return; }
        //#if MC >= 12000
        if (style == AnimationStyle.ZOOM) {
            RenderHelper.pushMatrix(g);
            float s = 0.85f + 0.15f * Animation.easeOutBack(alpha);
            RenderHelper.translate(g, width / 2f, height / 2f, 0);
            RenderHelper.scale(g, s, s, 1f);
            RenderHelper.translate(g, -width / 2f, -height / 2f, 0);
            render.run();
            RenderHelper.popMatrix(g);
        } else if (style == AnimationStyle.SLIDE) {
            RenderHelper.pushMatrix(g);
            RenderHelper.translate(g, 0, (1f - alpha) * 10f, 0);
            render.run();
            RenderHelper.popMatrix(g);
        } else {
            render.run();
        }
        //#else
        //$$ render.run();
        //#endif
    }

    @Override
    //#if MC >= 12109
    public boolean keyPressed(net.minecraft.client.input.KeyInput key) {
        int keyCode = key.key();
        int scanCode = key.scancode();
        int modifiers = key.modifiers();
    //#else
    //$$ public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //#endif
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
                GuiCompat.setWidgetFocused(sidebarSearchBox, false); setFocused(input); return true;
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

        //#if MC >= 11900
        //#if MC >= 12109
        if (commandSuggestions != null && commandSuggestions.keyPressed(key))
        //#else
        //$$ if (commandSuggestions != null && commandSuggestions.keyPressed(keyCode, scanCode, modifiers))
        //#endif
            return true;
        //#endif
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
        //#if MC >= 12109
        return super.keyPressed(key);
        //#else
        //$$ return super.keyPressed(keyCode, scanCode, modifiers);
        //#endif
    }

    @Override
    //#if MC >= 12004
    public boolean mouseScrolled(double mouseX, double mouseY, double scrollX, double scrollY) {
    //#else
    //$$ public boolean mouseScrolled(double mouseX, double mouseY, double scrollY) {
    //#endif
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
        //#if MC >= 11900
        if (commandSuggestions != null && commandSuggestions.mouseScrolled(scrollY)) return true;
        //#endif
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
    //#if MC >= 12109
    public boolean mouseClicked(Click click, boolean inside) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
    //#else
    //$$ public boolean mouseClicked(double mouseX, double mouseY, int button) {
    //#endif
        // Panel contents are translated by panelOffset during the open/close slide;
        // undo the shift here so hit-testing matches what is drawn. The sidebar and
        // EditBox render outside that translate (they set their own x), so they keep
        // the original coordinate.
        double origX = mouseX;
        if (isPanelSliding()) mouseX -= currentPanelOffset();

        // @mention popup click
        if (showMentions && button == 0) {
            int popupX = GuiCompat.getWidgetX(input);
            int popupH = Math.min(mentionCandidates.size(), 8) * textRenderer.fontHeight + 4;
            int popupY = GuiCompat.getWidgetY(input) - popupH - 2;
            if (popupY < msgTop) popupY = GuiCompat.getWidgetY(input) + input.getHeight() + 2;
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
                setFocused(sidebarSearchBox); GuiCompat.setWidgetFocused(input, false); return true;
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
                    //#if MC >= 12109
                    String name = info.getProfile().name();
                    //#else
                    //$$ String name = info.getProfile().getName();
                    //#endif
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

        //#if MC >= 11900
        //#if MC >= 12109
        if (commandSuggestions != null && commandSuggestions.mouseClicked(click))
        //#else
        //$$ if (commandSuggestions != null && commandSuggestions.mouseClicked((int) origX, (int) mouseY, button))
        //#endif
            return true;
        //#endif

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
                if (ChatQuickChatPanel.isInsideInput((int) mouseX, (int) mouseY, panelX, panelW, barTop,
                        ChatBubbleClientSetup.config().quickChatPhrases().size())) {
                    quickChatInput.setVisible(true);
                    setFocused(quickChatInput);
                    //#if MC >= 12000
                    input.setFocused(false);
                    //#endif
                    return true;
                }
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
                    //#if MC >= 12004
                    input.setCursorToEnd(false);
                    //#else
                    //$$ input.setCursorToEnd();
                    //#endif
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
            if (style != null) {
                ClickEvent clickEvent = style.getClickEvent();
                if (clickEvent != null) {
	                    //#if MC >= 12105
		                    if (clickEvent instanceof ClickEvent.SuggestCommand sc) {
	                        input.setText(sc.command()); return true;
	                    }
	                    if (clickEvent instanceof ClickEvent.OpenFile of) {
	                        Util.getOperatingSystem().open(of.path()); return true;
	                    }
	                    if (clickEvent instanceof ClickEvent.OpenUrl url) {
	                        Util.getOperatingSystem().open(url.uri()); return true;
	                    }
	                    if (clickEvent instanceof ClickEvent.RunCommand cmd) {
	                        String command = cmd.command();
                        GuiCompat.sendCommand(client.player.networkHandler, command); return true;
	                    }
	                    if (clickEvent instanceof ClickEvent.CopyToClipboard ctc) {
	                        client.keyboard.setClipboard(ctc.value()); return true;
	                    }
	                    //#else
	                    //$$ if (clickEvent.getAction() == ClickEvent.Action.SUGGEST_COMMAND) {
	                    //$$     input.setText(clickEvent.getValue()); return true;
	                    //$$ }
	                    //$$ if (clickEvent.getAction() == ClickEvent.Action.OPEN_FILE) {
	                    //$$     Util.getOperatingSystem().open(clickEvent.getValue()); return true;
	                    //$$ }
	                    //$$ if (clickEvent.getAction() == ClickEvent.Action.OPEN_URL) {
	                    //$$     Util.getOperatingSystem().open(clickEvent.getValue()); return true;
	                    //$$ }
	                    //$$ if (clickEvent.getAction() == ClickEvent.Action.RUN_COMMAND) {
	                    //$$     String command = clickEvent.getValue();
	                    //$$     if (command.startsWith("/")) command = command.substring(1);
	                    //$$     GuiCompat.sendCommand(client.player.networkHandler, command); return true;
	                    //$$ }
	                    //$$ if (clickEvent.getAction() == ClickEvent.Action.COPY_TO_CLIPBOARD) {
	                    //$$     client.keyboard.setClipboard(clickEvent.getValue()); return true;
	                    //$$ }
	                    //#endif
                    return true;
                }
            }
        }
        //#if MC >= 12109
        return super.mouseClicked(click, inside);
        //#else
        //$$ return super.mouseClicked(origX, mouseY, button);
        //#endif
    }

    @Override
    //#if MC >= 12109
    public boolean mouseDragged(Click click, double dx, double dy) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
        double dragX = dx;
        double dragY = dy;
    //#else
    //$$ public boolean mouseDragged(double mouseX, double mouseY, int button, double dragX, double dragY) {
    //#endif
        if (scrollbarDragging && maxScroll > 0) {
            lastScrollTime = Util.getMeasuringTimeMs();
            int effBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
            int trackH = effBottom - msgTop;
            int thumbH = Math.max(MIN_THUMB_H, (int) ((long) trackH * trackH / messageTotalH));
            thumbH = Math.min(thumbH, trackH);
            int travelRange = trackH - thumbH;
            if (travelRange > 0) {
                int dragDelta = (int) mouseY - scrollbarDragStartY;
                float newTarget = MathHelper.clamp(scrollbarDragStartOffset + (int) ((long) dragDelta * maxScroll / travelRange), 0, maxScroll);
                scrollAnimFrom = scrollOffset; scrollAnimTo = newTarget;
                scrollAnimStart = Util.getMeasuringTimeMs();
                if (!scrollAnimActive) { scrollAnimDuration = 80; scrollAnimActive = true; }
            }
            return true;
        }
        //#if MC >= 12109
        return super.mouseDragged(click, dx, dy);
        //#else
        //$$ return super.mouseDragged(mouseX, mouseY, button, dragX, dragY);
        //#endif
    }

    @Override
    //#if MC >= 12109
    public boolean mouseReleased(Click click) {
        double mouseX = click.x();
        double mouseY = click.y();
        int button = click.button();
    //#else
    //$$ public boolean mouseReleased(double mouseX, double mouseY, int button) {
    //#endif
        if (scrollbarDragging) { scrollbarDragging = false; return true; }
        //#if MC >= 12109
        return super.mouseReleased(click);
        //#else
        //$$ return super.mouseReleased(mouseX, mouseY, button);
        //#endif
    }

    private boolean handleIconClick(int mx, int my) {
        int iconY = barTop + (BAR_H - ICON_S) / 2;
        int gearX = panelX + 4;
        if (mx >= gearX && mx <= gearX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (emojiPanel.visible) emojiPanel.visible = false;
            if (searchPanel.visible) closeSearchPanel();
            boolean opening = !settingsMenu.visible;
            settingsMenu.visible = opening;
            if (opening) settingsAnimStart = Util.getMeasuringTimeMs();
            return true;
        }
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;
        if (mx >= emojiX && mx <= emojiX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (settingsMenu.visible) settingsMenu.visible = false;
            if (searchPanel.visible) closeSearchPanel();
            boolean opening = !emojiPanel.visible;
            emojiPanel.visible = opening;
            if (opening) emojiAnimStart = Util.getMeasuringTimeMs();
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
        int menuH = CTX_ITEM_H * 3 + 4;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;
        if (mx >= menuX && mx <= menuX + CTX_W) {
            ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
            String name = msg != null ? msg.rawPlayerName() : null;
            if (name == null || name.isEmpty()) { contextAvatarIndex = -1; return; }
            if (my >= menuY && my <= menuY + CTX_ITEM_H) {
                GuiCompat.sendCommand(client.player.networkHandler, (ChatMessageStore.useTpa() ? "tpa " : "tp ") + name);
            } else if (my >= menuY + CTX_ITEM_H + 2 && my <= menuY + CTX_ITEM_H * 2 + 2) {
                whisperPartner = name;
                ChatMessageStore.clearUnreadWhisper(name);
                if (sidebarSearchBox != null) sidebarSearchBox.setText("");
                setFocused(input); scrollToBottom = true;
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
        boolean nowBlocked = ChatMessageStore.isPlayerBlocked(
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
    //#if MC >= 12000
    //#if MC >= 26000
    public void extractRenderState(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    //#else
    public void render(DrawContext g, int mouseX, int mouseY, float delta) {
    //#endif
    //#else
    //$$ public void render(MatrixStack g, int mouseX, int mouseY, float delta) {
    //#endif
        tickSidebarAnimation();

        float anim = getAnimProgress();
        AnimationStyle pstyle = AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle());
        int panelOffset = (pstyle == AnimationStyle.SLIDE) ? currentPanelOffset() : 0;
        boolean zoom = (pstyle == AnimationStyle.ZOOM) && anim < 1f;
        float panelScale = 1f;
        if (zoom) panelScale = 0.8f + 0.2f * Animation.easeOutBack(anim);

        //#if MC >= 12106
        RenderHelper.pushMatrix(g);
        //#else
        //$$ RenderHelper.pushMatrix(g);
        //#endif
        //#if MC >= 12106
        RenderHelper.translate(g, panelOffset, 0);
        //#else
        //$$ RenderHelper.translate(g, panelOffset, 0, 0);
        //#endif
        if (zoom) {
            float cx = panelX + panelW / 2f;
            RenderHelper.translate(g, cx, height / 2f, 0);
            RenderHelper.scale(g, panelScale, panelScale, 1f);
            RenderHelper.translate(g, -cx, -height / 2f, 0);
        }

        float panelOpacity = ChatBubbleClientSetup.config().panelOpacity() / 100f * anim;
        currentPanelAlpha = anim;
        int fillLeft = (!sidebarAnimating && sidebarOpen && pstyle == AnimationStyle.SLIDE)
            ? (int)(anim * SIDEBAR_W) : panelX;
        if (ChatBubbleClientSetup.config().blurEnabled() && panelOpacity < 0.999f && !zoom) {
            BlurRenderer.blurPanel(g, fillLeft, 0, panelX + panelW - fillLeft, height);
        }
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.PANEL_BG),
            fillLeft, 0, panelX + panelW - fillLeft, height, panelOpacity);

        // Apply panel animation alpha to all content elements (not just background)
        if (anim < 0.999f) {
            //#if MC >= 12102
            // MC >= 1.21.2: alpha through color parameters (setShaderColor removed)
            RenderHelper.setAlphaMultiplier(anim);
            //#else
            //#if MC >= 12000
            //$$ ((DrawContext) g).draw();
            //#endif
            //#if MC >= 11700
            //$$ RenderSystem.setShaderColor(1f, 1f, 1f, anim);
            //#else
            //$$ RenderSystem.color4f(1f, 1f, 1f, anim);
            //#endif
            //#endif
        }

        renderTitleBar(g, mouseX, mouseY, panelOpacity);
        renderMessages(g, mouseX, mouseY);
        Style hovered = getHoveredStyle(mouseX, mouseY);
        if (hovered != null && hovered.getHoverEvent() != null) {
            //#if MC >= 12000
            //#if MC < 26000
            g.drawHoverEvent(textRenderer, hovered, mouseX, mouseY);
            //#endif
            //#endif
        }

        //#if MC >= 12106
        RenderHelper.translate(g, 0, 0);
        //#else
        //$$ RenderHelper.translate(g, 0.0F, 0.0F, 50.0F);
        //#endif
        renderNotificationBar(g, mouseX, mouseY);
        renderReplyBar(g, mouseX, mouseY);
        renderContextMenu(g, mouseX, mouseY);
        renderAvatarContextMenu(g, mouseX, mouseY);
        renderToast(g);
        renderPopupWithAnim(g, settingsAnimStart, a -> () -> settingsMenu.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, ChatBubbleScreen::iconTex, a));
        renderPopupWithAnim(g, emojiAnimStart, a -> () -> emojiPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, ICON_S, PAD, a));
        renderPopupWithAnim(g, quickAnimStart, a -> () -> quickChatPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, quickChatInput, a));
        renderPopupWithAnim(g, searchAnimStart, a -> () -> searchPanel.render(g, mouseX, mouseY, textRenderer, c(), panelX, panelW, barTop, searchInput, searchMatches, searchMatchIdx, a));
        if (quickChatPanel.visible && quickChatInput != null) quickChatInput.render(g, mouseX, mouseY, delta);
        if (searchPanel.visible && searchInput != null) searchInput.render(g, mouseX, mouseY, delta);
        renderBottomBar(g, mouseX, mouseY, panelOpacity);
        renderMentionPopup(g, mouseX, mouseY);

        RenderHelper.enableScissor(g, panelX, 0, panelX + panelW, height);
        //#if MC >= 11900
        if (commandSuggestions != null) commandSuggestions.render(g, mouseX, mouseY);
        //#endif
        RenderHelper.disableScissor(g);

        // Note: panel animation alpha is NOT reset here — it must stay active
        // through sidebar + super.render() so those elements also fade.
        // Reset happens after super.render(), before the banner.

        //#if MC >= 12106
        RenderHelper.popMatrix(g);
        //#else
        //$$ RenderHelper.popMatrix(g);
        //#endif

        if (sidebarOpen || sidebarAnimating) {
            //#if MC >= 12106
            RenderHelper.pushMatrix(g);
            //#else
            //$$ RenderHelper.pushMatrix(g);
            //#endif
            if (zoom) {
                float cx = panelX + panelW / 2f;
                RenderHelper.translate(g, cx, height / 2f, 0);
                RenderHelper.scale(g, panelScale, panelScale, 1f);
                RenderHelper.translate(g, -cx, -height / 2f, 0);
            }
            boolean fadeSidebar = !sidebarAnimating && (pstyle == AnimationStyle.FADE || zoom);
            int sidebarOffset = (closing && !fadeSidebar)
		                ? (int) ((getAnimProgress() - 1.0f) * SIDEBAR_W)
		                : (fadeSidebar ? 0 : getSidebarScreenX());
		            //#if MC >= 12106
            RenderHelper.translate(g, sidebarOffset, 0);
            //#else
            //$$ RenderHelper.translate(g, sidebarOffset, 0, 0);
            //#endif
            renderSidebar(g, mouseX - sidebarOffset, mouseY, fadeSidebar ? getAnimProgress() : 1f);
            //#if MC >= 12106
            RenderHelper.popMatrix(g);
            //#else
            //$$ RenderHelper.popMatrix(g);
            //#endif
            if (closing) sidebarSearchBox.setX(2 + sidebarOffset);
        }

        //#if MC >= 12106
        RenderHelper.pushMatrix(g);
        //#else
        //$$ RenderHelper.pushMatrix(g);
        //#endif
        //#if MC >= 12106
        RenderHelper.translate(g, 0, 0);
        //#else
        //$$ RenderHelper.translate(g, 0.0F, 0.0F, 50.0F);
        //#endif
        input.setX(inputX + panelOffset);
        super.render(g, mouseX, mouseY, delta);
        //#if MC >= 12106
        RenderHelper.popMatrix(g);
        //#else
        //$$ RenderHelper.popMatrix(g);
        //#endif

        // Flush and reset panel animation alpha (after sidebar + super.render)
        if (currentPanelAlpha < 0.999f) {
            //#if MC >= 12102
            // MC >= 1.21.2: alpha through color parameters (setShaderColor removed)
            RenderHelper.resetAlphaMultiplier();
            //#else
            //#if MC >= 12000
            //$$ ((DrawContext) g).draw();
            //#endif
            //#if MC >= 11700
            //$$ RenderSystem.setShaderColor(1f, 1f, 1f, 1f);
            //#else
            //$$ RenderSystem.color4f(1f, 1f, 1f, 1f);
            //#endif
            //#endif
        }

        // Render notification banner on top of the chat screen
        //#if MC >= 26000
        MentionNotificationBanner.INSTANCE.tick();
        //#endif
        //#if MC >= 12106
        // MC >= 1.21.6: use createNewRootLayer to ensure banner renders above panel content
        // (the new pipeline sorts by render layer, not submission order)
        ((DrawContext) g).createNewRootLayer();
        RenderHelper.pushMatrix(g);
        RenderHelper.translate(g, 0, 0);
        //#else
        //$$ RenderHelper.pushMatrix(g);
        //$$ RenderHelper.translate(g, 0.0F, 0.0F, 300.0F);
        //#endif
        MentionNotificationBanner.INSTANCE.render(g, width, height);
        //#if MC >= 12106
        RenderHelper.popMatrix(g);
        //#else
        //$$ RenderHelper.popMatrix(g);
        //#endif
    }

    private void renderTitleBar(Object g, int mouseX, int mouseY) {
        int ty = titleY;
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.TITLE_BAR), panelX, ty, 0f, 0f, panelW, TITLE_H, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), panelX, ty + TITLE_H, 0f, 0f, panelW, 1, 1, 1);

        int menuX = panelX + 3;
        int menuY = ty + (TITLE_H - ICON_S) / 2;
        boolean hoverMenu = mouseX >= menuX && mouseX <= menuX + ICON_S && mouseY >= menuY && mouseY <= menuY + ICON_S;
        if (hoverMenu) RenderHelper.fill(g, menuX - 1, menuY - 1, menuX + ICON_S + 1, menuY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("menu"), menuX, menuY, ICON_S);

        String title = getDisplayTitle();
        int titleW = textRenderer.getWidth(title);
        int titleX = UiLayout.centerX(panelX, panelW, titleW);
        int titleTextY = ty + (TITLE_H - textRenderer.fontHeight) / 2;
        RenderHelper.drawText(g, textRenderer, title, titleX, titleTextY, c().textPrimary(), false);

        String time = LocalTime.now().format(TIME_FMT);
        int timeW = textRenderer.getWidth(time);
        RenderHelper.drawText(g, textRenderer, time,
            panelX + panelW - PAD - 20 - timeW, ty + (TITLE_H - textRenderer.fontHeight) / 2, c().timeColor(), false);

        int closeX = panelX + panelW - 18;
        int closeY = ty + 6;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        int closeBg = hoverClose ? c().closeHoverBg() : c().closeBg();
        RenderHelper.fill(g, closeX, closeY, closeX + 12, closeY + 12, closeBg);
        RenderHelper.drawText(g, textRenderer, "✕", closeX + 6 - textRenderer.getWidth("✕") / 2, closeY + 2, c().closeText(), false);
    }

    private void renderTitleBar(Object g, int mouseX, int mouseY, float alpha) {
        renderTitleBar(g, mouseX, mouseY);
    }

    private boolean isMouseOverHamburger(double mx, double my) {
        int menuX = panelX + 3;
        int menuY = titleY + (TITLE_H - ICON_S) / 2;
        return mx >= menuX && mx <= menuX + ICON_S && my >= menuY && my <= menuY + ICON_S;
    }

    private void renderMessages(Object g, int mouseX, int mouseY) {
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
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.WHISPER_BAR), panelX, indY, 0f, 0f, panelW, indicatorH, 1, 1);
            String modeText = com.niuqu.chatbubble.Txt.translatable("e33chat.whisper.mode").getString() + ": " + whisperPartner;
            int modeTW = textRenderer.getWidth(modeText);
            RenderHelper.drawText(g, textRenderer, modeText, panelX + (panelW - modeTW) / 2, indY + 2, c().textPrimary(), false);
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

        RenderHelper.enableScissor(g, panelX, effectiveMsgTop, panelX + panelW, effectiveMsgBottom);

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
        RenderHelper.disableScissor(g);
    }

    private void renderScrollbar(Object g, int mouseX, int mouseY, int effectiveMsgBottom) {
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

    private void renderTimeSeparator(Object g, long timeMillis, int y) {
        String text = ChatMessageStore.formatTime(timeMillis);
        int tw = textRenderer.getWidth(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        RenderHelper.fill(g, tx - 6, y + 2, tx + tw + 6, y + TIME_SEP_H - 2, ChatBubbleTheme.alphaBlend(c().toastBg(), 0x44));
        RenderHelper.drawText(g, textRenderer, text, tx, y + 3, c().timeColor(), false);
    }

    private List<OrderedText> wrapContent(Text c, int width) {
        List<Text> paras = new ArrayList<>();
        MutableText[] cur = { com.niuqu.chatbubble.Txt.empty() };
        c.visit((style, text) -> {
            int start = 0;
            for (int i = 0; i < text.length(); i++) {
                if (text.charAt(i) == '\n') {
                    if (i > start) cur[0].append(com.niuqu.chatbubble.Txt.literal(text.substring(start, i)).fillStyle(style));
                    paras.add(cur[0]);
                    cur[0] = com.niuqu.chatbubble.Txt.empty();
                    start = i + 1;
                }
            }
            if (start < text.length()) cur[0].append(com.niuqu.chatbubble.Txt.literal(text.substring(start)).fillStyle(style));
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
        if (AnimationStyle.parse(ChatBubbleClientSetup.config().panelAnimStyle()) != AnimationStyle.SLIDE)
            return 0;
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

    private void renderBubble(Object g, ChatMessageStore.ChatMessage msg, int index, int baseY, int mouseX, int mouseY) {
        // Message entrance animation
        AnimationStyle msgStyle = AnimationStyle.parse(ChatBubbleClientSetup.config().messageAnimStyle());
        float msgAlpha = 1f;
        int msgSlideX = 0;
        float msgScale = 1f;

        if (ChatBubbleClientSetup.config().animationEnabled() && msgStyle != AnimationStyle.NONE) {
            long firstSeen = messageAnimStart.computeIfAbsent(index,
                k -> Util.getMeasuringTimeMs());
            long elapsed = Util.getMeasuringTimeMs() - firstSeen;
            if (elapsed < MSG_ANIM_MS) {
                float t = MathHelper.clamp((float) elapsed / MSG_ANIM_MS, 0f, 1f);
                msgAlpha = Animation.styleCurve(msgStyle, t);
                if (msgStyle == AnimationStyle.SLIDE) {
                    msgSlideX = msg.isOwn()
                        ? (int)((1f - msgAlpha) * 24)
                        : -(int)((1f - msgAlpha) * 24);
                }
                if (msgStyle == AnimationStyle.ZOOM) {
                    msgScale = 0.85f + 0.15f * Animation.easeOutBack(msgAlpha);
                }
            }
        }

        float effectiveAlpha = msgAlpha * currentPanelAlpha;
        boolean needsShaderAlpha = effectiveAlpha < 0.999f;

        if (needsShaderAlpha) {
            //#if MC >= 12102
            // MC >= 1.21.2: alpha through color parameters (setShaderColor removed)
            RenderHelper.setAlphaMultiplier(effectiveAlpha);
            //#else
            //#if MC >= 12000
            //$$ ((DrawContext) g).draw();
            //#endif
            //#if MC >= 11700
            //$$ RenderSystem.setShaderColor(1f, 1f, 1f, effectiveAlpha);
            //#else
            //$$ RenderSystem.color4f(1f, 1f, 1f, effectiveAlpha);
            //#endif
            //#endif
        }

        boolean useTransform = msgSlideX != 0 || msgScale != 1f;
        if (useTransform) {
            //#if MC >= 12106
            RenderHelper.pushMatrix(g);
            //#else
            //$$ RenderHelper.pushMatrix(g);
            //#endif
            if (msgScale != 1f) {
                float cx = panelX + panelW / 2f;
                RenderHelper.translate(g, cx, baseY, 0);
                RenderHelper.scale(g, msgScale, msgScale, 1f);
                RenderHelper.translate(g, -cx, -baseY, 0);
            }
            if (msgSlideX != 0) {
                //#if MC >= 12106
                RenderHelper.translate(g, msgSlideX, 0);
                //#else
                //$$ RenderHelper.translate(g, msgSlideX, 0, 0);
                //#endif
            }
        }

        renderBubbleInner(g, msg, index, baseY, mouseX, mouseY);

        if (useTransform) {
            //#if MC >= 12106
            RenderHelper.popMatrix(g);
            //#else
            //$$ RenderHelper.popMatrix(g);
            //#endif
        }

        if (needsShaderAlpha) {
            //#if MC >= 12102
            // MC >= 1.21.2: restore panel-level alpha (or full if panel not animating)
            float restoreAlpha = currentPanelAlpha < 0.999f ? currentPanelAlpha : 1f;
            RenderHelper.setAlphaMultiplier(restoreAlpha);
            //#else
            //#if MC >= 12000
            //$$ ((DrawContext) g).draw();
            //#endif
            //$$ // Restore panel-level alpha (or full if panel not animating)
            //$$ float restoreAlpha = currentPanelAlpha < 0.999f ? currentPanelAlpha : 1f;
            //#if MC >= 11700
            //$$ RenderSystem.setShaderColor(1f, 1f, 1f, restoreAlpha);
            //#else
            //$$ RenderSystem.color4f(1f, 1f, 1f, restoreAlpha);
            //#endif
            //#endif
        }
    }

    private void renderBubbleInner(Object g, ChatMessageStore.ChatMessage msg, int index, int baseY, int mouseX, int mouseY) {
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
                //#if MC >= 26000
                //$$ var cut = font.plainSubstrByWidth(sn.getString(), maxNameW - font.width("..."));
                //$$ nameSeq = Component.literal(cut + "...").getVisualOrderText();
                //#else
                var cut = textRenderer.trimToWidth(sn, maxNameW - textRenderer.getWidth("..."));
                nameSeq = Language.getInstance().reorder(
                    StringVisitable.concat(cut, StringVisitable.plain("...")));
                //#endif
            } else {
                nameSeq = sn.asOrderedText();
            }
            int nameW = textRenderer.getWidth(nameSeq);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            RenderHelper.drawText(g, textRenderer, nameSeq, startX, nameY, c().nameColor(), false);
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
        //#if MC >= 12004
        SkinTextures skin = getSkin(msg.senderUUID(), skinName);
        drawPlayerHead(g, skin, avatarX, avatarY, 20, 22);
        //#else
        Identifier skinTex = getSkinIdentifier(msg.senderUUID(), skinName);
        drawPlayerHead(g, skinTex, avatarX, avatarY, 20);
        //#endif

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = textRenderer.getWidth(label);
            int labelX, labelY = bubbleY + (bubbleH - textRenderer.fontHeight) / 2;
            if (own) { labelX = bubbleX - labelW - 3; } else { labelX = bubbleX + bubbleW + 3; }
            RenderHelper.drawText(g, textRenderer, label, labelX, labelY, c().duplicateLabel(), false);
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
            RenderHelper.drawText(g, textRenderer, quoteDisplay, quoteX + 4, quoteY + 2, c().textSecondary(), false);
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});

        if (index == searchHighlightIndex)
            drawBorder(g, bubbleX - 1, bubbleY - 1, bubbleW + 2, bubbleH + 2, ChatSearchPanel.HIGHLIGHT);
    }

    private void renderLineWithClicks(Object g, OrderedText line, int x, int y, int color) {
        renderLineWithClicks(g, line, x, y, color, null);
    }

    private void renderLineWithClicks(Object g, OrderedText line, int x, int y, int color, Style fallback) {
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
        RenderHelper.drawText(g, textRenderer, decorated, x, y, color, false);
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

    private void renderNotificationBar(Object g, int mouseX, int mouseY) {
        if (newMessageCount <= 0) return;
        int notifY = barTop - NOTIF_H;
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), panelX, notifY - 1, 0f, 0f, panelW, 1, 1, 1);
        int yellow = c().notificationText();
        int textY = notifY + (NOTIF_H - textRenderer.fontHeight) / 2;
        String ct = com.niuqu.chatbubble.Txt.translatable("e33chat.notif.new_messages", newMessageCount).getString() + " ▽";
        notifCountLeft = panelX + PAD;
        notifCountRight = notifCountLeft + textRenderer.getWidth(ct);
        notifBarTextY = textY;
        boolean h = mouseX >= notifCountLeft && mouseX <= notifCountRight
            && mouseY >= textY && mouseY <= textY + textRenderer.fontHeight;
        RenderHelper.drawText(g, textRenderer, ct, notifCountLeft, textY, h ? c().notificationText() : yellow, false);
        if (hasNewMentionOrQuote) {
            String mt = com.niuqu.chatbubble.Txt.translatable("e33chat.notif.mention").getString() + " ▽";
            notifMentionLeft = panelX + panelW - PAD - textRenderer.getWidth(mt);
            notifMentionRight = notifMentionLeft + textRenderer.getWidth(mt);
            h = mouseX >= notifMentionLeft && mouseX <= notifMentionRight
                && mouseY >= textY && mouseY <= textY + textRenderer.fontHeight;
            RenderHelper.drawText(g, textRenderer, mt, notifMentionLeft, textY, h ? c().notificationText() : yellow, false);
        } else {
            notifMentionLeft = -1; notifMentionRight = -1;
        }
    }

    private void renderContextMenu(Object g, int mouseX, int mouseY) {
        if (contextMsgIndex < 0) return;
        int menuH = CTX_ITEM_H * 2 + 2;
        int menuX = Math.min(contextX, panelX + panelW - CTX_W - 2);
        int menuY = contextY - menuH;
        if (menuY < msgTop) menuY = contextY + 4;

        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.CONTEXT_MENU_BG), menuX, menuY, 0f, 0f, CTX_W, menuH, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 0f, 0f, CTX_W, 1, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY + menuH - 1, 0f, 0f, CTX_W, 1, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 0f, 0f, 1, menuH, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), menuX + CTX_W - 1, menuY, 0f, 0f, 1, menuH, 1, 1);

        boolean hoverCopy = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        int copyBg = hoverCopy ? c().contextHover() : c().sidebarItemSelected();
        RenderHelper.fill(g, menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, copyBg);
        drawTextureIcon(g, iconTex("copy"), menuX + 5, menuY + 3, 12);
        RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable("e33chat.context.copy").getString(), menuX + 22, menuY + 4, c().textPrimary(), false);

        RenderHelper.fill(g, menuX + 4, menuY + CTX_ITEM_H, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 1, c().closeHoverBg());

        boolean hoverQuote = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 1 && mouseY <= menuY + menuH;
        int quoteBg = hoverQuote ? c().contextHover() : c().sidebarItemSelected();
        RenderHelper.fill(g, menuX + 1, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 1, menuY + menuH - 1, quoteBg);
        drawTextureIcon(g, iconTex("quote"), menuX + 5, menuY + CTX_ITEM_H + 3, 12);
        RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable("e33chat.context.quote").getString(), menuX + 22, menuY + CTX_ITEM_H + 5, c().textPrimary(), false);
    }

    private void renderAvatarContextMenu(Object g, int mouseX, int mouseY) {
        if (contextAvatarIndex < 0) return;
        int menuH = CTX_ITEM_H * 3 + 4;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;

        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.CONTEXT_MENU_BG), menuX, menuY, 0f, 0f, CTX_W, menuH, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 0f, 0f, CTX_W, 1, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY + menuH - 1, 0f, 0f, CTX_W, 1, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), menuX, menuY, 0f, 0f, 1, menuH, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), menuX + CTX_W - 1, menuY, 0f, 0f, 1, menuH, 1, 1);

        boolean hoverTp = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        int tpBg = hoverTp ? c().contextHover() : c().sidebarItemSelected();
        RenderHelper.fill(g, menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, tpBg);
        drawTextureIcon(g, iconTex("tp"), menuX + 5, menuY + 3, 12);
        RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable(ChatMessageStore.useTpa() ? "e33chat.context.tpa" : "e33chat.context.tp").getString(), menuX + 22, menuY + 4, c().textPrimary(), false);

        RenderHelper.fill(g, menuX + 4, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 2, c().closeHoverBg());

        boolean hoverWhisper = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 2 && mouseY <= menuY + CTX_ITEM_H * 2 + 2;
        int whBg = hoverWhisper ? c().contextHover() : c().sidebarItemSelected();
        RenderHelper.fill(g, menuX + 1, menuY + CTX_ITEM_H + 2, menuX + CTX_W - 1, menuY + CTX_ITEM_H * 2 + 2, whBg);
        drawTextureIcon(g, iconTex("whisper"), menuX + 5, menuY + CTX_ITEM_H + 4, 12);
        RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable("e33chat.context.whisper").getString(), menuX + 22, menuY + CTX_ITEM_H + 6, c().textPrimary(), false);

        RenderHelper.fill(g, menuX + 4, menuY + CTX_ITEM_H * 2 + 3, menuX + CTX_W - 4, menuY + CTX_ITEM_H * 2 + 4, c().closeHoverBg());

        boolean hoverBlock = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H * 2 + 4 && mouseY <= menuY + menuH;
        int blockBg = hoverBlock ? c().contextHover() : c().sidebarItemSelected();
        RenderHelper.fill(g, menuX + 1, menuY + CTX_ITEM_H * 2 + 4, menuX + CTX_W - 1, menuY + menuH - 1, blockBg);
        drawTextureIcon(g, iconTex("block"), menuX + 5, menuY + CTX_ITEM_H * 2 + 6, 12);
        ChatMessageStore.ChatMessage avaMsg = ChatMessageStore.getMessageAt(contextAvatarIndex);
        boolean isBlocked = avaMsg != null
            && ChatMessageStore.isPlayerBlocked(avaMsg.rawPlayerName(), avaMsg.senderName(),
                ChatBubbleClientSetup.config().blockedPlayers());
        RenderHelper.drawText(g, textRenderer, com.niuqu.chatbubble.Txt.translatable(isBlocked ? "e33chat.context.unblock" : "e33chat.context.block").getString(),
            menuX + 22, menuY + CTX_ITEM_H * 2 + 8, c().textPrimary(), false);
    }

    private static final int REPLY_BAR_H = 18;

    private void renderReplyBar(Object g, int mouseX, int mouseY) {
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
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), barX, barTop - notifOffset - 1, 0f, 0f, barW, 1, 1, 1);

        String sender = target.senderName().getString();
        if (sender.isEmpty()) sender = com.niuqu.chatbubble.Txt.translatable("e33chat.sender.system").getString();
        String preview = sender + ": " + target.content().getString();
        int maxW = barW - 24;
        String display = textRenderer.trimToWidth(preview, maxW - textRenderer.getWidth("..."));
        if (!display.equals(preview)) display += "...";
        RenderHelper.drawText(g, textRenderer, display, barX + 6, barY + 4, c().textSecondary(), false);

        int cx = barX + barW - 16;
        int cy = barY + 3;
        boolean hoverX = mouseX >= cx && mouseX <= cx + 12 && mouseY >= cy && mouseY <= cy + 12;
        int xBg = hoverX ? c().closeHoverBg() : c().sidebarItemSelected();
        RenderHelper.fill(g, cx, cy, cx + 12, cy + 12, xBg);
        RenderHelper.drawText(g, textRenderer, "✕", cx + 6 - textRenderer.getWidth("✕") / 2, cy + 2, c().closeText(), false);
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

    private void renderMentionPopup(Object g, int mouseX, int mouseY) {
        if (!showMentions || mentionCandidates.isEmpty()) return;
        int maxW = 60;
        for (String name : mentionCandidates) maxW = Math.max(maxW, textRenderer.getWidth(name));
        int popupW = maxW + 12;
        int visible = Math.min(mentionCandidates.size(), 8);
        int popupH = visible * textRenderer.fontHeight + 4;
        int popupX = GuiCompat.getWidgetX(input);
        int popupY = GuiCompat.getWidgetY(input) - popupH - 2;
        if (popupY < msgTop) popupY = GuiCompat.getWidgetY(input) + input.getHeight() + 2;

        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.POPUP_BG), popupX, popupY, 0f, 0f, popupW, popupH, 1, 1);
        drawBorder(g, popupX, popupY, popupW, popupH, c().divider());

        int startIdx = Math.max(0, mentionIdx - visible + 1);
        int endIdx = Math.min(mentionCandidates.size(), startIdx + visible);
        if (endIdx - startIdx < visible) startIdx = Math.max(0, endIdx - visible);
        for (int i = startIdx; i < endIdx; i++) {
            int ly = popupY + 2 + (i - startIdx) * textRenderer.fontHeight;
            if (i == mentionIdx)
                RenderHelper.fill(g, popupX + 1, ly, popupX + popupW - 1, ly + textRenderer.fontHeight, c().popupHover());
            RenderHelper.drawText(g, textRenderer, mentionCandidates.get(i), popupX + 4, ly, c().textPrimary(), false);
        }
    }

    private void renderToast(Object g) {
        if (copyToastTicks <= 0) return;
        int alpha = Animation.fadeInOut(copyToastTicks, 5, 20, 5);
        int color = (alpha << 24) | (c().toastText() & 0x00FFFFFF);
        String text = com.niuqu.chatbubble.Txt.translatable("e33chat.toast.copied").getString();
        int tw = textRenderer.getWidth(text);
        int tx = UiLayout.centerX(panelX, panelW, tw);
        int ty = msgBottom - 24;
        // Background fades with the text, at half opacity like the strong-hint bar
        RenderHelper.fill(g, tx - 6, ty - 2, tx + tw + 6, ty + textRenderer.fontHeight + 2, (alpha / 2) << 24);
        RenderHelper.drawText(g, textRenderer, text, tx, ty, color, false);
    }

    private void executeMenuAction(int action) {
        switch (action) {
            case 0: // search
                if (quickChatPanel.visible) { quickChatPanel.visible = false; quickChatInput.setVisible(false); }
                if (emojiPanel.visible) emojiPanel.visible = false;
                searchPanel.visible = true;
                searchAnimStart = Util.getMeasuringTimeMs();
                searchInput.setText("");
                searchMatches.clear(); searchMatchIdx = -1; searchHighlightIndex = -1;
                setFocused(searchInput);
                break;
            case 1: // quick_chat
                if (searchPanel.visible) closeSearchPanel();
                if (emojiPanel.visible) emojiPanel.visible = false;
                quickChatPanel.visible = true;
                quickAnimStart = Util.getMeasuringTimeMs();
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
                sidebarSearchBox.setUneditableColor(c().textMuted());
                quickChatInput.setEditableColor(editColor);
                quickChatInput.setUneditableColor(c().textMuted());
                searchInput.setEditableColor(editColor);
                searchInput.setUneditableColor(c().textMuted());
                //#if MC >= 11900
                int cmdAlpha = next == ChatBubbleTheme.LIGHT ? 0x99 : 0xDD;
                commandSuggestions = new ChatInputSuggestor(client, this, input, textRenderer,
                    false, false, 0, 8, true, ChatBubbleTheme.alphaBlend(c().panelBg(), cmdAlpha));
                commandSuggestions.setWindowActive(true);
                //#endif
                break;
            }
            case 3: // settings
                GuiCompat.setScreen(client, new ChatBubbleConfigScreen(this));
                break;
        }
    }

    private void closeSearchPanel() {
        searchPanel.visible = false;
        searchInput.setVisible(false);
        searchMatches.clear(); searchMatchIdx = -1; searchHighlightIndex = -1;
        setFocused(input);
    }

    private void renderBottomBar(Object g, int mouseX, int mouseY) {
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.BOTTOM_BAR), panelX, barTop, 0f, 0f, panelW, height - barTop, 1, 1);
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), panelX, barTop, 0f, 0f, panelW, 1, 1, 1);

        int iconY = barTop + (BAR_H - ICON_S) / 2;

        int ibX = inputX;
        int ibY = inputY;
        int ibW = input.getWidth();
        int ibH = INPUT_H;
        RenderHelper.drawTexture(g,UiTextureManager.rl(UiElement.DIVIDER), ibX - 1, ibY - 1, 0f, 0f, ibW + 1, 1, 1, 1);
        RenderHelper.fill(g, ibX - 1, ibY, ibX + ibW, ibY + ibH, c().inputBg());

        boolean hoverInput = mouseX >= ibX - 1 && mouseX <= ibX + ibW && mouseY >= ibY && mouseY <= ibY + ibH;
        if (hoverInput || input.isFocused())
            drawBorder(g, ibX - 1, ibY, ibW + 1, ibH, c().textMuted());

        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;

        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverGear) RenderHelper.fill(g, gearX - 1, iconY - 1, gearX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("settings"), gearX, iconY, ICON_S);

        boolean hoverEmoji = mouseX >= emojiX && mouseX <= emojiX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverEmoji || emojiPanel.visible) RenderHelper.fill(g, emojiX - 1, iconY - 1, emojiX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("emoji"), emojiX, iconY, ICON_S);

        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverSend) RenderHelper.fill(g, sendX - 1, iconY - 1, sendX + ICON_S + 1, iconY + ICON_S + 1, c().iconHover());
        drawTextureIcon(g, iconTex("send"), sendX, iconY, ICON_S);
    }

    private void renderBottomBar(Object g, int mouseX, int mouseY, float alpha) {
        renderBottomBar(g, mouseX, mouseY);
    }

    static void loadIconTextures() {
        String theme = ChatBubbleClientSetup.config().theme().toLowerCase();
        String base = "assets/e33chat/textures/gui/" + theme + "/";
        loadIconTexture(iconTex("chat_icon"), base + "chat_icon.png", "chat_icon");
        loadIconTexture(iconTex("settings"), base + "settings.png", "settings");
        loadIconTexture(iconTex("send"), base + "send.png", "send");
        loadIconTexture(iconTex("emoji"), base + "emoji.png", "emoji");
        loadIconTexture(iconTex("menu"), base + "menu.png", "menu");
        loadIconTexture(iconTex("public_icon"), base + "public_icon.png", "public_icon");
        loadIconTexture(iconTex("private_tip"), base + "private_tip.png", "private_tip");
        loadIconTexture(iconTex("no_online"), base + "no_online.png", "no_online");
        loadIconTexture(iconTex("theme"), base + "theme.png", "theme");
        loadIconTexture(iconTex("quick_chat"), base + "quick_chat.png", "quick_chat");
        loadIconTexture(iconTex("copy"), base + "copy.png", "copy");
        loadIconTexture(iconTex("quote"), base + "quote.png", "quote");
        loadIconTexture(iconTex("tp"), base + "tp.png", "tp");
        loadIconTexture(iconTex("whisper"), base + "whisper.png", "whisper");
        loadIconTexture(iconTex("search"), base + "search.png", "search");
    }

    static void loadIconTexture(Identifier loc, String classpath, String name) {
        try (InputStream in = ChatBubbleScreen.class.getClassLoader().getResourceAsStream(classpath)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                //#if MC >= 12105
                NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "icon", img);
                //#else
                //$$ NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
                //#endif
                MinecraftClient.getInstance().getTextureManager().registerTexture(loc, tex);
            } else {
                registerGeneratedIcon(loc, name);
            }
        } catch (Exception e) {
            e.printStackTrace();
            registerGeneratedIcon(loc, name);
        }
    }

    private static void registerGeneratedIcon(Identifier loc, String name) {
        NativeImage img = new NativeImage(16, 16, false);
        int clear = 0x00000000;
        int fg = "light".equalsIgnoreCase(ChatBubbleClientSetup.config().theme()) ? 0xFF222222 : 0xFFEFEFEF;
        int accent = "light".equalsIgnoreCase(ChatBubbleClientSetup.config().theme()) ? 0xFF3366CC : 0xFF66D9EF;
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                setIconPixel(img, x, y, clear);
            }
        }
        switch (name) {
            case "send" -> {
                line(img, 3, 3, 12, 8, accent);
                line(img, 3, 12, 12, 8, accent);
                line(img, 4, 8, 12, 8, accent);
                line(img, 4, 4, 4, 12, accent);
            }
            case "emoji" -> {
                rect(img, 3, 3, 10, 10, accent);
                dot(img, 6, 7, fg); dot(img, 10, 7, fg);
                line(img, 6, 11, 10, 11, fg);
            }
            case "settings" -> {
                rect(img, 6, 2, 4, 12, accent);
                rect(img, 2, 6, 12, 4, accent);
                rect(img, 5, 5, 6, 6, fg);
                rect(img, 7, 7, 2, 2, clear);
            }
            case "menu" -> {
                rect(img, 3, 4, 10, 2, fg);
                rect(img, 3, 7, 10, 2, fg);
                rect(img, 3, 10, 10, 2, fg);
            }
            case "copy" -> {
                outline(img, 4, 3, 7, 9, accent);
                outline(img, 7, 6, 6, 7, fg);
            }
            case "quote" -> {
                rect(img, 3, 4, 4, 5, accent);
                rect(img, 9, 4, 4, 5, accent);
                dot(img, 6, 10, accent); dot(img, 12, 10, accent);
            }
            case "search" -> {
                outline(img, 3, 3, 7, 7, accent);
                line(img, 9, 9, 13, 13, fg);
            }
            case "theme" -> {
                rect(img, 3, 3, 5, 10, accent);
                rect(img, 8, 3, 5, 10, fg);
            }
            case "quick_chat" -> {
                outline(img, 3, 3, 10, 8, accent);
                dot(img, 6, 7, fg); dot(img, 8, 7, fg); dot(img, 10, 7, fg);
                line(img, 6, 11, 4, 13, accent);
            }
            case "tp" -> {
                line(img, 3, 8, 12, 8, accent);
                line(img, 9, 5, 12, 8, accent);
                line(img, 9, 11, 12, 8, accent);
            }
            case "whisper", "private_tip" -> {
                rect(img, 5, 4, 6, 8, accent);
                rect(img, 7, 6, 2, 4, fg);
            }
            case "no_online" -> {
                outline(img, 4, 3, 8, 8, fg);
                line(img, 3, 13, 13, 3, accent);
            }
            case "public_icon" -> {
                outline(img, 3, 3, 10, 10, accent);
                line(img, 3, 8, 13, 8, fg);
                line(img, 8, 3, 8, 13, fg);
            }
            default -> {
                outline(img, 2, 2, 12, 12, accent);
                rect(img, 5, 5, 6, 6, fg);
            }
        }
        //#if MC >= 12105
        NativeImageBackedTexture tex = new NativeImageBackedTexture(() -> "generated_icon", img);
        //#else
        //$$ NativeImageBackedTexture tex = new NativeImageBackedTexture(img);
        //#endif
        MinecraftClient.getInstance().getTextureManager().registerTexture(loc, tex);
    }

    private static void setIconPixel(NativeImage img, int x, int y, int argb) {
        if (x < 0 || y < 0 || x >= 16 || y >= 16) return;
        //#if MC >= 12102
        img.setColorArgb(x, y, argb);
        //#else
        //#if MC >= 11800
        //$$ img.setColor(x, y, com.niuqu.chatbubble.texture.TextureGenerators.argbToAbgr(argb));
        //#else
        //$$ img.setPixelColor(x, y, com.niuqu.chatbubble.texture.TextureGenerators.argbToAbgr(argb));
        //#endif
        //#endif
    }

    private static void dot(NativeImage img, int x, int y, int color) {
        setIconPixel(img, x, y, color);
    }

    private static void rect(NativeImage img, int x, int y, int w, int h, int color) {
        for (int yy = y; yy < y + h; yy++) {
            for (int xx = x; xx < x + w; xx++) {
                setIconPixel(img, xx, yy, color);
            }
        }
    }

    private static void outline(NativeImage img, int x, int y, int w, int h, int color) {
        rect(img, x, y, w, 1, color);
        rect(img, x, y + h - 1, w, 1, color);
        rect(img, x, y, 1, h, color);
        rect(img, x + w - 1, y, 1, h, color);
    }

    private static void line(NativeImage img, int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0), sx = x0 < x1 ? 1 : -1;
        int dy = -Math.abs(y1 - y0), sy = y0 < y1 ? 1 : -1;
        int err = dx + dy;
        while (true) {
            setIconPixel(img, x0, y0, color);
            if (x0 == x1 && y0 == y1) break;
            int e2 = 2 * err;
            if (e2 >= dy) { err += dy; x0 += sx; }
            if (e2 <= dx) { err += dx; y0 += sy; }
        }
    }

    static void drawTextureIcon(Object g, Identifier tex, int x, int y, int size) {
        var tm = MinecraftClient.getInstance().getTextureManager();
        try {
            tm.getTexture(tex);
        } catch (Exception e) {
            loadIconTextures();
        }
        if (size < 16) {
            RenderHelper.drawTexture(g, tex, x, y, size, size, 1.0F, 1.0F, 14, 14, 16, 16);
        } else {
            RenderHelper.drawTexture(g, tex, x, y, 0, 0, size, size, size, size);
        }
    }

    static void drawTextureIconAlpha(Object g, Identifier tex, int x, int y, int size, float alpha) {
        if (alpha <= 0.003f) return;
        var tm = MinecraftClient.getInstance().getTextureManager();
        try {
            tm.getTexture(tex);
        } catch (Exception e) {
            loadIconTextures();
        }
        if (size < 16) {
            ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size, 1.0F, 1.0F, 14, 14, 16, 16, alpha);
        } else {
            ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size, 0f, 0f, size, size, size, size, alpha);
        }
    }

    private static final UUID NIL_UUID = new UUID(0, 0);

    private PlayerListEntry findOnlineSkinEntry(String name) {
        if (name == null || name.isEmpty() || client.getNetworkHandler() == null) return null;
        String stripped = name.replaceAll("§.", "").trim();
        for (PlayerListEntry info : client.getNetworkHandler().getPlayerList()) {
            //#if MC >= 12109
            String profile = info.getProfile().name();
            //#else
            //$$ String profile = info.getProfile().getName();
            //#endif
            if (matchesSkinName(name, stripped, profile)) return info;
            Text tab = info.getDisplayName();
            if (tab != null && matchesSkinName(name, stripped, tab.getString())) return info;
        }
        return null;
    }

    private static boolean matchesSkinName(String raw, String stripped, String candidate) {
        if (candidate == null || candidate.isEmpty()) return false;
        String c = candidate.replaceAll("§.", "").trim();
        return raw.equals(candidate) || stripped.equals(c)
            || (c.length() >= 3 && (stripped.contains(c) || raw.contains(candidate)));
    }

    private static String skinNameKey(String name) {
        if (name == null) return null;
        String canonical = ChatMessageStore.findSeenProfileName(name);
        String key = (canonical != null && !canonical.isEmpty() ? canonical : name)
            .replaceAll("§.", "").trim().toLowerCase(java.util.Locale.ROOT);
        return key.isEmpty() ? null : key;
    }

    //#if MC >= 12004
    private void drawPlayerHead(Object g, SkinTextures skin, int x, int y, int baseSize, int hatSize) {
        drawPlayerHead(g, skin, x, y, baseSize, hatSize, 1f);
    }

    private void drawPlayerHead(Object g, SkinTextures skin, int x, int y, int baseSize, int hatSize, float alpha) {
        if (skin == null || alpha <= 0.003f) return;
        //#if MC >= 12109
        Identifier tex = skin.body().texturePath();
        ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, baseSize, baseSize, 8f, 8f, 8, 8, 64, 64, alpha);
        int hatOff = (hatSize - baseSize) / 2;
        ColoredTextureRenderer.drawWithAlpha(g, tex, x - hatOff, y - hatOff, hatSize, hatSize, 40f, 8f, 8, 8, 64, 64, alpha);
        //#else
        //$$ Identifier tex = skin.texture();
        //$$ ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, baseSize, baseSize, 8f, 8f, 8, 8, 64, 64, alpha);
        //$$ int hatOff = (hatSize - baseSize) / 2;
        //$$ ColoredTextureRenderer.drawWithAlpha(g, tex, x - hatOff, y - hatOff, hatSize, hatSize, 40f, 8f, 8, 8, 64, 64, alpha);
        //#endif
    }

    private SkinTextures getSkin(UUID uuid, String name) {
        String canonicalName = ChatMessageStore.findSeenProfileName(name);
        if (canonicalName == null || canonicalName.isEmpty()) canonicalName = name;
        // Online players: read PlayerInfo fresh every frame — caching the first result
        // (default Steve/Alex while async download is in progress) would freeze the head
        // forever even after the real skin loaded. CSL intercepts the underlying lookup.
        // 读取到在线皮肤后仍写入常驻缓存；后续在线帧会继续刷新缓存，玩家离线/重进时复用上一次头像。
        if (client.getNetworkHandler() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            PlayerListEntry info = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (info != null) {
                SkinTextures textures = info.getSkinTextures();
                if (textures != null) {
                    //#if MC >= 12109
                    String profileName = info.getProfile().name();
                    //#else
                    //$$ String profileName = info.getProfile().getName();
                    //#endif
                    rememberSkin(uuid, profileName, textures);
                    rememberSkin(uuid, canonicalName, textures);
                    return textures;
                }
            }
        }
        PlayerListEntry onlineByName = findOnlineSkinEntry(canonicalName);
        if (onlineByName != null) {
            SkinTextures textures = onlineByName.getSkinTextures();
            if (textures != null) {
                //#if MC >= 12109
                UUID onlineUuid = onlineByName.getProfile().id();
                String profileName = onlineByName.getProfile().name();
                //#else
                //$$ UUID onlineUuid = onlineByName.getProfile().getId();
                //$$ String profileName = onlineByName.getProfile().getName();
                //#endif
                rememberSkin(onlineUuid, profileName, textures);
                rememberSkin(onlineUuid, canonicalName, textures);
                ChatMessageStore.rememberPlayer(onlineUuid, profileName, canonicalName);
                return textures;
            }
        }
        // Offline player / history mention: route through SkinProvider with a name-bearing
        // GameProfile so CSL can match offline names to imported skins. Cache this result.
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            SkinTextures cached = skinCache.get(uuid);
            if (cached != null) return cached;
        }
        String nameKey = skinNameKey(canonicalName);
        if (nameKey != null) {
            SkinTextures cachedByName = skinNameCache.get(nameKey);
            if (cachedByName != null) return cachedByName;
        }
        SkinTextures resolved = resolveSkin(uuid, canonicalName);
        rememberSkin(uuid, canonicalName, resolved);
        return resolved;
    }

    private void rememberSkin(UUID uuid, String name, SkinTextures skin) {
        if (skin == null) return;
        if (uuid != null && !uuid.equals(NIL_UUID)) skinCache.put(uuid, skin);
        String nameKey = skinNameKey(name);
        if (nameKey != null) skinNameCache.put(nameKey, skin);
    }

    private SkinTextures resolveSkin(UUID uuid, String name) {
        // Route through PlayerSkinProvider with a name-bearing GameProfile so CSL
        // can match offline players to imported skins. supplySkinTextures(GameProfile, boolean)
        // returns a Supplier<SkinTextures> that falls back to the default skin.
        if (name != null && !name.isEmpty()) {
            try {
                GameProfile profile = new GameProfile(
                    uuid != null && !uuid.equals(NIL_UUID) ? uuid : NIL_UUID, name);
            //#if MC >= 12109
            return client.getSkinProvider().supplySkinTextures(profile, false).get();
            //#else
            //$$ return client.getSkinProvider().getSkinTextures(profile);
            //#endif
            } catch (Exception ignored) {}
        }
        return DefaultSkinHelper.getSkinTextures(
            new GameProfile(uuid != null ? uuid : NIL_UUID, name != null ? name : ""));
    }
    //#else
    // Legacy skin rendering for MC < 1.20.4: uses Identifier + drawTexture
    // instead of PlayerSkinDrawer/SkinTextures which were added in 1.20.4.
    private static final java.util.Map<UUID, Identifier> legacySkinCache = new java.util.HashMap<>();
    private static final java.util.Map<String, Identifier> legacySkinNameCache = new java.util.HashMap<>();

    private void drawPlayerHead(Object g, Identifier skinTex, int x, int y, int size) {
        drawPlayerHead(g, skinTex, x, y, size, 1f);
    }

    private void drawPlayerHead(Object g, Identifier skinTex, int x, int y, int size, float alpha) {
        if (skinTex == null || alpha <= 0.003f) return;
        // Face: 8x8 region at UV (8,8) in the 64x64 skin texture
        ColoredTextureRenderer.drawWithAlpha(g, skinTex, x, y, size, size, 8f, 8f, 8, 8, 64, 64, alpha);
        // Hat overlay: 8x8 region at UV (40,8) in the 64x64 skin texture
        ColoredTextureRenderer.drawWithAlpha(g, skinTex, x, y, size, size, 40f, 8f, 8, 8, 64, 64, alpha);
    }

    private Identifier getSkinIdentifier(UUID uuid, String name) {
        String canonicalName = ChatMessageStore.findSeenProfileName(name);
        if (canonicalName == null || canonicalName.isEmpty()) canonicalName = name;
        // Online players: read fresh every frame so async skin downloads appear
        if (client.getNetworkHandler() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            PlayerListEntry info = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (info != null) {
                // getSkinTexture() returns Identifier directly in all MC < 1.20.4
                Identifier tex = info.getSkinTexture();
                if (tex != null) {
                    //#if MC >= 12109
                    String profileName = info.getProfile().name();
                    //#else
                    //$$ String profileName = info.getProfile().getName();
                    //#endif
                    rememberLegacySkin(uuid, profileName, tex);
                    rememberLegacySkin(uuid, canonicalName, tex);
                    return tex;
                }
            }
        }
        PlayerListEntry onlineByName = findOnlineSkinEntry(canonicalName);
        if (onlineByName != null) {
            Identifier tex = onlineByName.getSkinTexture();
            if (tex != null) {
                //#if MC >= 12109
                UUID onlineUuid = onlineByName.getProfile().id();
                String profileName = onlineByName.getProfile().name();
                //#else
                //$$ UUID onlineUuid = onlineByName.getProfile().getId();
                //$$ String profileName = onlineByName.getProfile().getName();
                //#endif
                rememberLegacySkin(onlineUuid, profileName, tex);
                rememberLegacySkin(onlineUuid, canonicalName, tex);
                ChatMessageStore.rememberPlayer(onlineUuid, profileName, canonicalName);
                return tex;
            }
        }
        // Offline player: check cache, then fall back to default skin
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            Identifier cached = legacySkinCache.get(uuid);
            if (cached != null) return cached;
        }
        String nameKey = skinNameKey(canonicalName);
        if (nameKey != null) {
            Identifier cachedByName = legacySkinNameCache.get(nameKey);
            if (cachedByName != null) return cachedByName;
        }
        Identifier fallback = DefaultSkinHelper.getTexture();
        rememberLegacySkin(uuid, canonicalName, fallback);
        return fallback;
    }

    private void rememberLegacySkin(UUID uuid, String name, Identifier skinTex) {
        if (skinTex == null) return;
        if (uuid != null && !uuid.equals(NIL_UUID)) legacySkinCache.put(uuid, skinTex);
        String nameKey = skinNameKey(name);
        if (nameKey != null) legacySkinNameCache.put(nameKey, skinTex);
    }
    //#endif

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
        if (s.indexOf('&') < 0) return com.niuqu.chatbubble.Txt.literal(s);
        MutableText out = com.niuqu.chatbubble.Txt.empty();
        Style style = Style.EMPTY;
        StringBuilder run = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c == '&' && i + 1 < s.length() && isFormatCode(s.charAt(i + 1))) {
                if (run.length() > 0) {
                    out.append(com.niuqu.chatbubble.Txt.literal(run.toString()).fillStyle(style));
                    run.setLength(0);
                }
                style = applyCode(style, s.charAt(i + 1));
                i++;
            } else {
                run.append(c);
            }
        }
        if (run.length() > 0) out.append(com.niuqu.chatbubble.Txt.literal(run.toString()).fillStyle(style));
        return out;
    }

    private static Style applyCode(Style st, char c) {
        return com.niuqu.chatbubble.Txt.applyFormattingCode(st, c);
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
                    //#if MC >= 12005
                    ClientPlayNetworking.send(new QuoteSyncPayload(quoteSender, quoted, displayText));
                    //#endif
                }
            }
            replyTargetIndex = -1;
        }

        if (text.startsWith("/"))
            GuiCompat.sendCommand(client.player.networkHandler, text);
        else
            GuiCompat.sendChat(client.player.networkHandler, text);
        client.inGameHud.getChatHud().addToMessageHistory(text);

        ChatMessageStore.debugLog("[e33chat] Send | cmd='" + text + "' | display='" + displayText + "' | whisperTarget=" + whisperTarget + " | localBubble=" + localBubble);
        if (localBubble) {
            Text contentForSend = cfg != null && cfg.colorCodes() ? parseColorCodes(displayText) : com.niuqu.chatbubble.Txt.literal(displayText);
            // Convert embedded image codes so the outgoing bubble previews the
            // image like the vanilla chat does (ChatImage may be absent — then
            // convert passes through unchanged)
            contentForSend = ChatImageCompat.convert(contentForSend);
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
                        com.niuqu.chatbubble.Txt.literal(playerName), contentForSend, false,
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
            GuiCompat.setScreen(client, null); return;
        }
        if (closing) return;
        closing = true;
        animStart = Util.getMeasuringTimeMs();
    }

    //#if MC >= 11700
    public boolean shouldPause() { return false; }
    //#else
    //$$ public boolean isPauseScreen() { return false; }
    //#endif

    private static void drawBorder(Object g, int x, int y, int w, int h, int color) {
        RenderHelper.fill(g, x, y, x + w, y + 1, color);
        RenderHelper.fill(g, x, y + h - 1, x + w, y + h, color);
        RenderHelper.fill(g, x, y + 1, x + 1, y + h - 1, color);
        RenderHelper.fill(g, x + w - 1, y + 1, x + w, y + h - 1, color);
    }

    private static class ClickableSpan {
        final int x, y, w, h;
        final Style style;
        ClickableSpan(int x, int y, int w, int h, Style style) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.style = style;
        }
    }
}
