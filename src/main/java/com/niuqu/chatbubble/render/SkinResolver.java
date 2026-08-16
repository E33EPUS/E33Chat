package com.niuqu.chatbubble.render;

import com.mojang.authlib.GameProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.client.util.DefaultSkinHelper;
import net.minecraft.util.Identifier;

/**
 * Player-head skin resolution with a merged UUID + name cache.
 *
 * Extracted from ChatBubbleScreen / ChatSidebar during the 2.3.14 restructure:
 * the two components previously kept separate LRU caches for the same data.
 * The resolution fallback chain (online fresh read -> uuid cache -> name cache ->
 * SkinProvider -> default) is unchanged; only the cache store is shared.
 */
public final class SkinResolver {
    private SkinResolver() {}

    private static final int SKIN_CACHE_CAP = 256;
    private static final UUID NIL_UUID = new UUID(0, 0);

    private static final Map<UUID, Identifier> skinCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, Identifier> eldest) {
            return size() > SKIN_CACHE_CAP;
        }
    };

    // Name-keyed skin cache: an offline player seen in chat history keeps the
    // real head when the UUID lookup fails (cracked servers, uuid dropped in
    // old history files). Key is the §-stripped lowercase name.
    private static final Map<String, Identifier> skinNameCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, Identifier> eldest) {
            return size() > SKIN_CACHE_CAP;
        }
    };

    private static String skinNameKey(String name) {
        if (name == null) return null;
        String key = name.replaceAll("§.", "").trim().toLowerCase(java.util.Locale.ROOT);
        return key.isEmpty() ? null : key;
    }

    private static void rememberSkin(UUID uuid, String name, Identifier tex) {
        if (tex == null) return;
        if (uuid != null && !uuid.equals(NIL_UUID)) skinCache.put(uuid, tex);
        String key = skinNameKey(name);
        if (key != null) skinNameCache.put(key, tex);
    }

    public static Identifier getSkin(UUID uuid, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Online players: read PlayerListEntry fresh every frame — caching the first
        // result (default Steve/Alex while the async download is in progress) would
        // freeze the head forever even after the real skin loaded. CSL intercepts the
        // underlying lookup.
        if (client.getNetworkHandler() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            PlayerListEntry info = client.getNetworkHandler().getPlayerListEntry(uuid);
            if (info != null) {
                Identifier tex = info.getSkinTextures().texture();
                rememberSkin(uuid, name, tex);
                return tex;
            }
        }
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            Identifier cached = skinCache.get(uuid);
            if (cached != null) return cached;
        }
        String nameKey = skinNameKey(name);
        if (nameKey != null) {
            Identifier cachedByName = skinNameCache.get(nameKey);
            if (cachedByName != null) return cachedByName;
        }
        Identifier resolved = resolveSkin(uuid, name);
        rememberSkin(uuid, name, resolved);
        return resolved;
    }

    private static Identifier resolveSkin(UUID uuid, String name) {
        MinecraftClient client = MinecraftClient.getInstance();
        // Route through PlayerSkinProvider with a name-bearing GameProfile so CSL
        // can match offline players to imported skins. getSkinTextures(GameProfile)
        // is the Yarn equivalent of Mojang's SkinManager.getInsecureSkin().
        if (name != null && !name.isEmpty()) {
            try {
                GameProfile profile = new GameProfile(
                    uuid != null && !uuid.equals(NIL_UUID) ? uuid : NIL_UUID, name);
                return client.getSkinProvider().getSkinTextures(profile).texture();
            } catch (Exception ignored) {}
        }
        return DefaultSkinHelper.getTexture();
    }
}
