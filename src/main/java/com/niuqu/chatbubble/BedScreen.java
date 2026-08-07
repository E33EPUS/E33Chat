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
        super(com.niuqu.chatbubble.Txt.translatable("multiplayer.stopSleeping"));
    }

    public static void setScreenBeforeSleep(Screen screen) {
        screenBeforeSleep = screen;
    }

    @Override
    protected void init() {
        GuiCompat.addDrawableChild(this, GuiCompat.button(com.niuqu.chatbubble.Txt.translatable("multiplayer.stopSleeping"), b -> sendWakeUp(),
            width / 2 - 100, height - 40, 200, 20));
    }

    //#if MC >= 12004
    @Override
    public void renderBackground(DrawContext g, int mouseX, int mouseY, float delta) {
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
            GuiCompat.setScreen(client, null);
            if (screenBeforeSleep instanceof ChatBubbleScreen) {
                GuiCompat.setScreen(client, screenBeforeSleep);
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
        if (GuiCompat.matchesChatKey(client, keyCode, scanCode)) {
        //#else
        //$$ if (GuiCompat.matchesChatKey(client, keyCode, scanCode)) {
        //#endif
            GuiCompat.setScreen(client, new ChatBubbleScreen(""));
            return true;
        }
        //#if MC >= 12109
        return super.keyPressed(key);
        //#else
        //$$ return super.keyPressed(keyCode, scanCode, modifiers);
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
            client.player.networkHandler.sendPacket(
                new ClientCommandC2SPacket(client.player, ClientCommandC2SPacket.Mode.STOP_SLEEPING));
        }
    }
}
