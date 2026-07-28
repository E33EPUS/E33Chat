package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatBubbleTheme;
import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.EditBox;
import net.minecraft.client.renderer.texture.AbstractTexture;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;

import java.util.*;

public final class ChatSidebar {

    public static final int WIDTH = 90;
    private static final int ITEM_H = 22;
    private static final int ICON_S = 20;
    private static final int SEARCH_H = 14;
    private static final UUID NIL_UUID = new UUID(0, 0);

    private static final Map<UUID, ResourceLocation> skinCache = new HashMap<>();

    private ChatSidebar() {}

    public static boolean handleMouseClicked(double mouseX, double mouseY,
                                              String whisperPartner, Font font,
                                              int screenX, boolean visible,
                                              EditBox searchBox, int scrollOffset) {
        if (!visible) return false;
        int localX = (int)mouseX - screenX;
        if (localX < 0 || localX > WIDTH) return false;

        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return false;

        if (mouseY >= 2 && mouseY <= 2 + SEARCH_H) return true;
        int y = 2 + SEARCH_H + 3;
        if (mouseY >= y && mouseY <= y + ITEM_H) return true;
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

    public static int render(GuiGraphics g, Font font, int mouseX, int mouseY,
                              ChatBubbleTheme.Colors c, int panelW, int msgBottom,
                              String whisperPartner, ResourceLocation publicIcon,
                              ResourceLocation noOnlineIcon, ResourceLocation privateTipIcon,
                              EditBox searchBox, int scrollOffset, int maxScroll) {
        int screenX = 0;
        int mouseAdj = mouseX - screenX;

        g.fill(screenX, 0, screenX + WIDTH, 999, c.sidebarBg());
        g.fill(screenX + WIDTH - 1, 0, screenX + WIDTH, 999, c.sidebarDivider());

        Minecraft mc = Minecraft.getInstance();
        int y = 2;

        int sbx = 2;
        int sby = 2;
        int sbw = WIDTH - 5;
        g.fill(sbx - 1 + screenX, sby, sbx + sbw + screenX, sby + SEARCH_H, c.inputBg());
        boolean hoverSearch = mouseAdj >= sbx - 1 && mouseAdj <= sbx + sbw
            && mouseY >= sby && mouseY <= sby + SEARCH_H;
        if (hoverSearch || searchBox.isFocused())
            g.renderOutline(sbx - 1 + screenX, sby, sbw + 1, SEARCH_H, c.textMuted());
        if (searchBox.getValue().isEmpty() && !searchBox.isFocused()) {
            g.drawString(font, Component.translatable("e33chat.sidebar.search"),
                sbx + screenX, sby + 3, c.textMuted(), false);
        }
        y = sby + SEARCH_H + 3;

        boolean isPublic = whisperPartner == null;
        int itemBg = isPublic ? c.sidebarItemSelected()
            : (mouseAdj >= 0 && mouseAdj <= WIDTH && mouseY >= y && mouseY <= y + ITEM_H
                ? c.sidebarItemHover() : 0);
        if (itemBg != 0) g.fill(screenX, y, screenX + WIDTH, y + ITEM_H, itemBg);
        drawIcon(g, publicIcon, 2 + screenX, y + 1, ICON_S);
        int nameX = 2 + ICON_S + 3;
        String publicLabel = Component.translatable("e33chat.sidebar.public").getString();
        g.drawString(font, Component.literal(publicLabel), nameX + screenX, y + 1, c.textPrimary(), false);
        ChatMessageStore.ChatMessage latestPub = ChatMessageStore.getLatestPublicMessage();
        if (latestPub != null) {
            int previewMaxW = WIDTH - nameX - 4;
            String preview = ChatMessageStore.singleLine(latestPub.content().getString());
            String previewDisplay = font.plainSubstrByWidth(preview, previewMaxW - font.width("..."));
            if (!previewDisplay.equals(preview)) previewDisplay += "...";
            g.drawString(font, Component.literal(previewDisplay),
                nameX + screenX, y + 1 + font.lineHeight, c.textMuted(), false);
        }
        y += ITEM_H + 2;

        int newMaxScroll = maxScroll;
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
                drawIcon(g, noOnlineIcon, (WIDTH - 32) / 2 + screenX, startY + 8, 32);
                String noPlayers = Component.translatable("e33chat.sidebar.no_players").getString();
                int textW = font.width(noPlayers);
                g.drawString(font, Component.literal(noPlayers),
                    (WIDTH - textW) / 2 + screenX, startY + 8 + 32 + 4, c.textMuted(), false);
            } else {
                newMaxScroll = Math.max(0, totalH - (visibleBottom - startY));
                int clampedOffset = Math.min(scrollOffset, newMaxScroll);

                g.enableScissor(screenX, startY, screenX + WIDTH, visibleBottom);
                int scrollY = startY - clampedOffset;
                for (var info : players) {
                    String name = info.getProfile().getName();
                    if (name.equals(selfName)) continue;
                    if (ChatBubbleConfig.isSidebarHidden(name)) continue;
                    if (!filter.isEmpty() && !name.toLowerCase().contains(filter)) continue;

                    if (scrollY + ITEM_H > startY && scrollY < visibleBottom) {
                        boolean sel = name.equals(whisperPartner);
                        int sbg = sel ? c.sidebarItemSelected()
                            : (mouseAdj >= 0 && mouseAdj <= WIDTH
                                && mouseY >= scrollY && mouseY <= scrollY + ITEM_H
                                ? c.sidebarItemHover() : 0);
                        if (sbg != 0) g.fill(screenX, scrollY, screenX + WIDTH, scrollY + ITEM_H, sbg);

                        ResourceLocation skin = getSkin(info.getProfile().getId(), name);
                        drawPlayerHead(g, skin, 4 + screenX, scrollY + 3, 16, 18);

                        int tipW = ChatMessageStore.hasUnreadWhisper(name) ? 16 : 0;
                        int maxNameW = WIDTH - nameX - 4 - tipW - 2;
                        String displayName = font.plainSubstrByWidth(name, maxNameW - font.width("..."));
                        if (!displayName.equals(name)) displayName += "...";
                        g.drawString(font, Component.literal(displayName),
                            nameX + screenX, scrollY + 1, c.textPrimary(), false);

                        ChatMessageStore.ChatMessage latest = ChatMessageStore.getLatestWhisperWith(name);
                        if (latest != null) {
                            String preview = ChatMessageStore.singleLine(latest.content().getString());
                            String previewDisplay = font.plainSubstrByWidth(preview, maxNameW - font.width("..."));
                            if (!previewDisplay.equals(preview)) previewDisplay += "...";
                            g.drawString(font, Component.literal(previewDisplay),
                                nameX + screenX, scrollY + 1 + font.lineHeight, c.textMuted(), false);
                        }

                        if (ChatMessageStore.hasUnreadWhisper(name)) {
                            int tipX = WIDTH - 16 - 2 + screenX;
                            double wave = Math.abs(Math.sin(System.currentTimeMillis() / 300.0)) * 3;
                            int tipY = scrollY + 3 + (int) wave;
                            drawIcon(g, privateTipIcon, tipX, tipY, 16);
                        }
                    }
                    scrollY += ITEM_H + 2;
                }
                g.disableScissor();
            }
        }
        return newMaxScroll;
    }

    private static ResourceLocation getSkin(UUID uuid, String name) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.getConnection() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            var info = mc.getConnection().getPlayerInfo(uuid);
            if (info != null) return info.getSkin().texture();
        }
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            ResourceLocation cached = skinCache.get(uuid);
            if (cached != null) return cached;
            var skin = mc.getSkinManager().getInsecureSkin(
                new com.mojang.authlib.GameProfile(uuid, name != null ? name : ""));
            if (skin != null) {
                ResourceLocation tex = skin.texture();
                skinCache.put(uuid, tex);
                return tex;
            }
        }
        return DefaultPlayerSkin.get(uuid != null ? uuid : NIL_UUID).texture();
    }

    private static void drawPlayerHead(GuiGraphics g, ResourceLocation skin, int x, int y,
                                         int baseSize, int hatSize) {
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(skin, x, y, baseSize, baseSize, 8.0F, 8.0F, 8, 8, 64, 64);
        int hatOff = (hatSize - baseSize) / 2;
        g.blit(skin, x - hatOff, y - hatOff, hatSize, hatSize, 40.0F, 8.0F, 8, 8, 64, 64);
        com.mojang.blaze3d.systems.RenderSystem.disableBlend();
    }

    private static void drawIcon(GuiGraphics g, ResourceLocation tex, int x, int y, int size) {
        var tm = Minecraft.getInstance().getTextureManager();
        AbstractTexture abstractTex;
        try {
            abstractTex = tm.getTexture(tex);
        } catch (Exception e) {
            abstractTex = tm.getTexture(tex);
        }
        com.mojang.blaze3d.systems.RenderSystem.setShaderTexture(0, abstractTex.getId());
        com.mojang.blaze3d.systems.RenderSystem.setShader(
            net.minecraft.client.renderer.GameRenderer::getPositionTexShader);
        com.mojang.blaze3d.systems.RenderSystem.enableBlend();
        g.blit(tex, x, y, 0, 0, size, size, size, size);
    }
}
