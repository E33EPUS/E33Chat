package com.niuqu.chatbubble.image;

import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class EmoteCodecTest {

    @Test
    void knownTokenBecomesPlaceholder() {
        Text out = EmoteCodec.process(Text.literal("hi [:happy] there"));
        String s = out.getString();
        assertTrue(s.contains("\u3000"), s);
        assertFalse(s.contains("[:happy]"), s);
        assertTrue(s.startsWith("hi "), s);
        assertTrue(s.endsWith(" there"), s);
    }

    @Test
    void unknownTokenStaysLiteral() {
        Text out = EmoteCodec.process(Text.literal("[:nosuchtoken]"));
        assertEquals("[:nosuchtoken]", out.getString());
    }

    @Test
    void placeholderStyleCarriesTokenMarker() {
        Text out = EmoteCodec.process(Text.literal("[:happy]"));
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
        Text input = Text.literal("just text");
        assertSame(input, EmoteCodec.process(input));
    }

    @Test
    void multipleTokensAllReplaced() {
        Text out = EmoteCodec.process(Text.literal("[:happy]a[:love]b[:sad]"));
        String s = out.getString();
        assertEquals("\u3000a\u3000b\u3000", s);
    }

    @Test
    void surroundingStyleIsPreserved() {
        Text input = Text.literal("x ").append(
            Text.literal("[:happy] tail").formatted(Formatting.RED));
        Text out = EmoteCodec.process(input);
        boolean[] redSeen = {false};
        out.visit((style, part) -> {
            if (part.contains("\u3000") && style.getColor() != null
                    && style.getColor().getRgb() == Formatting.RED.getColorValue()) {
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
        Text once = EmoteCodec.process(Text.literal("[:happy] x"));
        Text twice = EmoteCodec.process(once);
        assertEquals(once.getString(), twice.getString());
    }
}
