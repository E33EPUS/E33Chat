package com.niuqu.chatbubble.ui;

import com.niuqu.chatbubble.render.Appearance;
import com.niuqu.chatbubble.render.ChatBubbleTheme;
import com.niuqu.chatbubble.render.RoundRectRenderer;
import com.niuqu.chatbubble.render.SkinResolver;
import com.niuqu.chatbubble.texture.ColoredTextureRenderer;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.GameType;

import java.util.UUID;

/**
 * 玩家资料卡（2.4.10）：右键头像菜单"查看资料"打开。展示头像（face+hat）、
 * 名称、在线状态、UUID、延迟、游戏模式，带 私聊 / 复制UUID 快捷操作。
 * Esc 或点遮罩返回聊天界面。纯客户端（tab 列表信息），零服务器依赖。
 */
public class PlayerProfileScreen extends Screen {

    private static final int PANEL_W = 220;
    private static final int PANEL_H = 196;
    private static final int BTN_H = 16;

    private final Screen parent;
    private final String playerName;

    // Layout (computed in init/relocated on resize)
    private int panelX, panelY;
    private int btnWhisperX, btnCopyX, btnY, btnW;

    public PlayerProfileScreen(Screen parent, String playerName) {
        super(Component.translatable("e33chat.profile.title"));
        this.parent = parent;
        this.playerName = playerName;
    }

    @Override
    protected void init() {
        panelX = (width - PANEL_W) / 2;
        panelY = (height - PANEL_H) / 2;
        btnW = (PANEL_W - 24 - 8) / 2;
        btnWhisperX = panelX + 12;
        btnCopyX = btnWhisperX + btnW + 8;
        btnY = panelY + PANEL_H - 12 - BTN_H;
    }

    private PlayerInfo info() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return null;
        PlayerInfo exact = mc.player.connection.getPlayerInfo(playerName);
        if (exact != null) return exact;
        for (PlayerInfo p : mc.player.connection.getOnlinePlayers()) {
            if (p.getProfile().getName().equalsIgnoreCase(playerName)) return p;
        }
        return null;
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partialTick) {
        renderBackground(g);
        ChatBubbleTheme.Colors c = Appearance.snapshot();

        // Panel (SDF 圆角：阴影 + 底色，与气泡同画法)
        RoundRectRenderer.fill(g, panelX + 2, panelY + 3, panelX + PANEL_W + 2, panelY + PANEL_H + 3,
            8, 0x55000000);
        RoundRectRenderer.fill(g, panelX, panelY, panelX + PANEL_W, panelY + PANEL_H,
            8, 0xF21A1C20);

        PlayerInfo info = info();
        boolean online = info != null;
        boolean isSelf = Minecraft.getInstance().player != null
            && Minecraft.getInstance().player.getName().getString().equalsIgnoreCase(playerName);
        UUID uuid = online ? info.getProfile().getId() : null;

        // Hero: head (face + hat layer)
        int headS = 40;
        int headX = panelX + (PANEL_W - headS) / 2;
        int headY = panelY + 14;
        ResourceLocation skin = SkinResolver.getSkin(uuid, playerName);
        ColoredTextureRenderer.drawWithAlpha(g, skin,
            headX, headY, headS, headS, 8.0F, 8.0F, 8, 8, 64, 64, 1f);
        ColoredTextureRenderer.drawWithAlpha(g, skin,
            headX - 3, headY - 3, headS + 6, headS + 6, 40.0F, 8.0F, 8, 8, 64, 64, 1f);

        // Name + badge
        int nameW = font.width(playerName);
        String badge = Component.translatable(isSelf ? "e33chat.profile.self"
            : online ? "e33chat.profile.online" : "e33chat.profile.offline").getString();
        int badgeColor = isSelf ? 0xFF55FFFF : online ? 0xFF55FF55 : 0xFF888888;
        int totalW = nameW + 6 + font.width(badge);
        int nameX = panelX + (PANEL_W - totalW) / 2;
        int nameY = headY + headS + 8;
        g.drawString(font, playerName, nameX, nameY, c.textPrimary(), false);
        g.drawString(font, badge, nameX + nameW + 6, nameY, badgeColor, false);

        // Fields
        int fieldX = panelX + 16;
        int fieldY = nameY + 18;
        int lineH = font.lineHeight + 4;
        fieldY = drawField(g, Component.translatable("e33chat.profile.uuid").getString(),
            uuid != null ? uuid.toString() : "—", fieldX, fieldY, lineH, c);
        fieldY = drawField(g, Component.translatable("e33chat.profile.latency").getString(),
            online ? info.getLatency() + " ms" : "—", fieldX, fieldY, lineH, c);
        GameType gt = online ? info.getGameMode() : null;
        drawField(g, Component.translatable("e33chat.profile.gamemode").getString(),
            gt != null ? gt.getName() : "—", fieldX, fieldY, lineH, c);

        // Buttons
        boolean hoverW = over(mouseX, mouseY, btnWhisperX, btnY, btnW, BTN_H);
        boolean hoverC = over(mouseX, mouseY, btnCopyX, btnY, btnW, BTN_H);
        RoundRectRenderer.fill(g, btnWhisperX, btnY, btnWhisperX + btnW, btnY + BTN_H, 4,
            hoverW ? 0xFF3A5FCD : 0xFF2C4A9E);
        RoundRectRenderer.fill(g, btnCopyX, btnY, btnCopyX + btnW, btnY + BTN_H, 4,
            hoverC ? 0xFF4A4A52 : 0xFF36363E);
        String whisperLabel = Component.translatable("e33chat.context.whisper").getString();
        String copyLabel = Component.translatable("e33chat.profile.copy_uuid").getString();
        g.drawString(font, whisperLabel,
            btnWhisperX + (btnW - font.width(whisperLabel)) / 2, btnY + 4, 0xFFFFFFFF, false);
        g.drawString(font, copyLabel,
            btnCopyX + (btnW - font.width(copyLabel)) / 2, btnY + 4, 0xFFFFFFFF, false);
    }

    private int drawField(GuiGraphics g, String label, String value, int x, int y, int lineH,
                          ChatBubbleTheme.Colors c) {
        g.drawString(font, label, x, y, c.textSecondary(), false);
        String v = value;
        int maxW = PANEL_W - 32 - 60;
        if (font.width(v) > maxW) v = font.plainSubstrByWidth(v, maxW - font.width("…")) + "…";
        g.drawString(font, v, x + 60, y, c.textPrimary(), false);
        return y + lineH;
    }

    private static boolean over(double mx, double my, int x, int y, int w, int h) {
        return mx >= x && mx <= x + w && my >= y && my <= y + h;
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        if (button == 0) {
            if (over(mouseX, mouseY, btnWhisperX, btnY, btnW, BTN_H)) {
                Minecraft mc = Minecraft.getInstance();
                onClose();
                if (mc.player != null) mc.player.connection.sendCommand("msg " + playerName + " ");
                return true;
            }
            if (over(mouseX, mouseY, btnCopyX, btnY, btnW, BTN_H)) {
                PlayerInfo info = info();
                String text = info != null ? info.getProfile().getId().toString() : playerName;
                Minecraft.getInstance().keyboardHandler.setClipboard(text);
                return true;
            }
            // Click outside the panel closes (WeChat-style dismiss)
            if (!over(mouseX, mouseY, panelX, panelY, PANEL_W, PANEL_H)) {
                onClose();
                return true;
            }
        }
        return super.mouseClicked(mouseX, mouseY, button);
    }

    @Override
    public void onClose() {
        Minecraft.getInstance().setScreen(parent);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }
}
