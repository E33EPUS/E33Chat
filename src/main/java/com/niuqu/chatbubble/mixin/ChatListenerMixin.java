package com.niuqu.chatbubble.mixin;

import com.mojang.authlib.GameProfile;
import com.niuqu.chatbubble.ChatBubbleConfig;
import com.niuqu.chatbubble.ChatMessageStore;
import com.niuqu.chatbubble.ChatMessageStore.SenderMeta;
import com.niuqu.chatbubble.chat.MessagePresentation;
import com.niuqu.chatbubble.chat.TemplateMatcher;
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
    // 与 MessagePresentation.hasWhisperKeywordBeforeColon 检测词表对齐：缺词会让 "PM to X: hi" 式
    // 出站回显不触发抑制 → 误判入站私聊，且 pendingEcho 残留 → 10s 内 partner 真回复被当 echo 吞
    // 裸 "to you" 与 MessagePresentation 同步排除——tpa "wants to teleport to you" 会误判为回显
    private static final java.util.regex.Pattern ECHO_WHISPER_PATTERN =
        java.util.regex.Pattern.compile("\b(?:pm|message|msg|tell)\b");



    // Pulls styled server prefixes out of the decorated line: "[Group]<Steve> hi" -> "[Group]Steve"
    private static Component extractDecoratedName(Component fullLine, String contentStr,
                                                  String rawName, Component fallback) {
        if (contentStr == null || contentStr.isEmpty()) return fallback;
        String fullStr = fullLine.getString();
        int idx = fullStr.lastIndexOf(contentStr);
        if (idx <= 0) return fallback;
        return cleanNameArea(fullLine, 0, idx, rawName, fallback);
    }

    private static Component cleanNameArea(Component fullLine, int a, int b,
                                           String rawName, Component fallback) {
        String fullStr = fullLine.getString();
        while (a < b && Character.isWhitespace(fullStr.charAt(a))) a++;
        while (b > a) {
            char ch = fullStr.charAt(b - 1);
            if (Character.isWhitespace(ch) || ch == ':' || ch == '：' || ch == '»') b--;
            else if (ch == '>' && b >= a + 2 && fullStr.charAt(b - 2) == '>') b -= 2;
            else break;
        }
        if (a >= b) return fallback;
        Component nameArea = ChatMessageStore.sliceStyled(fullLine, a, b);
        String ns = nameArea.getString();
        if (rawName != null && !rawName.isEmpty()) {
            String bracketed = "<" + rawName + ">";
            int p = ns.indexOf(bracketed);
            if (p >= 0) {
                var out = Component.empty();
                if (p > 0) out.append(ChatMessageStore.sliceStyled(nameArea, 0, p));
                out.append(ChatMessageStore.sliceStyled(nameArea, p + 1, p + 1 + rawName.length()));
                int tail = p + bracketed.length();
                if (tail < ns.length()) out.append(ChatMessageStore.sliceStyled(nameArea, tail, ns.length()));
                return out;
            }
            // Team-decorated names sit inside the brackets: "<[Team]Steve>" -> "[Team]Steve"
            if (ns.length() > 2 && ns.charAt(0) == '<' && ns.charAt(ns.length() - 1) == '>') {
                return ChatMessageStore.sliceStyled(nameArea, 1, ns.length() - 1);
            }
        }
        return nameArea;
    }

    // Nick plugins put the tab-list display name in chat instead of the profile name;
    // legacy plugins may embed section-sign color codes in names, so offer stripped variants too
    private static String[] nameCandidates(net.minecraft.client.multiplayer.PlayerInfo info) {
        var out = new java.util.LinkedHashSet<String>();
        String profile = info.getProfile().getName();
        addNameVariants(out, profile);
        var tab = info.getTabListDisplayName();
        if (tab != null) addNameVariants(out, tab.getString().trim());
        return out.toArray(new String[0]);
    }

    private static void addNameVariants(java.util.Set<String> out, String name) {
        if (name == null || name.isEmpty()) return;
        out.add(name);
        String stripped = name.replaceAll("§.", "");
        if (!stripped.isEmpty()) out.add(stripped);
    }

    // Vanilla broadcasts (advancements/deaths/joins) lead with a clickable player name,
    // which tell-click would wrongly claim as chat — keep them as system messages.
    // chat.type.admin is the op echo "[Steve: Teleported ...]",
    // announcement/emote are /say and /me — same trap
    private static boolean isVanillaBroadcast(Component message) {
        if (message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc) {
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

    // ===== Layer 0: deterministic routing by vanilla translation key.=====
    // NCR/FreedomChat stuff the decorated component tree into system packets unchanged,
    // so the key survives conversion. Unknown keys fall through to the heuristics below.

    private static Component argAsComponent(Object arg) {
        return arg instanceof Component c ? c : Component.literal(String.valueOf(arg));
    }

    private static net.minecraft.client.multiplayer.PlayerInfo findOnlinePlayer(String displayName) {
        var player = Minecraft.getInstance().player;
        if (player == null || player.connection == null || displayName.isEmpty()) return null;
        var online = player.connection.getOnlinePlayers();
        for (var info : online) {
            for (String cand : nameCandidates(info)) {
                if (cand.equals(displayName)) return info;
            }
        }
        // Team/plugin decorations wrap the name ("[Title]Steve") — longest match wins
        // so "Steve2" is never claimed by "Steve". Min length 3 keeps 1-2 char names
        // from substring-matching random text when the real sender is offline
        net.minecraft.client.multiplayer.PlayerInfo best = null;
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

    private static boolean classifyByKey(Component message) {
        if (!(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc)) return false;
        String key = tc.getKey();
        Object[] args = tc.getArgs();

        if (key.equals("commands.message.display.incoming") && args.length >= 2) {
            Component name = argAsComponent(args[0]);
            Component content = argAsComponent(args[1]);
            String displayName = name.getString().replaceAll("§.", "").trim();
            var info = findOnlinePlayer(displayName);
            String profile = info != null ? info.getProfile().getName() : displayName;
            UUID uuid = info != null ? info.getProfile().getId() : new UUID(0, 0);
            ChatMessageStore.debugLog(() -> "[e33chat] Key(whisper in) | name=" + profile + " | content='" + content.getString() + "'");
            ChatMessageStore.setPendingMeta(new SenderMeta(uuid, name, content, false, profile, true, profile));
            return true;
        }

        if (key.equals("commands.message.display.outgoing")) {
            if (ChatMessageStore.hasPendingWhisperEcho()) {
                ChatMessageStore.consumeWhisperEcho();
                ChatMessageStore.markSuppressCapture();
                ChatMessageStore.debugLog(() -> "[e33chat] Key(whisper echo suppressed)");
                return true;
            }
            var player = Minecraft.getInstance().player;
            if (player != null && args.length >= 2) {
                // /msg sent outside our UI (another mod, key bind) — no local bubble exists
                String partner = argAsComponent(args[0]).getString().replaceAll("§.", "").trim();
                Component content = argAsComponent(args[1]);
                String own = player.getName().getString();
                ChatMessageStore.debugLog(() -> "[e33chat] Key(whisper out) | partner=" + partner + " | content='" + content.getString() + "'");
                ChatMessageStore.setPendingMeta(new SenderMeta(player.getUUID(),
                    Component.literal(own), content, false, own, true, partner));
                return true;
            }
            return false;
        }

        if (key.equals("chat.type.text") && args.length >= 2) {
            Component name = argAsComponent(args[0]);
            Component content = argAsComponent(args[1]);
            String contentStr = content.getString();
            // Xaero shares waypoint data as chat — converted servers wrap it in chat.type.text
            if (contentStr.startsWith("xaero-waypoint:")
                || contentStr.startsWith("xaero_waypoint:")
                || contentStr.startsWith("xaero_waypoint_add:")) {
                ChatMessageStore.debugLog(() -> "[e33chat] Key(waypoint data) -> system");
                ChatMessageStore.setPendingMeta(new SenderMeta(new UUID(0, 0),
                    Component.translatable("e33chat.sender.system"), message, true, null, false, null));
                return true;
            }
            String displayName = name.getString().replaceAll("§.", "").trim();
            var info = findOnlinePlayer(displayName);
            String profile;
            UUID uuid;
            if (info != null) {
                profile = info.getProfile().getName();
                uuid = info.getProfile().getId();
            } else {
                UUID su = ChatMessageStore.findSeenUuid(displayName);
                if (su != null) {
                    profile = displayName;
                    uuid = su;
                } else {
                    profile = displayName;
                    uuid = new UUID(0, 0);
                }
            }
            ChatMessageStore.debugLog(() -> "[e33chat] Key(chat) | name=" + profile + " | display='" + name.getString() + "' | content='" + content.getString() + "'");
            ChatMessageStore.setPendingMeta(new SenderMeta(uuid, name, content, false, profile, false, null));
            return true;
        }

        if (isVanillaBroadcast(message)) {
            boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE.get();
            ChatMessageStore.debugLog(() -> "[e33chat] Key(broadcast) | key=" + key);
            ChatMessageStore.setPendingMeta(new SenderMeta(new UUID(0, 0),
                Component.translatable("e33chat.sender.system"), message, isSystem, null, false, null));
            return true;
        }

        return false;
    }

    // Plugins attach "click to whisper" events to sender names — the command holds the
    // real profile name, giving deterministic attribution even on nickname servers
    private static SenderMeta detectByTellClick(Component message, String text) {
        if (isVanillaBroadcast(message)) return null;
        var player = Minecraft.getInstance().player;
        if (player == null || player.connection == null) return null;
        final int[] pos = {0};
        final int[] range = {-1, -1};
        final String[] tellName = {null};
        final String[] clickedText = {null};
        message.visit((style, str) -> {
            int s = pos[0], e = s + str.length();
            pos[0] = e;
            var click = style.getClickEvent();
            if (tellName[0] == null && click != null
                && click.getAction() == net.minecraft.network.chat.ClickEvent.Action.SUGGEST_COMMAND
                && click.getValue() != null) {
                String cmd = click.getValue();
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
            }
            return java.util.Optional.<Object>empty();
        }, net.minecraft.network.chat.Style.EMPTY);
        int nameRangeLimit = Math.max(32, text.length() / 3);
        if (tellName[0] == null || range[0] > nameRangeLimit) return null;

        net.minecraft.client.multiplayer.PlayerInfo sender = null;
        for (var info : player.connection.getOnlinePlayers()) {
            String profile = info.getProfile().getName();
            if (profile.equals(tellName[0]) || profile.replaceAll("§.", "").equals(tellName[0])) {
                sender = info;
                break;
            }
        }
        UUID cachedId = null;
        if (sender == null) {
            cachedId = ChatMessageStore.findSeenUuid(tellName[0]);
            if (cachedId == null) return null;
        }

        if (sender != null) {
            // The clicked segment must actually be the sender's displayed name — feedback like
            // "杀死了E33EPUS" carries a whole-line /tell click whose first segment is not a name
            String clicked = clickedText[0].replaceAll("§.", "").trim();
            boolean clickedIsName = false;
            for (String cand : nameCandidates(sender)) {
                if (!cand.isEmpty() && clicked.contains(cand)) { clickedIsName = true; break; }
            }
            if (!clickedIsName) return null;
        }

        int b = range[1];
        if (b < text.length() && text.charAt(b) == '>') b++;
        int contentStart = MessagePresentation.skipSeparators(text, b);
        if (contentStart >= text.length()) return null;

        String profile = sender != null ? sender.getProfile().getName() : tellName[0];
        UUID id = sender != null ? sender.getProfile().getId() : cachedId;
        Component displayName = cleanNameArea(message, 0, b, tellName[0], Component.literal(profile));
        Component content = ChatMessageStore.sliceStyled(message, contentStart, text.length());
        ChatMessageStore.debugLog(() -> "[e33chat] System(tell click) | text='" + text + "' | name=" + profile + " | display='" + displayName.getString() + "' | content='" + content.getString() + "'");
        return new SenderMeta(
            id,
            displayName,
            content,
            false,
            profile,
            false, null
        );
    }

    private static SenderMeta detectWhisperInSystemMessage(String text, String logTag) {
        var connection = Minecraft.getInstance().player.connection;
        if (connection == null) return null;
        // G3: 消息嵌 legacy 色码（S§6t§beve）时整条剥 § 再做名字锚点匹配
        String clean = text.replaceAll("§.", "");
        for (var info : connection.getOnlinePlayers()) {
            String profile = info.getProfile().getName();
            for (String cand : nameCandidates(info)) {
                int idx = clean.indexOf(cand);
                if (idx >= 0 && idx < 30) {
                    if (MessagePresentation.hasWhisperKeywordBeforeColon(clean)) {
                        String content = MessagePresentation.extractWhisperContent(clean, cand);
                        UUID senderId = info.getProfile().getId();
                        ChatMessageStore.debugLog(() -> "[e33chat] System(" + logTag + ") | text='" + clean + "' | name=" + cand + " | content='" + content + "'");
                        return new SenderMeta(
                            senderId,
                            Component.literal(cand),
                            Component.literal(content),
                            false,
                            profile,
                            true, profile
                        );
                    }
                }
            }
        }
        // cache fallback: try seen (offline) players
        for (var sp : ChatMessageStore.knownNameVariants()) {
            int idx = clean.indexOf(sp);
            if (idx >= 0 && idx < 30) {
                if (MessagePresentation.hasWhisperKeywordBeforeColon(clean)) {
                    UUID su = ChatMessageStore.findSeenUuid(sp);
                    if (su != null) {
                        String content = MessagePresentation.extractWhisperContent(clean, sp);
                        ChatMessageStore.debugLog(() -> "[e33chat] System(" + logTag + "/cache) | text='" + clean + "' | name=" + sp + " | content='" + content + "'");
                        return new SenderMeta(
                            su,
                            Component.literal(sp),
                            Component.literal(content),
                            false,
                            sp,
                            true, sp
                        );
                    }
                }
            }
        }
        return null;
    }

    // ===== Template layer (server-declared message formats) =====

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
        var player = Minecraft.getInstance().player;
        if (player != null) {
            String myName = player.getName().getString();
            if (!myName.isEmpty() && (name.equals(myName) || name.contains(myName))) return true;
        }
        return findOnlinePlayer(name) != null || ChatMessageStore.findSeenUuid(name) != null;
    }

    // Server template parse: exact field split with style-preserving offsets.
    // Returns null on no match (fall back to the guards) or when the line is our
    // own echo (already bubbled via the authoritative player channel / suppressed).
    private static SenderMeta matchByTemplate(Component message, String text) {
        var r = TemplateMatcher.match(text, ChatMessageStore.serverChatTemplates(),
            ChatMessageStore.serverWhisperTemplates(), ChatListenerMixin::isTemplateNameKnown);
        if (r.isEmpty()) {
            logTemplateMiss(text);
            return null;
        }
        var tpl = r.orElseThrow();
        String verified = tpl.verifiedName();
        var info = findOnlinePlayer(verified);
        UUID uid = info != null ? info.getProfile().getId() : ChatMessageStore.findSeenUuid(verified);
        String rawName = info != null ? info.getProfile().getName() : verified;
        boolean isSelf = uid != null && Minecraft.getInstance().player != null
            && uid.equals(Minecraft.getInstance().player.getUUID());
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
        Component nameComp = templateSlice(message, text, tpl.nameStart(), tpl.nameEnd());
        Component contentComp = templateSlice(message, text, tpl.contentStart(), tpl.contentEnd());
        boolean whisper = tpl.whisper();
        String partner = whisper ? tpl.sender() : null;
        ChatMessageStore.debugLog(() -> "[e33chat] System(template) | text='" + text + "' | name='" + nameComp.getString() + "' | whisper=" + whisper + " | partner=" + partner + " | content='" + contentComp.getString() + "'");
        return new SenderMeta(uid != null ? uid : new UUID(0, 0), nameComp, contentComp,
            false, rawName, whisper, partner);
    }

    // Template-path field slicing: if the captured region contains literal §-codes
    // (some plugins embed raw "§6" text instead of real styles), rebuild it with
    // parseStyledText to render actual colors; otherwise keep the original
    // component slice (preserves real per-run styles like the guards do).
    private static Component templateSlice(Component message, String text, int from, int to) {
        String sub = text.substring(from, to);
        if (sub.indexOf('§') >= 0) return ChatMessageStore.parseStyledText(sub);
        return ChatMessageStore.sliceStyled(message, from, to);
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
        if (Minecraft.getInstance().level != null) {
            var registry = Minecraft.getInstance().level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.CHAT_TYPE);
            var key = registry.getResourceKey(bound.chatType()).orElse(null);
            if (ChatType.MSG_COMMAND_INCOMING.equals(key)) {
                isWhisper = true;
                whisperPartner = gameProfile.getName();
            } else if (ChatType.MSG_COMMAND_OUTGOING.equals(key)) {
                isWhisper = true;
                isOutgoing = true;
                whisperPartner = bound.targetName() != null ? bound.targetName().getString() : null;
            }
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
        if (Minecraft.getInstance().level != null) {
            var registry = Minecraft.getInstance().level.registryAccess()
                .registryOrThrow(net.minecraft.core.registries.Registries.CHAT_TYPE);
            var key = registry.getResourceKey(bound.chatType()).orElse(null);
            if (ChatType.MSG_COMMAND_INCOMING.equals(key)) {
                isWhisper = true;
                whisperPartner = hasSender ? bound.name().getString() : null;
            } else if (ChatType.MSG_COMMAND_OUTGOING.equals(key)) {
                isWhisper = true;
                isOutgoing = true;
                whisperPartner = bound.targetName() != null ? bound.targetName().getString() : null;
            }
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
            var namesSet = new java.util.LinkedHashSet<String>();
            connection.getOnlinePlayers().forEach(info -> {
                for (String cand : nameCandidates(info)) namesSet.add(cand);
            });
            namesSet.addAll(ChatMessageStore.knownNameVariants());
            var onlineNames = new java.util.ArrayList<>(namesSet);
            var parsed = MessagePresentation.parseDecoratedPlayerLine(msgStr, onlineNames);
            if (parsed.isPresent()) {
                var pl = parsed.orElseThrow();
                // 偏移来自 parser（双侧剥 § 后的映射），嵌色名 S§6t§beve 也正确
                int nameIdx = pl.nameStart();
                int nameEnd = pl.nameEnd();
                int contentStart = pl.contentStart();
                // Whitespace-only gap = broadcast sentence (Steve joined the game),
                // not chat: server chat formats always separate name and content
                if (MessagePresentation.isWhitespaceOnlyGap(msgStr, nameEnd, contentStart)) {
                    ChatMessageStore.debugLog(() -> "[e33chat] Disguised(line skip: broadcast sentence) | text='" + msgStr + "'");
                } else {
                    var info = connection.getOnlinePlayers().stream()
                        .filter(i -> {
                            for (String cand : nameCandidates(i))
                                if (cand.equals(pl.playerName())) return true;
                            return false;
                        }).findFirst().orElse(null);
                    UUID uid;
                    if (info != null) {
                        uid = info.getProfile().getId();
                    } else {
                        UUID su = ChatMessageStore.findSeenUuid(pl.playerName());
                        uid = su != null ? su : new UUID(0, 0);
                    }
                    Component displayName = extractDecoratedName(message, pl.content(), pl.playerName(),
                        Component.literal((msgStr.substring(0, nameIdx) + pl.playerName()).trim()));
                    Component contentComp = ChatMessageStore.sliceStyled(message, contentStart, msgStr.length());
                    ChatMessageStore.debugLog(() -> "[e33chat] Disguised(player line) | name=" + pl.playerName() + " | content='" + pl.content() + "'");
                    ChatMessageStore.setPendingMeta(new SenderMeta(
                        uid, displayName, contentComp, false,
                        info != null ? info.getProfile().getName() : pl.playerName(),
                        false, null));
                    return;
                }
            }
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
        // Suppress outgoing whisper echo
        boolean hasEchoFlag = ChatMessageStore.hasPendingWhisperEcho();
        boolean hasKw = sysText.contains("悄悄") || sysText.contains("whispers") || sysText.contains("whisper")
            || sysText.contains("私聊") || sysText.contains("密语") || sysText.contains("密聊")
            || sysText.contains("私信") || sysText.contains("密谈")
            || sysText.contains("对你说")
            || ECHO_WHISPER_PATTERN.matcher(sysText.toLowerCase()).find();
        ChatMessageStore.debugLog(() -> "[e33chat] System(echo check) | text='" + sysText + "' | flag=" + hasEchoFlag + " | kw=" + hasKw);
        if (hasEchoFlag && hasKw) {
            var player = Minecraft.getInstance().player;
            boolean otherPlayerFound = false;
            if (player != null && player.connection != null) {
                String myName = player.getName().getString();
                String skipTarget = ChatMessageStore.getPendingWhisperTarget();
                for (var info : player.connection.getOnlinePlayers()) {
                    for (String cand : nameCandidates(info)) {
                        if (cand.equals(myName) || cand.isEmpty()) continue;
                        if (cand.equals(skipTarget)) continue;
                        int idx = sysText.indexOf(cand);
                        if (idx >= 0 && idx < 30) {
                            otherPlayerFound = true;
                            break;
                        }
                    }
                    if (otherPlayerFound) break;
                }
            }
            if (!otherPlayerFound) {
                ChatMessageStore.consumeWhisperEcho();
                ChatMessageStore.markSuppressCapture();
                ChatMessageStore.debugLog(() -> "[e33chat] System(echo suppressed) | text='" + sysText + "'");
                return;
            }
        }
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
            var namesSet = new java.util.LinkedHashSet<String>();
            connection.getOnlinePlayers().forEach(info -> {
                for (String cand : nameCandidates(info)) namesSet.add(cand);
            });
            namesSet.addAll(ChatMessageStore.knownNameVariants());
            var onlineNames = new java.util.ArrayList<>(namesSet);
            var parsed = MessagePresentation.parseDecoratedPlayerLine(text, onlineNames);
            if (parsed.isPresent()) {
                var pl = parsed.orElseThrow();
                // 偏移来自 parser（双侧剥 § 后的映射），嵌色名 S§6t§beve 也正确
                int nameIdx = pl.nameStart();
                int nameEnd = pl.nameEnd();
                int contentStart = pl.contentStart();
                // Whitespace-only gap = broadcast sentence (Steve joined the game),
                // not chat: server chat formats always separate name and content
                if (MessagePresentation.isWhitespaceOnlyGap(text, nameEnd, contentStart)) {
                    ChatMessageStore.debugLog(() -> "[e33chat] System(line skip: broadcast sentence) | text='" + text + "'");
                } else {
                    var info = connection.getOnlinePlayers().stream()
                        .filter(i -> {
                            for (String cand : nameCandidates(i))
                                if (cand.equals(pl.playerName())) return true;
                            return false;
                        }).findFirst().orElse(null);
                    UUID uid;
                    if (info != null) {
                        uid = info.getProfile().getId();
                    } else {
                        UUID su = ChatMessageStore.findSeenUuid(pl.playerName());
                        uid = su != null ? su : new UUID(0, 0);
                    }
                    Component displayName = extractDecoratedName(message, pl.content(), pl.playerName(),
                        Component.literal((text.substring(0, nameIdx) + pl.playerName()).trim()));
                    Component contentComp = ChatMessageStore.sliceStyled(message, contentStart, text.length());
                    ChatMessageStore.debugLog(() -> "[e33chat] System(player line) | name=" + pl.playerName() + " | content='" + pl.content() + "'");
                    ChatMessageStore.setPendingMeta(new SenderMeta(
                        uid, displayName, contentComp, false,
                        info != null ? info.getProfile().getName() : pl.playerName(),
                        false, null));
                    return;
                }
            }
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
