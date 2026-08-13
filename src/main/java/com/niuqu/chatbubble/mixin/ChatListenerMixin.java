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
    // 与 MessagePresentation.hasWhisperKeywordBeforeColon 检测词表对齐：缺词会让 "PM to X: hi" 式
    // 出站回显不触发抑制 → 误判入站私聊，且 pendingEcho 残留 → 10s 内 partner 真回复被当 echo 吞
    // 裸 "to you" 与 MessagePresentation 同步排除——tpa "wants to teleport to you" 会误判为回显
    private static final java.util.regex.Pattern ECHO_WHISPER_PATTERN =
        java.util.regex.Pattern.compile("\b(?:pm|message|msg|tell)\b");


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
                var out = Text.empty();
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
        // Fallback: when displayName is null, construct from team prefix + name + suffix
        if (tab == null) {
            var team = info.getScoreboardTeam();
            if (team != null) {
                StringBuilder sb = new StringBuilder();
                //#if MC >= 12004
                Text pfx = team.getPrefix();
                Text sfx = team.getSuffix();
                //#else
                //$$ Text pfx = null, sfx = null;
                //$$ if (team instanceof net.minecraft.scoreboard.Team) {
                //$$     net.minecraft.scoreboard.Team t = (net.minecraft.scoreboard.Team) team;
                //$$     pfx = t.getPrefix();
                //$$     sfx = t.getSuffix();
                //$$ }
                //#endif
                if (pfx != null) sb.append(pfx.getString());
                sb.append(profile);
                if (sfx != null) sb.append(sfx.getString());
                String decorated = sb.toString().trim();
                if (!decorated.isEmpty() && !decorated.equals(profile)) {
                    addNameVariants(out, decorated);
                }
            }
        }
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
        return arg instanceof Text c ? c : Text.literal(String.valueOf(arg));
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
                    Text.literal(own), content, false, own, true, partner));
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
                    Text.translatable("e33chat.sender.system"), message, true, null, false, null));
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
                Text.translatable("e33chat.sender.system"), message, isSystem, null, false, null));
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

        Text displayName = cleanNameArea(message, 0, b, tellName[0], Text.literal(profileName));
        Text content = ChatMessageStore.sliceStyled(message, contentStart, text.length());
        ChatMessageStore.debugLog("[e33chat] System(tell click) | text='" + text + "' | name=" + profileName + " | display='" + displayName.getString() + "' | content='" + content.getString() + "'");
        return new SenderMeta(senderUuid, displayName, content, false, profileName, false, null);
    }

    private static String extractWhisperContent(String fullText, String senderName) {
        if (senderName == null || senderName.isEmpty()) return fullText;
        int idx = fullText.indexOf(senderName);
        if (idx < 0) return fullText;
        String after = fullText.substring(idx + senderName.length());
        for (String sep : new String[]{": ", "：", " :", " ："}) {
            int i = after.lastIndexOf(sep);
            if (i >= 0) return after.substring(i + sep.length());
        }
        return after.trim();
    }

    private static SenderMeta detectWhisperInSystemMessage(String text, String logTag) {
        var player = MinecraftClient.getInstance().player;
        if (player == null || player.networkHandler == null) return null;
        // G3: 消息嵌 legacy 色码（S§6t§beve）时整条剥 § 再做名字锚点匹配
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
                        String content = extractWhisperContent(clean, cand);
                        //#if MC >= 12109
                        UUID senderId = info.getProfile().id();
                        //#else
                        //$$ UUID senderId = info.getProfile().getId();
                        //#endif
                        ChatMessageStore.debugLog("[e33chat] System(" + logTag + ") | text='" + clean + "' | name=" + cand + " | content='" + content + "'");
                        return new SenderMeta(senderId, Text.literal(cand),
                            Text.literal(content), false, profile, true, profile);
                    }
                }
            }
        }
        return null;
    }

    private static long templateMissWindowStart;
    private static int templateMissBurst;

    private static void logTemplateMiss(String text) {
        if (!ChatMessageStore.serverTemplateDebug()) return;
        long now = System.currentTimeMillis();
        if (now - templateMissWindowStart >= 60_000) {
            templateMissWindowStart = now;
            templateMissBurst = 0;
        }
        if (++templateMissBurst > 5) return;
        String s = text.length() <= 100 ? text : text.substring(0, 100) + "…";
        // G4: 诊断信息含已配置模板列表（原始串），方便核对模板是否写错/漏配
        StringBuilder tpl = new StringBuilder();
        for (var t : ChatMessageStore.serverChatTemplates()) tpl.append("\n  chat: ").append(t.raw());
        for (var t : ChatMessageStore.serverWhisperTemplates()) tpl.append("\n  whisper: ").append(t.raw());
        ChatMessageStore.debugLog(() -> "[e33chat] System(template miss) | text='" + s + "' | templates=" + tpl);
    }

    private static boolean isTemplateNameKnown(String name) {
        if (name == null || name.isEmpty()) return false;
        var player = net.minecraft.client.MinecraftClient.getInstance().player;
        if (player != null) {
            String myName = player.getName().getString();
            if (!myName.isEmpty() && (name.equals(myName) || name.contains(myName))) return true;
        }
        return findOnlinePlayer(name) != null || ChatMessageStore.findSeenUuid(name) != null;
    }

    // Server template parse: exact field split with style-preserving offsets.
    // Returns null on no match (fall back to the guards) or when the line is our
    // own echo (already bubbled via the authoritative player channel / suppressed).
    private static SenderMeta matchByTemplate(Text message, String text) {
        var r = com.niuqu.chatbubble.chat.TemplateMatcher.match(text, ChatMessageStore.serverChatTemplates(),
            ChatMessageStore.serverWhisperTemplates(), ChatListenerMixin::isTemplateNameKnown);
        if (r.isEmpty()) {
            logTemplateMiss(text);
            return null;
        }
        var tpl = r.orElseThrow();
        String verified = tpl.verifiedName();
        var info = findOnlinePlayer(verified);
        UUID uid = info != null ?
            //#if MC >= 12109
            info.getProfile().id() : ChatMessageStore.findSeenUuid(verified);
            //#else
            //$$ info.getProfile().getId() : ChatMessageStore.findSeenUuid(verified);
            //#endif
        String rawName = info != null ?
            //#if MC >= 12109
            info.getProfile().name() : verified;
            //#else
            //$$ info.getProfile().getName() : verified;
            //#endif
        var mc = net.minecraft.client.MinecraftClient.getInstance();
        boolean isSelf = uid != null && mc.player != null && uid.equals(mc.player.getUuid());
        if (isSelf) {
            if (tpl.whisper()) {
                // outgoing whisper echo — never bubble a second copy; the suppress
                // flag absorbs it when the pipeline reaches addMessage
                ChatMessageStore.markSuppressCapture();
                ChatMessageStore.debugLog(() -> "[e33chat] System(template outgoing whisper) | text='" + text + "'");
                return null;
            }
            // own public echo: the authoritative player channel already bubbled it;
            // keep the decorated name for repost/echo rendering
            ChatMessageStore.cacheOwnDecoratedName(
                templateSlice(message, text, tpl.nameStart(), tpl.nameEnd()));
            ChatMessageStore.debugLog(() -> "[e33chat] System(template own line) | text='" + text + "'");
            return null;
        }
        Text nameComp = templateSlice(message, text, tpl.nameStart(), tpl.nameEnd());
        Text contentComp = templateSlice(message, text, tpl.contentStart(), tpl.contentEnd());
        boolean whisper = tpl.whisper();
        String partner = whisper ? tpl.sender() : null;
        ChatMessageStore.debugLog(() -> "[e33chat] System(template) | text='" + text + "' | name='" + nameComp.getString() + "' | whisper=" + whisper + " | partner=" + partner + " | content='" + contentComp.getString() + "'");
        return new SenderMeta(uid != null ? uid : new UUID(0, 0), nameComp, contentComp,
            false, rawName, whisper, partner);
    }

    // Template-path field slicing: if the captured region contains literal §-codes
    // (some plugins embed raw "§6" text instead of real styles), rebuild it with
    // parseStyledText to render actual colors; otherwise keep the original
    // text slice (preserves real per-run styles like the guards do).
    private static Text templateSlice(Text message, String text, int from, int to) {
        String sub = text.substring(from, to);
        if (sub.indexOf('§') >= 0) return ChatMessageStore.parseStyledText(sub);
        return ChatMessageStore.sliceStyled(message, from, to);
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
        //$$ Text raw = message.decoratedContent();
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
        boolean isOutgoing = false;
        String whisperPartner = null;
        //#if MC >= 12005
        //#if MC >= 26000
        //$$ if (params.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
        //#else
        if (params.type().matchesKey(MessageType.MSG_COMMAND_INCOMING)) {
        //#endif
            isWhisper = true;
            whisperPartner = name;
        //#if MC >= 26000
        //$$ } else if (params.chatType().is(ChatType.MSG_COMMAND_OUTGOING)) {
        //#else
        } else if (params.type().matchesKey(MessageType.MSG_COMMAND_OUTGOING)) {
        //#endif
            isWhisper = true;
            isOutgoing = true;
            whisperPartner = params.targetName().map(Text::getString).orElse(null);
        }
        //#endif

        // Try to extract content from "<name> " pattern for proper decoration stripping
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

        // Fallback: use full line
        Text playerContent = raw;
        Text senderName = Text.literal(name);
        if (isWhisper) {
            playerContent = Text.literal(MessagePresentation.extractWhisperContent(rawStr, name));
            // The whisper line carries the server-decorated name ("你悄悄地对[称号]X说：")
            // — reuse it so reposts show prefix/team-color like plain chat does. The
            // outgoing echo's name slot holds the TARGET (self is "你"), so fall back
            // to the tab-list display name — same source as the system-channel
            // suppress path, keeping the repost dedup guard's strings identical.
            Text fallback = isOutgoing ? ChatMessageStore.ownDisplayName() : senderName;
            //#if MC >= 26000
            //$$ // 26.x: applyChatDecoration removed; use params.name() as fallback
            //$$ Text paramName = params.name();
            //$$ senderName = paramName != null ? ChatMessageStore.extractWhisperDisplayName(raw, paramName) : fallback;
            //#else
            senderName = ChatMessageStore.extractWhisperDisplayName(params.applyChatDecoration(raw), fallback);
            //#endif
        } else {
            //#if MC >= 26000
            //$$ // 26.x: applyChatDecoration removed; use params.name() directly
            //$$ Text paramName = params.name();
            //$$ senderName = paramName != null ? paramName : senderName;
            //#else
            Text fullLine = params.applyChatDecoration(raw);
            senderName = extractDecoratedName(fullLine, rawStr, name, senderName);
            //#endif
        }
        // Our own echoes never reach addMessage (echo guard returns early), so cache
        // the decorated name here — the outgoing whisper repost needs it later.
        if (senderId != null && senderId.equals(net.minecraft.client.MinecraftClient.getInstance().player.getUuid())) {
            ChatMessageStore.cacheOwnDecoratedName(senderName);
        }
        ChatMessageStore.debugLog("[e33chat] PlayerChat | raw='" + rawStr + "' | sender='" + senderName.getString() + "' | content='" + playerContent.getString() + "'");
        ChatMessageStore.setPendingMeta(new SenderMeta(
            senderId != null ? senderId : new UUID(0, 0),
            senderName, playerContent, false, name, isWhisper, whisperPartner));
    }

    //#if MC >= 11902
    @Inject(method = "onProfilelessMessage", at = @At("HEAD"))
    private void onDisguisedChat(Text content, MessageType.Parameters params, CallbackInfo ci) {
        String msgStr = content.getString();
        if (isXaeroWaypoint(msgStr)) return;
        boolean hasSender = params.name() != null;

        boolean isWhisper = false;
        boolean isOutgoing = false;
        String whisperPartner = null;
        //#if MC >= 12005
        //#if MC >= 26000
        //$$ if (params.chatType().is(ChatType.MSG_COMMAND_INCOMING)) {
        //#else
        if (params.type().matchesKey(MessageType.MSG_COMMAND_INCOMING)) {
        //#endif
            isWhisper = true;
            whisperPartner = hasSender ? params.name().getString() : null;
        //#if MC >= 26000
        //$$ } else if (params.chatType().is(ChatType.MSG_COMMAND_OUTGOING)) {
        //#else
        } else if (params.type().matchesKey(MessageType.MSG_COMMAND_OUTGOING)) {
        //#endif
            isWhisper = true;
            isOutgoing = true;
            whisperPartner = params.targetName().map(Text::getString).orElse(null);
        }
        //#endif

        // NCR fallback: keyword-based whisper detection for servers that strip chat type
        if (!isWhisper) {
            SenderMeta wm = detectWhisperInSystemMessage(msgStr, "disguised");
            if (wm != null) { ChatMessageStore.setPendingMeta(wm); return; }
        }

        if (hasSender) {
            Text disContent = content;
            Text disSender = params.name();
            if (isWhisper) {
                disContent = Text.literal(extractWhisperContent(msgStr, params.name().getString()));
                // outgoing disguised echo: params.name() is the TARGET — use our own
                // display name, matching the signed/system-channel repost paths
                Text fallback = isOutgoing ? ChatMessageStore.ownDisplayName() : disSender;
                disSender = ChatMessageStore.extractWhisperDisplayName(content, fallback);
            } else {
                //#if MC >= 26000
                //$$ // 26.x: applyChatDecoration removed; keep params.name() as sender display name
                //#else
                Text fullLine = params.applyChatDecoration(content);
                disSender = extractDecoratedName(fullLine, msgStr, params.name().getString(), disSender);
                //#endif
            }
            ChatMessageStore.debugLog("[e33chat] Disguised | raw='" + msgStr + "' | whisper=" + isWhisper + " | partner=" + whisperPartner + " | sender='" + disSender.getString() + "' | content='" + disContent.getString() + "'");
            ChatMessageStore.setPendingMeta(new SenderMeta(
                new UUID(0, 0), disSender, disContent, false,
                params.name().getString(), isWhisper, whisperPartner));
            return;
        }

        // bound.name() empty — try text heuristics
        var connection = MinecraftClient.getInstance().player != null
            ? MinecraftClient.getInstance().player.networkHandler : null;
        if (connection != null && !isWhisper) {
            var onlineNames = connection.getPlayerList().stream()
                .flatMap(info -> {
                    var names = new ArrayList<String>();
                    for (String cand : nameCandidates(info)) names.add(cand);
                    return names.stream();
                }).distinct().toList();
            var parsed = MessagePresentation.parseDecoratedPlayerLine(msgStr, onlineNames);
            if (parsed.isPresent()) {
                var pl = parsed.orElseThrow();
                var info = connection.getPlayerList().stream()
                    .filter(i -> {
                        for (String cand : nameCandidates(i))
                            if (cand.equals(pl.playerName())) return true;
                        return false;
                    }).findFirst().orElse(null);
                UUID uid = info != null ?
                    //#if MC >= 12109
                    info.getProfile().id() : new UUID(0, 0);
                    //#else
                    //$$ info.getProfile().getId() : new UUID(0, 0);
                    //#endif
                // 偏移来自 parser（双侧剥 § 后的映射），嵌色名 S§6t§beve 也正确
                int nameIdx = pl.nameStart();
                int cStart = pl.contentStart();
                Text displayName = extractDecoratedName(content, pl.content(), pl.playerName(),
                    Text.literal((msgStr.substring(0, nameIdx) + pl.playerName()).trim()));
                Text contentComp = ChatMessageStore.sliceStyled(content, cStart, msgStr.length());
                ChatMessageStore.debugLog("[e33chat] Disguised(player line) | name=" + pl.playerName() + " | content='" + pl.content() + "'");
                ChatMessageStore.setPendingMeta(new SenderMeta(
                    uid, displayName, contentComp, false,
                    info != null ?
                        //#if MC >= 12109
                        info.getProfile().name() : pl.playerName(),
                        //#else
                        //$$ info.getProfile().getName() : pl.playerName(),
                        //#endif
                    false, null));
                return;
            }
        }

        SenderMeta tc = detectByTellClick(content, msgStr);
        if (tc != null) { ChatMessageStore.setPendingMeta(tc); return; }

        // 守卫全未命中 → 灰字兜底（系统消息）
        ChatMessageStore.debugLog("[e33chat] Disguised(guard fallback -> gray) | text='" + msgStr + "'");
        var cfg = ChatBubbleClientSetup.config();
        boolean isSystem = cfg == null || !cfg.systemChatAsBubble();
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0), Text.translatable("e33chat.sender.system"),
            content, isSystem, null, false, null));
    }
    //#endif

    @Inject(method = "onGameMessage", at = @At("HEAD"))
    private void onSystemChat(Text message, boolean overlay, CallbackInfo ci) {
        if (overlay) return;

        if (classifyByKey(message)) return;

        String sysText = message.getString();
        boolean hasEchoFlag = ChatMessageStore.hasPendingWhisperEcho();
        boolean hasKw = sysText.contains("悄悄") || sysText.contains("whispers") || sysText.contains("whisper")
            || sysText.contains("私聊") || sysText.contains("密语") || sysText.contains("密聊")
            || sysText.contains("私信") || sysText.contains("密谈")
            || sysText.contains("对你说")
            || ECHO_WHISPER_PATTERN.matcher(sysText.toLowerCase()).find();
        ChatMessageStore.debugLog("[e33chat] System(echo check) | text='" + sysText + "' | flag=" + hasEchoFlag + " | kw=" + hasKw);
        if (hasEchoFlag && hasKw) {
            boolean otherPlayerInText = false;
            var conn = MinecraftClient.getInstance().player != null
                ? MinecraftClient.getInstance().player.networkHandler : null;
            if (conn != null) {
                String localName = MinecraftClient.getInstance().player.getName().getString();
                for (var info : conn.getPlayerList()) {
                    //#if MC >= 12109
                    String name = info.getProfile().name();
                    //#else
                    //$$ String name = info.getProfile().getName();
                    //#endif
                    if (name != null && !name.equals(localName) && sysText.contains(name)) {
                        otherPlayerInText = true;
                        break;
                    }
                }
            }
            if (!otherPlayerInText) {
                ChatMessageStore.consumeWhisperEcho();
                ChatMessageStore.debugLog("[e33chat] System(echo suppressed) | text='" + sysText + "'");
                ChatMessageStore.markSuppressCapture();
                return;
            }
        }
        ChatMessageStore.debugLog("[e33chat] System | text='" + sysText + "' | overlay=" + overlay);

        String text = message.getString();
        var connection = MinecraftClient.getInstance().player != null
            ? MinecraftClient.getInstance().player.networkHandler : null;

        // Template layer: server-declared formats parse exactly (strongest evidence).
        // Unconfigured or unmatched lines fall through to the heuristic guards below.
        if ((!ChatMessageStore.serverChatTemplates().isEmpty()
                || !ChatMessageStore.serverWhisperTemplates().isEmpty()) && connection != null) {
            SenderMeta tpl = matchByTemplate(message, text);
            if (tpl != null) { ChatMessageStore.setPendingMeta(tpl); return; }
        }

        // Layer 1: whisper detection FIRST — before name matching can steal it
        SenderMeta wm = detectWhisperInSystemMessage(text, "whisper");
        if (wm != null) { ChatMessageStore.setPendingMeta(wm); return; }

        // Layer 2: parse decorated player line (NCR/plugin plain-text player chat)
        if (connection != null) {
            var namesSet = new LinkedHashSet<String>();
            connection.getPlayerList().forEach(info -> {
                for (String cand : nameCandidates(info)) namesSet.add(cand);
            });
            namesSet.addAll(ChatMessageStore.knownNameVariants());
            var onlineNames = new ArrayList<>(namesSet);
            var parsed = MessagePresentation.parseDecoratedPlayerLine(text, onlineNames);
            if (parsed.isPresent()) {
                var pl = parsed.orElseThrow();
                var info = connection.getPlayerList().stream()
                    .filter(i -> {
                        for (String cand : nameCandidates(i))
                            if (cand.equals(pl.playerName())) return true;
                        return false;
                    }).findFirst().orElse(null);
                UUID uid = info != null ?
                    //#if MC >= 12109
                    info.getProfile().id() : new UUID(0, 0);
                    //#else
                    //$$ info.getProfile().getId() : new UUID(0, 0);
                    //#endif
                // 偏移来自 parser（双侧剥 § 后的映射），嵌色名 S§6t§beve 也正确
                int nameIdx = pl.nameStart();
                int cStart = pl.contentStart();
                if (MessagePresentation.isWhitespaceOnlyGap(text, nameIdx + pl.playerName().length(), cStart)) {
                    ChatMessageStore.debugLog("[e33chat] System(line skip: broadcast sentence) | text='" + text + "'");
                } else {
                    Text displayName = extractDecoratedName(message, pl.content(), pl.playerName(),
                        Text.literal((text.substring(0, nameIdx) + pl.playerName()).trim()));
                    Text contentComp = ChatMessageStore.sliceStyled(message, cStart, text.length());
                    ChatMessageStore.debugLog("[e33chat] System(player line) | name=" + pl.playerName() + " | content='" + pl.content() + "'");
                    ChatMessageStore.setPendingMeta(new SenderMeta(
                        uid, displayName, contentComp, false,
                        info != null ?
                            //#if MC >= 12109
                            info.getProfile().name() : pl.playerName(),
                            //#else
                            //$$ info.getProfile().getName() : pl.playerName(),
                            //#endif
                        false, null));
                }
                return;
            }
        }

        // Layer 3: tell-click attribution (nickname servers)
        SenderMeta tc = detectByTellClick(message, text);
        if (tc != null) { ChatMessageStore.setPendingMeta(tc); return; }

        // Fallback: real system message（模板 miss + 守卫1/2/3 全未命中 → 灰字兜底）
        ChatMessageStore.debugLog("[e33chat] System(guard fallback -> gray) | text='" + text + "'");
        var cfg = ChatBubbleClientSetup.config();
        boolean isSystem = cfg == null || !cfg.systemChatAsBubble();
        ChatMessageStore.setPendingMeta(new SenderMeta(
            new UUID(0, 0), Text.translatable("e33chat.sender.system"),
            message, isSystem, null, false, null));
    }
}
//#else
//$$ public class ChatListenerMixin {
//$$ }
//#endif