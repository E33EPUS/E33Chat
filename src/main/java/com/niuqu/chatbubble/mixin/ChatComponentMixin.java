package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import com.niuqu.chatbubble.image.BracketCodec;
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
    private Component lastComponent;
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
        // banner.quote/whisper carry a trailing space (banner prefix convention),
        // so content is appended without an extra separator.
        Component tag = (quoting
            ? Component.translatable("e33chat.banner.quote").withStyle(ChatFormatting.YELLOW)
            : Component.translatable("e33chat.banner.whisper").withStyle(ChatFormatting.LIGHT_PURPLE));
        Component reformatted = Component.empty()
            .append("<").append(name).append(">").append(tag)
            .append(Component.literal(content));
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

    // The vanilla chat gets the raw [[CICode,url=...]] line (long URL → spammy).
    // Rewrite it to a "[图片]" placeholder so the bubble renders the image while
    // the vanilla surface stays compact, independent of ChatImage being installed.
    private void rewriteVanillaImageCode(Component finalComponent, CallbackInfo ci) {
        Component placeholder = BracketCodec.toPlaceholderText(finalComponent);
        if (placeholder == finalComponent) return; // no image code, nothing to do
        ci.cancel();
        e33chat$reposting = true;
        ((ChatComponent) (Object) this).addMessage(placeholder, null, null);
        e33chat$reposting = false;
    }

    private void captureMessage(Component finalComponent, CallbackInfo ci) {
        if (!ChatBubbleConfig.ENABLED.get()) return;
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

        // Blocked sender: vanish completely — no vanilla line, no bubble, no
        // banner/sound (addMessage below never runs). Checked before the echo and
        // whisper-repost branches so a blocked player's whisper can't resurface
        // as a [私聊] rewrite.
        if (ChatMessageStore.isPlayerBlocked(meta.rawPlayerName(), meta.senderName(),
                ChatBubbleConfig.BLOCKED_PLAYERS.get())) {
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
        ChatMessageStore.EchoMatch echo = ChatMessageStore.consumeEchoIfSenderMatches(meta.senderUUID(), meta.senderName(), text);
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
        Component content;
        if (finalStr.contains(rawStr)) {
            content = meta.rawContent();
        } else {
            content = finalComponent;
        }
        // ChatImage rewrites the vanilla argument after our mixin, so the bubble
        // stored the pre-conversion CICode text — convert it back to the styled
        // 2.3.10+: image bracket codes are kept raw in storage; the bubble
        // renders them natively (BracketCodec strips the code, ImageLoader
        // draws the picture). The vanilla chat still gets ChatImage's own
        // conversion via ChatImage's mixins, so both surfaces agree.

        Component logComp = finalComponent, logContent = content;
        SenderMeta logMeta = meta;
        ChatMessageStore.debugLog(() -> "[e33chat] Capture | final='" + logComp.getString() + "' | content='" + logContent.getString() + "' | whisper=" + logMeta.whisper() + " | partner=" + logMeta.whisperPartner() + " | isSystem=" + logMeta.isSystem());
        rewriteVanillaImageCode(finalComponent, ci);
        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(), meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner(), false);
    }
}
