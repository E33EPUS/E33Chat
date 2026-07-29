package com.niuqu.chatbubble.chat;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class WhisperFormatsTest {

    // ---- hasWhisperKeywordBeforeColon ----

    @Test void keywordBeforeColonIsWhisper() {
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve 悄悄对你说: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve whispers to you: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("[私聊] Steve -> 你: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("密语 Steve: hi"));
    }

    @Test void keywordAfterColonIsPublicChat() {
        // NCR strips the chat key, so public chat discussing whispers reaches
        // the keyword layer — it must not be claimed as a whisper
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve: 为什么不能用私聊"));
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve: I used /whisper"));
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve：说说悄悄话"));
    }

    @Test void noColonWholeTextScanned() {
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve whispers to you"));
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve says hello"));
    }

    // ---- extractWhisperContent ----

    @Test void extractsAfterColon() {
        assertEquals("hi", MessagePresentation.extractWhisperContent("Steve 悄悄对你说: hi", "Steve"));
    }

    @Test void multiColonContentKeptWhole() {
        // lastIndexOf truncated at the LAST colon, dropping the first half
        assertEquals("看这句: 引用", MessagePresentation.extractWhisperContent(
            "Steve 悄悄对你说: 看这句: 引用", "Steve"));
    }

    @Test void arrowFormatExtractsAtColon() {
        assertEquals("hi", MessagePresentation.extractWhisperContent("Steve -> you: hi", "Steve"));
    }

    @Test void chevronFormatWithoutColon() {
        assertEquals("hi", MessagePresentation.extractWhisperContent("Steve >> hi", "Steve"));
    }

    @Test void noSeparatorTrims() {
        assertEquals("hi there", MessagePresentation.extractWhisperContent("Steve hi there", "Steve"));
    }
}
