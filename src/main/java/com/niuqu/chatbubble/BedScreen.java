package com.niuqu.chatbubble;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.widget.ButtonWidget;
import net.minecraft.network.packet.c2s.play.ClientCommandC2SPacket;
import net.minecraft.text.Text;

public class BedScreen extends Screen {

    private static Screen screenBeforeSleep;

    public BedScreen() {
        super(Text.translatable("multiplayer.stopSleeping"));
    }

    public static void setScreenBeforeSleep(Screen screen) {
        screenBeforeSleep = screen;
    }

    @Override
    protected void init() {
        addDrawableChild(ButtonWidget.builder(Text.translatable("multiplayer.stopSleeping"), b -> sendWakeUp())
            .dimensions(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
    }

    @Override
    public void tick() {
        if (client == null || client.player == null || !client.player.isSleeping()) {
            client.setScreen(null);
            if (screenBeforeSleep instanceof ChatBubbleScreen) {
                client.setScreen(screenBeforeSleep);
            }
            screenBeforeSleep = null;
        }
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
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            sendWakeUp();
            return true;
        }
        //#if MC >= 12109
        if (client.options.chatKey.matchesKey(key)) {
        //#else
        //$$ if (client.options.chatKey.matchesKey(keyCode, scanCode)) {
        //#endif
            client.setScreen(new ChatBubbleScreen(""));
            return true;
        }
        //#if MC >= 12109
        return super.keyPressed(key);
        //#else
        //$$ return super.keyPressed(keyCode, scanCode, modifiers);
        //#endif
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    private void sendWakeUp() {
        if (client != null && client.player != null) {
            client.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.STOP_SLEEPING));
        }
    }
}