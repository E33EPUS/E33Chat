package com.niuqu.chatbubble.chat;

import com.niuqu.chatbubble.server.GroupManager;
import com.niuqu.chatbubble.store.ChatMessageStore.ChatMessage;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** 2.4.10 group channel: name validation, tab classification and filtering. */
class GroupChannelStateTest {

    private static ChatMessage msg(boolean isSystem, String group) {
        return new ChatMessage(new UUID(0, 1), Text.literal("Steve"),
            Text.literal("hello"), 0L, false, isSystem,
            null, null, "h", 1, "Steve", false, null, group);
    }

    // ==== GroupManager.isValidGroupName ====

    @Test
    void groupNameValidation() {
        assertTrue(GroupManager.isValidGroupName("休闲聊天"));
        assertTrue(GroupManager.isValidGroupName("abc_123"));
        assertFalse(GroupManager.isValidGroupName(null));
        assertFalse(GroupManager.isValidGroupName(""));
        assertFalse(GroupManager.isValidGroupName("a b"));            // whitespace
        assertFalse(GroupManager.isValidGroupName("[x]"));            // brackets break the vanilla line
        assertFalse(GroupManager.isValidGroupName("<x>"));
        assertFalse(GroupManager.isValidGroupName("#world"));         // reserved pseudo-tab prefix
        assertFalse(GroupManager.isValidGroupName("123456789012345")); // > 12 chars
    }

    // ==== GroupManager.splitSay ====

    /** CJK names must survive the command path: Brigadier's string() rejects
     *  them unquoted, so msg passes one greedy rest argument and splits here. */
    @Test
    void splitSayHandlesCjkNameAndText() {
        assertArrayEquals(new String[]{"妈妈", "？？"}, GroupManager.splitSay("妈妈 ？？"));
        assertArrayEquals(new String[]{"妈妈", "hello world"}, GroupManager.splitSay("妈妈 hello world"));
        assertArrayEquals(new String[]{"mama", "hi"}, GroupManager.splitSay("mama hi"));
        // trailing/leading whitespace is trimmed; no space means empty text
        assertArrayEquals(new String[]{"妈妈", ""}, GroupManager.splitSay("  妈妈  "));
        assertArrayEquals(new String[]{"妈妈", ""}, GroupManager.splitSay("妈妈"));
        assertArrayEquals(new String[]{"", ""}, GroupManager.splitSay(null));
        assertArrayEquals(new String[]{"", ""}, GroupManager.splitSay("   "));
    }

    // ==== GroupChannelState.channelOf ====

    @Test
    void systemMessagesLandInSystemTab() {
        assertEquals(GroupChannelState.TAB_SYSTEM, GroupChannelState.channelOf(msg(true, null)));
    }

    @Test
    void playerMessagesWithoutGroupLandInWorldTab() {
        assertEquals(GroupChannelState.TAB_WORLD, GroupChannelState.channelOf(msg(false, null)));
        assertEquals(GroupChannelState.TAB_WORLD, GroupChannelState.channelOf(msg(false, "")));
    }

    @Test
    void groupMessagesKeepTheirGroup() {
        assertEquals("公会", GroupChannelState.channelOf(msg(false, "公会")));
        assertEquals("公会", GroupChannelState.channelOf(msg(true, "公会")),
            "group wins over system so directed group notices stay in their tab");
    }

    // ==== GroupChannelState.filterMessages ====

    @Test
    void allTabExcludesGroupMessages() {
        // 「全部」= 世界 + 系统：群消息隔离在自己的页签，不再混进默认视图
        List<ChatMessage> in = List.of(msg(false, null), msg(true, null), msg(false, "g"));
        List<ChatMessage> out = GroupChannelState.filterMessages(in, GroupChannelState.TAB_ALL);
        assertEquals(2, out.size());
        assertTrue(out.stream().noneMatch(m -> m.group() != null && !m.group().isEmpty()));
    }

    @Test
    void unsupportedStateReturnsEverything() {
        // 页签不可用（active=null，如未装服务端 mod）时不过滤，行为同旧版
        List<ChatMessage> in = List.of(msg(false, null), msg(true, null), msg(false, "g"));
        assertEquals(in, GroupChannelState.filterMessages(in, null));
    }

    @Test
    void worldTabHidesSystemAndGroupMessages() {
        List<ChatMessage> in = List.of(msg(false, null), msg(true, null), msg(false, "g"));
        List<ChatMessage> out = GroupChannelState.filterMessages(in, GroupChannelState.TAB_WORLD);
        assertEquals(1, out.size());
        assertEquals(GroupChannelState.TAB_WORLD, GroupChannelState.channelOf(out.get(0)));
    }

    @Test
    void systemTabOnlySystemMessages() {
        List<ChatMessage> in = List.of(msg(false, null), msg(true, null), msg(true, null));
        assertEquals(2, GroupChannelState.filterMessages(in, GroupChannelState.TAB_SYSTEM).size());
    }

    @Test
    void groupTabOnlyThatGroup() {
        List<ChatMessage> in = List.of(msg(false, "g1"), msg(false, "g2"), msg(false, null), msg(true, null));
        List<ChatMessage> out = GroupChannelState.filterMessages(in, "g1");
        assertEquals(1, out.size());
        assertEquals("g1", out.get(0).group());
    }
}
