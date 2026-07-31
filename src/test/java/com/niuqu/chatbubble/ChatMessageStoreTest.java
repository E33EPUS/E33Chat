package com.niuqu.chatbubble;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageStoreTest {

    // Static echo/quote state lives across tests; reset so each test starts clean
    @BeforeEach
    void resetState() throws Exception {
        var echoes = ChatMessageStore.class.getDeclaredField("pendingEchoes");
        echoes.setAccessible(true);
        ((List<?>) echoes.get(null)).clear();
        var quoteTime = ChatMessageStore.class.getDeclaredField("lastQuoteSendTime");
        quoteTime.setAccessible(true);
        quoteTime.setLong(null, 0);
    }

    // ---- containsWholeName: echo suppression must not misattribute
    // substring names (SteveAdmin / Steve2) to the local player ----

    @Test void wholeName_decoratedPrefixHits() {
        assertTrue(ChatMessageStore.containsWholeName("[VIP]Steve", "Steve"));
        assertTrue(ChatMessageStore.containsWholeName("[Admin][VIP10]Steve", "Steve"));
    }

    @Test void wholeName_angleBracketsHit() {
        assertTrue(ChatMessageStore.containsWholeName("<Steve>", "Steve"));
    }

    @Test void wholeName_colorCodesHit() {
        assertTrue(ChatMessageStore.containsWholeName("§6Steve§r", "Steve"));
        assertTrue(ChatMessageStore.containsWholeName("[VIP]Steve§r", "Steve"));
    }

    @Test void wholeName_exactMatchHits() {
        assertTrue(ChatMessageStore.containsWholeName("Steve", "Steve"));
    }

    @Test void wholeName_tabDisplayNameVariantHits() {
        assertTrue(ChatMessageStore.containsWholeName("[VIP]Steve", "[VIP]Steve"));
    }

    @Test void wholeName_suffixExtensionMisses() {
        assertFalse(ChatMessageStore.containsWholeName("SteveAdmin", "Steve"));
        assertFalse(ChatMessageStore.containsWholeName("Steve2", "Steve"));
        assertFalse(ChatMessageStore.containsWholeName("Steve_Miner", "Steve"));
    }

    @Test void wholeName_prefixExtensionMisses() {
        assertFalse(ChatMessageStore.containsWholeName("hiSteve", "Steve"));
        assertFalse(ChatMessageStore.containsWholeName("NotSteve: hello", "Steve"));
    }

    @Test void wholeName_nullAndEmptySafe() {
        assertFalse(ChatMessageStore.containsWholeName(null, "Steve"));
        assertFalse(ChatMessageStore.containsWholeName("[VIP]Steve", null));
        assertFalse(ChatMessageStore.containsWholeName("[VIP]Steve", ""));
    }

    @Test void isNamePart_classification() {
        assertTrue(ChatMessageStore.isNamePart('a'));
        assertTrue(ChatMessageStore.isNamePart('Z'));
        assertTrue(ChatMessageStore.isNamePart('0'));
        assertTrue(ChatMessageStore.isNamePart('_'));
        assertFalse(ChatMessageStore.isNamePart('§'));
        assertFalse(ChatMessageStore.isNamePart(']'));
        assertFalse(ChatMessageStore.isNamePart(' '));
    }

    // ---- extractWhisperContent: vanilla whisper line -> message content.
    // Used by the [私聊]/[引用] vanilla-chat repost; the outgoing-echo path must
    // pass null meta (echo never sets pending meta, residue would corrupt content) ----

    @Test void extractWhisper_semicolonColonEnglish() {
        assertEquals("hi", ChatMessageStore.extractWhisperContent("You whisper to Steve: hi", null));
    }

    @Test void extractWhisper_semicolonColonChinese() {
        assertEquals("你好", ChatMessageStore.extractWhisperContent("你悄悄对 Steve 说: 你好", null));
    }

    @Test void extractWhisper_fullWidthColon() {
        assertEquals("全角内容", ChatMessageStore.extractWhisperContent("你悄悄对 Steve 说：全角内容", null));
    }

    @Test void extractWhisper_selfWhisper() {
        assertEquals("hi", ChatMessageStore.extractWhisperContent("你悄悄对自己说: hi", null));
    }

    @Test void extractWhisper_noColonKeepsWholeLine() {
        assertEquals("no colon here", ChatMessageStore.extractWhisperContent("no colon here", null));
    }

    @Test void extractWhisper_metaContentWins() {
        ChatMessageStore.SenderMeta meta = new ChatMessageStore.SenderMeta(
            java.util.UUID.randomUUID(), net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("meta content"), false,
            "Steve", true, "Steve");
        assertEquals("meta content", ChatMessageStore.extractWhisperContent("Steve 悄悄对你说: junk", meta));
    }

    @Test void extractWhisper_blankMetaFallsBackToText() {
        ChatMessageStore.SenderMeta meta = new ChatMessageStore.SenderMeta(
            java.util.UUID.randomUUID(), net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("   "), false,
            "Steve", true, "Steve");
        assertEquals("fallback", ChatMessageStore.extractWhisperContent("你悄悄对 Steve 说: fallback", meta));
    }

    @Test void extractWhisper_trimTrailingSpaces() {
        assertEquals("hi", ChatMessageStore.extractWhisperContent("你悄悄对 Steve 说: hi   ", null));
    }

    // ---- wasRecentQuoteAt: quote reply residue drives the [引用] tag on the
    // outgoing echo (pendingReplyContent is consumed by the local bubble first).
    // Pure-function form (quoteTime, now) so tests never depend on shared state ----

    @Test void quoteWindow_neverSetIsFalse() {
        long now = System.currentTimeMillis();
        assertFalse(ChatMessageStore.wasRecentQuoteAt(0, now));
    }

    @Test void quoteWindow_withinWindowTrue() {
        long now = System.currentTimeMillis();
        assertTrue(ChatMessageStore.wasRecentQuoteAt(now - 1000, now));
    }

    @Test void quoteWindow_edgeExactlyWindowIsFalse() {
        long now = System.currentTimeMillis();
        assertFalse(ChatMessageStore.wasRecentQuoteAt(now - ChatMessageStore.QUOTE_ECHO_WINDOW_MS, now));
    }

    @Test void quoteWindow_expiredFalse() {
        long now = System.currentTimeMillis();
        assertFalse(ChatMessageStore.wasRecentQuoteAt(now - ChatMessageStore.QUOTE_ECHO_WINDOW_MS - 1, now));
    }

    @Test void quoteWindow_publicGetterAfterSetPendingReply() {
        ChatMessageStore.setPendingReply("> quoted", "Steve");
        assertTrue(ChatMessageStore.wasRecentQuote());
    }

    @Test void echoQuoted_quoteReplyCarriesFlag() {
        ChatMessageStore.setPendingReply("> quoted", "Steve");
        ChatMessageStore.incrementPendingEcho("quote reply text");
        var echo = ChatMessageStore.consumeEchoBySystemChat("quote reply text");
        assertTrue(echo.matched());
        assertTrue(echo.quoted());
    }

    @Test void echoQuoted_plainSendNotQuoted() {
        ChatMessageStore.incrementPendingEcho("plain text");
        var echo = ChatMessageStore.consumeEchoBySystemChat("plain text");
        assertTrue(echo.matched());
        assertFalse(echo.quoted());
    }

    @Test void echoQuoted_markerClearedAfterIncrement() {
        ChatMessageStore.setPendingReply("> quoted", "Steve");
        ChatMessageStore.incrementPendingEcho("first send");
        // marker is consumed onto the echo record: a second send inherits nothing
        ChatMessageStore.incrementPendingEcho("second send");
        var echo = ChatMessageStore.consumeEchoBySystemChat("second send");
        assertTrue(echo.matched());
        assertFalse(echo.quoted());
    }

    @Test void suppressQuoted_snapshotsLatestSend() {
        ChatMessageStore.setPendingReply("> quoted", "Steve");
        ChatMessageStore.incrementPendingEcho("quoted send");
        ChatMessageStore.markSuppressCapture();
        assertTrue(ChatMessageStore.consumeSuppressQuoted());
        // read-once
        assertFalse(ChatMessageStore.consumeSuppressQuoted());
    }

    @Test void suppressQuoted_plainSendNotQuoted() {
        ChatMessageStore.incrementPendingEcho("plain send");
        ChatMessageStore.markSuppressCapture();
        assertFalse(ChatMessageStore.consumeSuppressQuoted());
    }

    @Test void suppressQuoted_emptyQueueNotQuoted() {
        ChatMessageStore.markSuppressCapture();
        assertFalse(ChatMessageStore.consumeSuppressQuoted());
    }

    // ---- extractWhisperDisplayName: decorated sender from a vanilla whisper line,
    // keeping prefix ("[称号]") and team-color styles for the [私聊]/[引用] repost ----

    @Test void whisperName_zhOutgoing() {
        var line = net.minecraft.network.chat.Component.literal("你悄悄地对[称号]E33EPUS说：hi");
        assertEquals("[称号]E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("E33EPUS")).getString());
    }

    @Test void whisperName_zhIncoming() {
        var line = net.minecraft.network.chat.Component.literal("[称号]E33EPUS悄悄地对你说：hi");
        assertEquals("[称号]E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("E33EPUS")).getString());
    }

    @Test void whisperName_enOutgoing() {
        var line = net.minecraft.network.chat.Component.literal("You whisper to [VIP]Steve: hi");
        assertEquals("[VIP]Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("Steve")).getString());
    }

    @Test void whisperName_enIncoming() {
        var line = net.minecraft.network.chat.Component.literal("[VIP]Steve whispers to you: hi");
        assertEquals("[VIP]Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("Steve")).getString());
    }

    @Test void whisperName_noTemplateFallsBack() {
        var line = net.minecraft.network.chat.Component.literal("Steve sends you something");
        assertEquals("Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("Steve")).getString());
    }

    // ---- isRepostDuplicate: the server echoes a whisper twice (~15ms apart);
    // both would rewrite to the same <name>[私聊] line without this guard ----

    @Test void repostDedup_sameTextWithinWindowDuplicates() {
        assertTrue(ChatMessageStore.isRepostDuplicate("<A>[私聊] hi", 1000, "<A>[私聊] hi", 1500));
    }

    @Test void repostDedup_differentTextNotDuplicate() {
        assertFalse(ChatMessageStore.isRepostDuplicate("<A>[私聊] hi", 1000, "<A>[私聊] bye", 1500));
    }

    @Test void repostDedup_sameTextOutsideWindowNotDuplicate() {
        assertFalse(ChatMessageStore.isRepostDuplicate("<A>[私聊] hi", 1000, "<A>[私聊] hi", 1000 + ChatMessageStore.REPOST_DEDUP_MS));
    }

    @Test void repostDedup_firstRepostNeverDuplicate() {
        assertFalse(ChatMessageStore.isRepostDuplicate(null, 0, "<A>[私聊] hi", System.currentTimeMillis()));
    }
}
