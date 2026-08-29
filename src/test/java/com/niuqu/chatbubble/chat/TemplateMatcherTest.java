package com.niuqu.chatbubble.chat;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;

class TemplateMatcherTest {

    // Mirrors the mixin resolver: a name resolves when it equals or contains a known
    // player name (plugin decorations wrap the bare name, e.g. "[称号]Steve")
    private static final TemplateMatcher.NameResolver KNOWN = name -> {
        if (name == null) return false;
        for (String n : List.of("Steve", "Alex", "E33EPUS")) {
            if (name.equals(n) || name.contains(n)) return true;
        }
        return false;
    };

    private static TemplateMatcher.CompiledTemplate chat(String raw) {
        TemplateMatcher.CompileResult r = TemplateMatcher.compile(raw);
        assertNull(r.error(), () -> "compile failed: " + r.error() + " for " + raw);
        assertFalse(r.template().whisper(), "expected chat template: " + raw);
        return r.template();
    }

    private static TemplateMatcher.CompiledTemplate whisper(String raw) {
        TemplateMatcher.CompileResult r = TemplateMatcher.compile(raw);
        assertNull(r.error(), () -> "compile failed: " + r.error() + " for " + raw);
        assertTrue(r.template().whisper(), "expected whisper template: " + raw);
        return r.template();
    }

    private static Optional<TemplateMatcher.TemplateResult> matchChat(String text, String tpl) {
        return TemplateMatcher.match(text, List.of(chat(tpl)), List.of(), KNOWN);
    }

    private static Optional<TemplateMatcher.TemplateResult> match(String text, String whisperTpl, String chatTpl) {
        return TemplateMatcher.match(text,
            chatTpl != null ? List.of(chat(chatTpl)) : List.of(),
            whisperTpl != null ? List.of(whisper(whisperTpl)) : List.of(), KNOWN);
    }

    // ===== compile validation =====

    @Test void rejectsBlank() { assertNotNull(TemplateMatcher.compile("   ").error()); }

    @Test void rejectsLiteralOnly() { assertNotNull(TemplateMatcher.compile("Hello world").error()); }

    @Test void rejectsNoContent() { assertNotNull(TemplateMatcher.compile("{display_name}: ").error()); }

    @Test void rejectsMultipleContent() {
        assertNotNull(TemplateMatcher.compile("{display_name}: {content} {content}").error());
    }

    @Test void acceptsContentNotLast() {
        // 2.2.7: content may sit mid-template — suffix-style formats like "[聊天]" work
        TemplateMatcher.CompiledTemplate t = chat("{display_name}: {content} [聊天]");
        var r = TemplateMatcher.match("Steve: hi [聊天]", List.of(t), List.of(), KNOWN).orElseThrow();
        assertEquals("Steve", r.displayName());
        assertEquals("hi", r.content());
    }

    @Test void rejectsDuplicateField() {
        // 2.2.7: repeated fields would create duplicate named groups -> PatternSyntaxException;
        // compile must reject gracefully instead of crashing
        assertNotNull(TemplateMatcher.compile("{prefix}{prefix}{display_name}: {content}").error());
        assertNotNull(TemplateMatcher.compile("{display_name}{display_name}: {content}").error());
        assertNotNull(TemplateMatcher.compile("{sender}{sender}: {content}").error());
    }

    @Test void rejectsChatWithoutNameField() {
        assertNotNull(TemplateMatcher.compile("prefix {content}").error());
    }

    @Test void rejectsDispAndNameTogether() {
        assertNotNull(TemplateMatcher.compile("{display_name}{name}: {content}").error());
    }

    @Test void rejectsMisspelledContentPlaceholder() {
        // {conten} is unknown -> treated as literal -> no {content} field -> invalid
        assertNotNull(TemplateMatcher.compile("{display_name}: {conten}").error());
    }

    @Test void acceptsWhisperWithoutNameField() {
        whisper("{sender}悄悄地对你说: {content}");
    }

    @Test void acceptsUnknownLiteralBraces() {
        TemplateMatcher.CompiledTemplate t = chat("{display_name}: {foo} {content}");
        assertEquals(List.of("foo"), t.unknownFields());
    }

    // ===== chat matching =====

    @Test void matchesColonFormatWithOffsets() {
        var r = matchChat("Steve: hello", "{display_name}: {content}").orElseThrow();
        assertEquals("Steve", r.displayName());
        assertEquals("hello", r.content());
        assertEquals("Steve", r.verifiedName());
        assertEquals(0, r.nameStart());
        assertEquals(5, r.nameEnd());
        assertEquals(7, r.contentStart());
        assertEquals(12, r.contentEnd());
        assertFalse(r.whisper());
    }

    @Test void matchesDecoratedName() {
        var r = matchChat("[称号]Steve: hello", "{display_name}: {content}").orElseThrow();
        assertEquals("[称号]Steve", r.displayName());
        assertEquals("hello", r.content());
    }

    @Test void matchesArrowFormat() {
        var r = matchChat("Steve >> hello", "{display_name} >> {content}").orElseThrow();
        assertEquals("Steve", r.displayName());
        assertEquals("hello", r.content());
    }

    @Test void matchesPrefixField() {
        // the <...> brackets force the prefix group to capture the decoration
        var r = matchChat("[A]<Steve>: hello", "{prefix}<{display_name}>: {content}").orElseThrow();
        assertEquals("[A]", r.prefix());
        assertEquals("Steve", r.displayName());
        assertEquals(0, r.nameStart());
        assertEquals(9, r.nameEnd());
    }

    @Test void matchesNameContainingColon() {
        var r = matchChat("[A:1]Steve: hi", "{display_name}: {content}").orElseThrow();
        assertEquals("[A:1]Steve", r.displayName());
        assertEquals("hi", r.content());
    }

    @Test void matchesSpaceBeforeSeparator() {
        // the name area may itself contain ": " when the decoration does — the lazy
        // group backtracks to the separator that makes the whole line match
        var r = matchChat("[A] Steve: hi", "{display_name}: {content}").orElseThrow();
        assertEquals("[A] Steve", r.displayName());
        assertEquals("hi", r.content());
    }

    @Test void contentKeepsInnerColonsAndArrows() {
        var r = matchChat("Steve: a: b >> c", "{display_name}: {content}").orElseThrow();
        assertEquals("a: b >> c", r.content());
    }

    @Test void contentAllowsNewline() {
        var r = matchChat("Steve: line1\nline2", "{display_name}: {content}").orElseThrow();
        assertEquals("line1\nline2", r.content());
    }

    @Test void emojiOffsetsAlignWithJavaChars() {
        String text = "Steve: 你好😀世界";
        var r = matchChat(text, "{display_name}: {content}").orElseThrow();
        assertEquals("你好😀世界", r.content());
        assertEquals("Steve: ".length(), r.contentStart());
        assertEquals(text.length(), r.contentEnd());
        assertEquals("你好😀世界", text.substring(r.contentStart(), r.contentEnd()));
    }

    @Test void regexMetaInLiteralsIsQuoted() {
        var r = matchChat("Steve [x] hello", "{display_name} [x] {content}").orElseThrow();
        assertEquals("Steve", r.displayName());
        assertEquals("hello", r.content());
        var arrow = matchChat("Steve » hello", "{display_name} » {content}").orElseThrow();
        assertEquals("hello", arrow.content());
        var cn = matchChat("Steve（你好）", "{display_name}（{content}）").orElseThrow();
        assertEquals("你好", cn.content());
    }

    @Test void unknownPlaceholderActsAsLiteral() {
        // {foo} is not a field -> literal in the pattern, so it anchors the match
        var r = matchChat("Steve: {foo} hi", "{display_name}: {foo} {content}").orElseThrow();
        assertEquals("hi", r.content());
    }

    @Test void emptyContentMatches() {
        var r = matchChat("Steve: ", "{display_name}: {content}").orElseThrow();
        assertEquals("", r.content());
    }

    @Test void noMatchReturnsEmpty() {
        assertTrue(matchChat("Steve hello", "{display_name}: {content}").isEmpty());
        assertTrue(matchChat("Some random text without any name", "{display_name}: {content}").isEmpty());
    }

    @Test void unknownNameFailsGate() {
        assertTrue(matchChat("Server: 重启中", "{display_name}: {content}").isEmpty());
    }

    @Test void firstChatTemplateWins() {
        var r = TemplateMatcher.match("Steve >> hi",
            List.of(chat("{display_name}: {content}"), chat("{display_name} >> {content}")),
            List.of(), KNOWN).orElseThrow();
        assertEquals("hi", r.content());
        assertEquals("{display_name} >> {content}", r.template().raw());
    }

    // ===== 2.2.7: {sep} placeholder + real plugin formats =====

    @Test void sepMatchesCommonSeparators() {
        // {sep} matches >> / colon-family / » / > or plain spaces — one template fits
        // multiple separator styles (EssentialsChat, CMI, DeluxeChat)
        for (String line : List.of("Steve: hi", "Steve：hi", "Steve >> hi", "Steve » hi", "Steve > hi", "Steve hi")) {
            var r = matchChat(line, "{display_name}{sep}{content}");
            assertTrue(r.isPresent(), "should match: " + line);
            assertEquals("Steve", r.get().displayName(), line);
            assertEquals("hi", r.get().content(), line);
        }
    }

    @Test void matchesEssentialsXDefaultFormat() {
        var r = matchChat("<Steve> hello", "<{display_name}> {content}").orElseThrow();
        assertEquals("Steve", r.displayName());
        assertEquals("hello", r.content());
    }

    @Test void matchesEssentialsXPrefixedFormat() {
        // &7[...]&r arrive as literal text when the plugin did not parse them to styles
        var r = matchChat("&7[Guest]&r Steve&7:&r hello", "&7[Guest]&r {display_name}&7:&r {content}").orElseThrow();
        assertEquals("Steve", r.displayName());
        assertEquals("hello", r.content());
    }

    @Test void matchesDeluxeChatFormat() {
        var r = matchChat("[Guest] Steve > hello", "[Guest] {display_name} > {content}").orElseThrow();
        assertEquals("Steve", r.displayName());
        assertEquals("hello", r.content());
    }

    @Test void matchesCmiAdjacentPrefix() {
        // {prefix}{display_name} adjacent without an anchor: the lazy prefix takes the
        // empty match and display absorbs the decoration — the name still resolves
        // via contains, and the slice keeps the prefix like the guards do
        var r = matchChat("[Admin]Steve: hi", "{prefix}{display_name}: {content}").orElseThrow();
        assertEquals("", r.prefix());
        assertEquals("[Admin]Steve", r.displayName());
        assertEquals("hi", r.content());
    }

    @Test void matchesCmiWhisperFrom() {
        var r = match("[/msg from Alex] hi", "[/msg from {sender}] {content}", null).orElseThrow();
        assertEquals("Alex", r.sender());
        assertEquals("hi", r.content());
    }

    @Test void matchesDeluxeChatWhisperArrow() {
        var r = match("Alex -> Bob: hi", "{sender} -> {target}: {content}", null).orElseThrow();
        assertEquals("Alex", r.sender());
        assertEquals("Bob", r.target());
        assertEquals("hi", r.content());
    }

    // ===== whisper matching =====

    @Test void matchesIncomingWhisperFormat() {
        var r = match("Alex悄悄地对你说: hi", "{sender}悄悄地对你说: {content}", null).orElseThrow();
        assertEquals("Alex", r.sender());
        assertEquals("hi", r.content());
        assertEquals("Alex", r.verifiedName());
        assertTrue(r.whisper());
        assertEquals("Alex", r.displayName());
    }

    @Test void matchesWhisperArrowFormat() {
        var r = match("Alex → Bob: hi", "{sender} → {target}: {content}", null).orElseThrow();
        assertEquals("Alex", r.sender());
        assertEquals("Bob", r.target());
        assertEquals("Alex", r.verifiedName());
        assertTrue(r.whisper());
    }

    @Test void whisperVerifiedMayBeTarget() {
        TemplateMatcher.NameResolver onlyBob = name -> name.equals("Bob");
        var r = TemplateMatcher.match("Alex → Bob: hi",
            List.of(), List.of(whisper("{sender} → {target}: {content}")), onlyBob).orElseThrow();
        assertEquals("Bob", r.verifiedName());
        assertTrue(r.whisper());
    }

    @Test void whisperWithUnresolvableNamesFallsBackToChat() {
        TemplateMatcher.NameResolver nobody = name -> false;
        // whisper neither name known -> chat template also fails the gate -> empty
        assertTrue(TemplateMatcher.match("Alex → Bob: hi",
            List.of(chat("{display_name}: {content}")),
            List.of(whisper("{sender} → {target}: {content}")), nobody).isEmpty());
        // ...but a chat template that does match and resolve wins
        TemplateMatcher.NameResolver alex = name -> name.equals("Alex → Bob");
        var r = TemplateMatcher.match("Alex → Bob: hi",
            List.of(chat("{display_name}: {content}")),
            List.of(whisper("{sender} → {target}: {content}")), alex).orElseThrow();
        assertFalse(r.whisper());
        assertEquals("Alex → Bob", r.displayName());
    }

    // ===== template inference from a real message =====

    @Test void infersColonTemplate() {
        var tpl = TemplateMatcher.inferFromMessage("Steve: hello", List.of("Steve", "Alex"));
        assertEquals(Optional.of("{display_name}: {content}"), tpl);
    }

    @Test void infersDecoratedTemplate() {
        var tpl = TemplateMatcher.inferFromMessage("[大佬]Steve: 大家好", List.of("Steve", "Alex"));
        assertEquals(Optional.of("[大佬]{display_name}: {content}"), tpl);
    }

    @Test void infersArrowTemplate() {
        var tpl = TemplateMatcher.inferFromMessage("Steve >> hello world", List.of("Steve"));
        assertEquals(Optional.of("{display_name} >> {content}"), tpl);
    }

    @Test void inferredTemplateCompiles() {
        var tpl = TemplateMatcher.inferFromMessage("【生存一区】Steve: hi", List.of("Steve")).orElseThrow();
        assertNull(TemplateMatcher.compile(tpl).error(), tpl);
    }

    @Test void inferFailsWithoutKnownName() {
        assertTrue(TemplateMatcher.inferFromMessage("Server: 重启中", List.of("Steve", "Alex")).isEmpty());
    }

    @Test void inferFailsOnBlank() {
        assertTrue(TemplateMatcher.inferFromMessage("", List.of("Steve")).isEmpty());
    }

    @Test void whisperTemplatesTriedBeforeChat() {
        // both templates structurally match; whisper must win
        var r = TemplateMatcher.match("Alex悄悄地对你说: hi",
            List.of(chat("{display_name}: {content}")),
            List.of(whisper("{sender}悄悄地对你说: {content}")), KNOWN).orElseThrow();
        assertTrue(r.whisper());
        assertEquals("Alex", r.sender());
    }

    // ===== 2.4.3-beta: {external} =====

    @Test void externalAcceptsUnknownSenders() {
        var r = TemplateMatcher.match("[闲聊群] <小明(123456789)> 你好",
            List.of(chat("[{prefix}] <{external}> {content}")), List.of(), KNOWN).orElseThrow();
        assertFalse(r.whisper());
        assertEquals("小明(123456789)", r.displayName());
        assertEquals("你好", r.content());
    }

    @Test void externalStillMatchesWhenNameUnknown() {
        // KNOWN resolves nothing here; external templates trust the format
        assertTrue(matchChat("[群] <路人> 早", "[{prefix}] <{external}> {content}").isPresent());
    }

    @Test void externalConflictsWithDisplayName() {
        assertTrue(TemplateMatcher.compile("{display_name}{external}: {content}").template() == null);
    }

    @Test void externalRejectedInWhisper() {
        assertTrue(TemplateMatcher.compile("{sender}悄悄地对你说{sep}{external}: {content}").template() == null);
    }

    @Test void externalAloneSatisfiesChatRequirement() {
        assertNull(TemplateMatcher.compile("{external}: {content}").error());
    }
}
