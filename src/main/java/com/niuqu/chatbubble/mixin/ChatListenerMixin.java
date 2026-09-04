package com.niuqu.chatbubble.mixin;

import com.mojang.authlib.GameProfile;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.store.ChatMessageStore.SenderMeta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.Text;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = net.minecraft.client.network.message.MessageHandler.class, priority = 500)
public class ChatListenerMixin {
    // Whisper keywords live in chat.WhisperSignal (single constant source); the
    // echo check below matches them against the whole line.


    // Pulls styled server prefixes out of the decorated line: "[Group]<Steve> hi" -> "[Group]Steve"
    private static Text extractDecoratedName(Text fullLine, String contentStr,
                                                  String rawName, Text fallback) {
        return com.niuqu.chatbubble.chat.capture.ChatPipeline.extractDecoratedName(fullLine, contentStr, rawName, fallback);
    }


    private static Text cleanNameArea(Text fullLine, int a, int b,
                                           String rawName, Text fallback) {
        return com.niuqu.chatbubble.chat.capture.ChatPipeline.cleanNameArea(fullLine, a, b, rawName, fallback);
    }


    // Nick plugins put the tab-list display name in chat instead of the profile name;
    // legacy plugins may embed section-sign color codes in names, so offer stripped variants too
    private static String[] nameCandidates(net.minecraft.client.network.PlayerListEntry info) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.nameCandidates(info);
    }


    private static void addNameVariants(java.util.Set<String> out, String name) {
        com.niuqu.chatbubble.chat.capture.ChatClassifier.addNameVariants(out, name);
    }


    // Vanilla broadcasts (advancements/deaths/joins) lead with a clickable player name,
    // which tell-click would wrongly claim as chat — keep them as system messages.
    // chat.type.admin is the op echo "[Steve: Teleported ...]",
    // announcement/emote are /say and /me — same trap
    private static boolean isVanillaBroadcast(Text message) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.isVanillaBroadcast(message);
    }


    // ===== Layer 0: deterministic routing by vanilla translation key.=====
    // NCR/FreedomChat stuff the decorated component tree into system packets unchanged,
    // so the key survives conversion. Unknown keys fall through to the heuristics below.

    private static Text argAsComponent(Object arg) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.argAsComponent(arg);
    }


    private static net.minecraft.client.network.PlayerListEntry resolveOnlinePlayer(String displayName) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.resolveOnlinePlayer(displayName);
    }


    private static boolean classifyByKey(Text message) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.classifyByKey(message);
    }


    // Plugins attach "click to whisper" events to sender names — the command holds the
    // real profile name, giving deterministic attribution even on nickname servers
    private static SenderMeta detectByTellClick(Text message, String text) {
        return com.niuqu.chatbubble.chat.capture.TellClickDetector.detectByTellClick(message, text);
    }


    private static SenderMeta detectWhisperInSystemMessage(String text, String logTag) {
        return com.niuqu.chatbubble.chat.capture.WhisperDetector.detectWhisperInSystemMessage(text, logTag);
    }


    // ===== Template layer (server-declared message formats) =====

    private static void logTemplateMiss(String text) {
        com.niuqu.chatbubble.chat.capture.TemplateLayer.logTemplateMiss(text);
    }


    private static boolean isTemplateNameKnown(String name) {
        return com.niuqu.chatbubble.chat.capture.TemplateLayer.isTemplateNameKnown(name);
    }


    // Server template parse: exact field split with style-preserving offsets.
    // Returns null on no match (fall back to the guards) or when the line is our
    // own echo (already bubbled via the authoritative player channel / suppressed).
    private static SenderMeta matchByTemplate(Text message, String text) {
        return com.niuqu.chatbubble.chat.capture.TemplateLayer.matchByTemplate(message, text);
    }


    // Template-path field slicing: if the captured region contains literal §-codes
    // (some plugins embed raw "§6" text instead of real styles), rebuild it with
    // parseStyledText to render actual colors; otherwise keep the original
    // component slice (preserves real per-run styles like the guards do).
    private static Text templateSlice(Text message, String text, int from, int to) {
        return com.niuqu.chatbubble.chat.capture.TemplateLayer.templateSlice(message, text, from, to);
    }

    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void onPlayerChat(SignedMessage message, GameProfile gameProfile,
                               MessageType.Parameters params, CallbackInfo ci) {
        UUID senderId = gameProfile.getId();
        Text raw = message.getContent();
        String rawStr = raw.getString();
        if (rawStr.startsWith("xaero-waypoint:")
            || rawStr.startsWith("xaero_waypoint:")
            || rawStr.startsWith("xaero_waypoint_add:")) {
            return;
        }
        String name = gameProfile.getName();

        boolean isWhisper = false;
        boolean isOutgoing = false;
        String whisperPartner = null;
        if (params.type().matchesKey(MessageType.MSG_COMMAND_INCOMING)) {
            isWhisper = true;
            whisperPartner = name;
        } else if (params.type().matchesKey(MessageType.MSG_COMMAND_OUTGOING)) {
            isWhisper = true;
            isOutgoing = true;
            whisperPartner = params.targetName().map(Text::getString).orElse(null);
        }

        String pattern = "<" + name + "> ";
        int idx = rawStr.indexOf(pattern);
        int contentStart = idx >= 0 ? idx + pattern.length() : -1;
        int prefixEnd = idx;
        if (contentStart < 0) {
            int i2 = rawStr.indexOf(name + "> ");
            if (i2 > 0) {
                int open = rawStr.lastIndexOf('<', i2);
                if (open >= 0 && rawStr.indexOf('>', open) == i2 + name.length()) {
                    contentStart = i2 + name.length() + 2;
                    prefixEnd = open;
                }
            }
        }
        if (contentStart >= 0) {
            String cleanContent = rawStr.substring(contentStart);
            Text displayName = extractDecoratedName(raw, cleanContent, name,
                Text.literal((rawStr.substring(0, prefixEnd) + name).trim()));
            Text contentComp = ChatMessageStore.sliceStyled(raw, contentStart, rawStr.length());
            ChatMessageStore.setPendingMeta(new SenderMeta(
                senderId != null ? senderId : new UUID(0, 0),
                displayName, contentComp, false, name, isWhisper, whisperPartner));
            return;
        }

        Text playerContent = raw;
        Text senderName = Text.literal(name);
        if (isWhisper) {
            playerContent = Text.literal(com.niuqu.chatbubble.chat.MessagePresentation.extractWhisperContent(rawStr, name));
            Text fallback = isOutgoing ? ChatMessageStore.ownDisplayName() : senderName;
            senderName = ChatMessageStore.extractWhisperDisplayName(params.applyChatDecoration(raw), fallback);
        } else {
            Text fullLine = params.applyChatDecoration(raw);
            senderName = extractDecoratedName(fullLine, rawStr, name, senderName);
        }
        if (senderId != null && senderId.equals(MinecraftClient.getInstance().player.getUuid())) {
            ChatMessageStore.cacheOwnDecoratedName(senderName);
        }
        ChatMessageStore.debugLog("[e33chat] PlayerChat | raw='" + rawStr + "' | sender='" + senderName.getString() + "' | content='" + playerContent.getString() + "'");
        ChatMessageStore.setPendingMeta(new SenderMeta(
            senderId != null ? senderId : new UUID(0, 0),
            senderName, playerContent, false, name, isWhisper, whisperPartner));
    }

    @Inject(method = "onProfilelessMessage", at = @At("HEAD"))
    private void onDisguisedChat(Text message, MessageType.Parameters params, CallbackInfo ci) {
        String msgStr = message.getString();
        if (msgStr.startsWith("xaero-waypoint:")
            || msgStr.startsWith("xaero_waypoint:")
            || msgStr.startsWith("xaero_waypoint_add:")) {
            return;
        }
        boolean hasSender = params.name() != null;

        boolean isWhisper = false;
        boolean isOutgoing = false;
        String whisperPartner = null;
        if (params.type().matchesKey(MessageType.MSG_COMMAND_INCOMING)) {
            isWhisper = true;
            whisperPartner = hasSender ? params.name().getString() : null;
        } else if (params.type().matchesKey(MessageType.MSG_COMMAND_OUTGOING)) {
            isWhisper = true;
            isOutgoing = true;
            whisperPartner = params.targetName().map(Text::getString).orElse(null);
        }

        if (!isWhisper) {
            SenderMeta wm = detectWhisperInSystemMessage(msgStr, "disguised");
            if (wm != null) { ChatMessageStore.setPendingMeta(wm); return; }
        }

        if (hasSender) {
            Text disContent = message;
            Text disSender = params.name();
            if (isWhisper) {
                disContent = Text.literal(com.niuqu.chatbubble.chat.MessagePresentation.extractWhisperContent(msgStr, params.name().getString()));
                Text fallback = isOutgoing ? ChatMessageStore.ownDisplayName() : disSender;
                disSender = ChatMessageStore.extractWhisperDisplayName(message, fallback);
            } else {
                Text fullLine = params.applyChatDecoration(message);
                disSender = extractDecoratedName(fullLine, msgStr, params.name().getString(), disSender);
            }
            ChatMessageStore.debugLog("[e33chat] Disguised | raw='" + msgStr + "' | whisper=" + isWhisper + " | partner=" + whisperPartner + " | sender='" + disSender.getString() + "' | content='" + disContent.getString() + "'");
            ChatMessageStore.setPendingMeta(new SenderMeta(
                new UUID(0, 0), disSender, disContent, false,
                params.name().getString(), isWhisper, whisperPartner));
            return;
        }

        var connection = MinecraftClient.getInstance().player != null
            ? MinecraftClient.getInstance().player.networkHandler : null;

        SenderMeta tc = detectByTellClick(message, msgStr);
        if (tc != null) { ChatMessageStore.setPendingMeta(tc); return; }

        if (connection != null && !isWhisper) {
            SenderMeta parsed = com.niuqu.chatbubble.chat.capture.ChatPipeline.tryParsePlayerLine(message, msgStr, "Disguised");
            if (parsed != null) { ChatMessageStore.setPendingMeta(parsed); return; }
        }

        ChatMessageStore.debugLog(() -> "[e33chat] Disguised(guard fallback -> gray) | text='" + msgStr + "'");
        var cfg = ChatBubbleClientSetup.config();
        boolean isSystem = cfg == null || !cfg.systemChatAsBubble();
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0), Text.translatable("e33chat.sender.system"),
            message, isSystem, null, false, null));
    }

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void onSystemChat(Text message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;

        if (classifyByKey(message)) return;

        String sysText = message.getString();
        if (com.niuqu.chatbubble.chat.capture.EchoSuppressor.trySuppressOutgoingEcho(sysText)) return;
        ChatMessageStore.debugLog(() -> "[e33chat] System | text='" + sysText + "' | overlay=" + overlay);

        String text = message.getString();
        var connection = MinecraftClient.getInstance().player != null
            ? MinecraftClient.getInstance().player.networkHandler : null;

        if ((!ChatMessageStore.serverChatTemplates().isEmpty()
                || !ChatMessageStore.serverWhisperTemplates().isEmpty()) && connection != null) {
            SenderMeta tpl = matchByTemplate(message, text);
            if (tpl != null) { ChatMessageStore.setPendingMeta(tpl); return; }
        }

        // EasyBot compatibility (on by default; the server toggle overrides it):
        // parse QQ group relays as player messages before the generic
        // whisper/name heuristics can steal them.
        if (ChatMessageStore.isEasyBotCompat()) {
            SenderMeta eb = com.niuqu.chatbubble.chat.capture.EasyBotParser.tryParse(message, text);
            if (eb != null) {
                ChatMessageStore.debugLog(() -> "[e33chat] System(EasyBot) | name='" + eb.senderName().getString()
                    + "' | content='" + eb.rawContent().getString() + "'");
                ChatMessageStore.setPendingMeta(eb);
                return;
            }
            // Relay-shaped line the parser declined (blank content, a generic
            // broadcast label, or a name owned by a real player) — one debug
            // line so a template mismatch is diagnosable from the log alone.
            int open = text.indexOf('<');
            if (open >= 0 && text.indexOf('>', open) > open) {
                ChatMessageStore.debugLog(() -> "[e33chat] System(EasyBot miss) | text='" + text + "'");
            }
        }

        SenderMeta wm = detectWhisperInSystemMessage(text, "whisper");
        if (wm != null) { ChatMessageStore.setPendingMeta(wm); return; }

        SenderMeta tc = detectByTellClick(message, text);
        if (tc != null) { ChatMessageStore.setPendingMeta(tc); return; }

        if (connection != null) {
            SenderMeta parsed = com.niuqu.chatbubble.chat.capture.ChatPipeline.tryParsePlayerLine(message, text, "System");
            if (parsed != null) { ChatMessageStore.setPendingMeta(parsed); return; }
        }

        ChatMessageStore.debugLog(() -> "[e33chat] System(guard fallback -> gray) | text='" + text + "'");
        var cfg = ChatBubbleClientSetup.config();
        boolean isSystem = cfg == null || !cfg.systemChatAsBubble();
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0), Text.translatable("e33chat.sender.system"),
            message, isSystem, null, false, null));
    }
}
