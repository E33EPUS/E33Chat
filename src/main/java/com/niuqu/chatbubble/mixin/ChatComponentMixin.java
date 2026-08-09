package com.niuqu.chatbubble.mixin;

import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.ChatBubbleScreen;
import com.niuqu.chatbubble.ChatImageCompat;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import com.niuqu.chatbubble.RenderHelper;
import net.minecraft.client.MinecraftClient;
//#if MC >= 12000
import net.minecraft.client.gui.DrawContext;
//#else
//$$ import net.minecraft.client.util.math.MatrixStack;
//#endif
import net.minecraft.client.gui.hud.ChatHud;
//#if MC >= 11900
//#if MC < 26000
import net.minecraft.client.gui.hud.MessageIndicator;
import net.minecraft.network.message.MessageSignatureData;
//#endif
//#endif
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.spongepowered.asm.mixin.Mixin;
//#if MC >= 26000
//$$ import org.spongepowered.asm.mixin.Shadow;
//#endif
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ChatHud.class, priority = 500, remap = false)
public class ChatComponentMixin {
    private String lastText;
    private long lastTime;
    private boolean e33chat$shifted;
    private boolean e33chat$reposting;
    private String lastRepostText;
    private long lastRepostTime;

    //#if MC >= 26000
    //$$ @Shadow
    //$$ private void addMessage(net.minecraft.network.chat.Component message,
    //$$     net.minecraft.network.chat.MessageSignature signature,
    //$$     net.minecraft.client.multiplayer.chat.GuiMessageSource source,
    //$$     net.minecraft.client.multiplayer.chat.GuiMessageTag tag) {}
    //#endif

    //#if MC >= 11900
    //#if MC >= 12106
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
        at = @At("HEAD"), cancellable = true, remap = false)
    //#else
    //$$ @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
    //$$     at = @At("HEAD"), cancellable = true, remap = false)
    //#endif
    //#endif
    //#if MC >= 12000
    private void onRender(DrawContext context,
    //#else
    //$$ private void onRender(MatrixStack context,
    //#endif
        //#if MC >= 12106
        net.minecraft.client.font.TextRenderer textRenderer,
        //#endif
        int tickDelta, int mouseX, int mouseY,
        //#if MC >= 12106
        boolean focused, boolean something, CallbackInfo ci) {
        //#else
        //$$ boolean focused, CallbackInfo ci) {
        //#endif
        e33chat$shifted = false;
        if (ChatBubbleClientSetup.config().enabled()) {
            if (MinecraftClient.getInstance().currentScreen instanceof ChatBubbleScreen) {
                ci.cancel();
                return;
            }
            //#if MC >= 12106
            RenderHelper.pushMatrix(context);
            RenderHelper.translate(context, 0, -8);
            //#else
            //$$ RenderHelper.pushMatrix(context);
            //$$ RenderHelper.translate(context, 0, -8, 0);
            //#endif
            e33chat$shifted = true;
        }
    }

    //#if MC >= 11900
    //#if MC >= 12106
    @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;Lnet/minecraft/client/font/TextRenderer;IIIZZ)V",
        at = @At("RETURN"), remap = false)
    //#else
    //$$ @Inject(method = "render(Lnet/minecraft/client/gui/DrawContext;IIIZ)V",
    //$$     at = @At("RETURN"), remap = false)
    //#endif
    //#endif
    private void onRenderReturn(
        //#if MC >= 12000
        DrawContext context,
        //#else
        //$$ MatrixStack context,
        //#endif
        //#if MC >= 12106
        net.minecraft.client.font.TextRenderer textRenderer,
        //#endif
        int tickDelta, int mouseX, int mouseY,
        //#if MC >= 12106
        boolean focused, boolean something,
        //#else
        //$$ boolean focused,
        //#endif
        CallbackInfo ci) {
        if (e33chat$shifted) {
            //#if MC >= 12106
            RenderHelper.popMatrix(context);
            //#else
            //$$ RenderHelper.popMatrix(context);
            //#endif
        }
    }

    //#if MC < 26000
    @Inject(method = "addMessage(Lnet/minecraft/text/Text;)V",
            at = @At("HEAD"), cancellable = true)
    private void onAddMessage(Text message, CallbackInfo ci) {
        captureMessage(message, ci);
    }
    //#endif

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
            ? com.niuqu.chatbubble.Txt.literal("[引用]").formatted(Formatting.YELLOW)
            : com.niuqu.chatbubble.Txt.literal("[私聊]").formatted(Formatting.LIGHT_PURPLE));
        Text reformatted = com.niuqu.chatbubble.Txt.empty()
            .append(com.niuqu.chatbubble.Txt.literal("<")).append(name).append(com.niuqu.chatbubble.Txt.literal(">")).append(tag)
            .append(com.niuqu.chatbubble.Txt.literal(" " + content));
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
        //#if MC >= 26000
        //$$ addMessage(reformatted, null, null, null);
        //#else
        ((ChatHud) (Object) this).addMessage(reformatted);
        //#endif
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
        return mc.player != null ? mc.player.getName() : com.niuqu.chatbubble.Txt.literal("?");
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
                com.niuqu.chatbubble.Txt.translatable("e33chat.sender.system"),
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
        // ChatImage rewrites the vanilla argument after our mixin, so the bubble
        // stored the pre-conversion CICode text — convert it back to the styled
        // [Image] component so the bubble matches the vanilla chat
        content = ChatImageCompat.convert(content);

        // Fallback: if the sender name is just the raw GameProfile name (no
        // prefix/title decorations), try to extract the decorated name from the
        // fully rendered final component — covers servers whose chat-type
        // params.name() returned null or the bare name, but the chat-type
        // decoration added the prefix in the rendered output.
        if (!meta.isSystem() && meta.rawPlayerName() != null && !meta.rawPlayerName().isEmpty()
                && meta.senderName().getString().equals(meta.rawPlayerName())
                && finalStr.contains(rawStr) && !rawStr.isEmpty()) {
            Text decorated = ChatMessageStore.extractDecoratedName(
                finalComponent, rawStr, meta.rawPlayerName(), meta.senderName());
            if (!decorated.getString().equals(meta.senderName().getString())) {
                meta = new SenderMeta(meta.senderUUID(), decorated, meta.rawContent(),
                    meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner());
            }
        }

        Text logComp = finalComponent, logContent = content;
        SenderMeta logMeta = meta;
        ChatMessageStore.debugLog(() -> "[e33chat] Capture | final='" + logComp.getString() + "' | content='" + logContent.getString() + "' | whisper=" + logMeta.whisper() + " | partner=" + logMeta.whisperPartner() + " | isSystem=" + logMeta.isSystem());
        ChatMessageStore.addMessage(content, meta.senderUUID(), meta.senderName(), meta.isSystem(), meta.rawPlayerName(), meta.whisper(), meta.whisperPartner(), false);
    }
}
