package com.niuqu.chatbubble.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class WildcardPatternsTest {

    @Test
    void starMatchesAnyRun() {
        assertTrue(WildcardPatterns.matches("Islot_1", "Islot_*"));
        assertTrue(WildcardPatterns.matches("Islot_", "Islot_*"));
        assertTrue(WildcardPatterns.matches("[NPC]Bob", "*[NPC]*"));
        assertTrue(WildcardPatterns.matches("a", "*"));
    }

    @Test
    void starIsCaseInsensitive() {
        assertTrue(WildcardPatterns.matches("islot_a", "Islot_*"));
        assertTrue(WildcardPatterns.matches("ISLOT_A", "islot_*"));
    }

    @Test
    void questionMatchesExactlyOneChar() {
        assertTrue(WildcardPatterns.matches("Bob1", "Bob?"));
        assertFalse(WildcardPatterns.matches("Bob", "Bob?"));
        assertFalse(WildcardPatterns.matches("Bob12", "Bob?"));
    }

    @Test
    void regexMetaCharactersAreLiteral() {
        // The Fabric crash: raw '[', '(' and '+' used to be spliced into a regex.
        assertTrue(WildcardPatterns.matches("[NPC]Bob", "[NPC]*"));
        assertTrue(WildcardPatterns.matches("(Bot)Steve", "(Bot)*"));
        assertTrue(WildcardPatterns.matches("a+b", "a+b"));
        assertTrue(WildcardPatterns.matches("a.b", "a.b"));
        assertFalse(WildcardPatterns.matches("axb", "a.b"));
    }

    @Test
    void anchoredToWholeName() {
        // The Forge/Neo bug in reverse: a bare substring must not match.
        assertFalse(WildcardPatterns.matches("SteveAdmin", "Steve"));
        assertFalse(WildcardPatterns.matches("NotSteve", "Steve"));
        assertTrue(WildcardPatterns.matches("Steve", "Steve"));
    }

    @Test
    void guardsAgainstNullAndBlank() {
        assertFalse(WildcardPatterns.matches(null, "*"));
        assertFalse(WildcardPatterns.matches("Steve", null));
        assertFalse(WildcardPatterns.matches("Steve", "   "));
    }

    @Test
    void starInTheMiddle() {
        assertTrue(WildcardPatterns.matches("Steve_AFK", "Steve*AFK"));
        assertFalse(WildcardPatterns.matches("Steve_AFK", "Steve*XYZ"));
    }
}
