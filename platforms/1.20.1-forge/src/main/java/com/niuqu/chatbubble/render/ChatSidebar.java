package com.niuqu.chatbubble.render;
import com.niuqu.chatbubble.render.ChatBubbleScreen;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.texture.UiElement;
import com.niuqu.chatbubble.texture.UiTextureManager;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class ChatSidebar {

    public static final int WIDTH = 90;
    private static final int ITEM_H = 22;
    private static final int ICON_S = 20;
    private static final int SEARCH_H = 14;
    private ChatSidebar() {}

    // ---- Hit testing (called from ChatBubbleScreen) ----

    public static boolean handleMouseClicked(double mouseX, double mouseY,
                                              String whisperPartner, Font font,
                                              int screenX, boolean visible,
                                              EditBox searchBox, int scrollOffset) {
        if (!visible) return false;
        int localX = (int)mouseX - screenX;
        if (localX < 0 || localX > WIDTH) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return false;

        // Search box
        if (mouseY >= 2 && mouseY <= 2 + SEARCH_H) return true;
        // Public tab
        int y = 2 + SEARCH_H + 3;
        if (mouseY >= y && mouseY <= y + ITEM_H) return true;
        // Player list
        y += ITEM_H + 2;
        var players = new ArrayList<>(mc.player.connection.getOnlinePlayers());
        String selfName = mc.player.getName().getString();
        String filter = searchBox.getValue().toLowerCase().trim();
        int scrollY = y - scrollOffset;
        for (var info : players) {
            String name = info.getProfile().getName();
            if (name.equals(selfName)) continue;
            if (ChatBubbleConfig.isSidebarHidden(name)) continue;
            if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
            if (mouseY >= scrollY && mouseY <= scrollY + ITEM_H) return true;
            scrollY += ITEM_H + 2;
        }
        return false;
    }

    // ---- Rendering (called from ChatBubbleScreen) ----

    public static int render(GuiGraphics g, Font font, int mouseX, int mouseY,
                              ChatBubbleTheme.Colors c, int panelW, int msgBottom,
                              String whisperPartner, ResourceLocation publicIcon,
                              ResourceLocation noOnlineIcon, ResourceLocation privateTipIcon,
                              EditBox searchBox, int scrollOffset, int prevMaxScroll, float alpha) {
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_BG), 0, 0, WIDTH, 999, alpha);
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.DIVIDER), WIDTH - 1, 0, 1, 999, alpha);

        Minecraft mc = Minecraft.getInstance();
        int y = 2;

        // Search box
        int sbx = 2;
        int sby = 2;
        int sbw = WIDTH - 5;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.INPUT_BG), sbx - 1, sby, sbw + 1, SEARCH_H, alpha);
        boolean hoverSearch = mouseX >= sbx - 1 && mouseX <= sbx + sbw
            && mouseY >= sby && mouseY <= sby + SEARCH_H;
        if (hoverSearch || searchBox.isFocused())
            g.renderOutline(sbx - 1, sby, sbw + 1, SEARCH_H, c.textMuted());
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.drawString(font, Component.translatable("e33chat.sidebar.search"),
                sbx, sby + 3, c.textMuted(), false);
        }
        y = sby + SEARCH_H + 3;

        // Public tab
        boolean isPublic = whisperPartner == null;
        boolean hoverTab = mouseX >= 0 && mouseX <= WIDTH && mouseY >= y && mouseY <= y + ITEM_H;
        if (isPublic)
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_SELECTED), 0, y, WIDTH, ITEM_H, alpha);
        else if (hoverTab)
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_HOVER), 0, y, WIDTH, ITEM_H, alpha);
        drawIcon(g, publicIcon, 2, y + 1, ICON_S, alpha);
        int nameX = 2 + ICON_S + 3;
        String publicLabel = Component.translatable("e33chat.sidebar.public").getString();
        g.drawString(font, Component.literal(publicLabel), nameX, y + 1, c.textPrimary(), false);
        ChatMessageStore.ChatMessage latestPub = ChatMessageStore.getLatestPublicMessage();
        if (latestPub != null) {
            int previewMaxW = WIDTH - nameX - 4;
            String preview = ChatMessageStore.singleLine(latestPub.content().getString());
            String previewDisplay = font.plainSubstrByWidth(preview, previewMaxW - font.width("..."));
            if (!previewDisplay.equals(preview)) previewDisplay += "...";
            g.drawString(font, Component.literal(previewDisplay),
                nameX, y + 1 + font.lineHeight, c.textMuted(), false);
        }
        y += ITEM_H + 2;

        int newMaxScroll = prevMaxScroll;
        if (mc.player != null && mc.player.connection != null) {
            var players = new ArrayList<>(mc.player.connection.getOnlinePlayers());
            String selfName = mc.player.getName().getString();
            String filter = searchBox.getValue().toLowerCase().trim();

            int startY = y;
            int visibleBottom = msgBottom > 0 ? msgBottom : 300;
            int totalH = 0;
            for (var info : players) {
                String name = info.getProfile().getName();
                if (name.equals(selfName)) continue;
                if (ChatBubbleConfig.isSidebarHidden(name)) continue;
                if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;
                totalH += ITEM_H + 2;
            }

            if (totalH == 0) {
                drawIcon(g, noOnlineIcon, (WIDTH - 32) / 2, startY + 8, 32, alpha);
                String noPlayers = Component.translatable("e33chat.sidebar.no_players").getString();
                int textW = font.width(noPlayers);
                g.drawString(font, Component.literal(noPlayers),
                    (WIDTH - textW) / 2, startY + 8 + 32 + 4, c.textMuted(), false);
            } else {
                newMaxScroll = Math.max(0, totalH - (visibleBottom - startY));
                // Clamp so a shrinking player list can't leave the view scrolled
                // into empty space until the next scroll input (parity with NeoForge)
                int clampedOffset = Math.min(scrollOffset, newMaxScroll);

                g.enableScissor(0, startY, WIDTH, visibleBottom);
                int scrollY = startY - clampedOffset;
                for (var info : players) {
                    String name = info.getProfile().getName();
                    if (name.equals(selfName)) continue;
                    if (ChatBubbleConfig.isSidebarHidden(name)) continue;
                    if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;

                    if (scrollY + ITEM_H > startY && scrollY < visibleBottom) {
                        boolean sel = name.equals(whisperPartner);
                        boolean hoverRow = mouseX >= 0 && mouseX <= WIDTH
                            && mouseY >= scrollY && mouseY <= scrollY + ITEM_H;
                        if (sel)
                            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_SELECTED), 0, scrollY, WIDTH, ITEM_H, alpha);
                        else if (hoverRow)
                            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, UiTextureManager.rl(UiElement.SIDEBAR_HOVER), 0, scrollY, WIDTH, ITEM_H, alpha);

                        ResourceLocation skin = SkinResolver.getSkin(info.getProfile().getId(), name);
                        drawPlayerHead(g, skin, 4, scrollY + 3, 16, 18, alpha);

                        int tipW = ChatMessageStore.hasUnreadWhisper(name) ? 16 : 0;
                        int maxNameW = WIDTH - nameX - 4 - tipW - 2;
                        String displayName = font.plainSubstrByWidth(name, maxNameW - font.width("..."));
                        if (!displayName.equals(name)) displayName += "...";
                        g.drawString(font, Component.literal(displayName),
                            nameX, scrollY + 1, c.textPrimary(), false);

                        ChatMessageStore.ChatMessage latest = ChatMessageStore.getLatestWhisperWith(name);
                        if (latest != null) {
                            String preview = ChatMessageStore.singleLine(latest.content().getString());
                            String previewDisplay = font.plainSubstrByWidth(preview, maxNameW - font.width("..."));
                            if (!previewDisplay.equals(preview)) previewDisplay += "...";
                            g.drawString(font, Component.literal(previewDisplay),
                                nameX, scrollY + 1 + font.lineHeight, c.textMuted(), false);
                        }

                        if (ChatMessageStore.hasUnreadWhisper(name)) {
                            int tipX = WIDTH - 16 - 2;
                            double wave = Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 3;
                            int tipY = scrollY + 3 + (int) wave;
                            drawIcon(g, privateTipIcon, tipX, tipY, 16, alpha);
                        }
                    }
                    scrollY += ITEM_H + 2;
                }
                g.disableScissor();
            }
        }
        return newMaxScroll;
    }

    // ---- Helpers ----

    private static void drawPlayerHead(GuiGraphics g, ResourceLocation skin, int x, int y,
                                       int baseSize, int hatSize, float alpha) {
        if (alpha <= 0.003f) return;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, skin, x, y, baseSize, baseSize,
            8.0F, 8.0F, 8, 8, 64, 64, alpha);
        int hatOff = (hatSize - baseSize) / 2;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, skin, x - hatOff, y - hatOff, hatSize, hatSize,
            40.0F, 8.0F, 8, 8, 64, 64, alpha);
    }

    private static void drawIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size, float alpha) {
        if (alpha <= 0.003f) return;
        if (size < 16) {
            // 同 ChatBars.drawIcon：采样内容区 14x14（偏移1,1）完整绘制，避免切掉图标右/下缘
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size,
                1f, 1f, 14, 14, 16, 16, alpha);
        } else {
            com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(g, tex, x, y, size, size,
                0f, 0f, size, size, size, size, alpha);
        }
    }
}
