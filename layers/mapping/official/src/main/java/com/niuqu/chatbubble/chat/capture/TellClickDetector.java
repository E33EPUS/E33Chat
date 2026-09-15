package com.niuqu.chatbubble.chat.capture;

import com.niuqu.chatbubble.chat.MessagePresentation;
import com.niuqu.chatbubble.store.ChatMessageStore;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.network.chat.Component;

/**
 * Layer 2: structural tell-click attribution. Plugins attach "click to
 * whisper" events to sender names — the command holds the real profile name,
 * giving deterministic attribution even on nickname servers.
 *
 * Extracted from ChatListenerMixin during the 2.3.14 restructure; behaviour
 * unchanged.
 */
public final class TellClickDetector {
    private TellClickDetector() {}

    public static ChatMessageStore.SenderMeta detectByTellClick(Component message, String text) {
        if (ChatClassifier.isVanillaBroadcast(message)) return null;
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
            for (String cand : ChatClassifier.nameCandidates(sender)) {
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
        Component displayName = ChatPipeline.cleanNameArea(message, 0, b, tellName[0], Component.literal(profile));
        Component content = ChatMessageStore.sliceStyled(message, contentStart, text.length());
        ChatMessageStore.debugLog(() -> "[e33chat] System(tell click) | text='" + text + "' | name=" + profile + " | display='" + displayName.getString() + "' | content='" + content.getString() + "'");
        return new ChatMessageStore.SenderMeta(
            id,
            displayName,
            content,
            false,
            profile,
            false, null
        );
    }
}
