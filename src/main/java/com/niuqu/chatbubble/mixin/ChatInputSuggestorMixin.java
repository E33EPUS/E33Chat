package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleScreen;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatInputSuggestor.class, priority = 500)
public class ChatInputSuggestorMixin {

    @Inject(method = "renderMessages", at = @At("HEAD"), cancellable = true)
    private void onRenderMessages(DrawContext context, CallbackInfo ci) {
        if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
            ci.cancel();
        }
    }

    @ModifyArg(method = "show(Z)V", at = @At(value = "INVOKE",
        target = "Lnet/minecraft/client/gui/screen/ChatInputSuggestor$SuggestionWindow;<init>(Lnet/minecraft/client/gui/screen/ChatInputSuggestor;IIILjava/util/List;Z)V"),
        index = 2, require = 0)
    private int fixSuggestionY(int y) {
        if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
            return ChatBubbleScreen.getInputY() + 3;
        }
        return y;
    }
}
