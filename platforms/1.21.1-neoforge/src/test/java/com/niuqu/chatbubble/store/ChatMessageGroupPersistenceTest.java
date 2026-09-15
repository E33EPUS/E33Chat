package com.niuqu.chatbubble.store;

import com.niuqu.chatbubble.store.ChatMessageStore.ChatMessage;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2.4.10: the JSONL history line carries the group tag and reads it back. */
class ChatMessageGroupPersistenceTest {

    private static ChatMessage msg(String group) {
        return new ChatMessage(new UUID(0, 1), Component.literal("Steve"),
            Component.literal("hello world"), 1700000000000L, false, false,
            null, null, "h", 1, "Steve", false, null, group);
    }

    @Test
    void jsonlRoundTripPreservesGroup() {
        String line = HistoryStore.toLine(msg("公会"));
        assertTrue(line.contains("\"group\":\"公会\""), "group key must be written: " + line);
        ChatMessage back = HistoryStore.fromLine(line);
        assertEquals("公会", back.group());
        assertEquals("hello world", back.content().getString());
    }

    @Test
    void jsonlRoundTripWithoutGroupStaysNull() {
        String line = HistoryStore.toLine(msg(null));
        ChatMessage back = HistoryStore.fromLine(line);
        assertNull(back.group(), "pre-2.4.10 lines must read back with group=null");
    }

    @Test
    void legacyJsonLineWithoutGroupKeyReadsAsNull() {
        // A hand-written pre-2.4.10 line: no group key at all
        String line = "{\"time\":1700000000000,\"uuid\":\"00000000-0000-0000-0000-000000000001\","
            + "\"sender\":\"Steve\",\"content\":\"hi\",\"own\":false,\"system\":false}";
        ChatMessage back = HistoryStore.fromLine(line);
        assertEquals("hi", back.content().getString());
        assertNull(back.group());
    }
}
