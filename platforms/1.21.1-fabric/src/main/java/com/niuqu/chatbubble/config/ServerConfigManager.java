package com.niuqu.chatbubble.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.mojang.logging.LogUtils;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public final class ServerConfigManager {
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private ServerConfigManager() {}

    public static ServerConfig load(Path path) {
        if (Files.exists(path)) {
            try (Reader r = new InputStreamReader(Files.newInputStream(path), StandardCharsets.UTF_8)) {
                ServerConfig loaded = GSON.fromJson(r, ServerConfig.class);
                if (loaded != null) return loaded;
            } catch (Exception e) {
                LogUtils.getLogger().warn("[e33chat] Failed to read server config {}, keeping a backup", path, e);
                backupBrokenFile(path);
            }
        }
        ServerConfig def = ServerConfig.defaults();
        save(path, def);
        return def;
    }

    /** Keep a corrupt file on disk for recovery instead of letting the
     *  defaults overwrite the only copy (mirrors the client ConfigManager). */
    private static void backupBrokenFile(Path path) {
        try {
            Path backup = path.resolveSibling(path.getFileName() + ".broken-" + System.currentTimeMillis());
            Files.move(path, backup);
            LogUtils.getLogger().warn("[e33chat] Kept broken server config as {}", backup);
        } catch (Exception ignored) {}
    }

    public static boolean save(Path path, ServerConfig config) {
        try {
            Files.createDirectories(path.getParent());
            // Write-then-move so a crash mid-write cannot truncate the file.
            Path tmp = path.resolveSibling(path.getFileName() + ".tmp");
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(tmp), StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
            try {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                    java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            } catch (java.nio.file.AtomicMoveNotSupportedException unsupported) {
                Files.move(tmp, path, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            return true;
        } catch (Exception e) {
            LogUtils.getLogger().warn("[e33chat] Failed to save server config {}", path, e);
            return false;
        }
    }
}
