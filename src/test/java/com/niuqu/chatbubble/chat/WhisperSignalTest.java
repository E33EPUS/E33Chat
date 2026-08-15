package com.niuqu.chatbubble.chat;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Whisper keyword table + matching helpers. The EN pattern must use real
 * word boundaries (a single-backslash \b is a literal backspace and never
 * matches) — regression guard for the 2.3.14 regex fix.
 */
class WhisperSignalTest {

    @Test void zhWordsMatchPlainContains() {
        assertTrue(WhisperSignal.containsZh("你悄悄对 Steve 说"));
        assertTrue(WhisperSignal.containsZh("whispers to you"));
        assertTrue(WhisperSignal.containsZh("私聊"));
        assertTrue(WhisperSignal.containsZh("密谈"));
        assertFalse(WhisperSignal.containsZh("hello world"));
        assertFalse(WhisperSignal.containsZh(null));
    }

    @Test void enPatternUsesWordBoundaries() {
        // Real whisper formats
        assertTrue(WhisperSignal.EN.matcher("pm to Steve: hi").find());
        assertTrue(WhisperSignal.EN.matcher("msg you: hi").find());
        assertTrue(WhisperSignal.EN.matcher("tell Steve hi").find());
        // Words embedded in longer names must NOT match (boundary requirement)
        assertFalse(WhisperSignal.EN.matcher("msg2u").find());
        assertFalse(WhisperSignal.EN.matcher("telegraph").find());
        // Consumers lowercase before matching (mixin echo check and
        // hasWhisperKeywordBeforeColon both do); the pattern itself is case-sensitive
        assertTrue(WhisperSignal.EN.matcher("msg: hi").find());
    }

    @Test void enPatternHandlesEmptyAndNullSafe() {
        assertFalse(WhisperSignal.EN.matcher("").find());
    }
}