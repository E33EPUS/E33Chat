package com.niuqu.chatbubble.config;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

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
                // log and fall through to defaults
            }
        }
        ServerConfig def = ServerConfig.defaults();
        save(path, def);
        return def;
    }

    public static void save(Path path, ServerConfig config) {
        try {
            Files.createDirectories(path.getParent());
            try (Writer w = new OutputStreamWriter(Files.newOutputStream(path), StandardCharsets.UTF_8)) {
                GSON.toJson(config, w);
            }
        } catch (Exception ignored) {}
    }
}
