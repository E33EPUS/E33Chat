package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class MessageGroupingTest {

    private static long millisAt(String dateTime) {
        return java.time.LocalDateTime.parse(dateTime)
            .atZone(java.time.ZoneId.systemDefault()).toInstant().toEpochMilli();
    }

    private static ChatMessageStore.ChatMessage msg(String sender, String rawName, long time, boolean isSystem) {
        return new ChatMessageStore.ChatMessage(
            UUID.randomUUID(), Text.literal(sender), Text.literal("hi"), time,
            false, isSystem, null, null, null, 0, rawName, false, null);
    }

    @Test
    void isSameGroup_sameSenderWithinWindow() {
        long t = millisAt("2026-08-16T10:00:00");
        assertTrue(MessageGrouping.isSameGroup(msg("Alice", "Alice", t, false),
            msg("Alice", "Alice", t + 60_000, false)));
    }

    @Test
    void isSameGroup_differentSenderIsNewGroup() {
        long t = millisAt("2026-08-16T10:00:00");
        assertFalse(MessageGrouping.isSameGroup(msg("Alice", "Alice", t, false),
            msg("Bob", "Bob", t + 1000, false)));
    }

    @Test
    void isSameGroup_beyondFiveMinutesIsNewGroup() {
        long t = millisAt("2026-08-16T10:00:00");
        assertFalse(MessageGrouping.isSameGroup(msg("Alice", "Alice", t, false),
            msg("Alice", "Alice", t + MessageGrouping.GROUP_TIME_MS + 1, false)));
    }

    @Test
    void isSameGroup_systemMessagesNeverGrouped() {
        long t = millisAt("2026-08-16T10:00:00");
        assertFalse(MessageGrouping.isSameGroup(msg("sys", null, t, true),
            msg("Alice", "Alice", t + 1000, false)));
        assertFalse(MessageGrouping.isSameGroup(msg("Alice", "Alice", t, false),
            msg("sys", null, t + 1000, true)));
    }

    @Test
    void isSameGroup_nullPrevIsNewGroup() {
        long t = millisAt("2026-08-16T10:00:00");
        assertFalse(MessageGrouping.isSameGroup(null, msg("Alice", "Alice", t, false)));
    }

    @Test
    void isSameGroup_rawPlayerNameWinsOverDisplayName() {
        long t = millisAt("2026-08-16T10:00:00");
        assertTrue(MessageGrouping.isSameGroup(msg("AliceOld", "Alice", t, false),
            msg("AliceNew", "Alice", t + 1000, false)));
        assertFalse(MessageGrouping.isSameGroup(msg("Alice", "AliceA", t, false),
            msg("Alice", "AliceB", t + 1000, false)));
    }

    // ---- groupedGap (2.4.6 compact message groups) ----

    @Test
    void groupedGap_isOneThirdOfGap() {
        assertEquals(4, MessageGrouping.groupedGap(12));
        assertEquals(3, MessageGrouping.groupedGap(9));
    }

    @Test
    void groupedGap_flooredAtTwo() {
        assertEquals(2, MessageGrouping.groupedGap(6));
        assertEquals(2, MessageGrouping.groupedGap(3));
        assertEquals(2, MessageGrouping.groupedGap(0));
    }
}
