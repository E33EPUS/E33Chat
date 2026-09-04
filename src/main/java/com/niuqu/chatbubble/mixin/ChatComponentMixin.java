package com.niuqu.chatbubble.mixin;
import com.niuqu.chatbubble.store.EchoTracker;
import com.niuqu.chatbubble.store.BlockList;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.store.ChatMessageStore.SenderMeta;
import com.niuqu.chatbubble.image.BracketCodec;
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
    private Text lastComponent;
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
        // banner.quote/whisper carry a trailing space (banner prefix convention),
        // so content is appended without an extra separator.
        Text tag = (quoting
            ? Text.translatable("e33chat.banner.quote").formatted(Formatting.YELLOW)
            : Text.translatable("e33chat.banner.whisper").formatted(Formatting.LIGHT_PURPLE));
        Text reformatted = Text.empty()
            .append(Text.literal("<")).append(name).append(Text.literal(">")).append(tag)
            .append(Text.literal(content));
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
        // 3-arg addMessage with a null indicator: the 1-arg overload forces
        // MessageIndicator.system(), which logs "[System] [CHAT]" and styles the line
        ((ChatHud) (Object) this).addMessage(reformatted, null, null);
        e33chat$reposting = false;
    }

    // The vanilla chat gets the raw [[CICode,url=...]] line (long URL → spammy).
    // Rewrite it to a "[图片]" placeholder so the bubble renders the image while
    // the vanilla surface stays compact, independent of ChatImage being installed.
    private void rewriteVanillaImageCode(Text finalComponent, CallbackInfo ci) {
        Text placeholder = BracketCodec.toPlaceholderText(finalComponent);
        if (placeholder == finalComponent) return; // no image code, nothing to do
        ci.cancel();
        e33chat$reposting = true;
        ((ChatHud) (Object) this).addMessage(placeholder, null, null);
        e33chat$reposting = false;
    }

    private void captureMessage(Text finalComponent, CallbackInfo ci) {
        if (!ChatBubbleClientSetup.config().enabled()) return;
        if (e33chat$reposting) return;

        // 1-arg addMessage calls 3-arg internally with the SAME Component object —
        // dedupe on object identity so two genuinely identical messages (same text,
        // different objects) are never swallowed
        if (finalComponent == lastComponent) return;
        lastComponent = finalComponent;
        String text = finalComponent.getString();

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
                Text.translatable("e33chat.sender.system"),
                finalComponent,
                true,
                null,
                false, null
            );
        }

        // Blocked sender: vanish completely — no vanilla line, no bubble, no
        // banner/sound (addMessage below never runs). Checked before the echo and
        // whisper-repost branches so a blocked player's whisper can't resurface
        // as a [私聊] rewrite.
        if (BlockList.isPlayerBlocked(meta.rawPlayerName(), meta.senderName(),
                ChatBubbleClientSetup.config().blockedPlayers())) {
            final String blockedName = meta.senderName().getString();
            ci.cancel();
            ChatMessageStore.debugLog(() -> "[e33chat] Blocked message dropped | sender='" + blockedName + "'");
            return;
        }

        // Self-sent echo on the signed channel: plain chat keeps the vanilla line;
        // whisper echoes get the [私聊] rewrite, quote replies get the [引用] rewrite
        // (quote replies travel as plain chat, so the echo's quoted flag is their
        // only rewrite signal). meta is trusted here (freshly consumed) and carries
        // the server-decorated name + content, e.g. "[称号]E33EPUS" / "1234533425".
        EchoTracker.EchoMatch echo = ChatMessageStore.consumeEchoIfSenderMatches(meta.senderUUID(), meta.senderName(), text);
        if (echo.matched()) {
            if (meta.whisper() || echo.quoted()) {
                ci.cancel();
                repostToVanilla(meta.senderName(), ChatMessageStore.extractWhisperContent(text, meta), echo.quoted());
            } else {
                rewriteVanillaImageCode(finalComponent, ci);
            }
            return;
        }
        if (ChatMessageStore.consumeEchoBySystemChat(text).matched()) {
            rewriteVanillaImageCode(finalComponent, ci);
            return;
        }

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
        } else if (!rawStr.isBlank()
                && !BracketCodec.parseOrExtract(meta.rawContent()).images().isEmpty()) {
            // ChatImage (or a similar mod) rewrote the component before we
            // captured it: the [[CICode,...]] bracket is gone from the line.
            // Keep the pristine server-sent content so the bubble still renders
            // the image and does not repeat the sender name.
            content = meta.rawContent();
        } else {
            content = finalComponent;
        }
        // 2.3.10+: image bracket codes are kept raw in storage; the bubble
        // renders them natively (BracketCodec strips the code, ImageLoader
        // draws the picture). The vanilla chat still gets ChatImage's own
        // conversion via ChatImage's mixins, so both surfaces agree.

        Text logComp = finalComponent, logContent = content;
        SenderMeta logMeta = meta;
        ChatMessageStore.debugLog(() -> "[e33chat] Capture | final='" + logComp.getString() + "' | content='" + logContent.getString() + "' | whisper=" + logMeta.whisper() + " | partner=" + logMeta.whisperPartner() + " | isSystem=" + logMeta.isSystem());
        rewriteVanillaImageCode(finalComponent, ci);
        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(), meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner(), false);
    }
}
