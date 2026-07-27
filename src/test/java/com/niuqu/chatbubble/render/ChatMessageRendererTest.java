package com.niuqu.chatbubble.render;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.ClickEvent;
import org.junit.jupiter.api.Test;

import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class ChatMessageRendererTest {

    // ---- timeKey ----

    @Test
    void timeKey_disabledReturnsEmpty() {
        assertEquals("", ChatMessageRenderer.timeKey(LocalTime.of(14, 30), 0));
    }

    @Test
    void timeKey_interval1() {
        assertEquals("14:30", ChatMessageRenderer.timeKey(LocalTime.of(14, 30), 1));
    }

    @Test
    void timeKey_interval5_roundsDown() {
        assertEquals("14:30", ChatMessageRenderer.timeKey(LocalTime.of(14, 32), 5));
    }

    @Test
    void timeKey_interval5_topOfHour() {
        assertEquals("14:00", ChatMessageRenderer.timeKey(LocalTime.of(14, 2), 5));
    }

    @Test
    void timeKey_interval10() {
        assertEquals("14:30", ChatMessageRenderer.timeKey(LocalTime.of(14, 35), 10));
    }

    // ---- findClickStyle ----

    @Test
    void findClickStyle_nullOnPlainText() {
        assertNull(ChatMessageRenderer.findClickStyle(Component.literal("hello")));
    }

    @Test
    void findClickStyle_findsTopLevel() {
        var c = Component.literal("click me")
            .withStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.OPEN_URL, "https://example.com")));
        var found = ChatMessageRenderer.findClickStyle(c);
        assertNotNull(found);
        assertEquals("https://example.com", found.getClickEvent().getValue());
    }

    @Test
    void findClickStyle_findsInSibling() {
        var styled = Component.literal("link")
            .withStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.SUGGEST_COMMAND, "/help")));
        var c = Component.empty().append(Component.literal("prefix ")).append(styled);
        var found = ChatMessageRenderer.findClickStyle(c);
        assertNotNull(found);
        assertEquals("/help", found.getClickEvent().getValue());
    }

    @Test
    void findClickStyle_nullOnHoverOnly() {
        var c = Component.literal("hover")
            .withStyle(Style.EMPTY.withHoverEvent(
                new net.minecraft.network.chat.HoverEvent(
                    net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                    Component.literal("tooltip"))));
        assertNull(ChatMessageRenderer.findClickStyle(c));
    }

    @Test
    void findClickStyle_nestedSiblings() {
        var link = Component.literal("deep")
            .withStyle(Style.EMPTY.withClickEvent(
                new ClickEvent(ClickEvent.Action.OPEN_FILE, "/tmp/test")));
        var group = Component.empty().append(Component.literal("a")).append(link);
        var outer = Component.empty().append(Component.literal("p: ")).append(group);
        var found = ChatMessageRenderer.findClickStyle(outer);
        assertNotNull(found);
        assertEquals(ClickEvent.Action.OPEN_FILE, found.getClickEvent().getAction());
    }
}
