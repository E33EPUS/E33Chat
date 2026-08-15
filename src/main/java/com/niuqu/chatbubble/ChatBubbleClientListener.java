package com.niuqu.chatbubble;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.render.ChatBubbleScreen;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.render.ChatBubbleHudOverlay;
import com.niuqu.chatbubble.ui.BedScreen;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraft.client.gui.screens.InBedChatScreen;
import net.minecraft.client.gui.screens.Screen;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.InputEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;
import net.neoforged.neoforge.event.entity.player.CanPlayerSleepEvent;
import net.neoforged.neoforge.event.entity.player.PlayerWakeUpEvent;

public class ChatBubbleClientListener {

    private static Screen screenBeforeSleep;

    @SubscribeEvent
    public void onScreenOpen(ScreenEvent.Opening event) {
        // Minecraft.<init> fires ScreenEvent.Opening before NeoForge/Forge have
        // loaded configs; ConfigValue.get() then throws IllegalStateException
        // ("Cannot get config value before config is loaded") and crashes the
        // game at startup. Until configs are loaded, leave screens untouched.
        try {
            if (!ChatBubbleConfig.ENABLED.get()) return;
        } catch (IllegalStateException e) {
            return;
        }
        if (event.getScreen() instanceof InBedChatScreen) {
            // Vanilla force-opens InBedChatScreen every tick while sleeping; swap in
            // our minimal bed screen so the Leave Bed button survives the chat rework
            event.setCanceled(true);
            Minecraft.getInstance().setScreen(new BedScreen());
        } else if (event.getScreen() instanceof ChatScreen chatScreen
                && !(chatScreen instanceof ChatBubbleScreen)) {
            event.setCanceled(true);
            String initial = getChatInitialText(chatScreen);
            Minecraft.getInstance().setScreen(new ChatBubbleScreen(initial));
        }
    }

    private static String getChatInitialText(ChatScreen chatScreen) {
        try {
            var f = ChatScreen.class.getDeclaredField("initial");
            f.setAccessible(true);
            String val = (String) f.get(chatScreen);
            return val != null ? val : "";
        } catch (Exception e) {
            // ignore
        }
        for (var f : ChatScreen.class.getDeclaredFields()) {
            if (f.getType() == String.class) {
                f.setAccessible(true);
                try {
                    String val = (String) f.get(chatScreen);
                    if (val != null && !val.isEmpty()) return val;
                } catch (Exception ignored) {}
            }
        }
        return "";
    }

    @SubscribeEvent
    public void onSleepStart(CanPlayerSleepEvent event) {
        screenBeforeSleep = Minecraft.getInstance().screen;
    }

    @SubscribeEvent
    public void onWakeUp(PlayerWakeUpEvent event) {
        Minecraft mc = Minecraft.getInstance();
        mc.execute(() -> {
            if (screenBeforeSleep == null) {
                if (mc.screen instanceof ChatBubbleScreen) {
                    mc.setScreen(null);
                }
            }
            screenBeforeSleep = null;
        });
    }

    @SubscribeEvent
    public void onRenderGui(RenderGuiEvent.Post event) {
        if (!ChatBubbleConfig.ENABLED.get()) return;
        ChatBubbleHudOverlay.render(event.getGuiGraphics());
    }

    @SubscribeEvent
    public void onScreenRender(ScreenEvent.Render.Post event) {
        if (!ChatBubbleConfig.ENABLED.get()) return;
        ChatBubbleHudOverlay.renderBannerForScreen(event.getGuiGraphics());
    }

    @SubscribeEvent
    public void onClientTick(ClientTickEvent.Post event) {
        com.niuqu.chatbubble.image.ImageLoader.tick();
        Minecraft mc = Minecraft.getInstance();
        String key;
        if (mc.level == null || mc.player == null) {
            key = null;
        } else if (mc.getSingleplayerServer() != null) {
            key = "SP:" + mc.getSingleplayerServer().getWorldData().getLevelName();
        } else if (mc.getCurrentServer() != null) {
            key = "MP:" + mc.getCurrentServer().name;
        } else {
            key = "world";
        }
        ChatMessageStore.setCurrentWorld(key);
        ChatMessageStore.maybeAutoSave();
    }

    @SubscribeEvent
    public void onMouseClick(InputEvent.MouseButton.Pre event) {
        if (!ChatBubbleConfig.ENABLED.get()) return;
        if (event.getButton() != 0) return;
        Minecraft mc = Minecraft.getInstance();
        if (mc.screen != null) return;
        double mx = mc.mouseHandler.xpos() * (double) mc.getWindow().getGuiScaledWidth() / (double) mc.getWindow().getScreenWidth();
        double my = mc.mouseHandler.ypos() * (double) mc.getWindow().getGuiScaledHeight() / (double) mc.getWindow().getScreenHeight();
        if (ChatBubbleHudOverlay.isMouseOverIcon(mx, my)) {
            event.setCanceled(true);
            mc.setScreen(new ChatBubbleScreen(""));
        }
    }
}
