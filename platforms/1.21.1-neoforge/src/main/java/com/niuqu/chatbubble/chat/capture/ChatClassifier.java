package com.niuqu.chatbubble.chat.capture;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.store.ChatMessageStore;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.network.chat.Component;

/**
 * Guard 0: deterministic routing by vanilla translation key, plus the player
 * name resolution helpers it shares with the other guards.
 *
 * Extracted from ChatListenerMixin during the 2.3.14 restructure — the mixin
 * keeps only the @Inject shells and forwards here. Behaviour unchanged.
 */
public final class ChatClassifier {
    private ChatClassifier() {}

    // Nick plugins put the tab-list display name in chat instead of the profile name;
    // legacy plugins may embed section-sign color codes in names, so offer stripped variants too
    public static String[] nameCandidates(PlayerInfo info) {
        var out = new java.util.LinkedHashSet<String>();
        String profile = info.getProfile().getName();
        addNameVariants(out, profile);
        var tab = info.getTabListDisplayName();
        if (tab != null) addNameVariants(out, tab.getString().trim());
        return out.toArray(new String[0]);
    }

    public static void addNameVariants(java.util.Set<String> out, String name) {
        if (name == null || name.isEmpty()) return;
        out.add(name);
        String stripped = name.replaceAll("§.", "");
        if (!stripped.isEmpty()) out.add(stripped);
    }

    // Vanilla broadcasts (advancements/deaths/joins) lead with a clickable player name,
    // which tell-click would wrongly claim as chat — keep them as system messages.
    // chat.type.admin is the op echo "[Steve: Teleported ...]",
    // announcement/emote are /say and /me — same trap
    public static boolean isVanillaBroadcast(Component message) {
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

    public static Component argAsComponent(Object arg) {
        return arg instanceof Component c ? c : Component.literal(String.valueOf(arg));
    }

    public static PlayerInfo resolveOnlinePlayer(String displayName) {
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
        PlayerInfo best = null;
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

    // ===== Layer 0: deterministic routing by vanilla translation key.=====
    // NCR/FreedomChat stuff the decorated component tree into system packets unchanged,
    // so the key survives conversion. Unknown keys fall through to the heuristics below.

    /** @return true when the message was routed (pending meta set / suppressed). */
    public static boolean classifyByKey(Component message) {
        if (!(message.getContents() instanceof net.minecraft.network.chat.contents.TranslatableContents tc)) return false;
        String key = tc.getKey();
        Object[] args = tc.getArgs();

        if (key.equals("commands.message.display.incoming") && args.length >= 2) {
            Component name = argAsComponent(args[0]);
            Component content = argAsComponent(args[1]);
            String displayName = name.getString().replaceAll("§.", "").trim();
            var info = resolveOnlinePlayer(displayName);
            String profile = info != null ? info.getProfile().getName() : displayName;
            UUID uuid = info != null ? info.getProfile().getId() : new UUID(0, 0);
            ChatMessageStore.debugLog(() -> "[e33chat] Key(whisper in) | name=" + profile + " | content='" + content.getString() + "'");
            ChatMessageStore.setPendingMeta(new ChatMessageStore.SenderMeta(uuid, name, content, false, profile, true, profile));
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
                ChatMessageStore.setPendingMeta(new ChatMessageStore.SenderMeta(player.getUUID(),
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
                ChatMessageStore.setPendingMeta(new ChatMessageStore.SenderMeta(new UUID(0, 0),
                    Component.translatable("e33chat.sender.system"), message, true, null, false, null));
                return true;
            }
            String displayName = name.getString().replaceAll("§.", "").trim();
            var info = resolveOnlinePlayer(displayName);
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
            ChatMessageStore.setPendingMeta(new ChatMessageStore.SenderMeta(uuid, name, content, false, profile, false, null));
            return true;
        }

        if (isVanillaBroadcast(message)) {
            boolean isSystem = !ChatBubbleConfig.SYSTEM_CHAT_AS_BUBBLE.get();
            ChatMessageStore.debugLog(() -> "[e33chat] Key(broadcast) | key=" + key);
            ChatMessageStore.setPendingMeta(new ChatMessageStore.SenderMeta(new UUID(0, 0),
                Component.translatable("e33chat.sender.system"), message, isSystem, null, false, null));
            return true;
        }

        return false;
    }
}
