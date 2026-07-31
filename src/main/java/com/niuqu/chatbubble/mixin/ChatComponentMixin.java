package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ChatHud.class, priority = 500)
public class ChatComponentMixin {
    private String lastText;
    private long lastTime;
    private boolean e33chat$shifted;
    private boolean e33chat$reposting;
    private String lastRepostText;
    private long lastRepostTime;

    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int tickDelta, int mouseX, int mouseY,
                          boolean focused, CallbackInfo ci) {
        e33chat$shifted = false;
        if (ChatBubbleClientSetup.config().enabled()) {
            if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
                ci.cancel();
                return;
            }
            context.getMatrices().push();
            context.getMatrices().translate(0, -8, 0);
            e33chat$shifted = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(DrawContext context, int tickDelta, int mouseX, int mouseY,
                                boolean focused, CallbackInfo ci) {
        if (e33chat$shifted) {
            context.getMatrices().pop();
        }
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, CallbackInfo ci) {
        captureMessage(message, ci);
    }

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessageFull(Text message, MessageSignatureData signature,
                                  MessageIndicator indicator, CallbackInfo ci) {
        captureMessage(message, ci);
    }

    // Vanilla chat gets a unified player-style format for whispers/quotes:
    //   <sender>[私聊] content   (whisper in/out, incl. self-whisper)
    //   <sender>[引用] content   (quote reply, detected via the echo's quoted flag)
    // The sender component keeps its style so colored nicknames/prefixes survive.
    private void repostToVanilla(Text name, String content, boolean quoting) {
        Text tag = (quoting
            ? Text.literal("[引用]").formatted(Formatting.YELLOW)
            : Text.literal("[私聊]").formatted(Formatting.LIGHT_PURPLE));
        Text reformatted = Text.empty()
            .append(Text.literal("<")).append(name).append(Text.literal(">")).append(tag)
            .append(Text.literal(" " + content));
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
        ((ChatHud) (Object) this).addMessage(reformatted);
        e33chat$reposting = false;
    }

    // Sender name for the outgoing-echo repost: the tab-list display name carries
    // prefix/suffix and team color ("[称号]E33EPUS" in aqua), falling back to the
    // profile name when the server provides no display name.
    private static Text ownDisplayName(MinecraftClient mc) {
        if (mc.player != null && mc.player.networkHandler != null) {
            var info = mc.player.networkHandler.getPlayerListEntry(mc.player.getUuid());
            if (info != null && info.getDisplayName() != null) {
                return info.getDisplayName();
            }
        }
        return mc.player != null ? mc.player.getName() : Text.literal("?");
    }

    private void captureMessage(Text finalComponent, CallbackInfo ci) {
        if (!ChatBubbleClientSetup.config().enabled()) return;
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
            Text name = ChatMessageStore.extractWhisperDisplayName(finalComponent,
                ownDisplayName(MinecraftClient.getInstance()));
            repostToVanilla(name, ChatMessageStore.extractWhisperContent(text, null),
                ChatMessageStore.consumeSuppressQuoted());
            return;
        }

        SenderMeta meta = ChatMessageStore.consumePendingMeta();
        if (meta == null) {
            if (ChatMessageStore.isRecentDuplicate(text)) return;
            meta = new SenderMeta(
                new UUID(0, 0),
                Text.translatable("e33chat.sender.system"),
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
        Text content;
        if (finalStr.contains(rawStr)) {
            content = meta.rawContent();
        } else {
            content = finalComponent;
        }

        Text logComp = finalComponent, logContent = content;
        SenderMeta logMeta = meta;
        ChatMessageStore.debugLog(() -> "[e33chat] Capture | final='" + logComp.getString() + "' | content='" + logContent.getString() + "' | whisper=" + logMeta.whisper() + " | partner=" + logMeta.whisperPartner() + " | isSystem=" + logMeta.isSystem());
        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(), meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner());
    }
}
