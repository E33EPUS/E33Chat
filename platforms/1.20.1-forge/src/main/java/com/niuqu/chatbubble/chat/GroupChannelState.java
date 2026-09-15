package com.niuqu.chatbubble.chat;

import com.niuqu.chatbubble.store.ChatMessageStore.ChatMessage;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Client-side group channel state (2.4.10).
 *
 * Tab model: {@link #TAB_ALL} = "全部" (world + system, groups stay in their
 * own tab), {@link #TAB_WORLD} = public player messages, {@link #TAB_SYSTEM} =
 * system messages, otherwise the group name. All pseudo ids start with '#' and group names can never start with '#'
 * (server-side validation), so they can't collide with real groups.
 *
 * The tab strip is only shown once the server sent a GroupListPacket
 * ({@code enabled} flag) — singleplayer and vanilla servers hide it entirely.
 */
public final class GroupChannelState {

    public static final String TAB_ALL = "#all";
    public static final String TAB_WORLD = "#world";
    public static final String TAB_SYSTEM = "#system";

    /** Set from GroupListPacket.enabled; reset on disconnect. */
    public static volatile boolean enabled;

    /** Directory from the latest GroupListPacket. */
    public static final Set<String> knownGroups = new LinkedHashSet<>();
    public static final Set<String> myGroups = new LinkedHashSet<>();

    /** Active tab: TAB_ALL / TAB_WORLD / TAB_SYSTEM / a group name. */
    private static String active = TAB_ALL;

    private GroupChannelState() {}

    public static boolean supported() {
        return enabled;
    }

    public static String active() {
        return enabled ? active : null;
    }

    /** Returns true if the tab changed. */
    public static boolean setActive(String tab) {
        if (tab == null) tab = TAB_ALL;
        if (tab.equals(active)) return false;
        active = tab;
        return true;
    }

    public static void applyDirectory(List<String> names, List<Integer> counts, List<String> mine) {
        knownGroups.clear();
        if (names != null) knownGroups.addAll(names);
        myGroups.clear();
        if (mine != null) myGroups.addAll(mine);
        // The active tab must stay meaningful: if the group vanished (left,
        // deleted, disbanded) fall back to "全部".
        if (active != null && !isPseudoTab(active) && !knownGroups.contains(active)) {
            active = TAB_ALL;
        }
    }

    /** Disconnect / world switch: forget everything so state can't leak. */
    public static void reset() {
        enabled = false;
        knownGroups.clear();
        myGroups.clear();
        active = TAB_ALL;
    }

    public static boolean isPseudoTab(String tab) {
        return TAB_WORLD.equals(tab) || TAB_SYSTEM.equals(tab);
    }

    // ==== Pure classification (unit-tested; no Minecraft statics) ====

    /** Which tab a message belongs to. Explicit group metadata wins over the
     *  system flag — group messages are always delivered as player messages,
     *  but a system flag set by another path must not steal them out of their tab. */
    public static String channelOf(ChatMessage msg) {
        if (msg == null) return TAB_WORLD;
        if (msg.group() != null && !msg.group().isEmpty()) return msg.group();
        if (msg.isSystem()) return TAB_SYSTEM;
        return TAB_WORLD;
    }

    /** Tab filter over the public (non-whisper) message list.
     *  「全部」= world + system only: group messages stay quarantined in their
     *  own tab (user report 2026-09-09 — mixing private group chatter into the
     *  default view read as "leaking into the world channel"). */
    public static List<ChatMessage> filterMessages(List<ChatMessage> publicMessages, String activeTab) {
        if (activeTab == null) return publicMessages;
        if (TAB_ALL.equals(activeTab)) {
            List<ChatMessage> out = new ArrayList<>();
            for (ChatMessage m : publicMessages) {
                if (m.group() == null || m.group().isEmpty()) out.add(m);
            }
            return out;
        }
        List<ChatMessage> out = new ArrayList<>();
        for (ChatMessage m : publicMessages) {
            if (activeTab.equals(channelOf(m))) out.add(m);
        }
        return out;
    }
}
