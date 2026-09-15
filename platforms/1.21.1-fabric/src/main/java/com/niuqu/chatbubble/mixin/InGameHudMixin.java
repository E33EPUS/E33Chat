package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.render.HudVisibility;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla HUD while a translucent E33Chat screen is open (2.4.11).
 *
 * Previously the screens set {@code options.hudHidden}, which is the F1 flag —
 * {@code GameRenderer} also gates first-person hands/held items on it, so
 * opening the chat panel made the hand disappear. Cancelling the HUD render
 * here skips only the HUD layer.
 */
@Mixin(InGameHud.class)
public class InGameHudMixin {

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void e33chat$hideHudForTranslucentScreens(DrawContext context, RenderTickCounter tickCounter,
                                                      CallbackInfo ci) {
        if (HudVisibility.shouldHideHud()) ci.cancel();
    }
}
