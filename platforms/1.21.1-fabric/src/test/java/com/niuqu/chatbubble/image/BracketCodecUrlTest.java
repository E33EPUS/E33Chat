package com.niuqu.chatbubble.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * URL recovery out of ChatImage's converted components. Pure string tests —
 * deliberately no Minecraft types, so they run headless on all three ends.
 */
class BracketCodecUrlTest {

    // ChatImage 0.13+ hands the show_chatimage hover a ChatImageCode object
    // whose toString() is the original bracket, with the attributes re-ordered
    // (nsfw/suffix first, url last). The URL must still come back out.

    @Test
    void normalizeUrlReadsBackChatImageCodeString() {
        assertEquals("https://a.com/x.png",
            BracketCodec.normalizeUrl("[[CICode,nsfw=true,url=https://a.com/x.png]]"));
        assertEquals("https://a.com/x.png",
            BracketCodec.normalizeUrl("[[CICode,url=https://a.com/x.png,name=pic]]"));
        assertEquals("https://a.com/x.png",
            BracketCodec.normalizeUrl("[[CICode,url=https://a.com/x.png]]"));
        assertEquals("http://b.com/y.jpg",
            BracketCodec.normalizeUrl("[[cicode,url=http://b.com/y.jpg]]"));
    }

    @Test
    void normalizeUrlPassesBareUrlsThrough() {
        assertEquals("https://a.com/x.png", BracketCodec.normalizeUrl(" https://a.com/x.png "));
        assertEquals("http://a.com/x.png", BracketCodec.normalizeUrl("http://a.com/x.png"));
    }

    @Test
    void normalizeUrlRejectsNonUrls() {
        assertNull(BracketCodec.normalizeUrl(null));
        assertNull(BracketCodec.normalizeUrl(""));
        assertNull(BracketCodec.normalizeUrl("   "));
        assertNull(BracketCodec.normalizeUrl("codename.chatimage.default"));
        assertNull(BracketCodec.normalizeUrl("[[CICode,name=only]]"));
        assertNull(BracketCodec.normalizeUrl("ftp://a.com/x.png"));
        assertNull(BracketCodec.normalizeUrl("some random text"));
    }
}
