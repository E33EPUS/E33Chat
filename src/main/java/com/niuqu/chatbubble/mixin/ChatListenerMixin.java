package com.niuqu.chatbubble.mixin;
//#if MC >= 11900
import com.mojang.authlib.GameProfile;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import com.niuqu.chatbubble.chat.MessagePresentation;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.network.message.MessageType;
import net.minecraft.network.message.SignedMessage;
import net.minecraft.text.ClickEvent;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.text.TranslatableTextContent;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import java.util.*;
@Mixin(value = net.minecraft.client.network.message.MessageHandler.class, priority = 500)
public class ChatListenerMixin {
    private static Text extractDecoratedName(Text fullLine, String contentStr,
                                              String rawName, Text fallback) {
        if (contentStr == null || contentStr.isEmpty()) return fallback;
        String fullStr = fullLine.getString();
        int idx = fullStr.lastIndexOf(contentStr);
        if (idx <= 0) return fallback;
        return cleanNameArea(fullLine, 0, idx, rawName, fallback);
    }
    private static Text cleanNameArea(Text fullLine, int a, int b,
                                       String rawName, Text fallback) {
        String fullStr = fullLine.getString();
        while (a < b && Character.isWhitespace(fullStr.charAt(a))) a++;
        while (b > a) {
            char ch = fullStr.charAt(b - 1);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '：' || ch == '»') b--;
            else if (ch == '>' && b >= a + 2 && fullStr.charAt(b - 2) == '>') b -= 2;
            else break;
        }
        if (a >= b) return fallback;
        Text nameArea = ChatMessageStore.sliceStyled(fullLine, a, b);
        String ns = nameArea.getString();
        if (rawName != null && !rawName.isEmpty()) {
            String bracketed = "<" + rawName + ">";
            int p = ns.indexOf(bracketed);
            if (p >= 0) {
                var out = com.niuqu.chatbubble.Txt.empty();
                if (p > 0) out.append(ChatMessageStore.sliceStyled(nameArea, 0, p));
                out.append(ChatMessageStore.sliceStyled(nameArea, p + 1, p + 1 + rawName.length()));
                int tail = p + bracketed.length();
                if (tail < ns.length()) out.append(ChatMessageStore.sliceStyled(nameArea, tail, ns.length()));
                return out;
            }
            if (ns.length() > 2 && ns.charAt(0) == '<' && ns.charAt(ns.length() - 1) == '>')
                return ChatMessageStore.sliceStyled(nameArea, 1, ns.length() - 1);
        }
        return nameArea;
    }
    private static String[] nameCandidates(PlayerListEntry info) {
        var out = new LinkedHashSet<String>();
        //#if MC >= 12109
        String profile = info.getProfile().name();
        //#else
        //$$ String profile = info.getProfile().getName();
        //#endif
        addNameVariants(out, profile);
        var tab = info.getDisplayName();
        if (tab != null) addNameVariants(out, tab.getString().trim());
        return out.toArray(new String[0]);
    }
    private static void addNameVariants(Set<String> out, String name) {
        if (name == null || name.isEmpty()) return;
        out.add(name);
        String stripped = name.replaceAll("§.", "");
        if (!stripped.isEmpty()) out.add(stripped);
    }
    private static boolean isVanillaBroadcast(Text message) {
        if (message.getContent() instanceof TranslatableTextContent tc) {
            String key = tc.getKey();
            return key.startsWith("chat.type.advancement.")
                || key.startsWith("death.")
                || key.startsWith("multiplayer.player.")
                || key.startsWith("commands.")
                || key.equals("chat.type.admin")
                || key.equals("chat.type.announcement")
                || key.equals("chat.type.emote")
                || key.startsWith("chat.type.team.");
        }
        return false;
    }
    private static boolean isXaeroWaypoint(String s) {
        return s.startsWith("xaero-waypoint:")
            || s.startsWith("xaero_waypoint:")
            || s.startsWith("xaero_waypoint_add:");
    }
    private static Text argAsComponent(Object arg) {
        return arg instanceof Text c ? c : com.niuqu.chatbubble.Txt.literal(String.valueOf(arg));
    }
    private static PlayerListEntry findOnlinePlayer(String displayName) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null || displayName.isEmpty()) return null;
        var online = player.networkHandler.getPlayerList();
        for (var info : online) {
            for (String cand : nameCandidates(info))
                if (cand.equals(displayName)) return info;
        }
        PlayerListEntry best = null;
        int bestLen = 0;
        for (var info : online) {
            for (String cand : nameCandidates(info)) {
                if (cand.length() >= 3 && cand.length() > bestLen && displayName.contains(cand)) {
                    best = info;
                    bestLen = cand.length();
                }
            }
        }
        return best;
    }
    private static boolean classifyByKey(Text message) {
        if (!(message.getContent() instanceof TranslatableTextContent tc)) return false;
        String key = tc.getKey();
        Object[] args = tc.getArgs();
        if (key.equals("commands.message.display.incoming") && args.length >= 2) {
            Text name = argAsComponent(args[0]);
            Text content = argAsComponent(args[1]);
            String displayName = name.getString().replaceAll("§.", "").trim();
            var info = findOnlinePlayer(displayName);
            String profile;
            UUID uuid;
            if (info != null) {
	                //#if MC >= 12109
	                profile = info.getProfile().name();
	                uuid = info.getProfile().id();
	                //#else
	                //$$ profile = info.getProfile().getName();
	                //$$ uuid = info.getProfile().getId();
	                //#endif
	            } else {
	                UUID seenUuid = ChatMessageStore.findSeenUuid(displayName);
	                profile = displayName;
	                uuid = seenUuid != null ? seenUuid : new UUID(0, 0);
	            }
	            ChatMessageStore.rememberPlayer(uuid, profile, displayName);
	            ChatMessageStore.debugLog("[e33chat] Key(whisper in) | name=" + profile + " | content='" + content.getString() + "'");
            ChatMessageStore.setPendingMeta(new SenderMeta(uuid, name, content, false, profile, true, profile));
            return true;
        }
        if (key.equals("commands.message.display.outgoing")) {
            if (ChatMessageStore.hasPendingWhisperEcho()) {
                ChatMessageStore.consumeWhisperEcho();
                ChatMessageStore.markSuppressCapture();
                ChatMessageStore.debugLog("[e33chat] Key(whisper echo suppressed)");
                return true;
            }
            var player = MinecraftClient.getInstance().player;
            if (player != null && args.length >= 2) {
                String partner = argAsComponent(args[0]).getString().replaceAll("§.", "").trim();
                Text content = argAsComponent(args[1]);
                String own = player.getName().getString();
                ChatMessageStore.debugLog("[e33chat] Key(whisper out) | partner=" + partner + " | content='" + content.getString() + "'");
                ChatMessageStore.setPendingMeta(new SenderMeta(player.getUuid(),
                    com.niuqu.chatbubble.Txt.literal(own), content, false, own, true, partner));
                return true;
            }
            return false;
        }
        if (key.equals("chat.type.text") && args.length >= 2) {
            Text name = argAsComponent(args[0]);
            Text content = argAsComponent(args[1]);
            String contentStr = content.getString();
            if (isXaeroWaypoint(contentStr)) {
                ChatMessageStore.debugLog("[e33chat] Key(waypoint data) -> system");
                ChatMessageStore.setPendingMeta(new SenderMeta(new UUID(0, 0),
                    com.niuqu.chatbubble.Txt.translatable("e33chat.sender.system"), message, true, null, false, null));
                return true;
            }
            String displayName = name.getString().replaceAll("§.", "").trim();
            var info = findOnlinePlayer(displayName);
            String profile;
            UUID uuid;
            if (info != null) {
	                //#if MC >= 12109
	                profile = info.getProfile().name();
	                uuid = info.getProfile().id();
	                //#else
	                //$$ profile = info.getProfile().getName();
	                //$$ uuid = info.getProfile().getId();
	                //#endif
	            } else {
	                UUID seenUuid = ChatMessageStore.findSeenUuid(displayName);
	                profile = displayName;
	                uuid = seenUuid != null ? seenUuid : new UUID(0, 0);
	            }
	            ChatMessageStore.rememberPlayer(uuid, profile, displayName);
	            ChatMessageStore.debugLog("[e33chat] Key(chat) | name=" + profile + " | display='" + name.getString() + "' | content='" + contentStr + "'");
            ChatMessageStore.setPendingMeta(new SenderMeta(uuid, name, content, false, profile, false, null));
            return true;
        }
        if (isVanillaBroadcast(message)) {
            var cfg = ChatBubbleClientSetup.config();
            boolean isSystem = cfg == null || !cfg.systemChatAsBubble();
            ChatMessageStore.debugLog("[e33chat] Key(broadcast) | key=" + key);
            ChatMessageStore.setPendingMeta(new SenderMeta(new UUID(0, 0),
                com.niuqu.chatbubble.Txt.translatable("e33chat.sender.system"), message, isSystem, null, false, null));
            return true;
        }
        return false;
    }
    private static SenderMeta detectByTellClick(Text message, String text) {
        if (isVanillaBroadcast(message)) return null;
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) return null;
        final int[] pos = {0};
        final int[] range = {-1, -1};
        final String[] tellName = {null};
        final String[] clickedText = {null};
        message.visit((style, str) -> {
            int s = pos[0], e = s + str.length();
            pos[0] = e;
            var click = style.getClickEvent();
	            if (tellName[0] == null && click != null) {
	                //#if MC >= 12105
		                if (click instanceof ClickEvent.SuggestCommand sc
		                    && sc.command() != null) {
		                String cmd = sc.command();
		                //#else
		                //$$ if (click.getAction() == ClickEvent.Action.SUGGEST_COMMAND
		                //$$     && click.getValue() != null) {
		                //$$ String cmd = click.getValue();
	                //#endif
                for (String p : new String[]{"/tell ", "/msg ", "/w ", "/whisper "}) {
                    if (cmd.startsWith(p)) {
                        String n = cmd.substring(p.length()).trim();
                        int sp = n.indexOf(' ');
                        if (sp > 0) n = n.substring(0, sp);
                        if (!n.isEmpty()) {
                            tellName[0] = n;
                            range[0] = s;
                            range[1] = e;
                            clickedText[0] = str;
                        }
                        break;
                    }
                }
                //#if MC >= 12109
                }
                //#else
                //$$ }
                //#endif
            }
            return Optional.empty();
        }, Style.EMPTY);
        int nameRangeLimit = Math.max(32, text.length() / 3);
        if (tellName[0] == null || range[0] > nameRangeLimit) return null;
        PlayerListEntry sender = null;
        for (var info : player.networkHandler.getPlayerList()) {
            //#if MC >= 12109
            String profile = info.getProfile().name();
            //#else
            //$$ String profile = info.getProfile().getName();
            //#endif
            if (profile.equals(tellName[0]) || profile.replaceAll("§.", "").equals(tellName[0])) {
                sender = info;
                break;
            }
        }
        UUID senderUuid = null;
        String profileName = null;
        if (sender != null) {
            //#if MC >= 12109
            senderUuid = sender.getProfile().id();
            profileName = sender.getProfile().name();
            //#else
            //$$ senderUuid = sender.getProfile().getId();
            //$$ profileName = sender.getProfile().getName();
            //#endif
        } else {
            // Fallback to seen players cache
            senderUuid = ChatMessageStore.findSeenUuid(tellName[0]);
            if (senderUuid == null) return null;
            profileName = tellName[0];
        }
        ChatMessageStore.rememberPlayer(senderUuid, profileName, tellName[0]);
        if (sender != null) {
            String clicked = clickedText[0].replaceAll("§.", "").trim();
            boolean clickedIsName = false;
            for (String cand : nameCandidates(sender)) {
                if (!cand.isEmpty() && clicked.contains(cand)) { clickedIsName = true; break; }
            }
            if (!clickedIsName) return null;
        }
        int b = range[1];
        if (b < text.length() && text.charAt(b) == '>') b++;
        int contentStart = b;
        while (contentStart < text.length()) {
            char ch = text.charAt(contentStart);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '：' || ch == '»' || ch == '-') contentStart++;
            else break;
        }
        if (contentStart >= text.length()) return null;
        Text displayName = cleanNameArea(message, 0, b, tellName[0], com.niuqu.chatbubble.Txt.literal(profileName));
        Text content = ChatMessageStore.sliceStyled(message, contentStart, text.length());
        ChatMessageStore.debugLog("[e33chat] System(tell click) | text='" + text + "' | name=" + profileName + " | display='" + displayName.getString() + "' | content='" + content.getString() + "'");
        return new SenderMeta(senderUuid, displayName, content, false, profileName, false, null);
    }
    private static String extractWhisperContent(String fullText, String senderName) {
        if (senderName == null || senderName.isEmpty()) return fullText;
        int idx = fullText.indexOf(senderName);
        if (idx < 0) return fullText;
        String after = fullText.substring(idx + senderName.length());
        for (String sep : new String[]{": ", "：", " :", " ：", " -> ", " >> ", " » ", " | "}) {
            int i = after.indexOf(sep);
            if (i >= 0) return after.substring(i + sep.length());
        }
        return after.trim();
    }
    private static SenderMeta detectWhisperInSystemMessage(String text, String logTag) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) return null;
        // 消息嵌 legacy 色码（S§6t§beve）时整条剥 § 再做名字锚点匹配
        String clean = text.replaceAll("§.", "");
        for (var info : player.networkHandler.getPlayerList()) {
	            //#if MC >= 12109
	            String profile = info.getProfile().name();
	            //#else
	            //$$ String profile = info.getProfile().getName();
	            //#endif
	            for (String cand : nameCandidates(info)) {
	                int idx = clean.indexOf(cand);
	                if (idx >= 0 && idx < 30) {
	                    if (MessagePresentation.hasWhisperKeywordBeforeColon(clean)) {
	                        String content = MessagePresentation.extractWhisperContent(clean, cand);
	                        //#if MC >= 12109
	                        UUID senderId = info.getProfile().id();
	                        //#else
	                        //$$ UUID senderId = info.getProfile().getId();
	                        //#endif
                        ChatMessageStore.debugLog("[e33chat] System(" + logTag + ") | text='" + clean + "' | name=" + cand + " | content='" + content + "'");
                        return new SenderMeta(senderId, com.niuqu.chatbubble.Txt.literal(cand),
                            com.niuqu.chatbubble.Txt.literal(content), false, profile, true, profile);
                    }
                }
            }
        }
        return null;
    }
    @Inject(method = "onChatMessage", at = @At("HEAD"))
    private void onPlayerChat(SignedMessage message, GameProfile gameProfile,
                               MessageType.Parameters params, CallbackInfo ci) {
        //#if MC >= 12109
        UUID senderId = gameProfile.id();
        //#else
        //$$ UUID senderId = gameProfile.getId();
        //#endif
        //#if MC >= 26000
        //$$ Component raw = message.decoratedContent();
        //#else
        Text raw = message.getContent();
        //#endif
        String rawStr = raw.getString();
        if (isXaeroWaypoint(rawStr)) return;
        //#if MC >= 12109
        String name = gameProfile.name();
        //#else
        //$$ String name = gameProfile.getName();
        //#endif
        boolean isWhisper = false;
        String whisperPartner = null;
        //#if MC >= 12005
        //#if MC >= 26000
        //$$ if (params.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
        //#else
        if (params.type().matchesKey(MessageType.MSG_COMMAND_INCOMING)) {
        //#endif
            isWhisper = true;
            whisperPartner = name;
        }
        //#endif
        Text nameText = com.niuqu.chatbubble.Txt.literal(name);
        ChatMessageStore.debugLog("[e33chat] onChatMessage | name=" + name + " | content='" + rawStr + "' | isWhisper=" + isWhisper);
        ChatMessageStore.setPendingMeta(new SenderMeta(senderId, nameText, raw, false, name, isWhisper, whisperPartner));
    }
}
//#else
//$$ public class ChatListenerMixin {
//$$ }
//#endif
