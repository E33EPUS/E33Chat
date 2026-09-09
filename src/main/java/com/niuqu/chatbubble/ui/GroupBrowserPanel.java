package com.niuqu.chatbubble.ui;

import com.niuqu.chatbubble.chat.GroupChannelState;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.gui.Font;
import net.minecraft.network.chat.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * [+] group browser popup (2.4.10): lists the server's groups (capped at
 * MAX_ROWS; the rest stay reachable through /e33chat group list), lets the
 * player join by clicking a row, leave joined ones, and create new groups.
 *
 * The panel only paints and hit-tests; actions are returned encoded
 * (ACT_*) with the group name in {@link #actionGroup} so the screen owns the
 * packet dispatch — same contract as ChatSettingsMenu.handleQuickAction.
 */
public class GroupBrowserPanel {
    public static final int ACT_NONE = 0;
    public static final int ACT_JOIN = 101;
    public static final int ACT_LEAVE = 102;
    public static final int ACT_CREATE = 103;

    static final int PANEL_W = 230;
    static final int ROW_H = 16;
    static final int INPUT_H = 14;
    static final int BTN_H = 14;
    static final int TITLE_H = 16;
    static final int MAX_ROWS = 6;

    public boolean visible;

    /** Group name of the last returned action (join/leave/create). */
    public String actionGroup;

    private int px, py, w, h;
    private final List<int[]> rowRects = new ArrayList<>();
    private final List<String> rowGroups = new ArrayList<>();
    private int[] leaveBtn = null;
    private int[] createBtn = null;

    /** Visible row cap keeps the popup fixed-size; more groups → command hint. */
    private static int visibleRows(int groupCount) {
        return Math.min(groupCount, MAX_ROWS);
    }

    public static int panelHeight(int groupCount) {
        int rows = visibleRows(groupCount);
        return TITLE_H + 4 + rows * (ROW_H + 2) + 4 + INPUT_H + 6;
    }

    public void render(GuiGraphics g, int mouseX, int mouseY, Font font, ChatBubbleTheme.Colors c,
                       int panelX, int panelW, int barTop, EditBox createInput, float alpha) {
        if (!visible) return;
        int a255 = (int) (255 * alpha);
        w = Math.max(120, Math.min(PANEL_W, panelW - 4));
        h = panelHeight(GroupChannelState.knownGroups.size());
        px = ChatSearchPanel.clampX(panelX + panelW / 2 - w / 2, w, panelX, panelW);
        py = barTop - h - 4;

        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.CONTENT_BG),
            px, py, w, h, alpha);
        g.renderOutline(px, py, w, h, ChatBubbleTheme.alphaBlend(c.divider(), a255));

        int y = py + 3;
        g.drawString(font, Component.translatable("e33chat.group.browser_title"),
            px + 6, y, ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
        y += TITLE_H;

        rowRects.clear();
        rowGroups.clear();
        leaveBtn = null;
        var groups = List.copyOf(GroupChannelState.knownGroups);
        int rows = visibleRows(groups.size());
        for (int i = 0; i < rows; i++) {
            String name = groups.get(i);
            boolean joined = GroupChannelState.myGroups.contains(name);
            int rowY = y;
            boolean hover = mouseX >= px + 3 && mouseX <= px + w - 3
                && mouseY >= rowY && mouseY <= rowY + ROW_H;
            if (joined) {
                g.fill(px + 3, rowY, px + w - 3, rowY + ROW_H,
                    ChatBubbleTheme.alphaBlend(c.sidebarItemSelected(), a255));
            } else if (hover) {
                g.fill(px + 3, rowY, px + w - 3, rowY + ROW_H,
                    ChatBubbleTheme.alphaBlend(c.sidebarItemHover(), a255));
            }
            g.drawString(font, Component.literal(name), px + 6, rowY + (ROW_H - font.lineHeight) / 2 + 1,
                ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
            rowRects.add(new int[]{px + 3, rowY, px + w - 3, rowY + ROW_H});
            rowGroups.add(name);
            if (joined) {
                String leave = Component.translatable("e33chat.group.leave_btn").getString();
                int bw = font.width(leave) + 8;
                int bx = px + w - bw - 6;
                boolean btnHover = mouseX >= bx && mouseX <= bx + bw
                    && mouseY >= rowY + 1 && mouseY <= rowY + ROW_H - 1;
                g.fill(bx, rowY + 1, bx + bw, rowY + ROW_H - 1,
                    ChatBubbleTheme.alphaBlend(btnHover ? c.contextHover() : c.popupBg(), a255));
                g.drawString(font, Component.literal(leave), bx + 4, rowY + (ROW_H - font.lineHeight) / 2 + 1,
                    ChatBubbleTheme.alphaBlend(c.textSecondary(), a255), false);
                leaveBtn = new int[]{bx, rowY + 1, bx + bw, rowY + ROW_H - 1};
            }
            y += ROW_H + 2;
        }
        if (groups.size() > MAX_ROWS) {
            g.drawString(font, Component.translatable("e33chat.group.more_hint", groups.size() - MAX_ROWS),
                px + 6, y + 1, ChatBubbleTheme.alphaBlend(c.textMuted(), a255), false);
            y += ROW_H + 2;
        }

        y += 4;
        int inputW = w - 8 - 52;
        ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.INPUT_BG),
            px + 4, y, inputW, INPUT_H, alpha);
        boolean inputHover = mouseX >= px + 4 && mouseX <= px + 4 + inputW
            && mouseY >= y && mouseY <= y + INPUT_H;
        if (inputHover || createInput.isFocused())
            g.renderOutline(px + 4, y, inputW, INPUT_H, ChatBubbleTheme.alphaBlend(c.textMuted(), a255));
        createInput.setX(px + 6);
        createInput.setY(y + 3);
        createInput.setWidth(inputW - 4);
        createInput.setHeight(INPUT_H - 2);
        createInput.setVisible(true);
        if (createInput.getValue().isEmpty()) {
            g.drawString(font, Component.translatable("e33chat.group.create_placeholder"),
                px + 6, y + 3, ChatBubbleTheme.alphaBlend(c.textMuted(), a255), false);
        }

        String create = Component.translatable("e33chat.group.create_btn").getString();
        int bw = font.width(create) + 8;
        int bx = px + w - bw - 4;
        boolean btnHover = mouseX >= bx && mouseX <= bx + bw && mouseY >= y && mouseY <= y + INPUT_H;
        g.fill(bx, y, bx + bw, y + INPUT_H,
            ChatBubbleTheme.alphaBlend(btnHover ? c.contextHover() : c.popupBg(), a255));
        g.renderOutline(bx, y, bw, INPUT_H, ChatBubbleTheme.alphaBlend(c.divider(), a255));
        g.drawString(font, Component.literal(create), bx + 4, y + (INPUT_H - font.lineHeight) / 2 + 1,
            ChatBubbleTheme.alphaBlend(c.textPrimary(), a255), false);
        createBtn = new int[]{bx, y, bx + bw, y + INPUT_H};
    }

    /** Encoded action, or ACT_NONE. Clicks inside the panel are consumed (returns -1). */
    public int handleClick(double mx, double my, Font font,
                           int panelX, int panelW, int barTop, EditBox createInput) {
        if (!visible) return ACT_NONE;
        if (mx < px || mx > px + w || my < py || my > py + h) return ACT_NONE;
        if (createBtn != null && over(mx, my, createBtn)) {
            actionGroup = createInput.getValue().trim();
            if (!actionGroup.isEmpty()) {
                createInput.setValue("");
                return ACT_CREATE;
            }
            return -1;
        }
        if (leaveBtn != null && over(mx, my, leaveBtn)) {
            int idx = leaveRowIndex();
            actionGroup = idx >= 0 ? rowGroups.get(idx) : null;
            return actionGroup != null ? ACT_LEAVE : -1;
        }
        for (int i = 0; i < rowRects.size(); i++) {
            int[] r = rowRects.get(i);
            if (over(mx, my, new int[]{r[0], r[1], r[2], r[3]})) {
                actionGroup = rowGroups.get(i);
                return GroupChannelState.myGroups.contains(actionGroup) ? -1 : ACT_JOIN;
            }
        }
        return -1;
    }

    private int leaveRowIndex() {
        if (leaveBtn == null) return -1;
        for (int i = 0; i < rowRects.size(); i++) {
            int[] r = rowRects.get(i);
            int[] l = leaveBtn;
            if (l[1] >= r[1] && l[3] <= r[3]) return i;
        }
        return -1;
    }

    public boolean isClickOnPanel(double mx, double my) {
        return visible && mx >= px && mx <= px + w && my >= py && my <= py + h;
    }

    private static boolean over(double mx, double my, int[] r) {
        return mx >= r[0] && mx <= r[2] && my >= r[1] && my <= r[3];
    }
}
