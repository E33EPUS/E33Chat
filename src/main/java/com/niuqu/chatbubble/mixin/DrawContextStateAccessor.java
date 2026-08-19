package com.niuqu.chatbubble.mixin;

//#if MC >= 12106
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.render.state.GuiRenderState;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/** Package-private GuiRenderState field of DrawContext (1.21.11 render-state). */
@Mixin(DrawContext.class)
public interface DrawContextStateAccessor {
    @Accessor("state")
    GuiRenderState e33chat$state();
}
//#else
//$$ public interface DrawContextStateAccessor {
//$$ }
//#endif
