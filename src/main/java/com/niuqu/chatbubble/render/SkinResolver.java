package com.niuqu.chatbubble.render;

import com.mojang.authlib.GameProfile;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.minecraft.client.resources.DefaultPlayerSkin;
import net.minecraft.resources.ResourceLocation;

/**
 * Player-head skin resolution with a merged UUID + name cache.
 *
 * Extracted from ChatBubbleScreen / ChatSidebar during the 2.3.14 restructure:
 * the two components previously kept separate LRU caches for the same data.
 * The resolution fallback chain (online fresh read → uuid cache → name cache →
 * SkinManager → default) is unchanged; only the cache store is shared.
 */
public final class SkinResolver {
    private SkinResolver() {}

    private static final int SKIN_CACHE_CAP = 256;
    private static final UUID NIL_UUID = new UUID(0, 0);

    private static final Map<UUID, ResourceLocation> skinCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<UUID, ResourceLocation> eldest) {
            return size() > SKIN_CACHE_CAP;
        }
    };

    // Name-keyed skin cache: an offline player seen in chat history keeps the
    // real head when the UUID lookup fails (cracked servers, uuid dropped in
    // old history files). Key is the §-stripped lowercase name.
    private static final Map<String, ResourceLocation> skinNameCache = new LinkedHashMap<>(16, 0.75f, true) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<String, ResourceLocation> eldest) {
            return size() > SKIN_CACHE_CAP;
        }
    };

    private static String skinNameKey(String name) {
        if (name == null) return null;
        String key = name.replaceAll("§.", "").trim().toLowerCase(java.util.Locale.ROOT);
        return key.isEmpty() ? null : key;
    }

    private static void rememberSkin(UUID uuid, String name, ResourceLocation tex) {
        if (tex == null) return;
        if (uuid != null && !uuid.equals(NIL_UUID)) skinCache.put(uuid, tex);
        String key = skinNameKey(name);
        if (key != null) skinNameCache.put(key, tex);
    }

    public static ResourceLocation getSkin(UUID uuid, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        // Online players: read PlayerInfo fresh every frame. getSkin().texture() returns
        // the default skin and kicks off an async download on first call, then updates
        // in place once done. Caching that first (default) result froze the head on
        // Steve/Alex forever even after the real skin loaded — the entity model reads
        // this fresh each frame, which is why the body showed the skin but the head didn't.
        // CSL intercepts the underlying SkinManager lookup, so CSL skins flow through too.
        if (minecraft.getConnection() != null && uuid != null && !uuid.equals(NIL_UUID)) {
            PlayerInfo info = minecraft.getConnection().getPlayerInfo(uuid);
            if (info != null) {
                ResourceLocation tex = info.getSkin().texture();
                rememberSkin(uuid, name, tex);
                return tex;
            }
        }
        // Not in the tab list (offline player / history mention): route through the
        // SkinManager with a GameProfile carrying the name. CSL keys off the name, so
        // offline players with an imported skin resolve; otherwise vanilla (real skin
        // for paid accounts carrying textures, Steve/Alex otherwise). The first result
        // is final here, so cache it to avoid repeating the lookup every frame.
        if (uuid != null && !uuid.equals(NIL_UUID)) {
            ResourceLocation cached = skinCache.get(uuid);
            if (cached != null) return cached;
        }
        String nameKey = skinNameKey(name);
        if (nameKey != null) {
            ResourceLocation cachedByName = skinNameCache.get(nameKey);
            if (cachedByName != null) return cachedByName;
        }
        ResourceLocation resolved = resolveSkin(uuid, name);
        rememberSkin(uuid, name, resolved);
        return resolved;
    }

    private static ResourceLocation resolveSkin(UUID uuid, String name) {
        Minecraft minecraft = Minecraft.getInstance();
        if (name == null || name.isEmpty())
            return DefaultPlayerSkin.get(uuid != null ? uuid : NIL_UUID).texture();
        try {
            GameProfile profile = new GameProfile(
                uuid != null && !uuid.equals(NIL_UUID) ? uuid : NIL_UUID, name);
            return minecraft.getSkinManager().getInsecureSkin(profile).texture();
        } catch (Exception e) {
            return DefaultPlayerSkin.get(uuid != null ? uuid : NIL_UUID).texture();
        }
    }
}
