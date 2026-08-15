package com.niuqu.chatbubble.ui;
import com.niuqu.chatbubble.render.ChatBubbleScreen;

import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket;
import org.lwjgl.glfw.GLFW;

public class BedScreen extends Screen {

    public BedScreen() {
        super(Component.translatable("multiplayer.stopSleeping"));
    }

    @Override
    protected void init() {
        addRenderableWidget(Button.builder(Component.translatable("multiplayer.stopSleeping"), b -> sendWakeUp())
            .bounds(width / 2 - 100, height - 40, 200, 20).build());
    }

    @Override
    public void tick() {
        if (minecraft.player == null || !minecraft.player.isSleeping()) {
            minecraft.setScreen(null);
        }
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        // Like vanilla InBedChatScreen: ESC requests wake-up but keeps the screen
        // until the server confirms (tick() closes it once no longer sleeping)
        if (keyCode == GLFW.GLFW_KEY_ESCAPE) {
            sendWakeUp();
            return true;
        }
        if (keyCode == minecraft.options.keyChat.getKey().getValue()) {
            minecraft.setScreen(new ChatBubbleScreen(""));
            return true;
        }
        return super.keyPressed(keyCode, scanCode, modifiers);
    }

    @Override
    public boolean isPauseScreen() {
        return false;
    }

    private void sendWakeUp() {
        if (minecraft.player != null) {
            minecraft.player.connection.send(new ServerboundPlayerCommandPacket(
                minecraft.player, ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
        }
    }
}
