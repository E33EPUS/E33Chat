package com.niuqu.chatbubble.config;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** media_auto_clean (2.4.0 sync): Boolean default + old-file null merge. */
class ServerConfigManagerTest {

    @Test
    void defaultsHaveAutoCleanOn() {
        assertTrue(ServerConfig.defaults().media_auto_clean);
    }

    @Test
    void oldFileWithoutKeyMergesToEnabled() throws IOException {
        Path f = Files.createTempFile("e33srv", ".json");
        Files.writeString(f, "{\"use_tpa\":false,\"history_enabled\":false,\"media_enabled\":true}");
        ServerConfig cfg = ServerConfigManager.load(f);
        assertNotNull(cfg);
        // null in an old file = enabled (default on)
        assertTrue(cfg.media_auto_clean);
    }

    @Test
    void explicitFalseIsKept() throws IOException {
        Path f = Files.createTempFile("e33srv", ".json");
        Files.writeString(f, "{\"use_tpa\":false,\"media_enabled\":true,\"media_auto_clean\":false}");
        ServerConfig cfg = ServerConfigManager.load(f);
        assertNotNull(cfg);
        assertFalse(cfg.media_auto_clean);
    }
}
