package com.niuqu.chatbubble.store;

import java.util.List;
import net.minecraft.text.Text;

/**
 * Blocked-player matching rules (pure predicates, no state).
 *
 * Extracted from ChatMessageStore during the 2.3.14 restructure; the message
 * list operations that consume these predicates (purgeBlocked, load filters)
 * stay in ChatMessageStore because they own the list.
 */
public final class BlockList {
    private BlockList() {}

    // Exact-name matching (case-insensitive, §-stripped). rawPlayerName is the
    // primary key — the disguised channel carries a nil UUID, so UUID-only
    // matching would leak blocked players through that path.
    public static boolean matchesBlocked(String name, List<? extends String> blocked) {
        if (name == null || name.isEmpty() || blocked == null || blocked.isEmpty()) return false;
        // Both sides §-stripped and trimmed so color-coded names and stray spaces
        // in either the message or the config list can't break the match
        String stripped = name.replaceAll("§.", "").trim();
        for (String b : blocked) {
            if (b == null || b.isBlank()) continue;
            String candidate = b.replaceAll("§.", "").trim();
            if (stripped.equalsIgnoreCase(candidate)) return true;
        }
        return false;
    }

    // senderName (tab-list display name) as fallback covers nickname plugins where
    // the chat line carries the decorated name and rawPlayerName is the profile name
    public static boolean isPlayerBlocked(String rawPlayerName, Text senderName, List<? extends String> blocked) {
        if (blocked == null || blocked.isEmpty()) return false;
        if (matchesBlocked(rawPlayerName, blocked)) return true;
        return senderName != null && matchesBlocked(senderName.getString(), blocked);
    }

    // History restored from disk / server packets must not re-import blocked
    // senders' messages, or they reappear on the next world join
    public static boolean isBlocked(ChatMessageStore.ChatMessage m, List<? extends String> blocked) {
        return isPlayerBlocked(m.rawPlayerName(), m.senderName(), blocked);
    }

    public static boolean isBlocked(ChatMessageStore.ChatMessage m) {
        return isBlocked(m, com.niuqu.chatbubble.ChatBubbleClientSetup.config().blockedPlayers());
    }
}
