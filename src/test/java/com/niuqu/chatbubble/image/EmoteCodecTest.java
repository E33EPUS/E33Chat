package com.niuqu.chatbubble.image;

import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.Component;
import net.minecraft.ChatFormatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmoteCodecTest {

    @Test
    void knownTokenBecomesPlaceholder() {
        Component out = EmoteCodec.process(Component.literal("hi [:happy] there"));
        String s = out.getString();
        assertTrue(s.contains("\u3000"), s);
        assertFalse(s.contains("[:happy]"), s);
        assertTrue(s.startsWith("hi "), s);
        assertTrue(s.endsWith(" there"), s);
    }

    @Test
    void unknownTokenStaysLiteral() {
        Component out = EmoteCodec.process(Component.literal("[:nosuchtoken]"));
        assertEquals("[:nosuchtoken]", out.getString());
    }

    @Test
    void placeholderStyleCarriesTokenMarker() {
        Component out = EmoteCodec.process(Component.literal("[:happy]"));
        boolean[] found = {false};
        out.visit((style, part) -> {
            if (part.contains("\u3000")) {
                String token = EmoteCodec.tokenOf(style);
                if ("happy".equals(token)) found[0] = true;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        assertTrue(found[0], "placeholder style must carry e33emote:happy");
    }

    @Test
    void noTokenReturnsInputUnchanged() {
        Component input = Component.literal("just text");
        assertSame(input, EmoteCodec.process(input));
    }

    @Test
    void multipleTokensAllReplaced() {
        Component out = EmoteCodec.process(Component.literal("[:happy]a[:love]b[:sad]"));
        String s = out.getString();
        assertEquals("\u3000a\u3000b\u3000", s);
    }

    @Test
    void surroundingStyleIsPreserved() {
        Component input = Component.literal("x ").append(
            Component.literal("[:happy] tail").withStyle(ChatFormatting.RED));
        Component out = EmoteCodec.process(input);
        boolean[] redSeen = {false};
        out.visit((style, part) -> {
            if (part.contains("\u3000") && style.getColor() != null
                    && style.getColor().getValue() == ChatFormatting.RED.getColor()) {
                redSeen[0] = true;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        assertTrue(redSeen[0], "red style must survive emote replacement");
    }

    @Test
    void tokenOfReturnsNullForPlainStyle() {
        assertNull(EmoteCodec.tokenOf(Style.EMPTY));
        assertNull(EmoteCodec.tokenOf(null));
    }

    @Test
    void processIsIdempotent() {
        Component once = EmoteCodec.process(Component.literal("[:happy] x"));
        Component twice = EmoteCodec.process(once);
        assertEquals(once.getString(), twice.getString());
    }
}
