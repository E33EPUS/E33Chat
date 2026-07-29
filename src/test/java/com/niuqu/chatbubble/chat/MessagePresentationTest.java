package com.niuqu.chatbubble.chat;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MessagePresentationTest {
    @Test void parsesColonSeparatedPlayerMessage() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve: hello there", List.of("Steve", "Alex"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hello there", parsed.orElseThrow().content());
    }

    @Test void parsesDecoratedColonMessage() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "[薄荷一区][主城] PlayerTitle_user/Ciao_Min: 额，在吗？",
            List.of("Ciao_Min", "Other"));
        assertTrue(parsed.isPresent());
        assertEquals("Ciao_Min", parsed.orElseThrow().playerName());
        assertEquals("额，在吗？", parsed.orElseThrow().content());
    }

    @Test void parsesNcrDoubleAngleFormat() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve >> hello there", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hello there", parsed.orElseThrow().content());
    }

    @Test void parsesAngleBracketFormat() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "<Steve> hello there", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hello there", parsed.orElseThrow().content());
    }

    @Test void parsesDecoratedAngleBracketPlayerMessage() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "<[VIP]Steve> hello there", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hello there", parsed.orElseThrow().content());
    }

    @Test void parsesBracketPrefixColonMessage() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "[Admin] Steve: hello there", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hello there", parsed.orElseThrow().content());
    }

    @Test void parsesFullwidthColonFormat() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve： 你好", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("你好", parsed.orElseThrow().content());
    }

    @Test void parsesChevronSeparatorFormat() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve » hi", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void rejectsAnnouncementsThatContainNames() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
            "善良冰淇淋提示：遇到争执不要急，先交流下！",
            List.of("Ciao_Min")).isEmpty());
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
            "最新版本：3.4.2 点击此处查看更新内容",
            List.of("Ciao_Min")).isEmpty());
    }

    @Test void rejectsSubstringMatches() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
            "custom tom says hi", List.of("tom")).isEmpty());
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
            "hiSteve: hello", List.of("Steve")).isEmpty());
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
            "Steve2: hello", List.of("Steve")).isEmpty());
    }

    @Test void returnsEmptyForNullInputs() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(null, List.of("Steve")).isEmpty());
        assertTrue(MessagePresentation.parseDecoratedPlayerLine("hi", null).isEmpty());
    }

    @Test void parsesLongPrefixWithDecorativeBrackets() {
        var prefix = "[" + "a".repeat(35) + "]";
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            prefix + "Steve >> hi", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void parsesAngleBracketShortName() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "<a> hi", List.of("a"));
        assertTrue(parsed.isPresent());
        assertEquals("a", parsed.orElseThrow().playerName());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void parsesBracketPrefixShortNameWithColon() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "[T]a: hi", List.of("a"));
        assertTrue(parsed.isPresent());
        assertEquals("a", parsed.orElseThrow().playerName());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void parsesBareShortNameWithColon() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "a: hi", List.of("a"));
        assertTrue(parsed.isPresent());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void rejectsBareShortNameWithoutStructure() {
        // no colon after the name — broadcast sentence, stays rejected
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
            "a joined the game", List.of("a")).isEmpty());
    }

    // ---- offline-server short/Chinese names ----

    @Test void parsesBareChineseNameWithColon() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "小明: 你好", List.of("小明"));
        assertTrue(parsed.isPresent());
        assertEquals("小明", parsed.orElseThrow().playerName());
        assertEquals("你好", parsed.orElseThrow().content());
    }

    @Test void rejectsBareChineseNameBroadcast() {
        assertTrue(MessagePresentation.parseDecoratedPlayerLine(
            "小明 加入了游戏", List.of("小明")).isEmpty());
    }

    @Test void parsesBracketedChineseName() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "<小明> 你好", List.of("小明"));
        assertTrue(parsed.isPresent());
        assertEquals("你好", parsed.orElseThrow().content());
    }

    @Test void parsesWithOfflineCachedNameInList() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "[VIP]Steve >> hi", List.of("OfflineGuy", "Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void longNameOrdinaryFormatStillParses() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve: hello there", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
    }

    // ---- legacy § color codes embedded as literal text content ----

    @Test void parsesLegacyColorCodeColonFormat() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "§6Steve§r: hi", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void parsesLegacyColorCodeCandidateVariant() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "§6Steve§r: hi", List.of("§6Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("§6Steve", parsed.orElseThrow().playerName());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void parsesLegacyColorCodeChevronFormat() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "§6Steve§r » hi", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    // ---- whitespace-only gap: broadcast sentence vs chat separator ----

    @Test void whitespaceGap_pureSpacesIsBroadcast() {
        assertTrue(MessagePresentation.isWhitespaceOnlyGap("Steve joined the game", 5, 6));
    }

    @Test void whitespaceGap_colonIsChat() {
        assertFalse(MessagePresentation.isWhitespaceOnlyGap("Steve: hi", 5, 7));
    }

    @Test void whitespaceGap_pipeIsChat() {
        assertFalse(MessagePresentation.isWhitespaceOnlyGap("Steve|hi", 5, 6));
    }

    @Test void whitespaceGap_dashIsChat() {
        assertFalse(MessagePresentation.isWhitespaceOnlyGap("Steve-hi", 5, 6));
    }

    @Test void whitespaceGap_colorCodeAndColonIsChat() {
        assertFalse(MessagePresentation.isWhitespaceOnlyGap("§6Steve§r: hi", 7, 11));
    }

    @Test void whitespaceGap_emptyRangeNotBroadcast() {
        assertFalse(MessagePresentation.isWhitespaceOnlyGap("Steve", 5, 5));
    }

    // ---- name-suffix decorations parse like prefixes ----

    @Test void parsesNameSuffixBracketTitle() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve[LV.10]: hello", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("Steve", parsed.orElseThrow().playerName());
        assertEquals("hello", parsed.orElseThrow().content());
    }

    @Test void parsesNameSuffixAfkTag() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve[AFK]: hi", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void parsesNameSuffixParenTitle() {
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve(VIP): hi", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("hi", parsed.orElseThrow().content());
    }

    @Test void unicodeArrowSeparatorNotSkipped() {
        // Documented unsupported: the parser stops at ➤, and at the call
        // site the whitespace-only name/content gap routes the message to
        // system gray text. Loosening separators to "any non-name char"
        // would misattribute comma-style broadcasts (Steve，welcome...).
        var parsed = MessagePresentation.parseDecoratedPlayerLine(
            "Steve ➤ hi", List.of("Steve"));
        assertTrue(parsed.isPresent());
        assertEquals("➤ hi", parsed.orElseThrow().content());
    }
}
