package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleMod;
import com.mojang.brigadier.ParseResults;
import net.minecraft.server.command.CommandManager;
import net.minecraft.server.command.ServerCommandSource;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

// Fabric API has no command-execution event (Forge has CommandEvent), so private
// message quotes (/msg /tell /w /whisper) would never be consumed server-side.
// Inject vanilla CommandManager.execute before dispatch — same semantics as Forge.
@Mixin(CommandManager.class)
public class CommandManagerMixin {

    @Inject(method = "execute(Lcom/mojang/brigadier/ParseResults;Ljava/lang/String;)V",
        at = @At("HEAD"))
    private void onCommandExecuted(ParseResults<ServerCommandSource> parseResults, String command, CallbackInfo ci) {
        ChatBubbleMod.consumePrivateMessageQuote(parseResults, command);
    }
}
