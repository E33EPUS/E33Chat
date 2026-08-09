package com.niuqu.chatbubble.mixin;
//#if MC >= 11900
import com.niuqu.chatbubble.ChatBubbleScreen;
import net.minecraft.client.MinecraftClient;
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.util.math.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
@Mixin(value = ChatInputSuggestor.class, priority = 500)
public class ChatInputSuggestorMixin {
    //#if MC >= 26000
    //$$ @Inject(method = "extractRenderState", at = @At("HEAD"), cancellable = true)
    //$$ private void onRenderMessages(DrawContext context, int mouseX, int mouseY, CallbackInfo ci) {
    //#else
    @Inject(method = "renderMessages", at = @At("HEAD"), cancellable = true)
    //#if MC >= 12000
    private void onRenderMessages(DrawContext context, CallbackInfo ci) {
    //#else
    //$$ private void onRenderMessages(MatrixStack context, CallbackInfo ci) {
    //#endif
    //#endif
        if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
            ci.cancel();
        }
    }
    //#if MC >= 26000
    //$$ @Inject(method = "showSuggestions(Z)V", at = @At("TAIL"))
    //#else
    @Inject(method = "show(Z)V", at = @At("TAIL"))
    //#endif
    private void afterShow(CallbackInfo ci) {
        if (!(MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen)) return;
        ChatInputSuggestor.SuggestionWindow window = ((ChatInputSuggestorAccessor) this).getWindow();
        if (window == null) return;
        Rect2i area = ((SuggestionWindowAccessor) window).getArea();
        if (area == null) return;
        int newY = ChatBubbleScreen.getInputY() - area.getHeight() - 4;
        if (area.getY() != newY) area.setY(newY);
        if (area.getX() < ChatBubbleScreen.getInputX()) area.setX(ChatBubbleScreen.getInputX());
    }
}
//#else
//$$ public class ChatInputSuggestorMixin {
//$$ }
//#endif
