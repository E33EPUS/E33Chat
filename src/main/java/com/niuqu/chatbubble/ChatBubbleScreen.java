package com.niuqu.chatbubble;

import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.components.PlayerFaceRenderer;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.FormattedCharSequence;
import net.minecraft.util.Mth;
import com.niuqu.chatbubble.packets.QuoteSyncPayload;
import com.mojang.blaze3d.platform.NativeImage;
import java.io.InputStream;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

public class ChatBubbleScreen extends Screen {

    // Layout
    private int panelX, panelW;
    private static final int TITLE_H = 24;
    private int titleY, msgTop, msgBottom, barTop;
    private static final int PAD = 10;
    private static final int AVATAR = 20;
    private static final int BUBBLE_PAD_X = 6;
    private static final int BUBBLE_PAD_Y = 4;
    private static final int GAP = 6;
    private static final int NAME_H = 10;
    private static final int TIME_SEP_H = 14;
    private static final int BAR_H = 26;

    private static final int INPUT_H = 14;
    private static final int ICON_S = 14;
    private static final ResourceLocation TEX_GEAR = ResourceLocation.fromNamespaceAndPath("e33chat", "textures/gui/settings");
    private static final ResourceLocation TEX_SEND = ResourceLocation.fromNamespaceAndPath("e33chat", "textures/gui/send");
    private static final ResourceLocation TEX_EMOJI = ResourceLocation.fromNamespaceAndPath("e33chat", "textures/gui/emoji");
    private static boolean iconsLoaded;

    private static final int COLOR_NAME = 0xFFCCCCCC;
    private static final int COLOR_TIME = 0xFF999999;
    private static final int COLOR_PANEL_BG = 0xEE1E1E1E;
    private static final int COLOR_TITLE_BG = 0xFF242424;
    private static final int COLOR_BAR_BG = 0xFF242424;
    private static final int COLOR_POPUP_BG = 0xB31E1E1E;
    private static final int COLOR_POPUP_HOVER = 0xB3444444;
    private static final int COLOR_DIVIDER = 0xFF333333;
    private static final int COLOR_INPUT_BG = 0xFF2A2A2A;

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
    private final String initialText;
    private int scrollOffset;
    private int maxScroll;
    private boolean scrollToBottom = true;
    private static String savedInput = "";
    private String historyBuffer = "";
    private int historyPos = -1;
    private String worldName;
    private boolean editingTitle;
    private EditBox titleEditor;

    // Emoji panel
    private boolean showEmojiPanel;
    private int emojiScroll;
    private int emojiTab;

    // Scrollbar
    private static final int SCROLLBAR_WIDTH = 6;
    private static final int MIN_THUMB_H = 8;
    private static final int TRACK_COLOR = 0x1AFFFFFF;
    private static final int THUMB_COLOR = 0x66FFFFFF;
    private static final int THUMB_HOVER_COLOR = 0x88FFFFFF;
    private static final int THUMB_DRAG_COLOR = 0xAAFFFFFF;
    private boolean scrollbarDragging;
    private int scrollbarDragStartY;
    private int scrollbarDragStartOffset;
    private int messageTotalH;
    private boolean scrollbarHovered;

    private static final int EMOJI_PANEL_H = 132;
    private static final int EMOJI_TAB_H = 18;
    private static final int EMOJI_COLS = 9;
    private static final int EMOJI_SLOT = 18;
    private static final int KAO_ITEM_H = 13;

    private static final String[] EMOJI_EMOTES = {
        "😀","😃","😄","😁","😆","😅","🤣","😂",
        "🙂","😉","😊","😇","🥰","😍","🤩","😘",
        "😋","😛","😜","🤪","😎","🤗","🤔","😐",
        "😢","😭","😤","😡","🥺","😴","😷","🤒",
        "🐱","🐶","🐼","🐨","🐰","🦊","🐸","🐵",
        "🐭","🐹","🐮","🦁","🐯","🐻","🐧","🐤",
        "🐴","🦄","🐝","🐞","🦋","🐙","🦀","🐠",
        "❤️","🧡","💛","💚","💙","💜","🖤","💔",
        "💕","💖","💗","💘","💝","💟","❣️","💌",
        "👍","👎","👏","🙌","💪","🤝","👋","✌️",
        "🎮","🎯","🎨","🎵","🎶","🎤","🎧","🎼",
        "⭐","🌟","🔥","💧","🌈","❄️","🎉","🎊",
        "🍕","🍔","🌮","🍩","🍪","🎂","☕","🍺",
        "⬆️","⬇️","✅","❌","❓","❗","💤","💡",
        "💀","🗿","🤡","👀","💯","💢","💬","💭",
    };

    private static final String[] KAOMOJI = {
        "(｡•̀ᴗ-)✧","(๑˃̵ᴗ˂̵)و","(๑•̀ㅂ•́)و✧","(◍•ᴗ•◍)",
        "╰(*°▽°*)╯","(≧∇≦)ﾉ","(＾▽＾)","✧٩(ˊωˋ*)و✧",
        "ฅ^•ﻌ•^ฅ","(•ω•)","(￣▽￣*)","(⌒▽⌒)☆",
        "(o゜▽゜)o☆","＼(￣▽￣)／","(◔◡◔)","／(=✪ x ✪=)＼",
        "¯\\_(ツ)_/¯","(ー_ー゛)","(￢_￢)","(¬_¬)",
        "(⇀‸↼‶)","(｡ŏ_ŏ)","(・∀・)","_(:з」∠)_",
        "(╯°□°）╯︵ ┻━┻","(´;ω;｀)","Σ(°△°|||)","(◎ロ◎)",
        "(∪.∪ )...zzz",
    };

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

        panelW = Math.max(200, (int) (width * 0.4));
        panelX = 0;
        titleY = 0;
        msgTop = titleY + TITLE_H + 1;
        barTop = height - BAR_H;
        msgBottom = barTop - 1;

        // Input box: gear (left) → input → emoji → send (right)
        int ibY = barTop + (BAR_H - INPUT_H) / 2;
        inputY = ibY;
        inputX = panelX + 4 + ICON_S + 3;
        int sendX = panelX + panelW - PAD - ICON_S;
        int inputW = sendX - ICON_S - 8 - inputX;

        input = new EditBox(font, inputX, ibY, inputW, INPUT_H, Component.literal(""));
        input.setMaxLength(256);
        input.setBordered(false);
        input.setValue(initialText.isEmpty() && !savedInput.isEmpty() ? savedInput : initialText);
        input.setCanLoseFocus(false);
        input.setResponder(this::onEdited);
        addRenderableWidget(input);

        commandSuggestions = new CommandSuggestions(minecraft, this, input, font,
            false, false, 0, 8, true, 0xDD1E1E1E);
        commandSuggestions.updateCommandInfo();

        if (!iconsLoaded) {
            loadIconTextures();
            iconsLoaded = true;
        }

        worldName = getWorldName();

        String title = getDisplayTitle();
        int editW = Math.max(60, Math.min(180, font.width(title) + 16));
        int editX = panelX + (panelW - editW) / 2;
        int editY = titleY + (TITLE_H - INPUT_H) / 2;
        titleEditor = new EditBox(font, editX, editY, editW, INPUT_H, Component.literal(""));
        titleEditor.setMaxLength(32);
        titleEditor.setBordered(false);
        titleEditor.setVisible(false);
        addRenderableWidget(titleEditor);

        setInitialFocus(input);
    }

    private String getWorldName() {
        if (minecraft.getSingleplayerServer() != null)
            return minecraft.getSingleplayerServer().getWorldData().getLevelName();
        if (minecraft.getCurrentServer() != null)
            return minecraft.getCurrentServer().name;
        return Component.translatable("e33chat.title.fallback").getString();
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

    @Override
    public void tick() {
        if (copyToastTicks > 0) copyToastTicks--;
        if (closing && net.minecraft.Util.getMillis() - animStart >= ANIM_MS)
            minecraft.setScreen(null);
    }

    private float getAnimProgress() {
        if (!ChatBubbleConfig.ANIMATION_ENABLED.get()) return 1.0f;
        long elapsed = net.minecraft.Util.getMillis() - animStart;
        float t = Mth.clamp((float) elapsed / ANIM_MS, 0f, 1f);
        if (closing) t = 1.0f - t;
        return 1.0f - (1.0f - t) * (1.0f - t) * (1.0f - t);
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        if (editingTitle) {
            if (keyCode == 256) { exitTitleEdit(false); return true; }
            if (keyCode == 257 || keyCode == 335) { exitTitleEdit(true); return true; }
            return titleEditor.keyPressed(keyCode, scanCode, modifiers);
        }

        // Emoji panel gets ESC first
        if (showEmojiPanel && keyCode == 256) {
            showEmojiPanel = false;
            return true;
        }

        // @mention autocomplete keys
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
        if (showEmojiPanel) {
            emojiScroll = Mth.clamp(emojiScroll - (int) scrollY * 20, 0, 200);
            return true;
        }
        if (showMentions && !mentionCandidates.isEmpty()) {
            mentionIdx = Mth.clamp(mentionIdx - (int) scrollY, 0, mentionCandidates.size() - 1);
            return true;
        }
        if (commandSuggestions != null && commandSuggestions.mouseScrolled(scrollY))
            return true;
        scrollToBottom = false;
        scrollOffset -= (int) (scrollY * 20);
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);
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
            int trackX = panelX + panelW - SCROLLBAR_WIDTH;
            int effBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
            if (mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
                && mouseY >= msgTop && mouseY < effBottom) {
                int trackH = effBottom - msgTop;
                int thumbH = Math.max(MIN_THUMB_H, (int)((long)trackH * trackH / messageTotalH));
                thumbH = Math.min(thumbH, trackH);
                int travelRange = trackH - thumbH;
                int thumbY = msgTop + (int)((long)scrollOffset * travelRange / maxScroll);
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
            if (editingTitle) {
                if (!isMouseOverTitleEditor(mouseX, mouseY)) {
                    exitTitleEdit(true);
                    return true;
                }
                return super.mouseClicked(mouseX, mouseY, button);
            }
            if (isMouseOverPen(mouseX, mouseY)) {
                enterTitleEdit();
                return true;
            }
            if (mouseX >= panelX + panelW - 18 && mouseX <= panelX + panelW - 6
                && mouseY >= titleY + 6 && mouseY <= titleY + 18) {
                onClose();
                return true;
            }
            if (showEmojiPanel && handleEmojiClick((int) mouseX, (int) mouseY))
                return true;
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
                    String mention = "@" + msg.senderName().getString() + " ";
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
            int effBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
            int trackH = effBottom - msgTop;
            int thumbH = Math.max(MIN_THUMB_H, (int)((long)trackH * trackH / messageTotalH));
            thumbH = Math.min(thumbH, trackH);
            int travelRange = trackH - thumbH;
            if (travelRange > 0) {
                int dy = (int) mouseY - scrollbarDragStartY;
                int newOffset = scrollbarDragStartOffset + (int)((long)dy * maxScroll / travelRange);
                scrollOffset = Mth.clamp(newOffset, 0, maxScroll);
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

    private boolean handleEmojiClick(int mx, int my) {
        if (!showEmojiPanel) return false;
        int sendX = panelX + panelW - PAD - ICON_S;

        boolean isKaomoji = emojiTab == 1;
        int kCols = 2;
        int kColW = 90;
        int pw = isKaomoji ? kCols * kColW + 8 : EMOJI_COLS * EMOJI_SLOT + 8;
        int px = sendX + ICON_S / 2 - pw / 2;
        px = Mth.clamp(px, panelX + 2, panelX + panelW - pw - 2);
        int py = barTop - EMOJI_PANEL_H - 4;

        if (mx < px || mx > px + pw || my < py || my > py + EMOJI_PANEL_H) {
            showEmojiPanel = false;
            return false;
        }

        // Tab click
        if (my < py + EMOJI_TAB_H) {
            int tabW = pw / 2;
            int t = (mx - px) / tabW;
            if (t >= 0 && t <= 1) { emojiTab = t; emojiScroll = 0; }
            return true;
        }

        // Content click
        int cy = py + EMOJI_TAB_H + 1;
        if (isKaomoji) {
            int cw = (pw - 8) / kCols;
            int col = (mx - px - 4) / cw;
            int row = (my - cy - 2 + emojiScroll) / KAO_ITEM_H;
            int idx = row * kCols + col;
            if (idx >= 0 && idx < KAOMOJI.length) {
                input.setValue(input.getValue() + KAOMOJI[idx]);
                input.moveCursorToEnd(false);
            }
        } else {
            int col = (mx - px - 4) / EMOJI_SLOT;
            int row = (my - cy - 2 + emojiScroll) / EMOJI_SLOT;
            int idx = row * EMOJI_COLS + col;
            if (idx >= 0 && idx < EMOJI_EMOTES.length) {
                input.setValue(input.getValue() + EMOJI_EMOTES[idx]);
                input.moveCursorToEnd(false);
            }
        }
        showEmojiPanel = false;
        return true;
    }

    private boolean handleIconClick(int mx, int my) {
        int iconY = barTop + (BAR_H - ICON_S) / 2;
        // Gear icon (left)
        int gearX = panelX + 4;
        if (mx >= gearX && mx <= gearX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            minecraft.setScreen(new ChatBubbleConfigScreen(this));
            return true;
        }
        // Emoji icon
        int sendX = panelX + panelW - PAD - ICON_S;
        int emojiX = sendX - ICON_S - 6;
        if (mx >= emojiX && mx <= emojiX + ICON_S && my >= iconY && my <= iconY + ICON_S) {
            showEmojiPanel = !showEmojiPanel;
            showMentions = false;
            if (showEmojiPanel) emojiScroll = 0;
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
        int menuH = CTX_ITEM_H + 2;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;

        if (mx >= menuX && mx <= menuX + CTX_W && my >= menuY && my <= menuY + menuH) {
            ChatMessageStore.ChatMessage msg = ChatMessageStore.getMessageAt(contextAvatarIndex);
            if (msg != null && msg.rawPlayerName() != null && !msg.rawPlayerName().isEmpty()) {
                minecraft.player.connection.sendCommand("tp " + msg.rawPlayerName());
            }
        }
        contextAvatarIndex = -1;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        float anim = getAnimProgress();
        int panelOffset = (int) ((anim - 1.0f) * panelW);

        // Dark overlay to the right of panel
        int overlayAlpha = (int) (0.94f * anim * 160) << 24;
        if (overlayAlpha != 0)
            g.fill(panelX + panelW + panelOffset, 0, width, height, overlayAlpha | 0x000000);

        // Panel contents slide in from left
        g.pose().pushPose();
        g.pose().translate(panelOffset, 0, 0);

        g.fill(panelX, 0, panelX + panelW, height, COLOR_PANEL_BG);

        renderTitleBar(g, mouseX, mouseY);
        renderMessages(g, mouseX, mouseY);
        net.minecraft.network.chat.Style hovered = getHoveredStyle(mouseX, mouseY);
        if (hovered != null && hovered.getHoverEvent() != null) {
            g.renderComponentHoverEffect(font, hovered, mouseX, mouseY);
        }
        renderNotificationBar(g, mouseX, mouseY);
        renderReplyBar(g, mouseX, mouseY);
        renderContextMenu(g, mouseX, mouseY);
        renderAvatarContextMenu(g, mouseX, mouseY);
        renderToast(g);
        renderBottomBar(g, mouseX, mouseY);

        renderMentionPopup(g, mouseX, mouseY);
        renderEmojiPanel(g, mouseX, mouseY);

        g.enableScissor(panelX, 0, panelX + panelW, height);
        if (commandSuggestions != null) commandSuggestions.render(g, mouseX, mouseY);
        g.disableScissor();

        g.pose().popPose();

        super.render(g, mouseX, mouseY, partialTick);
    }


    @Override
    public void renderBackground(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        // no-op: disable vanilla blur
    }
    private void renderTitleBar(GuiGraphics g, int mouseX, int mouseY) {
        int ty = titleY;
        g.fill(panelX, ty, panelX + panelW, ty + TITLE_H, COLOR_TITLE_BG);
        g.fill(panelX, ty + TITLE_H, panelX + panelW, ty + TITLE_H + 1, COLOR_DIVIDER);

        if (editingTitle) {
            // titleEditor rendered via super.render() — just skip title text
        } else {
            String title = getDisplayTitle();
            int titleW = font.width(title);
            int titleX = panelX + (panelW - titleW) / 2;
            int titleTextY = ty + (TITLE_H - font.lineHeight) / 2;
            g.drawString(font, Component.literal(title), titleX, titleTextY, 0xFFFFFFFF, false);

            int penX = titleX + titleW + 3;
            int penY = ty + (TITLE_H - 9) / 2;
            boolean hoverPen = mouseX >= penX && mouseX <= penX + 9 && mouseY >= penY && mouseY <= penY + 9;
            int penColor = hoverPen ? 0xFFFFFF88 : 0xFF888888;
            g.drawString(font, Component.literal("✎"), penX, penY, penColor, false);
        }

        // Time
        String time = LocalTime.now().format(TIME_FMT);
        int timeW = font.width(time);
        g.drawString(font, Component.literal(time),
            panelX + panelW - PAD - 20 - timeW, ty + (TITLE_H - font.lineHeight) / 2, COLOR_TIME, false);

        // Close button
        int closeX = panelX + panelW - 18;
        int closeY = ty + 6;
        boolean hoverClose = mouseX >= closeX && mouseX <= closeX + 12 && mouseY >= closeY && mouseY <= closeY + 12;
        int closeBg = hoverClose ? 0xFF555555 : 0xFF333333;
        g.fill(closeX, closeY, closeX + 12, closeY + 12, closeBg);
        g.drawCenteredString(font, Component.literal("✕"), closeX + 6, closeY + 2, 0xFFCCCCCC);
    }

    private String getDisplayTitle() {
        String ct = ChatMessageStore.getCustomTitle();
        return ct != null ? ct : worldName;
    }

    private boolean isMouseOverPen(double mx, double my) {
        String title = getDisplayTitle();
        int titleW = font.width(title);
        int titleX = panelX + (panelW - titleW) / 2;
        int penX = titleX + titleW + 3;
        int penY = titleY + (TITLE_H - 9) / 2;
        return mx >= penX && mx <= penX + 9 && my >= penY && my <= penY + 9;
    }

    private boolean isMouseOverTitleEditor(double mx, double my) {
        return mx >= titleEditor.getX() && mx <= titleEditor.getX() + titleEditor.getWidth()
            && my >= titleEditor.getY() && my <= titleEditor.getY() + titleEditor.getHeight();
    }

    private void enterTitleEdit() {
        editingTitle = true;
        titleEditor.setVisible(true);
        titleEditor.setValue(getDisplayTitle());
        titleEditor.setCursorPosition(titleEditor.getValue().length());
        titleEditor.setFocused(true);
        input.setCanLoseFocus(true);
        setFocused(titleEditor);
    }

    private void exitTitleEdit(boolean save) {
        if (save) {
            ChatMessageStore.setCustomTitle(titleEditor.getValue().trim());
        }
        editingTitle = false;
        titleEditor.setVisible(false);
        input.setCanLoseFocus(false);
        setFocused(input);
    }

    private void renderMessages(GuiGraphics g, int mouseX, int mouseY) {
        bubbleRects.clear();
        clickableSpans.clear();
        List<ChatMessageStore.ChatMessage> messages = ChatMessageStore.getMessages();
        if (messages.isEmpty()) return;

        // Count time separators for total height
        int timeSeps = 0;
        String lastKey = null;
        for (var msg : messages) {
            if (!msg.isSystem()) {
                String key = timeKey(msg.time());
                if (!key.isEmpty() && (lastKey == null || !key.equals(lastKey))) { timeSeps++; lastKey = key; }
            }
        }

        int effectiveMsgBottom = newMessageCount > 0 ? barTop - NOTIF_H - 1 : msgBottom;
        int areaH = effectiveMsgBottom - msgTop;
        int totalH = 0;
        for (var msg : messages) totalH += getMsgHeight(msg) + GAP;
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
            if (lastSeenMessageCount > currentMsgCount) lastSeenMessageCount = currentMsgCount;
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

        if (scrollToBottom || wasAtBottom) {
            scrollOffset = maxScroll;
            scrollToBottom = false;
        }
        scrollOffset = Mth.clamp(scrollOffset, 0, maxScroll);

        g.enableScissor(panelX, msgTop, panelX + panelW, effectiveMsgBottom);

        int contentY = 0;
        lastKey = null;
        for (int i = 0; i < messages.size(); i++) {
            var msg = messages.get(i);

            // Time separator
            if (!msg.isSystem()) {
                String key = timeKey(msg.time());
                if (!key.isEmpty() && (lastKey == null || !key.equals(lastKey))) {
                    lastKey = key;
                    int ssy = msgTop + contentY - scrollOffset;
                    if (ssy + TIME_SEP_H > msgTop && ssy < msgBottom)
                        renderTimeSeparator(g, msg.time(), ssy);
                    contentY += TIME_SEP_H + GAP;
                }
            }

            int h = getMsgHeight(msg);
            int screenY = msgTop + contentY - scrollOffset;
            contentY += h + GAP;

            if (screenY + h <= msgTop || screenY >= effectiveMsgBottom) continue;
            renderBubble(g, msg, i, screenY, mouseX, mouseY);
        }
        renderScrollbar(g, mouseX, mouseY, effectiveMsgBottom);
        g.disableScissor();
    }

    private void renderScrollbar(GuiGraphics g, int mouseX, int mouseY, int effectiveMsgBottom) {
        if (maxScroll <= 0) return;
        int trackX = panelX + panelW - SCROLLBAR_WIDTH;
        int trackTop = msgTop;
        int trackBottom = effectiveMsgBottom;
        int trackH = trackBottom - trackTop;
        g.fill(trackX, trackTop, trackX + SCROLLBAR_WIDTH, trackBottom, TRACK_COLOR);
        int thumbH = Math.max(MIN_THUMB_H, (int)((long)trackH * trackH / messageTotalH));
        thumbH = Math.min(thumbH, trackH);
        int travelRange = trackH - thumbH;
        int thumbY = trackTop + (int)((long)scrollOffset * travelRange / maxScroll);
        boolean hovering = !scrollbarDragging
            && mouseX >= trackX && mouseX < trackX + SCROLLBAR_WIDTH
            && mouseY >= thumbY && mouseY < thumbY + thumbH;
        scrollbarHovered = hovering || scrollbarDragging;
        int thumbColor = scrollbarDragging ? THUMB_DRAG_COLOR
                       : scrollbarHovered   ? THUMB_HOVER_COLOR
                       : THUMB_COLOR;
        g.fill(trackX, thumbY, trackX + SCROLLBAR_WIDTH, thumbY + thumbH, thumbColor);
    }

    private void renderTimeSeparator(GuiGraphics g, LocalTime time, int y) {
        String text = time.format(TIME_FMT);
        int tw = font.width(text);
        int tx = panelX + (panelW - tw) / 2;
        g.fill(tx - 6, y + 2, tx + tw + 6, y + TIME_SEP_H - 2, 0x44000000);
        g.drawString(font, Component.literal(text), tx, y + 3, 0xFF999999, false);
    }

    private int getMsgHeight(ChatMessageStore.ChatMessage msg) {
        if (msg.isSystem()) {
            List<FormattedCharSequence> lines = font.split(msg.content(), panelW - PAD * 2 - 20);
            return lines.size() * font.lineHeight + 4;
        }
        int bubbleMaxW = panelW - AVATAR - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
        List<FormattedCharSequence> lines = font.split(msg.content(), bubbleMaxW);
        int h = lines.size() * font.lineHeight + BUBBLE_PAD_Y * 2 + NAME_H;
        if (msg.replyContent() != null) h += font.lineHeight + 2;
        return h;
    }

    private void renderBubble(GuiGraphics g, ChatMessageStore.ChatMessage msg,
                               int index, int baseY, int mouseX, int mouseY) {
        if (msg.isSystem()) {
            List<FormattedCharSequence> lines = font.split(msg.content(), panelW - PAD * 2 - 20);
            int yy = baseY + 2;
            net.minecraft.network.chat.Style fb = findClickStyle(msg.content());
            for (var line : lines) {
                int lw = font.width(line);
                renderLineWithClicks(g, line, panelX + (panelW - lw) / 2, yy, 0xFF888888, fb);
                yy += font.lineHeight;
            }
            return;
        }

        boolean own = msg.isOwn();
        int bubbleMaxW = panelW - AVATAR - PAD * 2 - BUBBLE_PAD_X * 2 - 16;
        List<FormattedCharSequence> lines = font.split(msg.content(), bubbleMaxW);

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

        // Reply preview
        if (msg.replyContent() != null) {
            int replyH = font.lineHeight;
            int replyBarX = own ? bubbleX : bubbleX;
            int replyMaxW = bubbleW - 10;
            String replyText = msg.replySender() + ": " + msg.replyContent();
            String replyDisplay = font.plainSubstrByWidth(replyText, replyMaxW - font.width("..."));
            if (!replyDisplay.equals(replyText)) replyDisplay += "...";
            g.fill(replyBarX, nameY, replyBarX + 2, nameY + replyH, 0xFFFFFFFF);
            g.drawString(font, Component.literal(replyDisplay), replyBarX + 6, nameY + 1, 0xFF999999, false);
            nameY += replyH + 2;
        }

        if (!msg.senderName().getString().isEmpty() && msg.replyContent() == null) {
            int maxNameW = panelW - AVATAR - PAD * 2 - 20;
            Component displayName = msg.senderName();
            if (font.width(displayName) > maxNameW)
                displayName = Component.literal(font.plainSubstrByWidth(displayName.getString(), maxNameW - font.width("...")) + "...");
            int nameW = font.width(displayName);
            int startX = own ? (bubbleX + bubbleW - nameW) : bubbleX;
            g.drawString(font, displayName, startX, nameY, COLOR_NAME, false);
        }

        int bubbleY = baseY + (msg.replyContent() != null ? font.lineHeight + 2 : NAME_H);
        int avatarY = baseY;

        int bg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_BUBBLE_COLOR.get(), 0xFF95EC69)
            : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_BUBBLE_COLOR.get(), 0xFF4A4A4A);
        int fg = own
            ? ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OWN_TEXT_COLOR.get(), 0xFF0A0A0A)
            : ChatBubbleConfig.parseHexColor(ChatBubbleConfig.OTHER_TEXT_COLOR.get(), 0xFFFFFFFF);

        g.fill(bubbleX, bubbleY, bubbleX + bubbleW, bubbleY + bubbleH, bg);

        net.minecraft.network.chat.Style fbP = findClickStyle(msg.content());
        for (int li = 0; li < lines.size(); li++)
            renderLineWithClicks(g, lines.get(li), bubbleX + BUBBLE_PAD_X,
                bubbleY + BUBBLE_PAD_Y + li * font.lineHeight, fg, fbP);

        ResourceLocation skin = getSkin(msg.senderUUID());
        PlayerFaceRenderer.draw(g, skin, avatarX, avatarY, AVATAR);

        if (msg.duplicateCount() > 1) {
            String label = "x" + msg.duplicateCount();
            int labelW = font.width(label);
            int labelX, labelY = bubbleY + (bubbleH - font.lineHeight) / 2;
            if (own) {
                labelX = bubbleX - labelW - 3;
            } else {
                labelX = bubbleX + bubbleW + 3;
            }
            g.drawString(font, Component.literal(label), labelX, labelY, 0xFFFFAA00, false);
        }

        bubbleRects.add(new int[]{bubbleX, bubbleY, bubbleW, bubbleH, index});
    }

    private void renderLineWithClicks(GuiGraphics g, FormattedCharSequence line,
                                       int x, int y, int color) {
        renderLineWithClicks(g, line, x, y, color, null);
    }

    private void renderLineWithClicks(GuiGraphics g, FormattedCharSequence line,
                                       int x, int y, int color,
                                       net.minecraft.network.chat.Style fallback) {
        g.drawString(font, line, x, y, color, false);

        final int[] pos = {0};
        final int[] spanStart = {-1};
        final net.minecraft.network.chat.Style[] spanStyle = {null};
        final int beforeCount = clickableSpans.size();

        line.accept((index, style, codePoint) -> {
            int charW = font.width(new String(Character.toChars(codePoint)));
            if (style.getClickEvent() != null) {
                if (spanStart[0] < 0) {
                    spanStart[0] = pos[0]; spanStyle[0] = style;
                } else if (!style.equals(spanStyle[0])) {
                    clickableSpans.add(new ClickableSpan(x + spanStart[0], y,
                        pos[0] - spanStart[0], font.lineHeight, spanStyle[0]));
                    spanStart[0] = pos[0]; spanStyle[0] = style;
                }
            } else {
                if (spanStart[0] >= 0) {
                    clickableSpans.add(new ClickableSpan(x + spanStart[0], y,
                        pos[0] - spanStart[0], font.lineHeight, spanStyle[0]));
                    spanStart[0] = -1; spanStyle[0] = null;
                }
            }
            pos[0] += charW;
            return true;
        });
        if (spanStart[0] >= 0) {
            clickableSpans.add(new ClickableSpan(x + spanStart[0], y,
                pos[0] - spanStart[0], font.lineHeight, spanStyle[0]));
        }
        if (clickableSpans.size() == beforeCount && fallback != null && fallback.getClickEvent() != null) {
            clickableSpans.add(new ClickableSpan(x, y, pos[0], font.lineHeight, fallback.withUnderlined(true)));
        }
    }

    private net.minecraft.network.chat.Style getHoveredStyle(double mouseX, double mouseY) {
        for (ClickableSpan s : clickableSpans) {
            if (mouseX >= s.x && mouseX <= s.x + s.w
                && mouseY >= s.y && mouseY <= s.y + s.h)
                return s.style;
        }
        return null;
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

    private void renderNotificationBar(GuiGraphics g, int mouseX, int mouseY) {
        if (newMessageCount <= 0) return;
        int notifY = barTop - NOTIF_H;
        g.fill(panelX, notifY - 1, panelX + panelW, notifY, COLOR_DIVIDER);
        int yellow = 0xFFFFFF55;
        int textY = notifY + (NOTIF_H - font.lineHeight) / 2;
        String ct = newMessageCount + Component.translatable("e33chat.notif.new_messages").getString() + " ▽";
        notifCountLeft = panelX + PAD;
        notifCountRight = notifCountLeft + font.width(ct);
        notifBarTextY = textY;
        boolean h = mouseX >= notifCountLeft && mouseX <= notifCountRight
            && mouseY >= textY && mouseY <= textY + font.lineHeight;
        g.drawString(font, Component.literal(ct), notifCountLeft, textY, h ? 0xFFFFFF88 : yellow, false);
        if (hasNewMentionOrQuote) {
            String mt = Component.translatable("e33chat.notif.mention").getString() + " ▽";
            notifMentionLeft = panelX + panelW - PAD - font.width(mt);
            notifMentionRight = notifMentionLeft + font.width(mt);
            h = mouseX >= notifMentionLeft && mouseX <= notifMentionRight
                && mouseY >= textY && mouseY <= textY + font.lineHeight;
            g.drawString(font, Component.literal(mt), notifMentionLeft, textY, h ? 0xFFFFFF88 : yellow, false);
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

        g.fill(menuX, menuY, menuX + CTX_W, menuY + menuH, 0xEE2A2A2A);
        g.fill(menuX, menuY, menuX + CTX_W, menuY + 1, COLOR_DIVIDER);
        g.fill(menuX, menuY + menuH - 1, menuX + CTX_W, menuY + menuH, COLOR_DIVIDER);
        g.fill(menuX, menuY, menuX + 1, menuY + menuH, COLOR_DIVIDER);
        g.fill(menuX + CTX_W - 1, menuY, menuX + CTX_W, menuY + menuH, COLOR_DIVIDER);

        boolean hoverCopy = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + CTX_ITEM_H;
        int copyBg = hoverCopy ? 0xFF4A4A4A : 0xFF3A3A3A;
        g.fill(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + CTX_ITEM_H, copyBg);
        g.drawString(font, Component.translatable("e33chat.context.copy"), menuX + 8, menuY + 4, 0xFFFFFFFF, false);

        g.fill(menuX + 4, menuY + CTX_ITEM_H, menuX + CTX_W - 4, menuY + CTX_ITEM_H + 1, 0xFF555555);

        boolean hoverQuote = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY + CTX_ITEM_H + 1 && mouseY <= menuY + menuH;
        int quoteBg = hoverQuote ? 0xFF4A4A4A : 0xFF3A3A3A;
        g.fill(menuX + 1, menuY + CTX_ITEM_H + 1, menuX + CTX_W - 1, menuY + menuH - 1, quoteBg);
        g.drawString(font, Component.translatable("e33chat.context.quote"), menuX + 8, menuY + CTX_ITEM_H + 5, 0xFFFFFFFF, false);
    }

    private void renderAvatarContextMenu(GuiGraphics g, int mouseX, int mouseY) {
        if (contextAvatarIndex < 0) return;
        int menuH = CTX_ITEM_H + 2;
        int menuX = Math.min(contextAvatarX, panelX + panelW - CTX_W - 2);
        int menuY = contextAvatarY - menuH;
        if (menuY < msgTop) menuY = contextAvatarY + 4;

        g.fill(menuX, menuY, menuX + CTX_W, menuY + menuH, 0xEE2A2A2A);
        g.fill(menuX, menuY, menuX + CTX_W, menuY + 1, COLOR_DIVIDER);
        g.fill(menuX, menuY + menuH - 1, menuX + CTX_W, menuY + menuH, COLOR_DIVIDER);
        g.fill(menuX, menuY, menuX + 1, menuY + menuH, COLOR_DIVIDER);
        g.fill(menuX + CTX_W - 1, menuY, menuX + CTX_W, menuY + menuH, COLOR_DIVIDER);

        boolean hover = mouseX >= menuX && mouseX <= menuX + CTX_W
            && mouseY >= menuY && mouseY <= menuY + menuH;
        int bg = hover ? 0xFF4A4A4A : 0xFF3A3A3A;
        g.fill(menuX + 1, menuY + 1, menuX + CTX_W - 1, menuY + menuH - 1, bg);
        g.drawString(font, Component.translatable("e33chat.context.tp"), menuX + 8, menuY + 4, 0xFFFFFFFF, false);
    }

    private static final int REPLY_BAR_H = 18;

    private void renderReplyBar(GuiGraphics g, int mouseX, int mouseY) {
        if (replyTargetIndex < 0) return;
        ChatMessageStore.ChatMessage target = ChatMessageStore.getMessageAt(replyTargetIndex);
        if (target == null) { replyTargetIndex = -1; return; }

        int notifOffset = (newMessageCount > 0) ? NOTIF_H : 0;
        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S;
        int barX = gearX + ICON_S + 4;
        int barW = sendX - 6 - barX;
        int barY = barTop - REPLY_BAR_H - notifOffset;

        g.fill(barX, barY, barX + barW, barTop - notifOffset, 0xEE1E1E1E);
        g.fill(barX, barTop - notifOffset - 1, barX + barW, barTop - notifOffset, COLOR_DIVIDER);

        String sender = target.senderName().getString();
        if (sender.isEmpty()) sender = Component.translatable("e33chat.sender.system").getString();
        String preview = sender + ": " + target.content().getString();
        int maxW = barW - 24;
        String display = font.plainSubstrByWidth(preview, maxW - font.width("..."));
        if (!display.equals(preview)) display += "...";
        g.drawString(font, Component.literal(display), barX + 6, barY + 4, 0xFFAAAAAA, false);

        int cx = barX + barW - 16;
        int cy = barY + 3;
        boolean hoverX = mouseX >= cx && mouseX <= cx + 12 && mouseY >= cy && mouseY <= cy + 12;
        int xBg = hoverX ? 0xFF555555 : 0xFF3A3A3A;
        g.fill(cx, cy, cx + 12, cy + 12, xBg);
        g.drawCenteredString(font, Component.literal("✕"), cx + 6, cy + 2, 0xFFCCCCCC);
    }

    private boolean isMouseOverReplyCancel(double mx, double my) {
        if (replyTargetIndex < 0) return false;
        int notifOffset = (newMessageCount > 0) ? NOTIF_H : 0;
        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S;
        int barX = gearX + ICON_S + 4;
        int barW = sendX - 6 - barX;
        int barY = barTop - REPLY_BAR_H - notifOffset;
        int cx = barX + barW - 16;
        int cy = barY + 3;
        return mx >= cx && mx <= cx + 12 && my >= cy && my <= cy + 12;
    }

    private void renderToast(GuiGraphics g) {
        if (copyToastTicks <= 0) return;
        int alpha = copyToastTicks > 5 ? 0xFF : (copyToastTicks * 255 / 5) << 24;
        int color = alpha | 0xFFFFFF;
        String text = Component.translatable("e33chat.toast.copied").getString();
        int tw = font.width(text);
        int tx = panelX + (panelW - tw) / 2;
        int ty = msgBottom - 24;
        g.fill(tx - 6, ty - 2, tx + tw + 6, ty + font.lineHeight + 2, 0xCC000000);
        g.drawString(font, Component.literal(text), tx, ty, color, false);
    }

    private void renderBottomBar(GuiGraphics g, int mouseX, int mouseY) {
        g.fill(panelX, barTop, panelX + panelW, height, COLOR_BAR_BG);
        g.fill(panelX, barTop, panelX + panelW, barTop + 1, COLOR_DIVIDER);

        int iconY = barTop + (BAR_H - ICON_S) / 2;

        // Input background + divider above it
        int gearX = panelX + 4;
        int sendX = panelX + panelW - PAD - ICON_S;
        int emojiX = sendX - ICON_S - 6;
        int ibX = gearX + ICON_S + 3;
        int ibY = barTop + (BAR_H - INPUT_H) / 2;
        int ibW = emojiX - 6 - ibX;
        int ibH = INPUT_H;
        g.fill(ibX, ibY - 1, ibX + ibW, ibY, COLOR_DIVIDER);
        g.fill(ibX, ibY, ibX + ibW, ibY + ibH, COLOR_INPUT_BG);

        // Gear icon (left)
        boolean hoverGear = mouseX >= gearX && mouseX <= gearX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverGear) g.fill(gearX - 1, iconY - 1, gearX + ICON_S + 1, iconY + ICON_S + 1, 0xFF444444);
        drawTextureIcon(g, TEX_GEAR, gearX, iconY, ICON_S);

        // Emoji icon (between input and send)
        boolean hoverEmoji = mouseX >= emojiX && mouseX <= emojiX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverEmoji || showEmojiPanel) g.fill(emojiX - 1, iconY - 1, emojiX + ICON_S + 1, iconY + ICON_S + 1, 0xFF444444);
        drawTextureIcon(g, TEX_EMOJI, emojiX, iconY, ICON_S);

        // Send icon (right)
        boolean hoverSend = mouseX >= sendX && mouseX <= sendX + ICON_S
            && mouseY >= iconY && mouseY <= iconY + ICON_S;
        if (hoverSend) g.fill(sendX - 1, iconY - 1, sendX + ICON_S + 1, iconY + ICON_S + 1, 0xFF444444);
        drawTextureIcon(g, TEX_SEND, sendX, iconY, ICON_S);
    }

    private void loadIconTextures() {
        loadIconTexture(TEX_GEAR, "assets/e33chat/textures/gui/settings.png");
        loadIconTexture(TEX_SEND, "assets/e33chat/textures/gui/send.png");
        loadIconTexture(TEX_EMOJI, "assets/e33chat/textures/gui/emoji.png");
    }

    private void loadIconTexture(ResourceLocation loc, String classpath) {
        try (InputStream in = getClass().getClassLoader().getResourceAsStream(classpath)) {
            if (in != null) {
                NativeImage img = NativeImage.read(in);
                DynamicTexture tex = new DynamicTexture(img);
                minecraft.getTextureManager().register(loc, tex);
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void drawTextureIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        AbstractTexture abstractTex;
        try {
            abstractTex = minecraft.getTextureManager().getTexture(tex);
        } catch (Exception e) {
            loadIconTextures();
            abstractTex = minecraft.getTextureManager().getTexture(tex);
        }
        RenderSystem.setShaderTexture(0, abstractTex.getId());
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.enableBlend();
        g.blit(tex, x, y, 0, 0, size, size, size, size);
    }

    private static final UUID NIL_UUID = new UUID(0, 0);

    private ResourceLocation getSkin(UUID uuid) {
        if (uuid == null || uuid.equals(NIL_UUID))
            return DefaultPlayerSkin.get(NIL_UUID).texture();
        if (minecraft.getConnection() == null)
            return DefaultPlayerSkin.get(NIL_UUID).texture();
        PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
        return info != null ? info.getSkin().texture() : DefaultPlayerSkin.get(uuid).texture();
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
                if (!k.isEmpty() && (lk == null || !k.equals(lk))) {
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

    private void sendMessage() {
        String text = input.getValue().trim();
        if (text.isEmpty()) return;

        if (replyTargetIndex >= 0) {
            ChatMessageStore.ChatMessage target = ChatMessageStore.getMessageAt(replyTargetIndex);
            if (target != null) {
                ChatMessageStore.setPendingReply(target.content().getString(), target.senderName().getString());
                QuoteSyncPayload.send(target.senderName().getString(), target.content().getString(), text);
            }
            replyTargetIndex = -1;
        }

        if (minecraft.player == null || minecraft.player.connection == null) return;

        if (text.startsWith("/"))
            minecraft.player.connection.sendCommand(text.substring(1));
        else
            minecraft.player.connection.sendChat(text);
        minecraft.gui.getChat().addRecentChat(text);

        ChatMessageStore.addMessage(Component.literal(text),
            minecraft.player.getUUID(),
            Component.literal(minecraft.player.getName().getString()),
            false,
            minecraft.player.getName().getString());
        ChatMessageStore.incrementPendingEcho(text);

        savedInput = "";
        input.setValue("");
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
        savedInput = input.getValue();
        ChatMessageStore.setScreenOpen(false);
        minecraft.gui.getChat().resetChatScroll();
    }

    @Override
    public void onClose() {
        savedInput = input.getValue();
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

        g.fill(popupX, popupY, popupX + popupW, popupY + popupH, COLOR_POPUP_BG);
        g.fill(popupX, popupY, popupX + popupW, popupY + 1, COLOR_DIVIDER);
        g.fill(popupX, popupY + popupH - 1, popupX + popupW, popupY + popupH, COLOR_DIVIDER);

        int startIdx = Math.max(0, mentionIdx - visible + 1);
        int endIdx = Math.min(mentionCandidates.size(), startIdx + visible);
        if (endIdx - startIdx < visible)
            startIdx = Math.max(0, endIdx - visible);
        for (int i = startIdx; i < endIdx; i++) {
            int ly = popupY + 2 + (i - startIdx) * font.lineHeight;
            boolean hover = mouseX >= popupX && mouseX <= popupX + popupW
                && mouseY >= ly && mouseY <= ly + font.lineHeight;
            if (hover || i == mentionIdx)
                g.fill(popupX + 1, ly, popupX + popupW - 1, ly + font.lineHeight,
                    i == mentionIdx ? COLOR_POPUP_HOVER : 0xFF333333);
            g.drawString(font, Component.literal(mentionCandidates.get(i)),
                popupX + 4, ly, 0xFFFFFFFF, false);
        }
    }

    private void renderEmojiPanel(GuiGraphics g, int mouseX, int mouseY) {
        if (!showEmojiPanel) return;
        int sendX = panelX + panelW - PAD - ICON_S;

        boolean isKaomoji = emojiTab == 1;
        int kCols = 2;
        int kColW = 90;
        int pw = isKaomoji ? kCols * kColW + 8 : EMOJI_COLS * EMOJI_SLOT + 8;
        int px = sendX + ICON_S / 2 - pw / 2;
        px = Mth.clamp(px, panelX + 2, panelX + panelW - pw - 2);
        int py = barTop - EMOJI_PANEL_H - 4;

        // Tab bar
        String[] tabLabels = {"😊 Emoji", "✧ 颜文字"};
        int tabW = pw / tabLabels.length;
        g.fill(px, py, px + pw, py + EMOJI_TAB_H + 1, COLOR_TITLE_BG);
        for (int t = 0; t < tabLabels.length; t++) {
            int tx = px + t * tabW;
            if (t == emojiTab) g.fill(tx, py, tx + tabW, py + EMOJI_TAB_H, COLOR_INPUT_BG);
            g.drawCenteredString(font, Component.literal(tabLabels[t]),
                tx + tabW / 2, py + (EMOJI_TAB_H - font.lineHeight) / 2, 0xFFFFFFFF);
        }
        g.fill(px, py + EMOJI_TAB_H, px + pw, py + EMOJI_TAB_H + 1, COLOR_DIVIDER);

        // Content area
        int cy = py + EMOJI_TAB_H + 1;
        int ch = EMOJI_PANEL_H - EMOJI_TAB_H - 1;
        g.fill(px, cy, px + pw, py + EMOJI_PANEL_H, COLOR_BAR_BG);
        g.renderOutline(px, py, pw, EMOJI_PANEL_H, COLOR_DIVIDER);

        if (isKaomoji) {
            renderKaomojiList(g, mouseX, mouseY, px, cy, pw, ch);
        } else {
            renderEmojiGrid(g, mouseX, mouseY, px, cy, pw, ch);
        }
    }

    private void renderEmojiGrid(GuiGraphics g, int mouseX, int mouseY,
                                  int px, int cy, int pw, int ch) {
        int rows = (EMOJI_EMOTES.length + EMOJI_COLS - 1) / EMOJI_COLS;
        int totalH = rows * EMOJI_SLOT + 4;
        int maxScroll = Math.max(0, totalH - ch + 4);
        emojiScroll = Mth.clamp(emojiScroll, 0, maxScroll);

        g.enableScissor(px + 1, cy + 1, px + pw - 1, cy + ch - 1);
        int sy = cy + 2 - emojiScroll;
        for (int i = 0; i < EMOJI_EMOTES.length; i++) {
            int col = i % EMOJI_COLS;
            int row = i / EMOJI_COLS;
            int ex = px + 4 + col * EMOJI_SLOT;
            int ey = sy + row * EMOJI_SLOT;
            if (ey + EMOJI_SLOT <= cy || ey >= cy + ch) continue;
            if (mouseX >= ex && mouseX <= ex + EMOJI_SLOT - 1
                && mouseY >= ey && mouseY <= ey + EMOJI_SLOT - 1)
                g.fill(ex, ey, ex + EMOJI_SLOT - 1, ey + EMOJI_SLOT - 1, 0xFF444444);
            g.drawCenteredString(font, Component.literal(EMOJI_EMOTES[i]),
                ex + EMOJI_SLOT / 2, ey + (EMOJI_SLOT - font.lineHeight) / 2, 0xFFFFFFFF);
        }
        g.disableScissor();
    }

    private void renderKaomojiList(GuiGraphics g, int mouseX, int mouseY,
                                    int px, int cy, int pw, int ch) {
        int kCols = 2;
        int kColW = (pw - 8) / kCols;
        int totalH = ((KAOMOJI.length + kCols - 1) / kCols) * KAO_ITEM_H + 4;
        int maxScroll = Math.max(0, totalH - ch + 4);
        emojiScroll = Mth.clamp(emojiScroll, 0, maxScroll);

        g.enableScissor(px + 1, cy + 1, px + pw - 1, cy + ch - 1);
        int sy = cy + 2 - emojiScroll;
        for (int i = 0; i < KAOMOJI.length; i++) {
            int col = i % kCols;
            int row = i / kCols;
            int ex = px + 4 + col * kColW;
            int ey = sy + row * KAO_ITEM_H;
            if (ey + KAO_ITEM_H <= cy || ey >= cy + ch) continue;
            if (mouseX >= ex && mouseX <= ex + kColW - 1
                && mouseY >= ey && mouseY <= ey + KAO_ITEM_H - 1)
                g.fill(ex, ey, ex + kColW - 1, ey + KAO_ITEM_H - 1, 0xFF444444);
            g.drawString(font, Component.literal(KAOMOJI[i]),
                ex + 2, ey + (KAO_ITEM_H - font.lineHeight) / 2, 0xFFFFFFFF);
        }
        g.disableScissor();
    }

    private static class ClickableSpan {
        final int x, y, w, h;
        final net.minecraft.network.chat.Style style;
        ClickableSpan(int x, int y, int w, int h, net.minecraft.network.chat.Style style) {
            this.x = x; this.y = y; this.w = w; this.h = h; this.style = style;
        }
    }
}
