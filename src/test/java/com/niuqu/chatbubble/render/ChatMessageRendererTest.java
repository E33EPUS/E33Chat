package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageRendererTest {

    // ---- timeKey: epoch-minute bucket carries the date ----

    private static long millisAt(String dateTime) {
        return java.time.LocalDateTime.parse(dateTime)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    @Test
    void timeKey_disabledReturnsEmpty() {
        assertEquals("", ChatMessageRenderer.timeKey(0, 0));
    }

    @Test
    void timeKey_interval1_bucketIsMinute() {
        long t = millisAt("2026-07-31T14:30:05");
        assertEquals(t / 60_000L, Long.parseLong(ChatMessageRenderer.timeKey(t, 1)));
    }

    @Test
    void timeKey_interval5_roundsDown() {
        // 14:32 falls in the 14:30-14:34 bucket; 14:35 starts a new one
        String early = ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:30"), 5);
        assertEquals(early, ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:32"), 5));
        assertNotEquals(early, ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:35"), 5));
    }

    @Test
    void timeKey_interval5_topOfHour() {
        // 14:02 and 14:00 share the 14:00 bucket
        String top = ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:00"), 5);
        assertEquals(top, ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:02"), 5));
    }

    @Test
    void timeKey_interval10() {
        String a = ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:30"), 10);
        String b = ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:35"), 10);
        String c = ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:40"), 10);
        assertEquals(a, b);
        assertNotEquals(a, c);
    }

    @Test
    void timeKey_midnightCrossingGetsNewKey() {
        String before = ChatMessageRenderer.timeKey(millisAt("2026-07-31T23:59:59"), 1);
        String after = ChatMessageRenderer.timeKey(millisAt("2026-08-01T00:00:01"), 1);
        assertNotEquals(before, after);
    }

    @Test
    void timeKey_sameMinuteSameKey() {
        String a = ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:30:05"), 1);
        String b = ChatMessageRenderer.timeKey(millisAt("2026-07-31T14:30:55"), 1);
        assertEquals(a, b);
    }

    // ---- formatTime: WeChat-style separator ----

    @Test
    void formatTime_sameDayShowsTimeOnly() {
        long today = millisAt(java.time.LocalDateTime.now().toLocalDate() + "T15:30");
        assertEquals("15:30", ChatMessageRenderer.formatTime(today));
    }

    @Test
    void formatTime_otherDayShowsMonthDay() {
        long yesterday = millisAt(java.time.LocalDate.now().minusDays(1) + "T15:30");
        assertEquals(java.time.LocalDate.now().minusDays(1).format(
            java.time.format.DateTimeFormatter.ofPattern("MM-dd")) + " 15:30",
            ChatMessageRenderer.formatTime(yesterday));
    }

    @Test
    void formatTime_otherYearShowsFullDate() {
        long old = millisAt("2025-12-31T15:30");
        assertEquals("2025-12-31 15:30", ChatMessageRenderer.formatTime(old));
    }

    // ---- findRootClickStyle (parent-level fallback only) ----

    @Test
    void findRootClickStyle_nullOnPlainText() {
        assertNull(ChatMessageRenderer.findRootClickStyle(Component.literal("hello")));
    }

    @Test
    void findRootClickStyle_findsTopLevel() {
        var c = Component.literal("click me")
            .withStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.com")));
        var found = ChatMessageRenderer.findRootClickStyle(c);
        assertNotNull(found);
        assertEquals("https://example.com", found.getClickEvent().getValue());
    }

    @Test
    void findRootClickStyle_findsRootOnParentWithSiblings() {
        var click = Style.EMPTY.withClickEvent(
            new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.com"));
        var c = Component.literal("A").withStyle(click).append(Component.literal("B"));
        var found = ChatMessageRenderer.findRootClickStyle(c);
        assertNotNull(found);
        assertEquals("https://example.com", found.getClickEvent().getValue());
    }

    @Test
    void findRootClickStyle_nullOnSiblingOnlyClick() {
        var styled = Component.literal("link")
            .withStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/help")));
        var c = Component.empty().append(Component.literal("prefix ")).append(styled);
        assertNull(ChatMessageRenderer.findRootClickStyle(c));
    }

    @Test
    void parentClickStyleIsInheritedBySiblings() {
        var click = Style.EMPTY.withClickEvent(
            new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.com"));
        var c = Component.literal("A").withStyle(click).append(Component.literal("B"));
        java.util.List<Style> seen = new java.util.ArrayList<>();
        c.visit((style, text) -> { seen.add(style); return java.util.Optional.empty(); }, Style.EMPTY);
        assertEquals(2, seen.size(), "A and B should be visited as separate segments");
        assertTrue(seen.get(0).getClickEvent() != null, "parent style must reach first sibling");
        assertTrue(seen.get(1).getClickEvent() != null, "parent style must reach later siblings");
    }

    @Test
    void findRootClickStyle_nullOnHoverOnly() {
        var c = Component.literal("hover")
            .withStyle(Style.EMPTY.withHoverEvent(
                new net.minecraft.network.chat.HoverEvent(
                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                    Component.literal("tooltip"))));
        assertNull(ChatMessageRenderer.findRootClickStyle(c));
    }

    @Test
    void findRootClickStyle_nullOnNestedSiblingClick() {
        var link = Component.literal("deep")
            .withStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.OPEN_FILE, "/tmp/test")));
        var group = Component.empty().append(Component.literal("a")).append(link);
        var outer = Component.empty().append(Component.literal("p: ")).append(group);
        assertNull(ChatMessageRenderer.findRootClickStyle(outer));
    }

    // ---- D07: message grouping (in-group vs section spacing) ----

    private static ChatMessageStore.ChatMessage msg(String sender, String rawName, long time, boolean isSystem) {
        return new ChatMessageStore.ChatMessage(
            UUID.randomUUID(), Component.literal(sender), Component.literal("hi"), time,
            false, isSystem, null, null, null, 0, rawName, false, null);
    }

    @Test
    void isSameGroup_sameSenderWithinWindow() {
        long t = millisAt("2026-08-16T10:00:00");
        assertTrue(ChatMessageRenderer.isSameGroup(msg("Alice", "Alice", t, false),
            msg("Alice", "Alice", t + 60_000, false)));
    }

    @Test
    void isSameGroup_differentSenderIsNewGroup() {
        long t = millisAt("2026-08-16T10:00:00");
        assertFalse(ChatMessageRenderer.isSameGroup(msg("Alice", "Alice", t, false),
            msg("Bob", "Bob", t + 1000, false)));
    }

    @Test
    void isSameGroup_beyondFiveMinutesIsNewGroup() {
        long t = millisAt("2026-08-16T10:00:00");
        assertFalse(ChatMessageRenderer.isSameGroup(msg("Alice", "Alice", t, false),
            msg("Alice", "Alice", t + ChatMessageRenderer.GROUP_TIME_MS + 1, false)));
    }

    @Test
    void isSameGroup_systemMessagesNeverGrouped() {
        long t = millisAt("2026-08-16T10:00:00");
        assertFalse(ChatMessageRenderer.isSameGroup(msg("sys", null, t, true),
            msg("Alice", "Alice", t + 1000, false)));
        assertFalse(ChatMessageRenderer.isSameGroup(msg("Alice", "Alice", t, false),
            msg("sys", null, t + 1000, true)));
    }

    @Test
    void isSameGroup_nullPrevIsNewGroup() {
        long t = millisAt("2026-08-16T10:00:00");
        assertFalse(ChatMessageRenderer.isSameGroup(null, msg("Alice", "Alice", t, false)));
    }

    // ---- bubble_size: pure helpers (config-free) ----

    @Test
    void scaleFor_defaultTextHeightIsOne() {
        assertEquals(1f, ChatMessageRenderer.scaleFor(9, 9), 0.0001f);
    }

    @Test
    void scaleFor_ratioToLineHeight() {
        assertEquals(1.5556f, ChatMessageRenderer.scaleFor(14, 9), 0.0001f);
        assertEquals(0.5556f, ChatMessageRenderer.scaleFor(5, 9), 0.0001f);
        assertEquals(0.9f, ChatMessageRenderer.scaleFor(9, 10), 0.0001f);
    }

    @Test
    void scaleFor_clampsSizeToRange() {
        assertEquals(ChatMessageRenderer.scaleFor(5, 9), ChatMessageRenderer.scaleFor(3, 9), 0.0001f);
        assertEquals(ChatMessageRenderer.scaleFor(14, 9), ChatMessageRenderer.scaleFor(99, 9), 0.0001f);
    }

    @Test
    void scaledWrapWidth_inverseProportional() {
        assertEquals(50, ChatMessageRenderer.scaledWrapWidth(100, 2f));   // double size: half the chars/line
        assertEquals(200, ChatMessageRenderer.scaledWrapWidth(100, 0.5f)); // half size: twice the chars/line
        assertEquals(100, ChatMessageRenderer.scaledWrapWidth(100, 1f));
    }

    @Test
    void scaledWrapWidth_clampsToLegibleMinimum() {
        assertEquals(16, ChatMessageRenderer.scaledWrapWidth(10, 2f));
        assertEquals(16, ChatMessageRenderer.scaledWrapWidth(0, 1f));
    }

    @Test
    void isSameGroup_rawPlayerNameWinsOverDisplayName() {
        long t = millisAt("2026-08-16T10:00:00");
        // 同一 rawPlayerName（改过显示名）仍是同组
        assertTrue(ChatMessageRenderer.isSameGroup(msg("AliceOld", "Alice", t, false),
            msg("AliceNew", "Alice", t + 1000, false)));
        // 不同 rawPlayerName（显示名相同）是新组
        assertFalse(ChatMessageRenderer.isSameGroup(msg("Alice", "AliceA", t, false),
            msg("Alice", "AliceB", t + 1000, false)));
    }

    // ---- groupedGap (2.4.6 compact message groups) ----

    @Test
    void groupedGap_isOneThirdOfGap() {
        assertEquals(4, ChatMessageRenderer.groupedGap(12));
        assertEquals(3, ChatMessageRenderer.groupedGap(9));
    }

    @Test
    void groupedGap_flooredAtTwo() {
        assertEquals(2, ChatMessageRenderer.groupedGap(6));
        assertEquals(2, ChatMessageRenderer.groupedGap(3));
        assertEquals(2, ChatMessageRenderer.groupedGap(0));
    }
}
