package com.niuqu.chatbubble.server;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure validation in the group backend. The class comment claimed these were
 * "unit-tested" while no test existed; this is that test.
 */
class GroupManagerValidationTest {

    @Test
    void validNamesPass() {
        assertTrue(GroupManager.isValidGroupName("abc"));
        assertTrue(GroupManager.isValidGroupName("Guild1"));
        assertTrue(GroupManager.isValidGroupName("小团体"), "non-ASCII names are allowed");
        assertTrue(GroupManager.isValidGroupName("a"), "single char is within length rules");
    }

    @Test
    void namesThatBreakTheVanillaLineAreRejected() {
        // Names appear in "[name] <player> text", so these characters are fatal.
        assertFalse(GroupManager.isValidGroupName("a b"), "whitespace splits the token");
        assertFalse(GroupManager.isValidGroupName("a[b"));
        assertFalse(GroupManager.isValidGroupName("a]b"));
        assertFalse(GroupManager.isValidGroupName("a<b"));
        assertFalse(GroupManager.isValidGroupName("a>b"));
        assertFalse(GroupManager.isValidGroupName("a§bb"));
    }

    @Test
    void hashPrefixedAndEmptyNamesAreRejected() {
        assertFalse(GroupManager.isValidGroupName("#pseudo"), "'#' is reserved for client pseudo tabs");
        assertFalse(GroupManager.isValidGroupName(""));
        assertFalse(GroupManager.isValidGroupName(null));
    }

    @Test
    void overlongNamesAreRejected() {
        assertFalse(GroupManager.isValidGroupName("x".repeat(GroupManager.MAX_NAME_LEN + 1)));
        assertTrue(GroupManager.isValidGroupName("x".repeat(GroupManager.MAX_NAME_LEN)));
    }

    @Test
    void splitSaySplitsOnFirstWhitespace() {
        // The command argument is greedyString, so a Chinese group name and its
        // text must split on the first space (the Brigadier ASCII-only bug).
        assertArrayEquals(new String[]{"群名", "你好 世界"}, GroupManager.splitSay("群名 你好 世界"));
        assertArrayEquals(new String[]{"Guild", "hello"}, GroupManager.splitSay("Guild hello"));
        assertArrayEquals(new String[]{"Guild", "a b c"}, GroupManager.splitSay("Guild   a b c"));
    }

    @Test
    void splitSayWithoutSpaceYieldsEmptyText() {
        assertArrayEquals(new String[]{"Guild", ""}, GroupManager.splitSay("Guild"));
        assertArrayEquals(new String[]{"", ""}, GroupManager.splitSay(""));
        assertArrayEquals(new String[]{"", ""}, GroupManager.splitSay(null));
    }
}
