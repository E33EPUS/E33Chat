package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ChatHud.class, priority = 500)
public class ChatComponentMixin {
    private String lastText;
    private long lastTime;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int tickDelta, int mouseX, int mouseY, boolean focused, CallbackInfo ci) {
        ChatBubbleConfig cfg = ChatBubbleClientSetup.config();
        if (cfg != null && cfg.enabled()) ci.cancel();
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V", at = @At("HEAD"))
    private void onAddMessage(Text message, CallbackInfo ci) {
        captureMessage(message);
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"))
    private void onAddMessageFull(Text message, MessageSignatureData signature,
                                   MessageIndicator indicator, CallbackInfo ci) {
        captureMessage(message);
    }

    private void captureMessage(Text finalComponent) {
        ChatBubbleConfig cfg = ChatBubbleClientSetup.config();
        if (cfg == null || !cfg.enabled()) return;

        String text = finalComponent.getString();
        long now = System.currentTimeMillis();
        if (text.equals(lastText) && now - lastTime < 100) return;
        lastText = text;
        lastTime = now;

        SenderMeta meta = ChatMessageStore.consumePendingMeta();
        if (meta == null) {
            if (ChatMessageStore.isRecentDuplicate(text)) return;
            meta = new SenderMeta(new UUID(0, 0),
                Text.translatable("e33chat.sender.system"),
                finalComponent, true, null, false, null);
        }

        // consumeSuppressCapture stays — it's for whisper echo suppression (reliable)
        if (ChatMessageStore.consumeSuppressCapture()) return;
        if (ChatMessageStore.consumeEchoIfSenderMatches(meta.senderUUID(), meta.senderName())) return;
        if (ChatMessageStore.consumeEchoBySystemChat(finalComponent.getString())) return;

        String rawStr = meta.rawContent().getString();
        String finalStr = finalComponent.getString();
        Text content = finalStr.contains(rawStr) ? meta.rawContent() : finalComponent;

        ChatMessageStore.debugLog("[e33chat] Capture | final='" + finalComponent.getString()
            + "' | content='" + content.getString() + "' | whisper=" + meta.whisper()
            + " | partner=" + meta.whisperPartner() + " | isSystem=" + meta.isSystem());
        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(),
            meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner());
    }
}
