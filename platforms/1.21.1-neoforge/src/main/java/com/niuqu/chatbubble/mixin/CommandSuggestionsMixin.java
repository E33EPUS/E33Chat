package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.render.ChatBubbleScreen;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.CommandSuggestions;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = CommandSuggestions.class, priority = 500)
public class CommandSuggestionsMixin {

    // TEMP DIAG (2.4.12, issue #8): the suggestion list does not answer mouse
    // clicks for some users. These two statics carry the constructor arguments
    // that the earlier @ModifyArg handlers produced, so the last one (width) can
    // log the exact rect the list is built with; ChatBubbleScreen logs the click
    // point. One reproduction then decides between "the drawn list and the hit
    // rect disagree" and "the click never reaches the list". Remove once fixed.
    private static int chatBubble$argX;
    private static int chatBubble$argY;

    @Inject(method = "renderUsage", at = @At("HEAD"), cancellable = true, require = 0)
    private void onRenderUsage(GuiGraphics g, CallbackInfo ci) {
        if (Minecraft.getInstance().screen instanceof ChatBubbleScreen) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "showSuggestions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/CommandSuggestions$SuggestionsList;<init>(Lnet/minecraft/client/gui/components/CommandSuggestions;IIILjava/util/List;Z)V"),
        index = 1, require = 0)
    private int fixSuggestionsX(int x) {
        if (Minecraft.getInstance().screen instanceof ChatBubbleScreen) {
            // vanilla x 已按光标 token 锚定并 clamp 到输入框右缘；只兜底左缘，
            // 不再钉死到输入框左端（否则补全列表永远不跟光标）
            chatBubble$argX = Math.max(x, ChatBubbleScreen.getInputX());
            return chatBubble$argX;
        }
        return x;
    }

    @ModifyArg(method = "showSuggestions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/CommandSuggestions$SuggestionsList;<init>(Lnet/minecraft/client/gui/components/CommandSuggestions;IIILjava/util/List;Z)V"),
        index = 2, require = 0)
    private int fixSuggestionsY(int y) {
        if (Minecraft.getInstance().screen instanceof ChatBubbleScreen) {
            chatBubble$argY = ChatBubbleScreen.getInputY() + 3;
            return chatBubble$argY;
        }
        return y;
    }

    /** Diagnostic only: sees the width, so it can log the finished rect. */
    @ModifyArg(method = "showSuggestions", at = @At(value = "INVOKE",
            target = "Lnet/minecraft/client/gui/components/CommandSuggestions$SuggestionsList;<init>(Lnet/minecraft/client/gui/components/CommandSuggestions;IIILjava/util/List;Z)V"),
        index = 3, require = 0)
    private int diagSuggestionsWidth(int width) {
        if (Minecraft.getInstance().screen instanceof ChatBubbleScreen
                && com.niuqu.chatbubble.config.ChatBubbleConfig.DEBUG_LOG.get()) {
            // Mirrors SuggestionsList's arithmetic: an unbordered input shifts x by
            // 1 and widens by 1, and anchorToBottom lifts the box above the input.
            String diag = "[e33chat] SuggClick list | argX=" + chatBubble$argX
                + " argY=" + chatBubble$argY + " width=" + width
                + " -> rectX=" + (chatBubble$argX - 1) + " rectW=" + (width + 1)
                + " | inputX=" + ChatBubbleScreen.getInputX()
                + " inputY=" + ChatBubbleScreen.getInputY();
            com.niuqu.chatbubble.store.ChatMessageStore.debugLog(() -> diag);
        }
        return width;
    }
}
