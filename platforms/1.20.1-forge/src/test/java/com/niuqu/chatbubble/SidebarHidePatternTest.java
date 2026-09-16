package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sidebar hide patterns go through the shared wildcard matcher. This pins the
 * behavior the spec documents ("* = wildcard, e.g. Islot_*, *[NPC]*") and
 * guards the quoting order that silently turned '*' into a literal.
 */
class SidebarHidePatternTest {

    @BeforeEach
    void loadConfig() {
        // Headless: ForgeConfigSpec values throw until a config is loaded.
        var cfg = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        cfg.set("configVersion", 1);
        ChatBubbleConfig.CLIENT_CONFIG.correct(cfg);
        ChatBubbleConfig.CLIENT_CONFIG.setConfig(cfg);
    }

    private static void setPatterns(List<String> patterns) {
        ChatBubbleConfig.SIDEBAR_HIDE_PATTERNS.set(patterns);
    }

    @Test
    void wildcardPatternsHideMatchingPlayers() {
        setPatterns(List.of("Islot_*", "*[NPC]*"));
        assertTrue(ChatBubbleConfig.isSidebarHidden("Islot_1"), "prefix wildcard must match");
        assertTrue(ChatBubbleConfig.isSidebarHidden("Islot_abc"), "prefix wildcard must match");
        assertTrue(ChatBubbleConfig.isSidebarHidden("Bob[NPC]"), "contains wildcard must match");
        assertFalse(ChatBubbleConfig.isSidebarHidden("Steve"), "unrelated names stay visible");
        assertFalse(ChatBubbleConfig.isSidebarHidden("Islot"), "a bare prefix is not a match");
    }

    @Test
    void literalBracketPatternDoesNotThrow() {
        setPatterns(List.of("[BOT]*"));
        assertTrue(ChatBubbleConfig.isSidebarHidden("[BOT]Steve"));
        assertFalse(ChatBubbleConfig.isSidebarHidden("Steve"));
    }

    @Test
    void noPatternsHidesNobody() {
        setPatterns(List.of());
        assertFalse(ChatBubbleConfig.isSidebarHidden("Anyone"));
    }

    @Test
    void nullAndEmptyNamesAreSafe() {
        setPatterns(List.of("*"));
        assertFalse(ChatBubbleConfig.isSidebarHidden(null));
        assertFalse(ChatBubbleConfig.isSidebarHidden(""));
    }
}
