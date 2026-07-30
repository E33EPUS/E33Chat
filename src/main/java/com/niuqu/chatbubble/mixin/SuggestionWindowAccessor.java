package com.niuqu.chatbubble.mixin;

import net.minecraft.client.util.math.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(targets = "net.minecraft.client.gui.screen.ChatInputSuggestor$SuggestionWindow")
public interface SuggestionWindowAccessor {
    @Accessor("area")
    Rect2i getArea();
}
