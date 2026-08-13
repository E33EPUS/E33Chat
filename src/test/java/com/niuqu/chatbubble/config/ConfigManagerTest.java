package com.niuqu.chatbubble.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

/** Config persistence contract: zero-valued settings survive a round trip,
 * missing keys fall back to defaults, corrupt files are preserved for recovery. */
class ConfigManagerTest {

    @TempDir
    Path dir;

    @Test
    void zeroValuesSurviveRoundTrip() throws Exception {
        Path p = dir.resolve("client.json");
        ChatBubbleConfig c = ChatBubbleConfig.defaults()
            .withTheme("dark"); // use a with-method to mutate through the record
        // Build a config with explicit zero values via the full constructor.
        c = new ChatBubbleConfig(
            true, "dark", true, false, true, false, true,
            false, 0, 5, 1000, 4,
            "#1E90FF", "#4A4A4A", "#FFFFFF", "#FFFFFF",
            false, false, true, false, true, false,
            java.util.List.of(), java.util.List.of(), java.util.List.of(),
            true, true, 0, true, true, true,
            true, 0, 0, false, false, false, 0, 0, 0,
            "slide", "slide", "fade", "fade",
            true, true,
            null, null, null, null);
        ConfigManager.save(p, c);

        ChatBubbleConfig loaded = ConfigManager.load(p);
        assertEquals(0, loaded.panelOpacity(), "panelOpacity 0 (fully transparent) must survive");
        assertEquals(0, loaded.soundVolume(), "soundVolume 0 (muted) must survive");
        assertEquals(0, loaded.bannerCornerRadius(), "bannerCornerRadius 0 (square) must survive");
        assertEquals(0, loaded.mentionBannerDuration(), "mentionBannerDuration 0 must survive");
    }

    @Test
    void missingKeysFallBackToDefaults() throws Exception {
        Path p = dir.resolve("client.json");
        // Hand-written legacy file without the new nullable keys.
        Files.writeString(p, "{"
            + "\"enabled\":true,\"theme\":\"dark\",\"panelOpacity\":40,\"soundVolume\":60"
            + "}", StandardCharsets.UTF_8);

        ChatBubbleConfig loaded = ConfigManager.load(p);
        assertEquals(40, loaded.panelOpacity(), "explicit panelOpacity must load");
        assertEquals(60, loaded.soundVolume(), "explicit soundVolume must load");
        assertEquals(4, loaded.bannerCornerRadius(), "missing corner radius falls back to default");
        assertEquals(4, loaded.mentionBannerDuration(), "missing duration falls back to default");
    }

    @Test
    void corruptFileIsPreservedAsBackup() throws Exception {
        Path p = dir.resolve("client.json");
        Files.writeString(p, "{ this is not json", StandardCharsets.UTF_8);

        ChatBubbleConfig loaded = ConfigManager.load(p);
        assertNotNull(loaded);
        assertTrue(loaded.enabled(), "corrupt file falls back to defaults");
        assertTrue(Files.exists(dir.resolve("client.json.bak")), "corrupt file kept for manual recovery");
        assertTrue(Files.exists(p), "fresh default file written");
    }
}
