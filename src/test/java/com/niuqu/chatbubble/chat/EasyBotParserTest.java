package com.niuqu.chatbubble.chat;

import com.niuqu.chatbubble.chat.capture.EasyBotParser;
import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.network.chat.Component;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EasyBotParserTest {

    @Test void parsesDefaultEasyBotFormat() {
        Component line = Component.literal("[闲聊群] <小明(123456789)> 你好");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("小明", meta.senderName().getString());
        assertEquals("123456789", meta.rawPlayerName());
        assertEquals("你好", meta.rawContent().getString());
        assertFalse(meta.isSystem());
        assertFalse(meta.whisper());
    }

    @Test void parsesQqOnly() {
        Component line = Component.literal("[闲聊群] <123456789> 在吗");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("123456789", meta.senderName().getString());
        assertEquals("123456789", meta.rawPlayerName());
        assertEquals("在吗", meta.rawContent().getString());
    }

    @Test void parsesNickWithoutQqIdForNonBroadcastGroup() {
        Component line = Component.literal("[闲聊群] <小明> 你好");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("小明", meta.senderName().getString());
        assertEquals("小明", meta.rawPlayerName());
        assertEquals("你好", meta.rawContent().getString());
    }

    @Test void rejectsBroadcastLabelWithoutQqId() {
        Component line = Component.literal("[系统] <Server> 重启完成");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsPlainSystemText() {
        Component line = Component.literal("服务器重启完成");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsMissingAngleBrackets() {
        Component line = Component.literal("[闲聊群] 小明: 你好");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void contentSlicesStyledRuns() {
        Component image = Component.literal("[图片]").withStyle(style ->
            style.withHoverEvent(new net.minecraft.network.chat.HoverEvent(
                net.minecraft.network.chat.HoverEvent.Action.SHOW_TEXT,
                Component.literal("[[CICode,url=https://a.com/x.png]]"))));
        Component line = Component.literal("[闲聊群] <小明(123456789)> ").append(image);
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("[图片]", meta.rawContent().getString());
        assertTrue(meta.rawContent().getSiblings().size() >= 1
            || meta.rawContent().getStyle().getHoverEvent() != null
            || meta.rawContent().getString().contains("[图片]"));
    }

    // ---- Issue #15: the relay line is assembled bot-side, so neither the
    // ---- [群名] label nor a QQ number is guaranteed.

    @Test void parsesRelayWithoutGroupLabel() {
        Component line = Component.literal("<QW_SunnyDaze> [图片]");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("QW_SunnyDaze", meta.senderName().getString());
        assertEquals("[图片]", meta.rawContent().getString());
        assertFalse(meta.isSystem());
    }

    @Test void parsesGroupCardSuffix() {
        Component line = Component.literal("<黑（群妈妈）> [动画表情]");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("黑（群妈妈）", meta.senderName().getString());
        assertEquals("[动画表情]", meta.rawContent().getString());
    }

    @Test void parsesFullWidthQqParens() {
        Component line = Component.literal("[闲聊群] <小明（123456789）> 你好");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("小明", meta.senderName().getString());
        assertEquals("123456789", meta.rawPlayerName());
        assertEquals("你好", meta.rawContent().getString());
    }

    @Test void rejectsBlankContent() {
        Component line = Component.literal("<QW_SunnyDaze>    ");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsBroadcastNameWithoutQqId() {
        Component line = Component.literal("<系统> 服务器五分钟后重启");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsAngleBracketsBehindAPrefix() {
        Component line = Component.literal("前缀 <小明> 你好");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsOverlongName() {
        Component line = Component.literal("<" + "x".repeat(33) + "> 你好");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }
}
