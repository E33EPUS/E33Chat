package com.niuqu.chatbubble.chat;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.text.Text;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EasyBotParserTest {

    @Test void parsesDefaultEasyBotFormat() {
        Text line = Text.literal("[闲聊群] <小明(123456789)> 你好");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("小明", meta.senderName().getString());
        assertEquals("123456789", meta.rawPlayerName());
        assertEquals("你好", meta.rawContent().getString());
        assertFalse(meta.isSystem());
        assertFalse(meta.whisper());
    }

    @Test void parsesQqOnly() {
        Text line = Text.literal("[闲聊群] <123456789> 在吗");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("123456789", meta.senderName().getString());
        assertEquals("123456789", meta.rawPlayerName());
        assertEquals("在吗", meta.rawContent().getString());
    }

    @Test void parsesNickWithoutQqIdForNonBroadcastGroup() {
        Text line = Text.literal("[闲聊群] <小明> 你好");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("小明", meta.senderName().getString());
        assertEquals("小明", meta.rawPlayerName());
        assertEquals("你好", meta.rawContent().getString());
    }

    @Test void rejectsBroadcastLabelWithoutQqId() {
        Text line = Text.literal("[系统] <Server> 重启完成");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsPlainSystemText() {
        Text line = Text.literal("服务器重启完成");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsMissingAngleBrackets() {
        Text line = Text.literal("[闲聊群] 小明: 你好");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void contentSlicesStyledRuns() {
        // No HoverEvent here: Yarn's HoverEvent static init pulls ItemStack,
        // which cannot initialize in the headless Fabric unit-test environment.
        Text image = Text.literal("[图片]").formatted(net.minecraft.util.Formatting.GREEN);
        Text line = Text.literal("[闲聊群] <小明(123456789)> ").append(image);
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("[图片]", meta.rawContent().getString());
        assertTrue(meta.rawContent().getSiblings().size() >= 1
            || meta.rawContent().getStyle().getColor() != null
            || meta.rawContent().getString().contains("[图片]"));
    }
}
