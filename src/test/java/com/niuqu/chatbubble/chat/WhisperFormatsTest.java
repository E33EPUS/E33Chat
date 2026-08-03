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

    // ---- plugin whisper keywords (2.2.8 audit G1) ----

    @Test void pluginKeywordBeforeColonIsWhisper() {
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("私信 Steve: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("[密谈] Steve: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve 密谈: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve PM you: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve message to you: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve msg you: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve tell you: hi"));
    }

    @Test void pluginKeywordCaseInsensitive() {
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve pm you: hi"));
        assertTrue(MessagePresentation.hasWhisperKeywordBeforeColon("Steve Pm: hi"));
    }

    @Test void shortEnglishWordsNeedWordBoundary() {
        // "pm"/"msg" 嵌在别的词里不算关键词（hepm/msgbox），防止公屏误判
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve hepm: hi"));
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve msgbox: hi"));
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve teller: hi"));
    }

    @Test void pluginKeywordAfterColonStillPublicChat() {
        // 关键词在冒号后 = 公屏内容讨论私聊，不算 whisper（G1 回归）
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve: 加我私信"));
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve: send me a PM"));
        assertFalse(MessagePresentation.hasWhisperKeywordBeforeColon("Steve: I used /msg"));
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
