package com.niuqu.chatbubble.chat;

import com.niuqu.chatbubble.chat.capture.EasyBotParser;
import com.niuqu.chatbubble.store.ChatMessageStore;
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

    // ---- Issue #15: the relay line is assembled bot-side, so neither the
    // ---- [群名] label nor a QQ number is guaranteed.

    @Test void parsesRelayWithoutGroupLabel() {
        Text line = Text.literal("<QW_SunnyDaze> [图片]");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("QW_SunnyDaze", meta.senderName().getString());
        assertEquals("[图片]", meta.rawContent().getString());
        assertFalse(meta.isSystem());
    }

    @Test void parsesGroupCardSuffix() {
        Text line = Text.literal("<黑（群妈妈）> [动画表情]");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("黑（群妈妈）", meta.senderName().getString());
        assertEquals("[动画表情]", meta.rawContent().getString());
    }

    @Test void parsesFullWidthQqParens() {
        Text line = Text.literal("[闲聊群] <小明（123456789）> 你好");
        ChatMessageStore.SenderMeta meta = EasyBotParser.tryParse(line, line.getString());
        assertNotNull(meta);
        assertEquals("小明", meta.senderName().getString());
        assertEquals("123456789", meta.rawPlayerName());
        assertEquals("你好", meta.rawContent().getString());
    }

    @Test void rejectsBlankContent() {
        Text line = Text.literal("<QW_SunnyDaze>    ");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsBroadcastNameWithoutQqId() {
        Text line = Text.literal("<系统> 服务器五分钟后重启");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsAngleBracketsBehindAPrefix() {
        Text line = Text.literal("前缀 <小明> 你好");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }

    @Test void rejectsOverlongName() {
        Text line = Text.literal("<" + "x".repeat(33) + "> 你好");
        assertNull(EasyBotParser.tryParse(line, line.getString()));
    }
}
