package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.util.math.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatInputSuggestor.class, priority = 500)
public class ChatInputSuggestorMixin {

    @Inject(method = "renderMessages", at = @At("HEAD"), cancellable = true)
    private void onRenderMessages(DrawContext context, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
            ci.cancel();
        }
    }

    @Inject(method = "show(Z)V", at = @At("TAIL"))
    private void afterShow(CallbackInfo ci) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen)) return;
        ChatInputSuggestor.SuggestionWindow window = ((ChatInputSuggestorAccessor) this).getWindow();
        if (window == null) return;
        Rect2i area = ((SuggestionWindowAccessor) window).getArea();
        if (area == null) return;
        int newY = ChatBubbleScreen.getInputY() - area.getHeight() - 4;
        if (area.getY() != newY) area.setY(newY);
        if (area.getX() < ChatBubbleScreen.getInputX()) area.setX(ChatBubbleScreen.getInputX());
        // TEMP DIAG (2.4.12, issue #8): the list does not answer mouse clicks for
        // some users. This is the rect the window renders with AND hit-tests with
        // (mouseClicked uses area.contains), so pairing it with the click point
        // logged in ChatBubbleScreen settles whether they disagree. Remove once fixed.
        if (com.niuqu.chatbubble.ChatBubbleClientSetup.config().debugLog()) {
            String diag = "[e33chat] SuggClick list | area=(" + area.getX() + "," + area.getY()
                + " " + area.getWidth() + "x" + area.getHeight() + ")"
                + " | inputX=" + ChatBubbleScreen.getInputX()
                + " inputY=" + ChatBubbleScreen.getInputY();
            com.niuqu.chatbubble.store.ChatMessageStore.debugLog(() -> diag);
        }
    }
}
