package com.niuqu.chatbubble.chat;

import static org.junit.jupiter.api.Assertions.*;
import org.junit.jupiter.api.Test;

public class MentionDetectorTest {

    @Test
    public void messageStartingWithPlayerName_requireAtOff_doesNotCrash() {
        // 回归：消息以玩家名开头（idx==0）+ requireAt=false → 旧代码 text.charAt(-1) 崩溃
        assertTrue(MentionDetector.isMentioned("Steve hello", "steve", false, null));
    }

    @Test
    public void atMention_requireAtOff_detected() {
        assertTrue(MentionDetector.isMentioned("hey @steve", "steve", false, null));
    }

    @Test
    public void nameInsideLongerWord_notMentioned() {
        // xsteve：名字嵌在词中间（左侧是字母）→ 不算提到
        assertFalse(MentionDetector.isMentioned("xsteve hello", "steve", false, null));
    }

    @Test
    public void nameWithSuffixLetter_notMentioned() {
        // stevex：名字后紧跟字母 → 不算提到
        assertFalse(MentionDetector.isMentioned("stevex hello", "steve", false, null));
    }

    @Test
    public void noAt_requireAtOn_notMentioned() {
        assertFalse(MentionDetector.isMentioned("steve hello", "steve", true, null));
    }

    @Test
    public void atMention_requireAtOn_detected() {
        assertTrue(MentionDetector.isMentioned("@steve hello", "steve", true, null));
    }

    @Test
    public void emptyOrNullText_notMentioned() {
        assertFalse(MentionDetector.isMentioned("", "steve", false, null));
        assertFalse(MentionDetector.isMentioned(null, "steve", false, null));
    }

    @Test
    public void replyFromSelf_detected() {
        assertTrue(MentionDetector.isMentioned("anything", "steve", true, "steve"));
    }
}
