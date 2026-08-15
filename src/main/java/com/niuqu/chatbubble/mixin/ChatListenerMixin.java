package com.niuqu.chatbubble.mixin;

import com.mojang.authlib.GameProfile;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.chat.MessagePresentation;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.store.ChatMessageStore.SenderMeta;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.chat.ChatListener;
import net.minecraft.network.chat.ChatType;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.PlayerChatMessage;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.util.UUID;

@Mixin(value = ChatListener.class, priority = 500)
public class ChatListenerMixin {
    // Whisper keywords live in chat.WhisperSignal (single constant source); the
    // echo check below matches them against the whole line.


    // Pulls styled server prefixes out of the decorated line: "[Group]<Steve> hi" -> "[Group]Steve"
    private static Component extractDecoratedName(Component fullLine, String contentStr,
                                                  String rawName, Component fallback) {
        return com.niuqu.chatbubble.chat.capture.ChatPipeline.extractDecoratedName(fullLine, contentStr, rawName, fallback);
    }


    private static Component cleanNameArea(Component fullLine, int a, int b,
                                           String rawName, Component fallback) {
        return com.niuqu.chatbubble.chat.capture.ChatPipeline.cleanNameArea(fullLine, a, b, rawName, fallback);
    }


    // Nick plugins put the tab-list display name in chat instead of the profile name;
    // legacy plugins may embed section-sign color codes in names, so offer stripped variants too
    private static String[] nameCandidates(net.minecraft.client.multiplayer.PlayerInfo info) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.nameCandidates(info);
    }


    private static void addNameVariants(java.util.Set<String> out, String name) {
        com.niuqu.chatbubble.chat.capture.ChatClassifier.addNameVariants(out, name);
    }


    // Vanilla broadcasts (advancements/deaths/joins) lead with a clickable player name,
    // which tell-click would wrongly claim as chat — keep them as system messages.
    // chat.type.admin is the op echo "[Steve: Teleported ...]",
    // announcement/emote are /say and /me — same trap
    private static boolean isVanillaBroadcast(Component message) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.isVanillaBroadcast(message);
    }


    // ===== Layer 0: deterministic routing by vanilla translation key.=====
    // NCR/FreedomChat stuff the decorated component tree into system packets unchanged,
    // so the key survives conversion. Unknown keys fall through to the heuristics below.

    private static Component argAsComponent(Object arg) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.argAsComponent(arg);
    }


    private static net.minecraft.client.multiplayer.PlayerInfo findOnlinePlayer(String displayName) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.findOnlinePlayer(displayName);
    }


    private static boolean classifyByKey(Component message) {
        return com.niuqu.chatbubble.chat.capture.ChatClassifier.classifyByKey(message);
    }


    // Plugins attach "click to whisper" events to sender names — the command holds the
    // real profile name, giving deterministic attribution even on nickname servers
    private static SenderMeta detectByTellClick(Component message, String text) {
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
    private static SenderMeta matchByTemplate(Component message, String text) {
        return com.niuqu.chatbubble.chat.capture.TemplateLayer.matchByTemplate(message, text);
    }


    // Template-path field slicing: if the captured region contains literal §-codes
    // (some plugins embed raw "§6" text instead of real styles), rebuild it with
    // parseStyledText to render actual colors; otherwise keep the original
    // component slice (preserves real per-run styles like the guards do).
    private static Component templateSlice(Component message, String text, int from, int to) {
        return com.niuqu.chatbubble.chat.capture.TemplateLayer.templateSlice(message, text, from, to);
    }


    @Inject(method = "handlePlayerChatMessage", at = @At("HEAD"))
    private void onPlayerChat(PlayerChatMessage message, GameProfile gameProfile,
                              ChatType.Bound bound, CallbackInfo ci) {
        UUID senderId = gameProfile.getId();
        Component raw = message.decoratedContent();
        String rawStr = raw.getString();
        if (rawStr.startsWith("xaero-waypoint:")
            || rawStr.startsWith("xaero_waypoint:")
            || rawStr.startsWith("xaero_waypoint_add:")) {
            return;
        }

        boolean isWhisper = false;
        boolean isOutgoing = false;
        String whisperPartner = null;
        if (bound.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
            isWhisper = true;
            whisperPartner = gameProfile.getName();
        } else if (bound.chatType().is(ChatType.MSG_COMMAND_OUTGOING)) {
            isWhisper = true;
            isOutgoing = true;
            whisperPartner = bound.targetName().map(Component::getString).orElse(null);
        }
        String name = gameProfile.getName();
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
            Component displayName = extractDecoratedName(raw, cleanContent, name,
                Component.literal((rawStr.substring(0, prefixEnd) + name).trim()));
            Component contentComp = ChatMessageStore.sliceStyled(raw, contentStart, rawStr.length());
            ChatMessageStore.setPendingMeta(new SenderMeta(
                senderId != null ? senderId : new UUID(0, 0),
                displayName,
                contentComp,
                false,
                name,
                isWhisper, whisperPartner
            ));
            return;
        }

        Component playerContent = raw;
        Component senderName = Component.literal(gameProfile.getName());
        if (isWhisper) {
            playerContent = Component.literal(MessagePresentation.extractWhisperContent(rawStr, gameProfile.getName()));
            // The whisper line carries the server-decorated name ("你悄悄地对[称号]X说：")
            // — reuse it so reposts show prefix/team-color like plain chat does. The
            // outgoing echo's name slot holds the TARGET (self is "你"), so fall back
            // to the tab-list display name — same source as the system-channel
            // suppress path, keeping the repost dedup guard's strings identical.
            Component fallback = isOutgoing ? ChatMessageStore.ownDisplayName() : senderName;
            senderName = ChatMessageStore.extractWhisperDisplayName(bound.decorate(raw), fallback);
        } else {
            Component fullLine = bound.decorate(raw);
            senderName = extractDecoratedName(fullLine, rawStr, gameProfile.getName(), senderName);
        }
        // Our own echoes never reach addMessage (echo guard returns early), so cache
        // the decorated name here — the outgoing whisper repost needs it later.
        if (senderId != null && senderId.equals(Minecraft.getInstance().player.getUUID())) {
            ChatMessageStore.cacheOwnDecoratedName(senderName);
        }
        boolean logW = isWhisper; String logWP = whisperPartner; Component logSN = senderName, logPC = playerContent;
        ChatMessageStore.debugLog(() -> "[e33chat] PlayerChat | raw='" + rawStr + "' | whisper=" + logW + " | partner=" + logWP + " | sender='" + logSN.getString() + "' | content='" + logPC.getString() + "'");
        ChatMessageStore.setPendingMeta(new SenderMeta(
            senderId != null ? senderId : new UUID(0, 0),
            senderName,
            playerContent,
            false,
            gameProfile.getName(),
            isWhisper, whisperPartner
        ));
    }

    @Inject(method = "handleDisguisedChatMessage", at = @At("HEAD"))
    private void onDisguisedChat(Component message, ChatType.Bound bound, CallbackInfo ci) {
        String msgStr = message.getString();
        if (msgStr.startsWith("xaero-waypoint:")
            || msgStr.startsWith("xaero_waypoint:")
            || msgStr.startsWith("xaero_waypoint_add:")) {
            return;
        }
        boolean hasSender = bound.name() != null;

        boolean isWhisper = false;
        boolean isOutgoing = false;
        String whisperPartner = null;
        if (bound.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
            isWhisper = true;
            whisperPartner = hasSender ? bound.name().getString() : null;
        } else if (bound.chatType().is(ChatType.MSG_COMMAND_OUTGOING)) {
            isWhisper = true;
            isOutgoing = true;
            whisperPartner = bound.targetName().map(Component::getString).orElse(null);
        }

        // NCR fallback: keyword-based whisper detection for servers that strip chat type
        if (!isWhisper) {
            SenderMeta wm = detectWhisperInSystemMessage(msgStr, "disguised");
            if (wm != null) { ChatMessageStore.setPendingMeta(wm); return; }
        }

        Component disContent = message;
        Component disSender = hasSender ? bound.name() : Component.translatable("e33chat.sender.system");
        if (isWhisper && hasSender) {
            disContent = Component.literal(MessagePresentation.extractWhisperContent(msgStr, bound.name().getString()));
            // outgoing disguised echo: bound.name() is the TARGET — use our own
            // display name, matching the signed/system-channel repost paths
            Component fallback = isOutgoing ? ChatMessageStore.ownDisplayName() : disSender;
            disSender = ChatMessageStore.extractWhisperDisplayName(message, fallback);
        } else if (hasSender) {
            Component fullLine = bound.decorate(message);
            disSender = extractDecoratedName(fullLine, msgStr, bound.name().getString(), disSender);
        }
        boolean logWD = isWhisper; String logWPD = whisperPartner; Component logSND = disSender, logDCD = disContent;
        ChatMessageStore.debugLog(() -> "[e33chat] Disguised | raw='" + msgStr + "' | whisper=" + logWD + " | partner=" + logWPD + " | sender='" + logSND.getString() + "' | content='" + logDCD.getString() + "'");
        if (hasSender) {
            ChatMessageStore.setPendingMeta(new SenderMeta(
                new UUID(0, 0),
                disSender,
                disContent,
                false,
                bound.name().getString(),
                isWhisper, whisperPartner
            ));
            return;
        }

        // bound.name() empty: try tell-click first (structural), then text parsing
        var connection = Minecraft.getInstance().player.connection;

        // Layer 2: tell-click attribution — structural detection before text parsing
        SenderMeta tc = detectByTellClick(message, msgStr);
        if (tc != null) { ChatMessageStore.setPendingMeta(tc); return; }

        // Layer 3: parse decorated player line — text-level fallback
        if (connection != null && !isWhisper) {
            SenderMeta parsed = com.niuqu.chatbubble.chat.capture.ChatPipeline.tryParsePlayerLine(message, msgStr, "Disguised");
            if (parsed != null) { ChatMessageStore.setPendingMeta(parsed); return; }
        }

        // 守卫全未命中 → 灰字兜底（系统消息）
        ChatMessageStore.debugLog(() -> "[e33chat] Disguised(guard fallback -> gray) | text='" + msgStr + "'");
        boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE.get();
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0),
            Component.translatable("e33chat.sender.system"),
            message,
            isSystem,
            null,
            false, null
        ));
    }

    @Inject(method = "handleSystemMessage", at = @At("HEAD"))
    private void onSystemChat(Component message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;

        // Layer 0: known translation keys route deterministically; keyword echo check
        // stays below it so a partner's reply can never be eaten as our own echo
        if (classifyByKey(message)) return;

        String sysText = message.getString();
        // Suppress outgoing whisper echo (text path; the key path lives in
        // ChatClassifier.classifyByKey)
        if (com.niuqu.chatbubble.chat.capture.EchoSuppressor.trySuppressOutgoingEcho(sysText)) return;
        ChatMessageStore.debugLog(() -> "[e33chat] System | text='" + sysText + "' | overlay=" + overlay);

        String text = message.getString();
        var connection = Minecraft.getInstance().player.connection;

        // Template layer: server-declared formats parse exactly (strongest evidence).
        // Unconfigured or unmatched lines fall through to the heuristic guards below.
        if ((!ChatMessageStore.serverChatTemplates().isEmpty()
                || !ChatMessageStore.serverWhisperTemplates().isEmpty()) && connection != null) {
            SenderMeta tpl = matchByTemplate(message, text);
            if (tpl != null) { ChatMessageStore.setPendingMeta(tpl); return; }
        }

        // Layer 1: whisper detection FIRST — before name matching can steal it as public chat
        SenderMeta wm = detectWhisperInSystemMessage(text, "whisper");
        if (wm != null) { ChatMessageStore.setPendingMeta(wm); return; }

        // Layer 2: tell-click attribution — structural, catches NCR messages with
        // click events deterministically (before text parsing can misidentify broadcasts)
        SenderMeta tc = detectByTellClick(message, text);
        if (tc != null) { ChatMessageStore.setPendingMeta(tc); return; }

        // Layer 3: parse decorated player line — text-level fallback for servers
        // that strip click events from chat messages
        if (connection != null) {
            SenderMeta parsed = com.niuqu.chatbubble.chat.capture.ChatPipeline.tryParsePlayerLine(message, text, "System");
            if (parsed != null) { ChatMessageStore.setPendingMeta(parsed); return; }
        }

        // Fallback: real system message（模板 miss + 守卫1/2/3 全未命中 → 灰字兜底）
        ChatMessageStore.debugLog(() -> "[e33chat] System(guard fallback -> gray) | text='" + text + "'");
        boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE.get();
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0),
            Component.translatable("e33chat.sender.system"),
            message,
            isSystem,
            null,
            false, null
        ));
    }
}
