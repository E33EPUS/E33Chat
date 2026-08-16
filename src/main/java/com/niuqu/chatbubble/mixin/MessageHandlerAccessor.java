package com.niuqu.chatbubble.mixin;

//#if MC >= 11900
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import net.minecraft.client.network.message.MessageHandler;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(MessageHandler.class)
public interface MessageHandlerAccessor {
    @Invoker("tryParseAsPlayerMessage")
    static SenderMeta e33chat$invokeTryParseAsPlayerMessage(Text message, String text) {
        throw new AssertionError();
    }
}
//#else
//$$ public interface MessageHandlerAccessor {
//$$ }
//#endif
