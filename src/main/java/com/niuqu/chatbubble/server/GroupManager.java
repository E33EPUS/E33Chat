package com.niuqu.chatbubble.server;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.niuqu.chatbubble.config.ChatServerConfig;
import com.niuqu.chatbubble.network.NetworkHandler;
import com.niuqu.chatbubble.packets.GroupChatPacket;
import com.niuqu.chatbubble.packets.GroupListPacket;
import com.niuqu.chatbubble.server.ChatServerListener.QuotePending;
import com.niuqu.chatbubble.packets.HistoryPacket;
import net.minecraft.network.chat.Component;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.server.ServerLifecycleHooks;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * In-mod group chat backend (2.4.10). Groups live inside the E33Chat server
 * mod itself — no external Bukkit plugin, unlike the CoreChat fork whose
 * channel routing requires its closed-source bridge.
 *
 * Routing: a member's message is delivered as a {@link GroupChatPacket} to
 * members running the mod and as a plain "[group] <name> text" line to vanilla
 * members. The sender receives the packet too, which replaces the local echo
 * bubble (the client rewrites sends to /e33chat group msg, so nothing echoes).
 */
public final class GroupManager {

    /** Client side needs this too (command suggestion tab completes); keep generous. */
    public static final int MAX_NAME_LEN = 12;
    /** Commands bypass vanilla chat spam kicks — keep a cheap per-player floor. */
    static final long SAY_COOLDOWN_MS = 500;
    private static final int MAX_CONTENT = 1024;
    private static final String FILE_NAME = "e33chat-groups.json";

    public static final class Group {
        public UUID owner;
        public LinkedHashSet<UUID> members = new LinkedHashSet<>();

        Group(UUID owner, LinkedHashSet<UUID> members) {
            this.owner = owner;
            this.members = members;
        }
    }

    private static final Map<String, Group> groups = new LinkedHashMap<>();
    private static final Set<UUID> modClients = new java.util.HashSet<>();
    private static final Map<UUID, Long> lastSay = new HashMap<>();
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static volatile boolean loaded;

    private GroupManager() {}

    // ==== Pure validation (unit-tested) ====

    /** Names must survive the vanilla "[name] <player> text" line unambiguous,
     *  and must not start with '#' (reserved for the client's pseudo tabs). */
    public static boolean isValidGroupName(String name) {
        if (name == null || name.isEmpty() || name.length() > MAX_NAME_LEN) return false;
        if (name.charAt(0) == '#') return false;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (Character.isWhitespace(c)) return false;
            if (c == '[' || c == ']' || c == '<' || c == '>' || c == '§') return false;
        }
        return true;
    }

    /** Split the {@code /e33chat group msg <name> <text>} rest argument.
     *  Group names never contain whitespace, so the first space separates them
     *  from the text. Returns {@code {name, text}}; a missing space yields an
     *  empty text so the caller reports the usual "message must not be empty". */
    public static String[] splitSay(String rest) {
        String s = rest == null ? "" : rest.trim();
        for (int i = 0; i < s.length(); i++) {
            if (Character.isWhitespace(s.charAt(i))) {
                return new String[]{s.substring(0, i), s.substring(i + 1).trim()};
            }
        }
        return new String[]{s, ""};
    }

    public static boolean exists(String name) {
        ensureLoaded();
        return name != null && groups.containsKey(name);
    }

    public static boolean isMember(String name, UUID id) {
        ensureLoaded();
        Group g = name != null ? groups.get(name) : null;
        return g != null && g.members.contains(id);
    }

    /** Read-only snapshot for tests/tools; caller must not mutate. */
    public static Map<String, Group> snapshot() {
        ensureLoaded();
        return Collections.unmodifiableMap(groups);
    }

    // ==== Mutation (server thread) — each sends its own feedback and re-syncs ====

    public static void create(ServerPlayer player, String name) {
        if (!enabled(player, "e33chat.group.disabled")) return;
        if (!isValidGroupName(name)) { fail(player, "e33chat.group.bad_name"); return; }
        ensureLoaded();
        if (groups.containsKey(name)) { fail(player, "e33chat.group.exists"); return; }
        if (ChatServerConfig.GROUP_CREATE_OP_ONLY.get() && !player.hasPermissions(2)) {
            fail(player, "e33chat.group.op_only");
            return;
        }
        int max = ChatServerConfig.GROUP_MAX_COUNT.get();
        if (groups.size() >= max) { fail(player, "e33chat.group.limit", max); return; }
        LinkedHashSet<UUID> members = new LinkedHashSet<>();
        members.add(player.getUUID());
        groups.put(name, new Group(player.getUUID(), members));
        save();
        ok(player, "e33chat.group.created", name);
        broadcastGroupList();
    }

    public static void join(ServerPlayer player, String name) {
        if (!enabled(player, "e33chat.group.disabled")) return;
        Group g = groups.get(name);
        if (g == null) { fail(player, "e33chat.group.missing", name); return; }
        if (g.members.contains(player.getUUID())) { fail(player, "e33chat.group.already_in", name); return; }
        int max = ChatServerConfig.GROUP_MAX_MEMBERS.get();
        if (g.members.size() >= max) { fail(player, "e33chat.group.full", name, max); return; }
        g.members.add(player.getUUID());
        save();
        ok(player, "e33chat.group.joined", name);
        broadcastGroupList();
    }

    public static void leave(ServerPlayer player, String name) {
        if (!enabled(player, "e33chat.group.disabled")) return;
        Group g = groups.get(name);
        if (g == null || !g.members.remove(player.getUUID())) {
            fail(player, "e33chat.group.not_member_short", name);
            return;
        }
        if (g.members.isEmpty()) {
            groups.remove(name);
            ok(player, "e33chat.group.disbanded_empty", name);
        } else {
            if (player.getUUID().equals(g.owner)) {
                g.owner = g.members.iterator().next();
            }
            ok(player, "e33chat.group.left", name);
        }
        save();
        broadcastGroupList();
    }

    public static void delete(ServerPlayer player, String name) {
        if (!enabled(player, "e33chat.group.disabled")) return;
        Group g = groups.get(name);
        if (g == null) { fail(player, "e33chat.group.missing", name); return; }
        if (!player.getUUID().equals(g.owner) && !player.hasPermissions(2)) {
            fail(player, "e33chat.group.not_owner", name);
            return;
        }
        groups.remove(name);
        save();
        ok(player, "e33chat.group.deleted", name);
        broadcastGroupList();
    }

    public static void say(ServerPlayer sender, String name, String content) {
        if (!ChatServerConfig.GROUPS_ENABLED.get()) { fail(sender, "e33chat.group.disabled"); return; }
        Group g = groups.get(name);
        if (g == null) { fail(sender, "e33chat.group.missing", name); return; }
        if (!g.members.contains(sender.getUUID())) { fail(sender, "e33chat.group.not_member_short", name); return; }
        long now = System.currentTimeMillis();
        Long last = lastSay.get(sender.getUUID());
        if (last != null && now - last < SAY_COOLDOWN_MS) { fail(sender, "e33chat.group.cooldown"); return; }
        String text = content == null ? "" : content.trim();
        if (text.isEmpty()) { fail(sender, "e33chat.group.empty"); return; }
        if (text.length() > MAX_CONTENT) text = text.substring(0, MAX_CONTENT);
        lastSay.put(sender.getUUID(), now);

        String senderName = sender.getName().getString();
        QuotePending quote = ChatServerListener.consumeQuote(sender.getUUID());
        GroupChatPacket packet = new GroupChatPacket(
            sender.getUUID(), senderName, name, text,
            quote != null ? quote.quotedSenderName() : null,
            quote != null ? quote.quotedContent() : null);
        String vanillaLine = "[" + name + "] <" + senderName + "> " + text;

        List<ServerPlayer> players = sender.getServer().getPlayerList().getPlayers();
        for (ServerPlayer p : players) {
            if (!g.members.contains(p.getUUID())) continue;
            if (modClients.contains(p.getUUID())) {
                NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> p), packet);
            } else {
                p.sendSystemMessage(Component.literal(vanillaLine));
            }
        }
        ChatServerListener.addHistoryEntry(new HistoryPacket.HistoryEntry(
            sender.getUUID(), senderName, text, now, false,
            quote != null ? quote.quotedContent() : null,
            quote != null ? quote.quotedSenderName() : null,
            name));
    }

    // ==== Client tracking + directory sync ====

    public static void onClientHello(ServerPlayer player) {
        modClients.add(player.getUUID());
        sendGroupList(player);
    }

    public static void onPlayerLoggedOut(UUID id) {
        modClients.remove(id);
        lastSay.remove(id);
    }

    public static void sendGroupList(ServerPlayer player) {
        boolean enabled = ChatServerConfig.GROUPS_ENABLED.get();
        ensureLoaded();
        List<String> names = new ArrayList<>(groups.keySet());
        List<Integer> counts = new ArrayList<>();
        List<String> mine = new ArrayList<>();
        for (var e : groups.entrySet()) {
            counts.add(e.getValue().members.size());
            if (e.getValue().members.contains(player.getUUID())) mine.add(e.getKey());
        }
        NetworkHandler.CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
            new GroupListPacket(enabled, names, counts, mine));
    }

    public static void broadcastGroupList() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null || server.getPlayerList() == null) return;
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (modClients.contains(p.getUUID())) sendGroupList(p);
        }
    }

    public static void handleAction(ServerPlayer player, int action, String name) {
        switch (action) {
            case com.niuqu.chatbubble.packets.GroupActionPacket.CREATE -> create(player, name);
            case com.niuqu.chatbubble.packets.GroupActionPacket.JOIN -> join(player, name);
            case com.niuqu.chatbubble.packets.GroupActionPacket.LEAVE -> leave(player, name);
            case com.niuqu.chatbubble.packets.GroupActionPacket.DELETE -> delete(player, name);
            default -> { /* unknown action from a newer client — ignore */ }
        }
    }

    // ==== Persistence ====

    private static void ensureLoaded() {
        if (loaded) return;
        synchronized (GroupManager.class) {
            if (loaded) return;
            MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
            if (server != null) {
                load(server);
            }
            loaded = true;
        }
    }

    static void resetForTesting() {
        groups.clear();
        modClients.clear();
        lastSay.clear();
        loaded = true;
    }

    private static Path file(MinecraftServer server) {
        return server.getWorldPath(net.minecraft.world.level.storage.LevelResource.ROOT)
            .resolve("serverconfig").resolve(FILE_NAME);
    }

    private static void load(MinecraftServer server) {
        groups.clear();
        Path f = file(server);
        if (!Files.exists(f)) return;
        try {
            String json = Files.readString(f, StandardCharsets.UTF_8);
            Root root = GSON.fromJson(json, Root.class);
            if (root == null || root.groups == null) return;
            for (var e : root.groups.entrySet()) {
                if (e.getValue() == null || e.getValue().members == null) continue;
                if (!isValidGroupName(e.getKey())) continue;
                Group g = new Group(e.getValue().owner, new LinkedHashSet<>(e.getValue().members));
                groups.put(e.getKey(), g);
            }
        } catch (Exception ex) {
            com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to load groups", ex);
        }
    }

    private static void save() {
        MinecraftServer server = ServerLifecycleHooks.getCurrentServer();
        if (server == null) return;
        try {
            Path f = file(server);
            Files.createDirectories(f.getParent());
            Root root = new Root();
            root.groups = new LinkedHashMap<>();
            for (var e : groups.entrySet()) {
                StoredGroup sg = new StoredGroup();
                sg.owner = e.getValue().owner;
                sg.members = new ArrayList<>(e.getValue().members);
                root.groups.put(e.getKey(), sg);
            }
            Files.writeString(f, GSON.toJson(root), StandardCharsets.UTF_8);
        } catch (IOException ex) {
            com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Failed to save groups", ex);
        }
    }

    public static void onServerStopping() {
        save();
        groups.clear();
        modClients.clear();
        lastSay.clear();
        loaded = false;
    }

    // ==== feedback helpers ====

    private static boolean enabled(ServerPlayer p, String key) {
        if (ChatServerConfig.GROUPS_ENABLED.get()) return true;
        fail(p, key);
        return false;
    }

    private static void ok(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args));
    }

    private static void fail(ServerPlayer p, String key, Object... args) {
        p.sendSystemMessage(Component.translatable(key, args));
    }

    // ==== persistence shapes ====

    public static class Root {
        public Map<String, StoredGroup> groups;
    }

    public static class StoredGroup {
        public UUID owner;
        public List<UUID> members;
    }
}
