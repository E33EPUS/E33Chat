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
        // Headless env: no Minecraft client, so addMessage must not touch it
        ChatMessageStore.localPlayerSupplier = () -> null;
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
            java.util.UUID.randomUUID(), net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("meta content"), false,
            "Steve", true, "Steve");
        assertEquals("meta content", ChatMessageStore.extractWhisperContent("Steve 悄悄对你说: junk", meta));
    }

    @Test void extractWhisper_blankMetaFallsBackToText() {
        ChatMessageStore.SenderMeta meta = new ChatMessageStore.SenderMeta(
            java.util.UUID.randomUUID(), net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("   "), false,
            "Steve", true, "Steve");
        assertEquals("fallback", ChatMessageStore.extractWhisperContent("你悄悄对 Steve 说: fallback", meta));
    }

    @Test void extractWhisper_trimTrailingSpaces() {
        assertEquals("hi", ChatMessageStore.extractWhisperContent("你悄悄对 Steve 说: hi   ", null));
    }

    @Test void extractWhisper_firstSeparatorNotLast() {
        // 2.2.7: content may itself contain ": " — the structural colon is the FIRST
        // one (lastIndexOf would truncate the content at its inner colon)
        assertEquals("a: b", ChatMessageStore.extractWhisperContent("Steve: a: b", null));
        assertEquals("a：b", ChatMessageStore.extractWhisperContent("Steve：a：b", null));
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
        // the name slot is the TARGET — the sender is self, so fallback wins
        var line = net.minecraft.text.Text.literal("你悄悄地对[称号]E33EPUS说：hi");
        assertEquals("E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("E33EPUS")).getString());
    }

    @Test void whisperName_zhIncoming() {
        var line = net.minecraft.text.Text.literal("[称号]E33EPUS悄悄地对你说：hi");
        assertEquals("[称号]E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("E33EPUS")).getString());
    }

    @Test void whisperName_enOutgoing() {
        // the name slot is the TARGET — the sender is self, so fallback wins
        var line = net.minecraft.text.Text.literal("You whisper to [VIP]Steve: hi");
        assertEquals("Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("Steve")).getString());
    }

    @Test void whisperName_enIncoming() {
        var line = net.minecraft.text.Text.literal("[VIP]Steve whispers to you: hi");
        assertEquals("[VIP]Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("Steve")).getString());
    }

    @Test void whisperName_incomingResetsVanillaItalic() {
        // vanilla decorates whisper lines gray+italic; the extracted name must not
        // inherit the line decoration's italic (applies to child runs, hence mapStyle)
        var line = net.minecraft.text.Text.literal("[称号]E33EPUS悄悄地对你说：hi")
            .fillStyle(net.minecraft.text.Style.EMPTY.withItalic(true));
        var name = ChatMessageStore.extractWhisperDisplayName(line,
            net.minecraft.text.Text.literal("E33EPUS"));
        assertEquals("[称号]E33EPUS", name.getString());
        var it = new boolean[]{true};
        name.visit((style, text) -> { if (style.isItalic()) it[0] = false; return java.util.Optional.empty(); },
            net.minecraft.text.Style.EMPTY);
        assertTrue(it[0], "whisper sender name must not be italic");
    }

    @Test void whisperName_noTemplateFallsBack() {
        var line = net.minecraft.text.Text.literal("Steve sends you something");
        assertEquals("Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("Steve")).getString());
    }

    @Test void whisperName_zhOutgoingPluginDecoratedSender() {
        // Some plugins echo the outgoing line with the SENDER's decorated name
        // in front ("[称号]E33EPUS悄悄地对Steve说") — extract it instead of the bare fallback.
        var line = net.minecraft.text.Text.literal("[称号]E33EPUS悄悄地对Steve说：hi");
        assertEquals("[称号]E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("E33EPUS")).getString());
    }

    @Test void whisperName_enOutgoingPluginDecoratedSender() {
        var line = net.minecraft.text.Text.literal("[VIP]E33EPUS whisper to Steve: hi");
        assertEquals("[VIP]E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("E33EPUS")).getString());
    }

    @Test void whisperName_zhOutgoingVanillaStillFallsBack() {
        // vanilla "你悄悄地对X说" must keep falling back to self — the prefix "你" is
        // the pronoun, not a real name
        var line = net.minecraft.text.Text.literal("你悄悄地对[称号]E33EPUS说：hi");
        assertEquals("E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("E33EPUS")).getString());
    }

    @Test void whisperName_enOutgoingVanillaStillFallsBack() {
        var line = net.minecraft.text.Text.literal("You whisper to [VIP]Steve: hi");
        assertEquals("Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.text.Text.literal("Steve")).getString());
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

    // ---- Plain-text history lines: date-time \t sender \t content \t flags ----

    private static ChatMessageStore.ChatMessage testMsg(boolean own, boolean system) {
        return new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("今天去打龙吗"),
            1782900000000L,
            own, system, null, null, "", 1, null, false, null);
    }

    @Test void tsv_roundTripPreservesCore() {
        var msg = testMsg(true, false);
        var back = ChatMessageStore.fromLine(ChatMessageStore.toLine(msg));
        assertNotNull(back);
        assertEquals(msg.time(), back.time());
        assertEquals("Steve", back.senderName().getString());
        assertEquals("今天去打龙吗", back.content().getString());
        assertTrue(back.isOwn());
        assertFalse(back.isSystem());
    }

    @Test void tsv_lineReadsLikeALog() {
        String line = ChatMessageStore.toLine(testMsg(false, false));
        // no JSON syntax at all: no braces, quotes or escapes
        assertFalse(line.contains("{") || line.contains("\"") || line.contains("\\"), line);
        assertTrue(line.endsWith("\t"), line);
        // local-time rendering: date part is fixed, hour depends on timezone
        assertTrue(line.startsWith("2026-07-01T"), line);
        assertTrue(line.contains(":00:00\tSteve\t今天去打龙吗\t"), line);
    }

    @Test void tsv_flagsCombinable() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("hi"),
            1782900000000L,
            true, false, null, null, "", 1, null, true, null);
        String line = ChatMessageStore.toLine(msg);
        assertTrue(line.endsWith("\tMW"), line);
        var back = ChatMessageStore.fromLine(line);
        assertNotNull(back);
        assertTrue(back.isOwn());
        assertTrue(back.whisper());
    }

    @Test void tsv_systemFlag() {
        String line = ChatMessageStore.toLine(testMsg(false, true));
        assertTrue(line.endsWith("\tS"), line);
        assertTrue(ChatMessageStore.fromLine(line).isSystem());
    }

    @Test void tsv_escapingRoundTrip() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("a\tb\nc\\d\r\nx"),
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        String line = ChatMessageStore.toLine(msg);
        // escaped, so the line still has exactly 4 tab-separated fields
        assertEquals(4, line.split("\t", -1).length);
        assertFalse(line.contains("\r"), line);
        var back = ChatMessageStore.fromLine(line);
        assertNotNull(back);
        assertEquals("a\tb\nc\\d\r\nx", back.content().getString());
    }

    @Test void tsv_optionalColumnsWhisperPartnerAndReply() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("hi"),
            1782900000000L,
            false, false, "引用的内容", "Alex", "", 1, "Steve", true, "Alex");
        String line = ChatMessageStore.toLine(msg);
        // columns: time sender content flags partner replySender replyContent
        assertEquals(7, line.split("\t", -1).length);
        var back = ChatMessageStore.fromLine(line);
        assertNotNull(back);
        assertTrue(back.whisper());
        assertEquals("Alex", back.whisperPartner());
        assertEquals("引用的内容", back.replyContent());
        assertEquals("Alex", back.replySender());
    }

    @Test void tsv_plainLineHasNoTrailingColumns() {
        String line = ChatMessageStore.toLine(testMsg(false, false));
        assertEquals(4, line.split("\t", -1).length);
    }

    @Test void tsv_styledSenderStoredAsPlainText() {
        var styled = net.minecraft.text.Text.literal("Steve")
            .formatted(net.minecraft.util.Formatting.AQUA);
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(), styled,
            net.minecraft.text.Text.literal("hi"),
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        String line = ChatMessageStore.toLine(msg);
        // styling is dropped entirely: no § codes in the file
        assertFalse(line.contains("§"), line);
        assertTrue(line.contains("\tSteve\thi\t"), line);
        var back = ChatMessageStore.fromLine(line);
        assertNotNull(back);
        assertEquals("Steve", back.senderName().getString());
    }

    @Test void tsv_badLineReturnsNull() {
        assertNull(ChatMessageStore.fromLine(""));
        assertNull(ChatMessageStore.fromLine("two\tfields"));
        assertNull(ChatMessageStore.fromLine("not-a-date\tSteve\thi\t"));
        assertNull(ChatMessageStore.fromLine("2026-07-01T12:00:00\tSteve\t   \t"));
    }

    @Test void tsv_blankContentDropped() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("   "),
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        assertNull(ChatMessageStore.fromLine(ChatMessageStore.toLine(msg)));
    }

    // ---- parseStyledText: section codes back into styled components ----

    @Test void parseStyled_colorAndReset() {
        var c = ChatMessageStore.parseStyledText("§6[称号]§bE33EPUS");
        // getString() is plain text; the colors live in the styled siblings
        assertEquals("[称号]E33EPUS", c.getString());
        assertEquals(net.minecraft.util.Formatting.GOLD.getColorValue(),
            c.getSiblings().get(0).getStyle().getColor().getRgb());
        assertEquals(net.minecraft.util.Formatting.AQUA.getColorValue(),
            c.getSiblings().get(1).getStyle().getColor().getRgb());
    }

    @Test void parseStyled_boldItalic() {
        var c = ChatMessageStore.parseStyledText("§lhi");
        assertTrue(c.getSiblings().get(0).getStyle().isBold());
        var c2 = ChatMessageStore.parseStyledText("§o斜体");
        assertTrue(c2.getSiblings().get(0).getStyle().isItalic());
    }

    @Test void parseStyled_plainTextNoStyle() {
        var c = ChatMessageStore.parseStyledText("hello");
        assertEquals("hello", c.getString());
        assertTrue(c.getStyle().isEmpty());
    }

    @Test void parseStyled_unknownCodeFallsThrough() {
        var c = ChatMessageStore.parseStyledText("ab§xcd");
        assertEquals("ab§xcd", c.getString());
    }

    @Test void legacyJsonLineStillLoads() {
        String legacy = "{\"sender\":\"Steve\",\"content\":\"hi\",\"time\":1782900000000,\"own\":true,\"system\":false}";
        var back = ChatMessageStore.fromLine(legacy);
        assertNotNull(back);
        assertEquals("Steve", back.senderName().getString());
        assertEquals("hi", back.content().getString());
        assertTrue(back.isOwn());
        assertEquals(1782900000000L, back.time());
    }

    // ---- sensitive commands never land in the history file ----

    @Test void sensitiveCommand_loginWithPassword() {
        assertTrue(ChatMessageStore.isSensitiveCommand("/login hunter2"));
        assertTrue(ChatMessageStore.isSensitiveCommand("/l hunter2"));
    }

    @Test void sensitiveCommand_registerAndAuth() {
        assertTrue(ChatMessageStore.isSensitiveCommand("/register hunter2 hunter2"));
        assertTrue(ChatMessageStore.isSensitiveCommand("/reg hunter2"));
        assertTrue(ChatMessageStore.isSensitiveCommand("/auth 123456"));
    }

    @Test void sensitiveCommand_changepass() {
        assertTrue(ChatMessageStore.isSensitiveCommand("/changepassword old new"));
        assertTrue(ChatMessageStore.isSensitiveCommand("/changepass old new"));
        assertTrue(ChatMessageStore.isSensitiveCommand("/cp old new"));
    }

    @Test void sensitiveCommand_noArgStillSensitive() {
        assertTrue(ChatMessageStore.isSensitiveCommand("/login"));
        assertTrue(ChatMessageStore.isSensitiveCommand("  /register  "));
    }

    @Test void sensitiveCommand_caseInsensitive() {
        assertTrue(ChatMessageStore.isSensitiveCommand("/LOGIN hunter2"));
    }

    @Test void sensitiveCommand_plainChatNotFlagged() {
        assertFalse(ChatMessageStore.isSensitiveCommand("login please"));
        assertFalse(ChatMessageStore.isSensitiveCommand("hi everyone"));
        assertFalse(ChatMessageStore.isSensitiveCommand("/list"));
        assertFalse(ChatMessageStore.isSensitiveCommand("/log"));
        assertFalse(ChatMessageStore.isSensitiveCommand(null));
    }

    @Test void sensitiveCommand_skippedFromLine() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("/login hunter2"),
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        assertNull(ChatMessageStore.toLine(msg));
    }

    // ---- retention cleanup ----

    @Test void retention_zeroKeepsForever() {
        assertFalse(ChatMessageStore.isExpired(0, 10_000_000L, 0));
    }

    @Test void retention_olderThanDaysExpires() {
        long now = 10_000_000L;
        assertTrue(ChatMessageStore.isExpired(now - 31L * 24 * 3600_000, now, 30));
    }

    @Test void retention_withinDaysNotExpired() {
        long now = 10_000_000L;
        assertFalse(ChatMessageStore.isExpired(now - 29L * 24 * 3600_000, now, 30));
    }

    @Test void retention_exactlyAtBoundaryKept() {
        long now = 10_000_000L;
        assertFalse(ChatMessageStore.isExpired(now - 30L * 24 * 3600_000, now, 30));
    }

    // ---- blocked players: exact-name match (case-insensitive, §-stripped) ----

    @Test void blocked_exactNameHits() {
        assertTrue(ChatMessageStore.matchesBlocked("Steve", List.of("Steve")));
        assertTrue(ChatMessageStore.matchesBlocked("Steve", List.of("Alex", "Steve", "Bob")));
    }

    @Test void blocked_caseInsensitiveHits() {
        assertTrue(ChatMessageStore.matchesBlocked("Steve", List.of("steve")));
        assertTrue(ChatMessageStore.matchesBlocked("STEVE", List.of("Steve")));
    }

    @Test void blocked_colorCodesHit() {
        assertTrue(ChatMessageStore.matchesBlocked("§6Steve§r", List.of("Steve")));
        assertTrue(ChatMessageStore.matchesBlocked("Steve", List.of("§6Steve§r")));
    }

    @Test void blocked_whitespaceTrimmed() {
        assertTrue(ChatMessageStore.matchesBlocked("Steve", List.of("  Steve  ")));
        assertTrue(ChatMessageStore.matchesBlocked("  Steve  ", List.of("Steve")));
    }

    @Test void blocked_substringMisses() {
        assertFalse(ChatMessageStore.matchesBlocked("SteveAdmin", List.of("Steve")));
        assertFalse(ChatMessageStore.matchesBlocked("Steve", List.of("Stev")));
    }

    @Test void blocked_emptyOrNullSafe() {
        assertFalse(ChatMessageStore.matchesBlocked(null, List.of("Steve")));
        assertFalse(ChatMessageStore.matchesBlocked("", List.of("Steve")));
        assertFalse(ChatMessageStore.matchesBlocked("Steve", null));
        assertFalse(ChatMessageStore.matchesBlocked("Steve", List.of()));
        assertFalse(ChatMessageStore.matchesBlocked("Steve", List.of("  ")));
    }

    @Test void blocked_senderNameFallbackHits() {
        // Nickname plugins put the tab-list display name in senderName; exact match
        // on the full decorated string (list holds the display name as shown)
        var decorated = net.minecraft.text.Text.literal("[VIP]Steve");
        assertTrue(ChatMessageStore.isPlayerBlocked(null, decorated, List.of("[VIP]Steve")));
        assertTrue(ChatMessageStore.isPlayerBlocked("Alex", decorated, List.of("[vip]steve")));
        // Exact-name semantics: a bare profile name does NOT match a decorated display name
        assertFalse(ChatMessageStore.isPlayerBlocked(null, decorated, List.of("Steve")));
    }

    @Test void blocked_rawNamePrimaryKeyHits() {
        assertTrue(ChatMessageStore.isPlayerBlocked("Steve", null, List.of("Steve")));
        assertFalse(ChatMessageStore.isPlayerBlocked("Steve", null, List.of("Alex")));
    }

    @Test void blocked_purgeDropsSenderKeepsOwnAndSystem() throws Exception {
        var blockedMsg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("hello"),
            1L, false, false, null, null, "", 1, "Steve", false, null);
        var ownMsg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Me"),
            net.minecraft.text.Text.literal("hi"),
            2L, true, false, null, null, "", 1, "Me", false, null);
        var sysMsg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.text.Text.literal("Steve"),
            net.minecraft.text.Text.literal("joined the game"),
            3L, false, true, null, null, "", 1, "Steve", false, null);

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<Object>) field.get(null);
        messages.clear();
        messages.add(blockedMsg);
        messages.add(ownMsg);
        messages.add(sysMsg);

        ChatMessageStore.purgeBlocked(List.of("Steve"));

        assertEquals(2, messages.size());
        assertSame(ownMsg, messages.get(0));
        assertSame(sysMsg, messages.get(1));
    }

    // ---- anti-spam merge must not inherit the previous bubble's quote block ----
    // Regression: send a quoted message, then an identical unquoted follow-up.
    // The merge collapsed them into one bubble but copied last.replyContent()/
    // replySender(), leaving a stale [引用] block the follow-up never had.

    @Test void antiSpamMerge_identicalFollowUpDropsStaleReplyBlock() throws Exception {
        clearMessagesAndMetas();
        var uuid = java.util.UUID.randomUUID();
        var sender = net.minecraft.text.Text.literal("Steve");
        // Server pre-registers a quote meta for the first message
        ChatMessageStore.applyChatMeta(uuid, String.valueOf("妈妈".hashCode()),
            "A", "A的话", List.of());
        ChatMessageStore.addMessage(net.minecraft.text.Text.literal("妈妈"),
            uuid, sender, false, "Steve", false, null, false);
        // Identical follow-up with no quote meta
        ChatMessageStore.addMessage(net.minecraft.text.Text.literal("妈妈"),
            uuid, sender, false, "Steve", false, null, false);

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(1, messages.size(), "identical sends must collapse into one bubble");
        var merged = messages.get(0);
        assertEquals(2, merged.duplicateCount());
        assertNull(merged.replyContent(), "merged bubble must not inherit the first send's quote block");
        assertNull(merged.replySender());
    }

    @Test void antiSpamMerge_quotedFollowUpKeepsReplyBlock() throws Exception {
        clearMessagesAndMetas();
        var uuid = java.util.UUID.randomUUID();
        var sender = net.minecraft.text.Text.literal("Steve");
        // First message quoted, follow-up identical AND quoted again
        ChatMessageStore.applyChatMeta(uuid, String.valueOf("妈妈".hashCode()),
            "A", "A的话", List.of());
        ChatMessageStore.addMessage(net.minecraft.text.Text.literal("妈妈"),
            uuid, sender, false, "Steve", false, null, false);
        ChatMessageStore.applyChatMeta(uuid, String.valueOf("妈妈".hashCode()),
            "A", "A的话", List.of());
        ChatMessageStore.addMessage(net.minecraft.text.Text.literal("妈妈"),
            uuid, sender, false, "Steve", false, null, false);

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(1, messages.size());
        var merged = messages.get(0);
        assertEquals("A的话", merged.replyContent(), "re-quoted follow-up keeps the quote block");
    }

    private static void clearMessagesAndMetas() throws Exception {
        var messagesField = ChatMessageStore.class.getDeclaredField("messages");
        messagesField.setAccessible(true);
        ((List<?>) messagesField.get(null)).clear();
        var metasField = ChatMessageStore.class.getDeclaredField("pendingMetas");
        metasField.setAccessible(true);
        ((java.util.Map<?, ?>) metasField.get(null)).clear();
    }

    // ---- stale server quote broadcast must not tag a merged bubble ----
    // B quotes A's "妈妈" and sends "？" (server broadcasts ChatMeta), then sends
    // an identical unquoted "？" which anti-spam merges into the first bubble.
    // The first quote's ChatMeta can arrive late (network round-trip) — the merge
    // keeps the first message's hash, so applyChatMeta matches the merged bubble
    // and would wrongly add a quote block the second send never had.

    @Test void applyChatMeta_lateQuoteDoesNotTagMergedBubble() throws Exception {
        clearMessagesAndMetas();
        var bUuid = java.util.UUID.randomUUID();
        var sender = net.minecraft.text.Text.literal("B");
        String hash = String.valueOf("？".hashCode());
        // First send: unquoted (this test isolates the late-ChatMeta path)
        ChatMessageStore.addMessage(net.minecraft.text.Text.literal("？"),
            bUuid, sender, false, "B", false, null, false);
        // Identical follow-up: anti-spam merges into one bubble, no quote block
        ChatMessageStore.addMessage(net.minecraft.text.Text.literal("？"),
            bUuid, sender, false, "B", false, null, false);
        // The first send's ChatMeta arrives after the merge
        ChatMessageStore.applyChatMeta(bUuid, hash, "A", "妈妈", List.of());

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(1, messages.size());
        var merged = messages.get(0);
        assertNull(merged.replyContent(),
            "late ChatMeta for the first send must not tag the merged bubble");
        assertNull(merged.replySender());
    }

    @Test void applyChatMeta_quoteStillTagsPlainMessage() throws Exception {
        clearMessagesAndMetas();
        var bUuid = java.util.UUID.randomUUID();
        var sender = net.minecraft.text.Text.literal("B");
        String hash = String.valueOf("？".hashCode());
        ChatMessageStore.addMessage(net.minecraft.text.Text.literal("？"),
            bUuid, sender, false, "B", false, null, false);
        ChatMessageStore.applyChatMeta(bUuid, hash, "A", "妈妈", List.of());

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(1, messages.size());
        assertEquals("妈妈", messages.get(0).replyContent(),
            "a non-merged message still receives its own quote meta");
    }
}
