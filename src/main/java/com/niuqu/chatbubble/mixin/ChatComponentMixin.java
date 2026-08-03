package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import java.util.UUID;
import net.minecraft.ChatFormatting;
import net.minecraft.client.GuiMessageTag;
import net.minecraft.client.Minecraft;
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
    private boolean e33chat$shifted;
    private boolean e33chat$reposting;
    private String lastRepostText;
    private long lastRepostTime;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(GuiGraphics guiGraphics, int tickCount, int mouseX,
                          int mouseY, boolean focused, CallbackInfo ci) {
        e33chat$shifted = false;
        if (ChatBubbleConfig.ENABLED.get()) {
            if (Minecraft.getInstance().screen instanceof ChatBubbleScreen) {
                ci.cancel();
                return;
            }
            guiGraphics.pose().pushPose();
            guiGraphics.pose().translate(0, -8, 0);
            e33chat$shifted = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(GuiGraphics guiGraphics, int tickCount, int mouseX,
                                int mouseY, boolean focused, CallbackInfo ci) {
        if (e33chat$shifted) {
            guiGraphics.pose().popPose();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Component message, CallbackInfo ci) {
        captureMessage(message, ci);
    }

    @Inject(method = "addMessage(Lnet/minecraft/network/chat/Component;Lnet/minecraft/network/chat/MessageSignature;Lnet/minecraft/client/GuiMessageTag;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessageFull(Component message, MessageSignature signature,
                                   GuiMessageTag tag, CallbackInfo ci) {
        captureMessage(message, ci);
    }

    // Vanilla chat gets a unified player-style format for whispers/quotes:
    //   <sender>[私聊] content   (whisper in/out, incl. self-whisper)
    //   <sender>[引用] content   (quote reply, detected via the echo's quoted flag)
    // The sender component keeps its style so colored nicknames/prefixes survive.
    private void repostToVanilla(Component name, String content, boolean quoting) {
        Component tag = (quoting
            ? Component.literal("[引用]").withStyle(ChatFormatting.YELLOW)
            : Component.literal("[私聊]").withStyle(ChatFormatting.LIGHT_PURPLE));
        Component reformatted = Component.empty()
            .append("<").append(name).append(">").append(tag)
            .append(Component.literal(" " + content));
        String repostStr = reformatted.getString();
        long nowMs = System.currentTimeMillis();
        // Server echoes a whisper twice (signed outgoing + incoming) within ~15ms;
        // both would rewrite to the same line without this guard.
        if (ChatMessageStore.isRepostDuplicate(lastRepostText, lastRepostTime, repostStr, nowMs)) {
            ChatMessageStore.debugLog(() -> "[e33chat] Repost deduped | '" + repostStr + "'");
            return;
        }
        lastRepostText = repostStr;
        lastRepostTime = nowMs;
        ChatMessageStore.debugLog(() -> "[e33chat] Repost to vanilla | '" + repostStr + "' | quoting=" + quoting);
        e33chat$reposting = true;
        // 3-arg addMessage with a null tag: the 1-arg overload forces
        // GuiMessageTag.system(), which logs "[System] [CHAT]" and styles the line
        ((ChatComponent) (Object) this).addMessage(reformatted, null, null);
        e33chat$reposting = false;
    }

    private void captureMessage(Component finalComponent, CallbackInfo ci) {
        if (!ChatBubbleConfig.ENABLED.get()) return;
        if (e33chat$reposting) return;

        // 3-arg addMessage calls 1-arg internally — skip the duplicate
        String text = finalComponent.getString();
        long now = System.currentTimeMillis();
        if (text.equals(lastText) && now - lastTime < 100) return;
        lastText = text;
        lastTime = now;

        // Outgoing whisper echo via the system channel ("你悄悄对 Steve 说: hi"):
        // suppress the vanilla line and repost it as <me>[私聊] hi. Checked BEFORE
        // consumePendingMeta: this path never sets pending meta, so consuming it here
        // would eat a stale residue and misattribute the next real message.
        if (ChatMessageStore.consumeSuppressCapture()) {
            ci.cancel();
            // Decorated name from the line itself, so this path matches the signed
            // echo path's meta.senderName() — otherwise the repost dedup guard sees
            // different strings (tab name vs chat-decorated name) and shows both
            Component name = ChatMessageStore.extractWhisperDisplayName(finalComponent,
                ChatMessageStore.ownDisplayName());
            // Vanilla outgoing lines carry only the target ("你悄悄地对X说" / "You
            // whisper to X") — ownDisplayName() then supplies our name. Either way
            // the local bubble was created with a bare name: patch it now that the
            // echo reveals the real self display name.
            ChatMessageStore.cacheOwnDecoratedName(name);
            ChatMessageStore.updateLatestOwnSenderName(name);
            repostToVanilla(name, ChatMessageStore.extractWhisperContent(text, null),
                ChatMessageStore.consumeSuppressQuoted());
            return;
        }

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

        // Self-sent echo on the signed channel: plain chat keeps the vanilla line;
        // whisper echoes get the [私聊] rewrite, quote replies get the [引用] rewrite
        // (quote replies travel as plain chat, so the echo's quoted flag is their
        // only rewrite signal). meta is trusted here (freshly consumed) and carries
        // the server-decorated name + content, e.g. "[称号]E33EPUS" / "1234533425".
        ChatMessageStore.EchoMatch echo = ChatMessageStore.consumeEchoIfSenderMatches(meta.senderUUID(), meta.senderName());
        if (echo.matched()) {
            if (meta.whisper() || echo.quoted()) {
                ci.cancel();
                repostToVanilla(meta.senderName(), ChatMessageStore.extractWhisperContent(text, meta), echo.quoted());
            }
            return;
        }
        if (ChatMessageStore.consumeEchoBySystemChat(text).matched()) return;

        // Incoming whisper (someone whispers you): same unified format, sender's name
        if (meta.whisper()) {
            ci.cancel();
            repostToVanilla(meta.senderName(), ChatMessageStore.extractWhisperContent(text, meta), false);
        }

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
        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(), meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner(), false);
    }
}
