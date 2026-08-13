package com.niuqu.chatbubble;

//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
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
        //#if MC >= 11903
        addDrawableChild(ButtonWidget.builder(Text.translatable("multiplayer.stopSleeping"), b -> sendWakeUp())
            .dimensions(width / 2 - 100, height - 40, 200, 20).build());
        //#else
        //$$ addDrawableChild(new ButtonWidget(width / 2 - 100, height - 40, 200, 20, Text.translatable("multiplayer.stopSleeping"), b -> sendWakeUp()));
        //#endif
    }

    //#if MC >= 12004
    //#if MC >= 26000
    @Override
    public void extractBackground(GuiGraphicsExtractor g, int mouseX, int mouseY, float delta) {
    //#else
    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
    //#endif
    }
    //#else
    //#if MC >= 12000
    //$$ @Override
    //$$ public void renderBackground(DrawContext g) {
    //$$ }
    //#else
    //$$ @Override
    //$$ public void renderBackground(net.minecraft.client.util.math.MatrixStack g) {
    //$$ }
    //#endif
    //#endif

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
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
    //#endif
        if (keyCode == org.lwjgl.glfw.GLFW.GLFW_KEY_ESCAPE) {
            sendWakeUp();
            return true;
        }
        //#if MC >= 12109
        if (client.options.chatKey.matchesKey(new net.minecraft.client.input.KeyInput(keyCode, scanCode, 0))) {
        //#else
        if (client.options.chatKey.matchesKey(keyCode, scanCode)) {
        //#endif
            client.setScreen(new ChatBubbleScreen(""));
            return true;
        }
        //#if MC >= 12109
        return super.keyPressed(key);
        //#else
        return super.keyPressed(keyCode, scanCode, modifiers);
        //#endif
    }

    //#if MC >= 11700
    @Override
    public boolean shouldPause() {
        return false;
    }
    //#else
    //$$ @Override
    //$$ public boolean isPauseScreen() {
    //$$     return false;
    //$$ }
    //#endif

    private void sendWakeUp() {
        if (client != null && client.player != null) {
            //#if MC >= 26000
            //$$ client.player.connection.send(
            //$$     new net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket(
            //$$         client.player, net.minecraft.network.protocol.game.ServerboundPlayerCommandPacket.Action.STOP_SLEEPING));
            //#else
            client.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.STOP_SLEEPING));
            //#endif
        }
    }
}
