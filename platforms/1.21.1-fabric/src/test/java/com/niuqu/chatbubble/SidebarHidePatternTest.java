package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Sidebar hide patterns go through the shared wildcard matcher. This pins the
 * behavior the Forge/Neo spec documents ("* = wildcard, e.g. Islot_*, *[NPC]*")
 * and guards the regex-splice crash Fabric used to throw on '['.
 */
class SidebarHidePatternTest {

    @Test
    void wildcardPatternsHideMatchingPlayers() {
        ChatBubbleConfig c = ChatBubbleConfig.defaults()
            .withSidebarHidePatterns(List.of("Islot_*", "*[NPC]*"));

        assertTrue(c.isSidebarHidden("Islot_1"), "prefix wildcard must match");
        assertTrue(c.isSidebarHidden("Islot_abc"), "prefix wildcard must match");
        assertTrue(c.isSidebarHidden("Bob[NPC]"), "contains wildcard must match");
        assertFalse(c.isSidebarHidden("Steve"), "unrelated names stay visible");
        assertFalse(c.isSidebarHidden("Islot"), "a bare prefix is not a match");
    }

    @Test
    void literalBracketPatternDoesNotThrow() {
        ChatBubbleConfig c = ChatBubbleConfig.defaults()
            .withSidebarHidePatterns(List.of("[BOT]*"));
        // Fabric used to splice this into a regex and throw PatternSyntaxException.
        assertTrue(c.isSidebarHidden("[BOT]Steve"));
        assertFalse(c.isSidebarHidden("Steve"));
    }

    @Test
    void noPatternsHidesNobody() {
        ChatBubbleConfig c = ChatBubbleConfig.defaults()
            .withSidebarHidePatterns(List.of());
        assertFalse(c.isSidebarHidden("Anyone"));
    }

    @Test
    void nullAndEmptyNamesAreSafe() {
        ChatBubbleConfig c = ChatBubbleConfig.defaults()
            .withSidebarHidePatterns(List.of("*"));
        assertFalse(c.isSidebarHidden(null));
        assertFalse(c.isSidebarHidden(""));
    }
}
