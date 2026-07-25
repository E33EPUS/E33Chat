package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import java.util.UUID;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.ChatComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MessageSignature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = ChatComponent.class, priority = 500)
public class ChatComponentMixin {
    private String lastText;
    private long lastTime;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics guiGraphics, int tickCount, int mouseX,
                          int mouseY, CallbackInfo ci) {
        if (ChatBubbleConfig.ENABLED.get()) {
            ci.cancel();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"))
    private void onAddMessage(Component message, CallbackInfo ci) {
        captureMessage(message);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"))
    private void onAddMessageFull(Component message, MessageSignature signature,
                                   GuiMessageTag tag, CallbackInfo ci) {
        captureMessage(message);
    }

    private void captureMessage(Component finalComponent) {
        if (!ChatBubbleConfig.ENABLED.get()) return;

        // 3-arg addMessage calls 1-arg internally — skip the duplicate
        String text = finalComponent.getString();
        long now = System.currentTimeMillis();
        if (text.equals(lastText) && now - lastTime < 100) return;
        lastText = text;
        lastTime = now;

        SenderMeta meta = ChatMessageStore.consumePendingMeta();
        if (meta == null) {
            if (ChatMessageStore.isRecentDuplicate(text)) return;
            meta = new SenderMeta(
                new UUID(0, 0),
                Component.translatable("e33chat.sender.system"),
                finalComponent,
                true,
                null,
                false, null
            );
        }

        if (ChatMessageStore.consumeSuppressCapture()) return;
        if (ChatMessageStore.consumeEchoIfSenderMatches(meta.senderName())) return;
        if (ChatMessageStore.consumeEchoBySystemChat(finalComponent.getString())) return;

        String rawStr = meta.rawContent().getString();
        String finalStr = finalComponent.getString();
        Component content;
        if (finalStr.contains(rawStr)) {
            content = meta.rawContent();
        } else {
            content = finalComponent;
        }

        Component logComp = finalComponent, logContent = content;
        SenderMeta logMeta = meta;
        ChatMessageStore.debugLog(() -> "[e33chat] Capture | final='" + logComp.getString() + "' | content='" + logContent.getString() + "' | whisper=" + logMeta.whisper() + " | partner=" + logMeta.whisperPartner() + " | isSystem=" + logMeta.isSystem());
        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(), meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner());
    }
}
