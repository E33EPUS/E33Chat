package com.niuqu.chatbubble;

import com.niuqu.chatbubble.image.BracketCodec;
import com.niuqu.chatbubble.image.BracketCodec.ImageRef;
import com.niuqu.chatbubble.image.BracketCodec.ParseResult;
import com.niuqu.chatbubble.image.ImageLoader;
import java.util.List;
import net.minecraft.text.Style;
import net.minecraft.text.Text;
import net.minecraft.util.Formatting;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class BracketCodecTest {

    @Test
    void cicodeTagIsParsed() {
        ParseResult r = BracketCodec.parse(Text.literal("hi [[CICode,url=https://a.com/x.png]] there"));
        assertEquals(1, r.images().size());
        assertEquals("https://a.com/x.png", r.images().get(0).url());
        assertEquals("hi  there", r.textWithoutImages().getString());
    }

    @Test
    void chatUpgradeTagIsParsed() {
        ParseResult r = BracketCodec.parse(Text.literal("[[ChatUpgrade,url=http://b.com/y.jpg,name=pic]]"));
        assertEquals(1, r.images().size());
        assertEquals("http://b.com/y.jpg", r.images().get(0).url());
        assertEquals("pic", r.images().get(0).name());
        assertEquals("", r.textWithoutImages().getString());
    }

    @Test
    void tagMatchingIsCaseInsensitive() {
        ParseResult r = BracketCodec.parse(Text.literal("[[cicode,url=https://a.com/x.png]]"));
        assertEquals(1, r.images().size());
        assertEquals("https://a.com/x.png", r.images().get(0).url());
    }

    @Test
    void multipleImagesInOneMessage() {
        ParseResult r = BracketCodec.parse(Text.literal(
            "a [[CICode,url=https://a.com/1.png]] b [[ChatUpgrade,url=https://a.com/2.png]] c"));
        assertEquals(2, r.images().size());
        assertEquals("https://a.com/1.png", r.images().get(0).url());
        assertEquals("https://a.com/2.png", r.images().get(1).url());
        assertEquals("a  b  c", r.textWithoutImages().getString());
    }

    @Test
    void surroundingStyleIsPreserved() {
        Text input = Text.literal("plain ").append(
            Text.literal("code [[CICode,url=https://a.com/x.png]] tail").formatted(Formatting.RED));
        ParseResult r = BracketCodec.parse(input);
        assertEquals(1, r.images().size());
        assertEquals("plain code  tail", r.textWithoutImages().getString());
        // the red run keeps its formatting on the surviving text
        boolean[] redSeen = {false};
        r.textWithoutImages().visit((style, part) -> {
            if (part.contains("code") && style.getColor() != null
                    && style.getColor().getRgb() == Formatting.RED.getColorValue()) {
                redSeen[0] = true;
            }
            return java.util.Optional.empty();
        }, Style.EMPTY);
        assertTrue(redSeen[0], "red style should survive bracket strip");
    }

    @Test
    void imageOnlyMessageLeavesEmptyText() {
        ParseResult r = BracketCodec.parse(Text.literal("[[CICode,url=https://a.com/x.png]]"));
        assertEquals(1, r.images().size());
        assertEquals("", r.textWithoutImages().getString());
    }

    @Test
    void missingUrlIsIgnored() {
        ParseResult r = BracketCodec.parse(Text.literal("[[CICode,name=only]] text"));
        assertTrue(r.images().isEmpty());
        assertEquals(" text", r.textWithoutImages().getString());
    }

    @Test
    void noBracketReturnsInputUnchanged() {
        Text input = Text.literal("just text").formatted(Formatting.BLUE);
        ParseResult r = BracketCodec.parse(input);
        assertTrue(r.images().isEmpty());
        assertSame(input, r.textWithoutImages());
    }

    @Test
    void nonImageTypeStaysStrippedNotRendered() {
        ParseResult r = BracketCodec.parse(Text.literal("[[ChatUpgrade,url=https://a.com/s.mp3,type=audio]] x"));
        assertTrue(r.images().isEmpty(), "audio refs are not image cards");
        assertEquals(" x", r.textWithoutImages().getString());
    }

    @Test
    void parseOrExtractFallsBackToPlainText() {
        Text input = Text.literal("no image here");
        ParseResult r = BracketCodec.parseOrExtract(input);
        assertTrue(r.images().isEmpty());
        assertSame(input, r.textWithoutImages());
    }

    @Test
    void usableUrlChecks() {
        assertTrue(ImageLoader.isUsableUrl("https://a.com/x.png"));
        assertTrue(ImageLoader.isUsableUrl("http://localhost:8080/x.png"));
        assertFalse(ImageLoader.isUsableUrl(null));
        assertFalse(ImageLoader.isUsableUrl(""));
        assertFalse(ImageLoader.isUsableUrl("ftp://a.com/x.png"));
        assertFalse(ImageLoader.isUsableUrl("not a url"));
        assertFalse(ImageLoader.isUsableUrl("https://"));
    }
}
