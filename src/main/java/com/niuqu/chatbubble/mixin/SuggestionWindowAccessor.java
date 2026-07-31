package com.niuqu.chatbubble.mixin;

import net.minecraft.client.gui.screen.ChatInputSuggestor;
import net.minecraft.client.util.math.Rect2i;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = ChatInputSuggestor.SuggestionWindow.class)
public interface SuggestionWindowAccessor {
    @Accessor("area")
    Rect2i getArea();
}
