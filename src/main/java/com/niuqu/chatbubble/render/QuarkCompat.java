package com.niuqu.chatbubble.render;

import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.ChatBubbleTheme;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.resources.language.I18n;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.util.*;

public final class QuarkCompat {
    private static final Logger LOG = LogUtils.getLogger();
    private static final int EMOTE_W = 25;
    private static final int EMOTES_PER_ROW = 3;
    private static final int TOGGLE_H = 20;

    private static boolean checked;
    private static boolean loaded;
    private static boolean initFailed;
    private static List<EmoteBtn> buttons;
    private static boolean emotesVisible;
    private static int toggleX, toggleY, toggleW, toggleH;
    private static int localTier;

    private QuarkCompat() {}

    public static boolean isLoaded() {
        if (!checked) {
            checked = true;
            try {
                Class.forName("org.violetmoon.quark.content.tweaks.module.EmotesModule");
                loaded = true;
                LOG.info("[e33chat] QuarkCompat: Quark detected");
            } catch (ClassNotFoundException e) {
                loaded = false;
            }
        }
        return loaded;
    }

    public static void init(int screenW, int screenH) {
        if (!isLoaded() || initFailed) return;
        LOG.info("[e33chat] QuarkCompat: init start");
        try {
            buttons = new ArrayList<>();
            try {
                Class<?> rewardCls = Class.forName("org.violetmoon.quark.base.handler.ContributorRewardHandler");
                localTier = rewardCls.getField("localPatronTier").getInt(null);
            } catch (Exception e) {
                LOG.warn("[e33chat] QuarkCompat: failed to read patron tier", e);
            }

            Map<String, ?> emoteMap;
            try {
                Class<?> handlerCls = Class.forName("org.violetmoon.quark.content.tweaks.client.emote.EmoteHandler");
                emoteMap = (Map<String, ?>) handlerCls.getField("emoteMap").get(null);
            } catch (Exception e) {
                LOG.warn("[e33chat] QuarkCompat: failed to read emoteMap", e);
                initFailed = true;
                return;
            }
            if (emoteMap == null || emoteMap.isEmpty()) {
                LOG.info("[e33chat] QuarkCompat: emoteMap empty, no buttons");
                return;
            }

            Minecraft mc = Minecraft.getInstance();
            boolean expandDown = mc.options.showSubtitles().get();

            List<Object> descriptors = new ArrayList<>();
            for (Object desc : emoteMap.values()) {
                try {
                    int tier = (int) desc.getClass().getMethod("getTier").invoke(desc);
                    if (tier <= localTier) descriptors.add(desc);
                } catch (Exception ignored) {}
            }

            int rows = (descriptors.size() + EMOTES_PER_ROW - 1) / EMOTES_PER_ROW;
            int baseX = screenW - 2 - EMOTE_W * (EMOTES_PER_ROW + 1);
            int baseY = expandDown ? 2 : screenH - 40;

            int row = 0, col = 0;
            for (Object desc : descriptors) {
                int rowSize = Math.min(descriptors.size() - row * EMOTES_PER_ROW, EMOTES_PER_ROW);
                int xOff = ((col + 1) * 2 + EMOTES_PER_ROW - rowSize) * EMOTE_W / 2 + 1;
                int x = baseX + xOff + col * EMOTE_W;
                int y = baseY + (EMOTE_W * (rows - row - 1)) * (expandDown ? 1 : -1);

                try {
                    String regName = (String) desc.getClass().getMethod("getRegistryName").invoke(desc);
                    String name = I18n.get((String) desc.getClass().getMethod("getTranslationKey").invoke(desc));
                    ResourceLocation tex = (ResourceLocation) desc.getClass().getField("texture").get(desc);
                    buttons.add(new EmoteBtn(x, y, EMOTE_W - 1, EMOTE_W - 1, regName, name, tex));
                } catch (Exception ignored) {}

                if (++col == EMOTES_PER_ROW) { row++; col = 0; }
            }

            toggleX = baseX + EMOTE_W;
            toggleY = baseY;
            toggleW = EMOTE_W * EMOTES_PER_ROW;
            toggleH = TOGGLE_H;
            LOG.info("[e33chat] QuarkCompat: init done, {} buttons", buttons.size());
        } catch (Exception e) {
            LOG.error("[e33chat] QuarkCompat: init failed, disabling", e);
            initFailed = true;
            buttons = null;
        }
    }

    public static void render(GuiGraphics g, Font font, int mouseX, int mouseY,
                              ChatBubbleTheme.Colors c) {
        if (!isLoaded() || initFailed || buttons == null || buttons.isEmpty()) return;
        try {
            drawButton(g, font, toggleX, toggleY, toggleW, toggleH,
                Component.translatable("quark.gui.button.emotes").getString(),
                mouseX, mouseY, c);

            if (emotesVisible) {
                for (EmoteBtn btn : buttons) {
                    boolean hover = mouseX >= btn.x && mouseX <= btn.x + btn.w
                        && mouseY >= btn.y && mouseY <= btn.y + btn.h;
                    if (hover) g.fill(btn.x, btn.y, btn.x + btn.w, btn.y + btn.h, c.iconHover());

                    RenderSystem.enableBlend();
                    RenderSystem.defaultBlendFunc();
                    RenderSystem.setShaderTexture(0, btn.texture);
                    g.blit(btn.texture, btn.x + 2, btn.y + 2, 0, 0, btn.w - 4, btn.h - 4, btn.w - 4, btn.h - 4);
                }
            }
        } catch (Exception e) {
            LOG.error("[e33chat] QuarkCompat: render failed", e);
        }
    }

    private static void drawButton(GuiGraphics g, Font font, int x, int y, int w, int h,
                                   String text, int mouseX, int mouseY, ChatBubbleTheme.Colors c) {
        boolean hover = mouseX >= x && mouseX <= x + w && mouseY >= y && mouseY <= y + h;
        g.fill(x, y, x + w, y + h, hover ? c.sidebarItemHover() : c.sidebarBg());
        g.renderOutline(x, y, w, h, c.divider());
        int tw = font.width(text);
        g.drawString(font, Component.literal(text), x + (w - tw) / 2, y + (h - 8) / 2,
            c.textPrimary(), false);
    }

    public static boolean handleClick(double mouseX, double mouseY) {
        if (!isLoaded() || initFailed || buttons == null) return false;
        try {
            if (mouseX >= toggleX && mouseX <= toggleX + toggleW
                && mouseY >= toggleY && mouseY <= toggleY + toggleH) {
                emotesVisible = !emotesVisible;
                return true;
            }

            if (!emotesVisible) return false;

            for (EmoteBtn btn : buttons) {
                if (mouseX >= btn.x && mouseX <= btn.x + btn.w
                    && mouseY >= btn.y && mouseY <= btn.y + btn.h) {
                    sendEmote(btn.regName);
                    emotesVisible = false;
                    return true;
                }
            }
        } catch (Exception e) {
            LOG.error("[e33chat] QuarkCompat: handleClick failed", e);
        }
        return false;
    }

    private static void sendEmote(String regName) {
        try {
            Class<?> reqCls = Class.forName("org.violetmoon.quark.base.network.message.RequestEmoteMessage");
            Object msg = reqCls.getConstructor(String.class).newInstance(regName);
            try {
                Class<?> pd = Class.forName("net.neoforged.neoforge.network.PacketDistributor");
                pd.getMethod("sendToServer", Object.class).invoke(null, msg);
            } catch (ClassNotFoundException e1) {
                try {
                    Class<?> zn = Class.forName("org.violetmoon.zeta.network.ZetaNetwork");
                    Object net = zn.getMethod("getInstance").invoke(null);
                    net.getClass().getMethod("sendToServer", Object.class).invoke(net, msg);
                } catch (Exception e2) {
                    try {
                        Class<?> nw = Class.forName("org.violetmoon.quark.base.Quark");
                        Object net = nw.getField("ZETA").get(null);
                        Object ch = net.getClass().getMethod("getNetwork").invoke(net);
                        ch.getClass().getMethod("sendToServer", Object.class).invoke(ch, msg);
                    } catch (Exception ignored3) {}
                }
            }
        } catch (Exception e) {
            LOG.error("[e33chat] QuarkCompat: sendEmote failed", e);
        }
    }

    public static void reset() {
        buttons = null;
        emotesVisible = false;
    }

    private record EmoteBtn(int x, int y, int w, int h,
                            String regName, String displayName, ResourceLocation texture) {}
}
