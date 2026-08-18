package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.store.BlockList;
import com.niuqu.chatbubble.ChatBubbleMod;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.config.ChatBubbleConfigScreen;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.render.Animation;
import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.render.AnimationStyle;
import com.niuqu.chatbubble.render.UiLayout;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.render.BlurRenderer;
import com.niuqu.chatbubble.compat.NativeFileDialog;
import com.niuqu.chatbubble.compat.IMBlockerCompat;
import com.niuqu.chatbubble.ui.BedScreen;
import com.niuqu.chatbubble.ui.EmoteStore;
import com.niuqu.chatbubble.ui.ChatQuickChatPanel;
import com.niuqu.chatbubble.ui.ChatSearchPanel;
import com.niuqu.chatbubble.ui.ChatSettingsMenu;
import com.niuqu.chatbubble.ui.ChatEmojiPanel;

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
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.chat.notification.MentionNotificationBanner;
import com.niuqu.chatbubble.packets.QuoteSyncPayload;
import com.niuqu.chatbubble.render.ChatBars;
import com.niuqu.chatbubble.render.ChatContextMenus;
import com.niuqu.chatbubble.render.ChatLayout;
import com.niuqu.chatbubble.image.ImageLoader;
import com.niuqu.chatbubble.image.ImageUploader;
import com.niuqu.chatbubble.image.LocalImageSource;
import com.niuqu.chatbubble.render.ChatMessageRenderer;
import com.niuqu.chatbubble.render.ChatScrollbar;
import com.niuqu.chatbubble.render.ChatSidebar;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
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
    private static final int NAME_H = 10;
    private static final int TIME_SEP_H = 14;
    public static final int BAR_H = 26;
    private static final int SIDEBAR_W = 90;
    private static final int SIDEBAR_ITEM_H = 22;
    private static final int SIDEBAR_ICON_S = 20;

    private ChatBubbleTheme.Colors c() {
        return Appearance.snapshot();
    }

    private static final int INPUT_H = 14;
    private static final int ICON_S = 14;

    public static ResourceLocation iconTex(String name) {
        String theme = ChatBubbleConfig.THEME.get().name().toLowerCase();
        return ResourceLocation.fromNamespaceAndPath(ChatBubbleMod.MODID, "textures/gui/" + theme + "/" + name + ".png");
    }




    private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm");

    private static String timeKey(long t) {
        return ChatMessageRenderer.timeKey(t, ChatBubbleConfig.TIME_SEPARATOR_MINUTES.get());
    }

    private static int inputX, inputY;
    // Caches resolved head skins per player uuid so the SkinManager isn't hit every frame
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
    // Popup close animation timestamps (0 = not closing; D07-6)
    private long settingsCloseStart, emojiCloseStart, quickCloseStart, searchCloseStart;

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
    private int uploadToastTicks = 0;
    /** Upload-in-progress hint; set while a job is running, cleared on completion. */
    private int uploadBusyTicks = 0;
    // Serial upload pipeline (queue + worker live in UploadQueue; UI effects
    // come back through the callbacks below)
    private final com.niuqu.chatbubble.image.UploadQueue uploadQueue =
        new com.niuqu.chatbubble.image.UploadQueue(new com.niuqu.chatbubble.image.UploadQueue.Callbacks() {
            @Override public void onBusyStart() { uploadBusyTicks = 60; }
            @Override public void onIdle() { uploadBusyTicks = 0; }
            @Override public void onFailure() { uploadBusyTicks = 0; uploadToastTicks = 60; }
            @Override public void onEmoteSent(String url) { sendMessageText(url); }
            @Override public void onSendText(String text) { sendMessageText(text); }
            @Override public void onInputImage(String code) {
                String cur = input.getValue();
                if (cur.contains("[[CICode,url=file://")) {
                    // Replace the local file:// CICode (chatimage drag/paste) with
                    // the real upload URL instead of appending a second link.
                    cur = cur.replaceFirst("\\[\\[CICode,url=file://[^]]*]]", code);
                } else {
                    cur = cur.isEmpty() ? code : cur + " " + code;
                }
                input.setValue(cur);
                input.setCursorPosition(input.getValue().length());
            }
            @Override public void onRestoreInput(String text) { input.setValue(text); }
        });

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


        sidebarSearchBox = new EditBox(font, 2, 5, SIDEBAR_W - 5, 14, Component.literal(""));
        sidebarSearchBox.setMaxLength(20);
        sidebarSearchBox.setBordered(false);
        sidebarSearchBox.setTextColor(editColor);
        sidebarSearchBox.setTextColorUneditable(editColor);
        sidebarSearchBox.setVisible(sidebarOpen);
        sidebarSearchBox.setCanLoseFocus(true);
        sidebarSearchBox.setResponder(s -> sidebarScrollOffset = 0);
        if (sidebarOpen) sidebarSearchBox.setX(2);
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

        // D07-6: 弹层关闭动画钩子——visible 延迟置 false，先播 150ms 关闭动画
        settingsMenu.closeRequest = () -> beginPopupClose(s -> settingsCloseStart = s,
            () -> settingsMenu.visible = false);
        emojiPanel.closeRequest = () -> beginPopupClose(s -> emojiCloseStart = s,
            () -> emojiPanel.visible = false);
        quickChatPanel.closeRequest = () -> beginPopupClose(s -> quickCloseStart = s, () -> {
            quickChatPanel.visible = false;
            quickChatInput.setVisible(false);
        });

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

    private void renderSidebar(GuiGraphics g, int mouseX, int mouseY, float alpha) {
        sidebarMaxScroll = ChatSidebar.render(g, font, mouseX, mouseY, c(), panelW,
            msgBottom > 0 ? msgBottom : height - BAR_H, whisperPartner,
            iconTex("public_icon"), iconTex("no_online"), iconTex("private_tip"),
            sidebarSearchBox, sidebarScrollOffset, sidebarMaxScroll, alpha);
        if (sidebarScrollOffset > sidebarMaxScroll) sidebarScrollOffset = sidebarMaxScroll;
    }

    private void insertMention(String name) {
        String text = input.getValue();
        int atIdx = text.lastIndexOf('@');
        input.setValue(text.substring(0, atIdx) + "@" + name + " ");
        input.moveCursorToEnd(false);
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

    // 上下栏背景透明度：跟面板开合动画同步（150ms），但用线性曲线——
    // easeOutCubic 前 75ms 就到 87% 不透明，观感=瞬间出现（2.3.13 用户反馈）。
    private float getBarAlpha() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) return 1.0f;
        AnimationStyle style = ChatBubbleConfig.PANEL_ANIM_STYLE.get();
        if (style == AnimationStyle.NONE) return 1.0f;
        long elapsed = net.minecraft.Util.getMillis() - animStart;
        float t = Mth.clamp((float) elapsed / ANIM_MS, 0f, 1f);
        if (closing) return 1.0f - t;
        return t;
    }

    // Popup open/close animation (D07-6: closing is no longer instant).
    // Open: 200ms style curve (ease-out); close: 150ms ease-in fade + style displacement.
    private void renderPopupWithAnim(GuiGraphics g, long openStartMs, long closeStartMs,
                                     java.util.function.Function<Float, Runnable> renderer) {
        AnimationStyle style = ChatBubbleConfig.POPUP_ANIM_STYLE.get();
        float alpha = 1f;
        float t = 1f;
        if (closeStartMs > 0) {
            // Closing: ease-in fade-out (07 §2.6: exit 150ms ease-in)
            float tc = Mth.clamp((float) (net.minecraft.Util.getMillis() - closeStartMs) / UiTokens.POPUP_CLOSE_MS, 0f, 1f);
            float a = (1f - tc) * (1f - tc);
            Runnable render = renderer.apply(a);
            if (style == AnimationStyle.ZOOM) {
                g.pose().pushPose();
                float s = 0.85f + 0.15f * a;
                g.pose().translate(width / 2f, height / 2f, 0);
                g.pose().scale(s, s, 1f);
                g.pose().translate(-width / 2f, -height / 2f, 0);
                render.run();
                g.pose().popPose();
            } else if (style == AnimationStyle.SLIDE) {
                // 与打开同向位移：打开从下往上滑入，关闭向下滑出
                g.pose().pushPose();
                g.pose().translate(0, (1f - a) * 10f, 0);
                render.run();
                g.pose().popPose();
            } else {
                render.run();
            }
            return;
        }
        if (ChatBubbleConfig.ANIMATION_ENABLED.get() && style != AnimationStyle.NONE) {
            t = Mth.clamp((float) (net.minecraft.Util.getMillis() - openStartMs) / UiTokens.POPUP_OPEN_MS, 0f, 1f);
            alpha = Animation.styleCurve(style, t);
        }
        Runnable render = renderer.apply(alpha);
        if (t >= 1f || style == AnimationStyle.NONE) { render.run(); return; }
        if (style == AnimationStyle.ZOOM) {
            g.pose().pushPose();
            float s = 0.85f + 0.15f * Animation.easeOutBack(alpha);
            g.pose().translate(width / 2f, height / 2f, 0);
            g.pose().scale(s, s, 1f);
            g.pose().translate(-width / 2f, -height / 2f, 0);
            render.run();
            g.pose().popPose();
        } else if (style == AnimationStyle.SLIDE) {
            // SLIDE: rise up from below while fading in
            g.pose().pushPose();
            g.pose().translate(0, (1f - alpha) * 10f, 0);
            render.run();
            g.pose().popPose();
        } else {
            render.run();
        }
    }

    /** 开始弹层关闭动画（D07-6）：动画关/风格 NONE 时立即隐藏，否则 150ms 后由 tick 隐藏。 */
    private void beginPopupClose(java.util.function.LongConsumer setCloseStart, Runnable hide) {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()
                || ChatBubbleConfig.POPUP_ANIM_STYLE.get() == AnimationStyle.NONE) {
            setCloseStart.accept(0);
            hide.run();
            return;
        }
        setCloseStart.accept(System.currentTimeMillis());
    }

    /** tick 调用：关闭动画到期后真正隐藏（D07-6）。 */
    private void finishPopupClose(long closeStart, Runnable hide) {
        if (closeStart > 0 && System.currentTimeMillis() - closeStart >= UiTokens.POPUP_CLOSE_MS) {
            hide.run();
        }
    }

    private float getSidebarAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) return sidebarOpen ? 1f : 0f;
        AnimationStyle style = ChatBubbleConfig.PANEL_ANIM_STYLE.get();
        // Hamburger toggle always slides, regardless of the panel animation style
        if (sidebarAnimating) {
            long elapsed = net.minecraft.Util.getMillis() - sidebarAnimStart;
            float t = Mth.clamp((float) elapsed / ANIM_MS, 0f, 1f);
            float progress = Animation.styleCurve(AnimationStyle.SLIDE, t);
            return sidebarTargetOpen ? progress : 1.0f - progress;
        }
        // FADE/NONE have no horizontal displacement: the sidebar fades in place.
        if (style == AnimationStyle.FADE || style == AnimationStyle.NONE) return sidebarOpen ? 1f : 0f;
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
        // Ctrl+V with an image in the clipboard uploads it and inserts the code;
        // on the custom-emote tab it adds the image to the emote pack instead.
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_V && (modifiers & 0x2) != 0) {
            if (emojiPanel.visible && emojiPanel.tab == 2) {
                addClipboardEmote();
            } else {
                startUploadFromClipboard();
            }
        }
        // Settings menu / emoji panel gets ESC first
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
                this.clearFocus();
                path = super.nextFocusPath(nav);
            }
            if (path != null) this.changeFocus(path);
        }
        return false;
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
            mentionNavigated = true;
            return true;
        }
        int sidebarX = (int) getSidebarScreenX();
        if ((sidebarOpen || sidebarAnimating)
            && mouseX >= sidebarX && mouseX <= sidebarX + SIDEBAR_W) {
            sidebarScrollOffset = Mth.clamp(sidebarScrollOffset - (int)(scrollY * 20), 0, sidebarMaxScroll);
            return true;
        }
        if (suggestions != null && suggestions.mouseScrolled(scrollY))
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
        int sidebarX = (int) getSidebarScreenX();
        if (ChatSidebar.handleMouseClicked(origX, mouseY, whisperPartner, font,
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
                            if (EmoteStore.add(f)) emojiPanel.visible = true;
                        });
                    } else {
                        // insertText honors the cursor position and replaces any selection
                        input.insertText(emojiText);
                    }
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
                int avatarX = msg.isOwn() ? r[0] + r[2] + UiTokens.AVATAR_GAP : r[0] - Appearance.avatarSize() - UiTokens.AVATAR_GAP;
                int avatarY = msg.replyContent() != null ? r[1] - font.lineHeight - 2 : r[1] - NAME_H;
                if (mouseX >= avatarX && mouseX <= avatarX + Appearance.avatarSize()
                    && mouseY >= avatarY && mouseY <= avatarY + Appearance.avatarSize()) {
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
                int avatarX = r[0] - Appearance.avatarSize() - UiTokens.AVATAR_GAP;
                int avatarY = msg.replyContent() != null ? r[1] - font.lineHeight - 2 : r[1] - NAME_H;
                if (mouseX >= avatarX && mouseX <= avatarX + Appearance.avatarSize()
                    && mouseY >= avatarY && mouseY <= avatarY + Appearance.avatarSize()) {
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
                    // Local file:// links (e.g. legacy chatimage messages) are not
                    // browser URLs; opening them throws URISyntaxException. Only
                    // hand http(s) to the vanilla handler.
                    String clickUrl = click.getValue();
                    if (clickUrl != null && (clickUrl.startsWith("http://") || clickUrl.startsWith("https://"))) {
                        handleComponentClicked(style);
                    }
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
            if (emojiPanel.visible) beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
            if (searchPanel.visible) closeSearchPanel();
            boolean opening = !settingsMenu.visible;
            if (opening) {
                settingsMenu.visible = true;
                settingsAnimStart = net.minecraft.Util.getMillis();
            } else {
                beginPopupClose(s -> settingsCloseStart = s, () -> settingsMenu.visible = false);
            }
            return true;
        }
        // Emoji icon — toggle emoji panel
        int sendX = panelX + panelW - PAD - ICON_S + 2;
        int emojiX = sendX - ICON_S - 6;
        if (mx >= emojiX && mx <= emojiX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            if (settingsMenu.visible) beginPopupClose(s -> settingsCloseStart = s, () -> settingsMenu.visible = false);
            if (searchPanel.visible) closeSearchPanel();
            boolean opening = !emojiPanel.visible;
            if (opening) {
                emojiPanel.visible = true;
                emojiAnimStart = net.minecraft.Util.getMillis();
                showMentions = false;
                emojiPanel.scroll = 0;
            } else {
                beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
            }
            return true;
        }

        // Send icon (right)
        if (mx >= sendX && mx <= sendX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            sendMessage();
            return true;
        }
        return false;
    }


    // ---- Local image upload (2.3.11) ----

    /** OS file drag onto the window (vanilla drop hook): upload the first image dropped. */
    @Override
    public void onFilesDrop(java.util.List<java.nio.file.Path> paths) {
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
                Minecraft.getInstance().execute(() -> setFocused(input));
                return;
            }
        }
    }

    private void addClipboardEmote() {
        com.niuqu.chatbubble.image.ImageLoader.executor().execute(() -> {
            LocalImageSource.PreparedImage prep = readClipboard();
            if (prep == null) return; // no image in clipboard
            minecraft.execute(() -> EmoteStore.addBytes(prep.bytes(), "paste_" + System.currentTimeMillis() + ".png"));
        });
    }

    private void startUploadFromClipboard() {
        com.niuqu.chatbubble.image.ImageLoader.executor().execute(() -> {
            LocalImageSource.PreparedImage prep = readClipboard();
            if (prep == null) return; // no image in clipboard — let vanilla paste text
            minecraft.execute(() -> uploadQueue.enqueue(new com.niuqu.chatbubble.image.UploadQueue.UploadJob(null, prep.bytes(), "clipboard", false, null)));
        });
    }

    private static LocalImageSource.PreparedImage readClipboard() {
        try {
            return LocalImageSource.fromClipboard();
        } catch (Throwable t) {
            return null;
        }
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
        boolean nowBlocked = BlockList.isPlayerBlocked(
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
        // When sidebar is synced to main animation, extend panel bg to
        // sidebar's right edge so there's no gap between them. Only SLIDE
        // moves horizontally (the panel slides in); FADE/ZOOM keep the bg
        // in place and fade/scale it in place instead.
        int fillLeft = (!sidebarAnimating && sidebarOpen && pstyle == AnimationStyle.SLIDE)
            ? (int)(anim * SIDEBAR_W) : panelX;

        int panelOffset = (pstyle == AnimationStyle.SLIDE) ? currentPanelOffset() : 0;
        boolean zoom = (pstyle == AnimationStyle.ZOOM) && anim < 1f;
        float panelScale = 1f;
        if (zoom) panelScale = 0.8f + 0.2f * Animation.easeOutBack(anim);

        if (ChatBubbleConfig.BLUR_ENABLED.get() && panelOpacity < 0.999f && !zoom) {
            BlurRenderer.blurPanel(panelOffset + fillLeft, 0, panelX + panelW - fillLeft, height);
        }

        g.pose().pushPose();
        g.pose().translate(panelOffset, 0, 0);
        if (zoom) {
            float cx = panelX + panelW / 2f;
            g.pose().translate(cx, height / 2f, 0);
            g.pose().scale(panelScale, panelScale, 1f);
            g.pose().translate(-cx, -height / 2f, 0);
        }

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
        renderPopupWithAnim(g, settingsAnimStart, settingsCloseStart, a -> () -> settingsMenu.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, ChatBubbleScreen::iconTex, a));
        renderPopupWithAnim(g, emojiAnimStart, emojiCloseStart, a -> () -> emojiPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, ICON_S, PAD, a));
        renderPopupWithAnim(g, quickAnimStart, quickCloseStart, a -> () -> quickChatPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, quickChatInput, a));
        renderPopupWithAnim(g, searchAnimStart, searchCloseStart, a -> () -> searchPanel.render(g, mouseX, mouseY, font, c(), panelX, panelW, barTop, searchInput, searchMatches, searchMatchIdx, a));
        // 输入框 widget 在 z=50 的 renderables 循环渲染，会被这里 z=100 的不透明面板背景盖住
        // （5bb740e 弹层 z 提升引入）——面板打开时在同 z 重画一次，文字/光标才可见。
        // widget 无背景（setBordered(false)），只画文字/光标，不遮挡面板内容
        if (quickChatPanel.visible && quickChatInput != null) quickChatInput.render(g, mouseX, mouseY, partialTick);
        if (searchPanel.visible && searchInput != null) searchInput.render(g, mouseX, mouseY, partialTick);
        g.pose().popPose();

        g.pose().popPose();

        // Sidebar on top of chat panel, with its own slide animation
        if (sidebarOpen || sidebarAnimating) {
            g.pose().pushPose();
            // ZOOM: the sidebar scales with the panel around the panel center
            if (zoom) {
                float cx = panelX + panelW / 2f;
                g.pose().translate(cx, height / 2f, 0);
                g.pose().scale(panelScale, panelScale, 1f);
                g.pose().translate(-cx, -height / 2f, 0);
            }
            // Fade/zoom-in-place applies only to the panel's own open/close
            // animation; the hamburger toggle always slides.
            boolean fadeSidebar = !sidebarAnimating && (pstyle == AnimationStyle.FADE || zoom);
            int sidebarOffset = (closing && !fadeSidebar)
                ? (int)((getAnimProgress() - 1.0f) * SIDEBAR_W)
                : (fadeSidebar ? 0 : (int) getSidebarScreenX());
            g.pose().translate(sidebarOffset, 0, 50);
            // Per-element alpha (vanilla blit ignores setShaderColor; the sidebar
            // fades its own textures through ChatSidebar's alpha path)
            renderSidebar(g, mouseX - sidebarOffset, mouseY, fadeSidebar ? getAnimProgress() : 1f);
            g.pose().popPose();
            if (closing) sidebarSearchBox.setX(2 + sidebarOffset);
        }

        g.pose().pushPose();
        g.pose().translate(0, 0, 50);
        // EditBox is a widget drawn by super.render() at its real coords — it doesn't
        // follow the panel's pose translate, so slide it with the open/close animation
        input.setX(inputX + panelOffset);
        // 不调 super.render（ChatScreen.render 访问 package-private suggestions，
        // 跨包无法初始化）；复制 Screen.render 的 widgets 遍历渲染
        for (net.minecraft.client.gui.components.Renderable w : this.renderables) {
            w.render(g, mouseX, mouseY, partialTick);
        }

        // 建议框定位基于 input.getScreenX()（屏幕坐标），与 input 同坐标空间渲染
        g.enableScissor(panelX, 0, panelX + panelW, height);
        if (suggestions != null) suggestions.render(g, mouseX, mouseY);
        g.disableScissor();

        // Banner rendered here after super.render() so it's always on top of the panel
        // (the HUD-layer render draws behind the screen batch on Forge/NeoForge).
        MentionNotificationBanner.INSTANCE.render(g,
            Minecraft.getInstance().getWindow().getGuiScaledWidth(),
            Minecraft.getInstance().getWindow().getGuiScaledHeight());

        g.pose().popPose();

    }

    private void renderTitleBar(GuiGraphics g, int mouseX, int mouseY) {
        // 上下栏背景只跟开合动画（fade 终点 1.0 不透明），不乘 PANEL_OPACITY（2.3.7 起永久半透明回归）
        float barAlpha = getBarAlpha();
        ChatBars.renderTitleBar(g, font, mouseX, mouseY, c(), panelX, panelW,
            getDisplayTitle(), LocalTime.now().format(TIME_FMT), iconTex("menu"), barAlpha, getAnimProgress());
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
            ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.WHISPER_BAR), panelX, indY, panelW, indicatorH, getAnimProgress());
            String modeText = Component.translatable("e33chat.whisper.mode").getString() + ": " + whisperPartner;
            int modeTW = font.width(modeText);
            g.drawString(font, Component.literal(modeText), panelX + (panelW - modeTW) / 2, indY + 2, c().textPrimary(), false);
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
            if (prevMsg != null) totalH += ChatBubbleConfig.MESSAGE_GAP.get();
            totalH += getMsgHeight(msg);
            prevMsg = msg;
        }
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
        ChatMessageStore.ChatMessage prevRenderMsg = null;
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
                    contentY += TIME_SEP_H + Appearance.messageGap();
                    prevRenderMsg = null;
                }
            }

            int h = getMsgHeight(msg);
            if (prevRenderMsg != null) contentY += ChatBubbleConfig.MESSAGE_GAP.get();
            int screenY = effectiveMsgTop + contentY - scrollOffset;
            // showAvatar 必须用“上一条消息”比较；先赋值 prevRenderMsg 再比会恒自比（2.3.16 回归）
            boolean showAvatar = !(ChatBubbleConfig.HIDE_REPEATED_AVATARS.get()
                && ChatMessageRenderer.isSameGroup(prevRenderMsg, msg));
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
                        switch (mstyle) {
                            case SLIDE -> mDx = Math.round((1f - curve) * 40f) * (msg.isOwn() ? 1 : -1);
                            case FADE -> { /* pure fade, no displacement */ }
                            case ZOOM -> mScale = 0.8f + 0.2f * Animation.easeOutBack(curve);
                            default -> { }
                        }
                    }
                }
            }
            g.pose().pushPose();
            g.pose().translate(mDx, mDy, 0);
            if (mScale != 1f) {
                // Bubble top-left for the ZOOM pivot (mirrors ChatMessageRenderer's layout incl. bubble_size)
                float bs = ChatMessageRenderer.bubbleScale(font.lineHeight);
                int zMaxW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2 - ChatMessageRenderer.BUBBLE_PAD_X * 2 - 16;
                int zW = 0;
                for (var zl : ChatMessageRenderer.wrapContent(msg.content(), font, ChatMessageRenderer.bubbleWrapWidth(zMaxW, font.lineHeight)))
                    zW = Math.max(zW, font.width(zl));
                int zBubbleW = (int)((zW + ChatMessageRenderer.BUBBLE_PAD_X * 2) * bs);
                int zBubbleX = msg.isOwn()
                    ? panelX + panelW - ChatLayout.PAD - Appearance.avatarSize() - UiTokens.AVATAR_GAP - zBubbleW
                    : panelX + ChatLayout.PAD + Appearance.avatarSize() + UiTokens.AVATAR_GAP;
                int zBubbleY = screenY + 10; // ChatMessageRenderer.NAME_H (package-private)
                g.pose().translate(zBubbleX + zBubbleW / 2f, zBubbleY, 0);
                g.pose().scale(mScale, mScale, 1f);
                g.pose().translate(-(zBubbleX + zBubbleW / 2f), -zBubbleY, 0);
            }
            renderBubble(g, msg, fullIdx, screenY, mouseX, mouseY, mAlpha, showAvatar);
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
            scrollbarDragging, scrollbarAlpha, effectiveMsgBottom, c().scrollbar());
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
        int bubbleMaxW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2
            - ChatMessageRenderer.BUBBLE_PAD_X * 2 - 16;
        int h = ChatMessageRenderer.msgHeight(msg, font, bubbleMaxW, panelW);
        msgHeightCache.put(msg, h);
        return h;
    }


    private void renderBubble(GuiGraphics g, ChatMessageStore.ChatMessage msg,
                               int index, int baseY, int mouseX, int mouseY, float alpha, boolean showAvatar) {
        boolean own = msg.isOwn();
        int bubbleMaxW = panelW - Appearance.avatarSize() - ChatLayout.PAD * 2
            - ChatMessageRenderer.BUBBLE_PAD_X * 2 - 16;
        int ownBg = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_BUBBLE_COLOR.get(), 0xFF1E90FF);
        int otherBg = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_BUBBLE_COLOR.get(), c().contextHover());
        int ownFg = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR.get(), 0xFFFFFFFF);
        int otherFg = ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR.get(), c().textPrimary());
        String skinName = (msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty())
            ? msg.rawPlayerName() : msg.senderName().getString();
        ResourceLocation skin = SkinResolver.getSkin(msg.senderUUID(), skinName);

        ChatMessageRenderer.renderBubble(g, font, msg, index, baseY, mouseX, mouseY,
            panelX, panelW, ownBg, otherBg, ownFg, otherFg, own,
            ChatBubbleConfig.BUBBLE_CORNER_RADIUS.get(), c(), skin,
            searchHighlightIndex, bubbleMaxW, bubbleRects, clickableSpans, alpha, showAvatar);
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
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), panelX, notifY - 1, panelW, 1, getAnimProgress());
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
            msgTop, iconTex("copy"), iconTex("quote"), contextX, contextY, getAnimProgress());
    }

    private void renderAvatarContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        if (contextAvatarIndex < 0) return;
        ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
        boolean isBlocked = msg != null
            && BlockList.isPlayerBlocked(msg.rawPlayerName(), msg.senderName(),
                ChatBubbleConfig.BLOCKED_PLAYERS.get());
        ChatContextMenus.renderAvatarMenu(g, font, mouseX, mouseY, c(), panelX, panelW,
            msgTop, iconTex("tp"), iconTex("whisper"), iconTex("block"), isBlocked,
            contextAvatarX, contextAvatarY, ChatMessageStore.useTpa(), getAnimProgress());
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
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), barX, barTop - notifOffset - 1, barW, 1, getAnimProgress());

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
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(hoverX ? UiElement.CLOSE_HOVER : UiElement.SIDEBAR_SELECTED),
            cx, cy, 12, 12, getAnimProgress());
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

        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.POPUP_BG), popupX, popupY, popupW, popupH, getAnimProgress());
        g.renderOutline(popupX, popupY, popupW, popupH, ChatBubbleTheme.alphaBlend(c().divider(), (int) (255 * getAnimProgress())));

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
        if (copyToastTicks <= 0 && uploadToastTicks <= 0 && uploadBusyTicks <= 0) return;
        if (uploadToastTicks > 0) {
            uploadToastTicks--;
            if (uploadToastTicks <= 0) return;
            int alpha = Animation.fadeInOut(uploadToastTicks, 5, 20, 5);
            int color = (alpha << 24) | 0x00FF5555;
            String text = Component.translatable("e33chat.upload.failed").getString();
            int tw = font.width(text);
            int tx = UiLayout.centerX(panelX, panelW, tw);
            int ty = msgBottom - 24;
            g.fill(tx - 4, ty - 2, tx + tw + 4, ty + font.lineHeight + 2, (alpha << 24) | 0x000000);
            g.drawString(font, text, tx, ty, color, false);
            return;
        }
        if (uploadBusyTicks > 0) {
            // Upload-in-progress hint; cleared by the worker when the job finishes.
            // Same look as the copy toast (TOAST_BG texture + toastText color).
            int alpha = 200;
            String text = Component.translatable("e33chat.upload.start").getString();
            int tw = font.width(text);
            int tx = UiLayout.centerX(panelX, panelW, tw);
            int ty = msgBottom - 24;
            ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.TOAST_BG),
                tx - 6, ty - 2, tw + 12, font.lineHeight + 4, (alpha / 2) / 255f);
            g.drawString(font, Component.literal(text), tx, ty,
                (alpha << 24) | (c().toastText() & 0x00FFFFFF), false);
            return;
        }
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
                if (quickChatPanel.visible) beginPopupClose(s -> quickCloseStart = s, () -> {
                    quickChatPanel.visible = false;
                    quickChatInput.setVisible(false);
                });
                if (emojiPanel.visible) beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
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
                if (emojiPanel.visible) beginPopupClose(s -> emojiCloseStart = s, () -> emojiPanel.visible = false);
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
        beginPopupClose(s -> searchCloseStart = s, () -> {
            searchPanel.visible = false;
            searchInput.setVisible(false);
        });
        searchMatches.clear();
        searchMatchIdx = -1;
        searchHighlightIndex = -1;
        setFocused(input);
    }




    private void renderBottomBar(GuiGraphics g, int mouseX, int mouseY) {
        // 上下栏背景只跟开合动画（fade 终点 1.0 不透明），不乘 PANEL_OPACITY（2.3.7 起永久半透明回归）
        float barAlpha = getBarAlpha();
        ChatBars.renderBottomBar(g, font, mouseX, mouseY, c(), panelX, panelW, barTop, height,
            inputX, inputY, input.getWidth(), input.isFocused(), emojiPanel.visible,
            iconTex("settings"), iconTex("emoji"), iconTex("send"), barAlpha, getAnimProgress());

    }

    /** 带透明度图标的绘制：与 drawTextureIcon 同采样语义，但走带 alpha 的渲染路径（弹层淡入用）。 */
    public static void drawTextureIconAlpha(GuiGraphics g, ResourceLocation tex, int x, int y, int size, float alpha) {
        if (alpha <= 0.003f) return;
        if (size < 16) {
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size, 1f, 1f, 14, 14, 16, 16, alpha);
        } else {
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size, 0f, 0f, size, size, size, size, alpha);
        }
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
                if (lk == null || !k.equals(lk)) {
                    lk = k;
                    cy += TIME_SEP_H + Appearance.messageGap();
                    prevMsg = null;
                }
            }
            if (prevMsg != null) cy += ChatBubbleConfig.MESSAGE_GAP.get();
            cy += getMsgHeight(m);
            prevMsg = m;
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
        String raw = input.getValue().trim();
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
                ChatMessageStore.debugLog(() -> "[e33chat] upload skip | not a live file | raw=" + raw);
                sendMessageText(raw);
                return;
            }
            if (uploadQueue.enqueue(new com.niuqu.chatbubble.image.UploadQueue.UploadJob(new java.io.File(localPath), null, null, false, raw))) {
                input.setValue("");
                savedInput = "";
                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("e33chat.upload.wait"));
                ChatMessageStore.debugLog(() -> "[e33chat] upload block | queued=" + uploadQueue.pending() + " | raw=" + raw);
            } else {
                Minecraft.getInstance().player.sendSystemMessage(Component.translatable("e33chat.upload.queue_full"));
            }
            return;
        }
        sendMessageText(raw);
    }

    private void sendMessageText(String text) {
        String raw = text;
        // Send the text UNCHANGED (raw '&', never '§'): vanilla servers reject '§' in
        // player chat and kick, so converting client-side is a dead end. Server color
        // plugins (Essentials etc.) translate '&' for everyone; on plain servers others
        // see the literal '&'. Local coloring of our own bubble is done at addMessage.

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
        // Keep the history cursor at the newest end: this screen stays open after
        // send (vanilla closes), so init()'s one-time historyPos snapshot goes
        // stale and up-arrow would skip the freshly sent entries.
        historyPos = minecraft.gui.getChat().getRecentChat().size();

        String logCmd = text, logDisp = displayText, logTarget = whisperTarget;
        boolean logBub = localBubble;
        ChatMessageStore.debugLog(() -> "[e33chat] Send | cmd='" + logCmd + "' | display='" + logDisp + "' | whisperTarget=" + logTarget + " | localBubble=" + logBub);
        if (localBubble) {
            Component bubbleContent = ChatBubbleConfig.COLOR_CODES.get()
                ? parseColorCodes(displayText) : Component.literal(displayText);
            // Convert embedded image codes so the outgoing bubble previews the
            // image like the vanilla chat does (ChatImage may be absent — then
            // convert passes through unchanged)
            // 2.3.10+: keep image bracket codes raw so the local bubble renders
            // the picture natively (BracketCodec + ImageLoader); the vanilla chat
            // echo is converted by ChatImage's own mixins when installed.
            ChatMessageStore.addMessage(bubbleContent,
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
        // Optional vanilla-style behaviour: close the chat screen right after the
        // message goes out (off by default — this screen supports multi-send).
        if (ChatBubbleConfig.CLOSE_CHAT_ON_SEND.get()) onClose();
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
