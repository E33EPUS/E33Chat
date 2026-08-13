package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import net.minecraft.client.MinecraftClient;
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#endif
//#if MC >= 12111
import net.minecraft.client.font.TextRenderer;
//#endif
//#if MC < 12000
import net.minecraft.client.util.math.MatrixStack;
//#endif
import net.minecraft.client.gui.hud.ChatHud;
//#if MC >= 11900
//#if MC < 26000
import net.minecraft.client.gui.hud.MessageIndicator;
//#endif
import net.minecraft.network.message.MessageSignatureData;
//#endif
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ChatHud.class, priority = 500)
public abstract class ChatComponentMixin {
    private Text lastComponent;
    private boolean e33chat$shifted;
    private boolean e33chat$reposting;
    private String lastRepostText;
    private long lastRepostTime;

    //#if MC >= 26000
    //$$ @Invoker("addMessage")
    //$$ abstract void e33chat$invokeAddMessage(Text message, MessageSignatureData signature,
    //$$         net.minecraft.client.multiplayer.message.GuiMessageSource source,
    //$$         net.minecraft.client.multiplayer.message.GuiMessageTag tag);
    //#endif

    //#if MC >= 12000
    //#if MC >= 12111
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, TextRenderer textRenderer, int currentTick,
                          int mouseX, int mouseY, boolean interactable, boolean bool, CallbackInfo ci) {
        e33chat$shifted = false;
        if (ChatBubbleClientSetup.config().enabled()) {
            if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
                ci.cancel();
                return;
            }
            // Skip matrix shift during world unload — the chat HUD may render
            // briefly after world=null, and push/pop without a matching render
            // can leave the matrix stack unbalanced if the render throws.
            if (MinecraftClient.getInstance().world == null) return;
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0, -8);
            e33chat$shifted = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(DrawContext context, TextRenderer textRenderer, int currentTick,
                                int mouseX, int mouseY, boolean interactable, boolean bool, CallbackInfo ci) {
        if (e33chat$shifted) {
            context.getMatrices().popMatrix();
        }
    }
    //#else
    //#if MC >= 12005
    @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    private void onRender(DrawContext context, int tickDelta, int mouseX, int mouseY,
                          boolean focused, CallbackInfo ci) {
        e33chat$shifted = false;
        if (ChatBubbleClientSetup.config().enabled()) {
            if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
                ci.cancel();
                return;
            }
            // Skip matrix shift during world unload (see 1.21.11 branch comment).
            if (MinecraftClient.getInstance().world == null) return;
            //#if MC >= 12106
            context.getMatrices().pushMatrix();
            context.getMatrices().translate(0, -8);
            //#else
            //$$ context.getMatrices().push();
            //$$ context.getMatrices().translate(0, -8, 0);
            //#endif
            e33chat$shifted = true;
        }
    }

    @Inject(method = "render", at = @At("RETURN"))
    private void onRenderReturn(DrawContext context, int tickDelta, int mouseX, int mouseY,
                                boolean focused, CallbackInfo ci) {
        if (e33chat$shifted) {
            //#if MC >= 12106
            context.getMatrices().popMatrix();
            //#else
            //$$ context.getMatrices().pop();
            //#endif
        }
    }
    //#else
    //$$ @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //$$ private void onRender(DrawContext context, int tickDelta, int mouseX, int mouseY,
    //$$                       CallbackInfo ci) {
    //$$     e33chat$shifted = false;
    //$$     if (ChatBubbleClientSetup.config().enabled()) {
    //$$         if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
    //$$             ci.cancel();
    //$$             return;
    //$$         }
    //$$         context.getMatrices().push();
    //$$         context.getMatrices().translate(0, -8, 0);
    //$$         e33chat$shifted = true;
    //$$     }
    //$$ }
    //$$ @Inject(method = "render", at = @At("RETURN"))
    //$$ private void onRenderReturn(DrawContext context, int tickDelta, int mouseX, int mouseY,
    //$$                             CallbackInfo ci) {
    //$$     if (e33chat$shifted) {
    //$$         context.getMatrices().pop();
    //$$     }
    //$$ }
    //#endif
    //#endif
    //#else
    //$$ @Inject(method = "render", at = @At("HEAD"), cancellable = true)
    //$$ private void onRender(MatrixStack context, int ticks, CallbackInfo ci) {
    //$$     e33chat$shifted = false;
    //$$     if (ChatBubbleClientSetup.config().enabled()) {
    //$$         if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
    //$$             ci.cancel();
    //$$             return;
    //$$         }
    //$$         context.push();
    //$$         context.translate(0, -8, 0);
    //$$         e33chat$shifted = true;
    //$$     }
    //$$ }
    //$$ @Inject(method = "render", at = @At("RETURN"))
    //$$ private void onRenderReturn(MatrixStack context, int ticks, CallbackInfo ci) {
    //$$     if (e33chat$shifted) {
    //$$         context.pop();
    //$$     }
    //$$ }
    //#endif

    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, CallbackInfo ci) {
        captureMessage(message, ci);
    }

    //#if MC >= 11900
    //#if MC < 26000
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;Lnet/minecraft/network/message/MessageSignatureData;Lnet/minecraft/client/gui/hud/MessageIndicator;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessageFull(Text message, MessageSignatureData signature,
                                  MessageIndicator indicator, CallbackInfo ci) {
        captureMessage(message, ci);
    }
    //#endif
    //#endif

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
        // 3-arg addMessage with a null indicator: the 1-arg overload forces
        // MessageIndicator.system(), which logs "[System] [CHAT]" and styles the line
        //#if MC >= 26000
        //$$ e33chat$invokeAddMessage(reformatted, null, null, null);
        //#else
        //#if MC >= 11900
        ((ChatHud) (Object) this).addMessage(reformatted, null, null);
        //#else
        //$$ ((ChatHud) (Object) this).addMessage(reformatted);
        //#endif
        //#endif
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
        if (ChatMessageStore.isPlayerBlocked(meta.rawPlayerName(), meta.senderName(),
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
        // 2.3.10+: image bracket codes are kept raw in storage; the bubble
        // renders them natively (BracketCodec strips the code, ImageLoader
        // draws the picture). The vanilla chat still gets ChatImage's own
        // conversion via ChatImage's mixins, so both surfaces agree.

        Text logComp = finalComponent, logContent = content;
        SenderMeta logMeta = meta;
        ChatMessageStore.debugLog(() -> "[e33chat] Capture | final='" + logComp.getString() + "' | content='" + logContent.getString() + "' | whisper=" + logMeta.whisper() + " | partner=" + logMeta.whisperPartner() + " | isSystem=" + logMeta.isSystem());
        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(), meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner(), false);
    }
}
