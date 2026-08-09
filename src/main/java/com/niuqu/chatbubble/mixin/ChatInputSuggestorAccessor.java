package com.niuqu.chatbubble.mixin;
//#if MC >= 11900
import net.minecraft.client.gui.screen.ChatInputSuggestor;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;
@Mixin(ChatInputSuggestor.class)
public interface ChatInputSuggestorAccessor {
    //#if MC >= 26000
    //$$ @Accessor("suggestions")
    //#else
    @Accessor("window")
    //#endif
    ChatInputSuggestor.SuggestionWindow getWindow();
}
//#else
//$$ public interface ChatInputSuggestorAccessor {
//$$ }
//#endif
