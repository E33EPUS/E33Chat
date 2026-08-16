package com.niuqu.chatbubble;
import com.niuqu.chatbubble.store.EchoTracker;
import com.niuqu.chatbubble.store.BlockList;
import com.niuqu.chatbubble.store.ChatMessageStore;
import com.niuqu.chatbubble.config.ChatBubbleConfig;

import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageStoreTest {

    // Static echo/quote state lives across tests; reset so each test starts clean
    @BeforeEach
    void resetState() throws Exception {
        var echoes = EchoTracker.class.getDeclaredField("pendingEchoes");
        echoes.setAccessible(true);
        ((List<?>) echoes.get(null)).clear();
        var quoteTime = EchoTracker.class.getDeclaredField("lastQuoteSendTime");
        quoteTime.setAccessible(true);
        quoteTime.setLong(null, 0);
        // Headless env: no Minecraft client, so addMessage must not touch it
        ChatMessageStore.localPlayerSupplier = () -> null;
        // ChatBubbleConfig values throw before the spec is loaded; load defaults
        // so ChatBubbleConfig.ANTI_SPAM.get() works headless
        var cfg = com.electronwill.nightconfig.core.CommentedConfig.inMemory();
        cfg.set("configVersion", 1);
        ChatBubbleConfig.CLIENT_CONFIG.correct(cfg);
        ChatBubbleConfig.CLIENT_CONFIG.setConfig(cfg);
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
        assertFalse(ChatMessageStore.wasRecentQuoteAt(now - EchoTracker.QUOTE_ECHO_WINDOW_MS, now));
    }

    @Test void quoteWindow_expiredFalse() {
        long now = System.currentTimeMillis();
        assertFalse(ChatMessageStore.wasRecentQuoteAt(now - EchoTracker.QUOTE_ECHO_WINDOW_MS - 1, now));
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
        var line = net.minecraft.network.chat.Component.literal("你悄悄地对[称号]E33EPUS说：hi");
        assertEquals("E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("E33EPUS")).getString());
    }

    @Test void whisperName_zhIncoming() {
        var line = net.minecraft.network.chat.Component.literal("[称号]E33EPUS悄悄地对你说：hi");
        assertEquals("[称号]E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("E33EPUS")).getString());
    }

    @Test void whisperName_enOutgoing() {
        // the name slot is the TARGET — the sender is self, so fallback wins
        var line = net.minecraft.network.chat.Component.literal("You whisper to [VIP]Steve: hi");
        assertEquals("Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("Steve")).getString());
    }

    @Test void whisperName_enIncoming() {
        var line = net.minecraft.network.chat.Component.literal("[VIP]Steve whispers to you: hi");
        assertEquals("[VIP]Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("Steve")).getString());
    }

    @Test void whisperName_incomingResetsVanillaItalic() {
        // vanilla decorates whisper lines gray+italic; the extracted name must not
        // inherit the line decoration's italic (applies to child runs, hence mapStyle)
        var line = net.minecraft.network.chat.Component.literal("[称号]E33EPUS悄悄地对你说：hi")
            .withStyle(s -> s.withItalic(true));
        var name = ChatMessageStore.extractWhisperDisplayName(line,
            net.minecraft.network.chat.Component.literal("E33EPUS"));
        assertEquals("[称号]E33EPUS", name.getString());
        var it = new boolean[]{true};
        name.visit((style, text) -> { if (style.isItalic()) it[0] = false; return java.util.Optional.empty(); },
            net.minecraft.network.chat.Style.EMPTY);
        assertTrue(it[0], "whisper sender name must not be italic");
    }

    @Test void whisperName_noTemplateFallsBack() {
        var line = net.minecraft.network.chat.Component.literal("Steve sends you something");
        assertEquals("Steve", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("Steve")).getString());
    }

    @Test void whisperName_zhOutgoingPluginDecoratedSender() {
        // Some plugins echo the outgoing line with the SENDER's decorated name
        // in front ("[称号]E33EPUS悄悄地对Steve说") — extract it instead of the bare fallback.
        var line = net.minecraft.network.chat.Component.literal("[称号]E33EPUS悄悄地对Steve说：hi");
        assertEquals("[称号]E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("E33EPUS")).getString());
    }

    @Test void whisperName_enOutgoingPluginDecoratedSender() {
        var line = net.minecraft.network.chat.Component.literal("[VIP]E33EPUS whisper to Steve: hi");
        assertEquals("[VIP]E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("E33EPUS")).getString());
    }

    @Test void whisperName_zhOutgoingVanillaStillFallsBack() {
        // vanilla "你悄悄地对X说" must keep falling back to self — the prefix "你" is
        // the pronoun, not a real name
        var line = net.minecraft.network.chat.Component.literal("你悄悄地对[称号]E33EPUS说：hi");
        assertEquals("E33EPUS", ChatMessageStore.extractWhisperDisplayName(line, net.minecraft.network.chat.Component.literal("E33EPUS")).getString());
    }

    @Test void whisperName_enOutgoingVanillaStillFallsBack() {
        var line = net.minecraft.network.chat.Component.literal("You whisper to [VIP]Steve: hi");
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
        assertFalse(ChatMessageStore.isRepostDuplicate("<A>[私聊] hi", 1000, "<A>[私聊] hi", 1000 + EchoTracker.REPOST_DEDUP_MS));
    }

    @Test void repostDedup_firstRepostNeverDuplicate() {
        assertFalse(ChatMessageStore.isRepostDuplicate(null, 0, "<A>[私聊] hi", System.currentTimeMillis()));
    }

    // ---- JSONL history lines: full styled sender/content + uuid ----
    // One JSON object per line: colors, click/hover events and the sender UUID
    // survive the quit-to-title reload. Plain-text \t lines from older builds
    // still load (fromLine legacy branch).

    private static ChatMessageStore.ChatMessage testMsg(boolean own, boolean system) {
        return new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("今天去打龙吗"),
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

    @Test void jsonl_lineIsJsonWithFullText() {
        String line = ChatMessageStore.toLine(testMsg(false, false));
        // JSONL: starts with a brace and carries styled-component JSON when the
        // component codecs are available (in-game), plain-text fields headless
        assertTrue(line.startsWith("{"), line);
        assertTrue(line.contains("\"uuid\""), line);
        assertTrue(line.contains("今天去打龙吗"), line);
        assertTrue(line.contains("\"senderJson\"") || line.contains("\"sender\""), line);
        assertTrue(line.contains("\"contentJson\"") || line.contains("\"content\""), line);
    }

    @Test void jsonl_flagsCombinable() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("hi"),
            1782900000000L,
            true, false, null, null, "", 1, null, true, null);
        var back = ChatMessageStore.fromLine(ChatMessageStore.toLine(msg));
        assertNotNull(back);
        assertTrue(back.isOwn());
        assertTrue(back.whisper());
    }

    @Test void jsonl_systemFlag() {
        String line = ChatMessageStore.toLine(testMsg(false, true));
        assertTrue(line.contains("\"system\":true"), line);
        assertTrue(ChatMessageStore.fromLine(line).isSystem());
    }

    @Test void jsonl_escapingRoundTrip() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("a\tb\nc\\d\r\nx"),
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        var back = ChatMessageStore.fromLine(ChatMessageStore.toLine(msg));
        assertNotNull(back);
        assertEquals("a\tb\nc\\d\r\nx", back.content().getString());
    }

    @Test void jsonl_optionalColumnsWhisperPartnerAndReply() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("hi"),
            1782900000000L,
            false, false, "引用的内容", "Alex", "", 1, "Steve", true, "Alex");
        var back = ChatMessageStore.fromLine(ChatMessageStore.toLine(msg));
        assertNotNull(back);
        assertTrue(back.whisper());
        assertEquals("Alex", back.whisperPartner());
        assertEquals("引用的内容", back.replyContent());
        assertEquals("Alex", back.replySender());
        assertEquals("Steve", back.rawPlayerName());
    }

    @Test void jsonl_uuidPersisted() {
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.nameUUIDFromBytes("steve".getBytes()),
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("hi"),
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        var back = ChatMessageStore.fromLine(ChatMessageStore.toLine(msg));
        assertNotNull(back);
        assertEquals(msg.senderUUID(), back.senderUUID());
    }

    @Test void jsonl_styledSenderStylePreserved() {
        // Style codecs need a live Minecraft registries environment; headless
        // tests can't bootstrap them, so toLine degrades to plain-text fields
        // here. The styled path is exercised in-game.
        org.junit.jupiter.api.Assumptions.assumeTrue(
            net.minecraft.client.Minecraft.getInstance() != null,
            "styled serialization requires a running Minecraft client");
        var styled = net.minecraft.network.chat.Component.literal("Steve")
            .withStyle(net.minecraft.ChatFormatting.AQUA);
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(), styled,
            net.minecraft.network.chat.Component.literal("hi"),
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        var back = ChatMessageStore.fromLine(ChatMessageStore.toLine(msg));
        assertNotNull(back);
        assertEquals("Steve", back.senderName().getString());
        assertEquals(net.minecraft.ChatFormatting.AQUA.getColor(),
            back.senderName().getStyle().getColor());
    }

    @Test void jsonl_clickPreserved() {
        // Same headless limitation as styledSenderStylePreserved — in-game verified.
        org.junit.jupiter.api.Assumptions.assumeTrue(
            net.minecraft.client.Minecraft.getInstance() != null,
            "click/hover serialization requires a running Minecraft client");
        var click = new net.minecraft.network.chat.ClickEvent(
            net.minecraft.network.chat.ClickEvent.Action.RUN_COMMAND, "/tp Steve 0 100 0");
        var content = net.minecraft.network.chat.Component.literal("传我一下")
            .withStyle(s -> s.withClickEvent(click));
        var msg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Steve"),
            content,
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        var back = ChatMessageStore.fromLine(ChatMessageStore.toLine(msg));
        assertNotNull(back);
        assertEquals(click, back.content().getStyle().getClickEvent());
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
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("   "),
            1782900000000L, false, false, null, null, "", 1, null, false, null);
        assertNull(ChatMessageStore.fromLine(ChatMessageStore.toLine(msg)));
    }

    // ---- parseStyledText: section codes back into styled components ----

    @Test void parseStyled_colorAndReset() {
        var c = ChatMessageStore.parseStyledText("§6[称号]§bE33EPUS");
        // getString() is plain text; the colors live in the styled siblings
        assertEquals("[称号]E33EPUS", c.getString());
        assertEquals(net.minecraft.ChatFormatting.GOLD.getColor(),
            c.getSiblings().get(0).getStyle().getColor().getValue());
        assertEquals(net.minecraft.ChatFormatting.AQUA.getColor(),
            c.getSiblings().get(1).getStyle().getColor().getValue());
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
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("/login hunter2"),
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
        assertTrue(BlockList.matchesBlocked("Steve", List.of("Steve")));
        assertTrue(BlockList.matchesBlocked("Steve", List.of("Alex", "Steve", "Bob")));
    }

    @Test void blocked_caseInsensitiveHits() {
        assertTrue(BlockList.matchesBlocked("Steve", List.of("steve")));
        assertTrue(BlockList.matchesBlocked("STEVE", List.of("Steve")));
    }

    @Test void blocked_colorCodesHit() {
        assertTrue(BlockList.matchesBlocked("§6Steve§r", List.of("Steve")));
        assertTrue(BlockList.matchesBlocked("Steve", List.of("§6Steve§r")));
    }

    @Test void blocked_whitespaceTrimmed() {
        assertTrue(BlockList.matchesBlocked("Steve", List.of("  Steve  ")));
        assertTrue(BlockList.matchesBlocked("  Steve  ", List.of("Steve")));
    }

    @Test void blocked_substringMisses() {
        assertFalse(BlockList.matchesBlocked("SteveAdmin", List.of("Steve")));
        assertFalse(BlockList.matchesBlocked("Steve", List.of("Stev")));
    }

    @Test void blocked_emptyOrNullSafe() {
        assertFalse(BlockList.matchesBlocked(null, List.of("Steve")));
        assertFalse(BlockList.matchesBlocked("", List.of("Steve")));
        assertFalse(BlockList.matchesBlocked("Steve", null));
        assertFalse(BlockList.matchesBlocked("Steve", List.of()));
        assertFalse(BlockList.matchesBlocked("Steve", List.of("  ")));
    }

    @Test void blocked_senderNameFallbackHits() {
        // Nickname plugins put the tab-list display name in senderName; exact match
        // on the full decorated string (list holds the display name as shown)
        var decorated = net.minecraft.network.chat.Component.literal("[VIP]Steve");
        assertTrue(BlockList.isPlayerBlocked(null, decorated, List.of("[VIP]Steve")));
        assertTrue(BlockList.isPlayerBlocked("Alex", decorated, List.of("[vip]steve")));
        // Exact-name semantics: a bare profile name does NOT match a decorated display name
        assertFalse(BlockList.isPlayerBlocked(null, decorated, List.of("Steve")));
    }

    @Test void blocked_rawNamePrimaryKeyHits() {
        assertTrue(BlockList.isPlayerBlocked("Steve", null, List.of("Steve")));
        assertFalse(BlockList.isPlayerBlocked("Steve", null, List.of("Alex")));
    }

    @Test void blocked_purgeDropsSenderKeepsOwnAndSystem() throws Exception {
        var blockedMsg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("hello"),
            1L, false, false, null, null, "", 1, "Steve", false, null);
        var ownMsg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Me"),
            net.minecraft.network.chat.Component.literal("hi"),
            2L, true, false, null, null, "", 1, "Me", false, null);
        var sysMsg = new ChatMessageStore.ChatMessage(
            java.util.UUID.randomUUID(),
            net.minecraft.network.chat.Component.literal("Steve"),
            net.minecraft.network.chat.Component.literal("joined the game"),
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
        var sender = net.minecraft.network.chat.Component.literal("Steve");
        // Server pre-registers a quote meta for the first message
        ChatMessageStore.applyChatMeta(uuid, "Steve", String.valueOf("妈妈".hashCode()),
            "A", "A的话", List.of());
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("妈妈"),
            uuid, sender, false, "Steve", false, null, false);
        // Identical follow-up with no quote meta
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("妈妈"),
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
        var sender = net.minecraft.network.chat.Component.literal("Steve");
        // First message quoted, follow-up identical AND quoted again
        ChatMessageStore.applyChatMeta(uuid, "Steve", String.valueOf("妈妈".hashCode()),
            "A", "A的话", List.of());
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("妈妈"),
            uuid, sender, false, "Steve", false, null, false);
        ChatMessageStore.applyChatMeta(uuid, "Steve", String.valueOf("妈妈".hashCode()),
            "A", "A的话", List.of());
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("妈妈"),
            uuid, sender, false, "Steve", false, null, false);

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(1, messages.size());
        var merged = messages.get(0);
        assertEquals("A的话", merged.replyContent(), "re-quoted follow-up keeps the quote block");
    }

    // ---- B4: 通知/声音副作用经观察者委托（store 不再直接调 Minecraft/controller）----

    @Test void effectObserver_systemBannerDelegatedToRegisteredObserver() throws Exception {
        clearMessagesAndMetas();
        final int[] calls = {0};
        ChatMessageStore.setMessageEffectObserver(new ChatMessageStore.MessageEffectObserver() {
            @Override public void onSystemMessage(net.minecraft.network.chat.Component content, int index) { calls[0]++; }
            @Override public void onMentionOrQuote(net.minecraft.network.chat.Component content, ChatMessageStore.SenderMeta meta, int index, String replySender) {}
            @Override public void onWhisperReceived(java.util.UUID senderUUID, net.minecraft.network.chat.Component senderName, net.minecraft.network.chat.Component content, int index) {}
            @Override public void onPublicChatSound() {}
            @Override public void onQuoteSound() {}
        });
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("死亡消息"),
            new java.util.UUID(0, 0), net.minecraft.network.chat.Component.literal("系统"),
            true, "系统", false, null, false);
        assertEquals(1, calls[0], "system banner enabled (default) must delegate to the observer");
        ChatMessageStore.setMessageEffectObserver(null);
    }

    private static void clearMessagesAndMetas() throws Exception {
        var messagesField = ChatMessageStore.class.getDeclaredField("messages");
        messagesField.setAccessible(true);
        ((List<?>) messagesField.get(null)).clear();
        var metasField = EchoTracker.class.getDeclaredField("pendingMetas");
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
        var sender = net.minecraft.network.chat.Component.literal("B");
        String hash = String.valueOf("？".hashCode());
        // First send: unquoted (this test isolates the late-ChatMeta path)
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("？"),
            bUuid, sender, false, "B", false, null, false);
        // Identical follow-up: anti-spam merges into one bubble, no quote block
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("？"),
            bUuid, sender, false, "B", false, null, false);
        // The first send's ChatMeta arrives after the merge
        ChatMessageStore.applyChatMeta(bUuid, "B", hash, "A", "妈妈", List.of());

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
        var sender = net.minecraft.network.chat.Component.literal("B");
        String hash = String.valueOf("？".hashCode());
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("？"),
            bUuid, sender, false, "B", false, null, false);
        ChatMessageStore.applyChatMeta(bUuid, "B", hash, "A", "妈妈", List.of());

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(1, messages.size());
        assertEquals("妈妈", messages.get(0).replyContent(),
            "a non-merged message still receives its own quote meta");
    }

    // ---- quoted send then an UNQUOTED send with different content ----
    // No merge happens (different text), so the second bubble must not inherit
    // the first send's quote block. User-required behavior.

    @Test void quoteInheritance_differentContentSecondSendIsClean() throws Exception {
        clearMessagesAndMetas();
        var uuid = java.util.UUID.randomUUID();
        var sender = net.minecraft.network.chat.Component.literal("B");
        // First send: quoted (server pre-registers quote meta for "？")
        ChatMessageStore.applyChatMeta(uuid, "B", String.valueOf("？".hashCode()),
            "A", "妈妈", List.of());
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("？"),
            uuid, sender, false, "B", false, null, false);
        // Second send: different content, no quote — no merge, independent bubble
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("别的"),
            uuid, sender, false, "B", false, null, false);

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(2, messages.size(), "different content must not anti-spam merge");
        assertEquals("妈妈", messages.get(0).replyContent(), "first quoted send keeps its quote");
        assertNull(messages.get(1).replyContent(),
            "unquoted second send with different content must not inherit a quote block");
        assertNull(messages.get(1).replySender());
    }

    // ---- offline-mode fallback: receiving side stores UUID(0,0) ----
    // Server broadcasts the sender's real UUID, but on the receiving client the
    // message was attributed to UUID(0,0) (offline/cracked player). The quote
    // meta must still match via the raw player name.

    @Test void applyChatMeta_offlinePlayerMatchesByRawName() throws Exception {
        clearMessagesAndMetas();
        var bUuid = java.util.UUID.randomUUID();
        var sender = net.minecraft.network.chat.Component.literal("B");
        String hash = String.valueOf("？".hashCode());
        // B's message lands with UUID(0,0) on A's client (offline fallback)
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("？"),
            new java.util.UUID(0, 0), sender, false, "B", false, null, false);
        // Server broadcasts B's real UUID + name; UUID won't match, name must
        ChatMessageStore.applyChatMeta(bUuid, "B", hash, "A", "妈妈", List.of());

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(1, messages.size());
        assertEquals("妈妈", messages.get(0).replyContent(),
            "offline player's message must receive the quote block via name match");
    }

    @Test void applyChatMeta_uuidMatchStillWorksWhenNameAbsent() throws Exception {
        clearMessagesAndMetas();
        var bUuid = java.util.UUID.randomUUID();
        var sender = net.minecraft.network.chat.Component.literal("B");
        String hash = String.valueOf("？".hashCode());
        // Normal (online) path: UUID matches, senderName empty in the meta
        ChatMessageStore.addMessage(net.minecraft.network.chat.Component.literal("？"),
            bUuid, sender, false, "B", false, null, false);
        ChatMessageStore.applyChatMeta(bUuid, "", hash, "A", "妈妈", List.of());

        var field = ChatMessageStore.class.getDeclaredField("messages");
        field.setAccessible(true);
        @SuppressWarnings("unchecked")
        var messages = (List<ChatMessageStore.ChatMessage>) field.get(null);
        assertEquals(1, messages.size());
        assertEquals("妈妈", messages.get(0).replyContent(),
            "UUID match alone must still apply the quote meta");
    }
}
