package com.niuqu.chatbubble.image;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ImageUploaderTest {

    @Test
    void extractUrlJsonPathModeReadsNestedArrayField() {
        // uguu.se response shape
        String body = "{\"success\":true,\"files\":[{\"hash\":\"abc\",\"filename\":\"x.png\","
            + "\"url\":\"https://d.uguu.se/abc.png\",\"size\":42}]}";
        assertEquals("https://d.uguu.se/abc.png",
            ImageUploader.extractUrl(body, "json:files[0].url"));
        // deep object path
        assertEquals("https://d.uguu.se/abc.png",
            ImageUploader.extractUrl(body, "json:files[0].url"));
        // legacy top-level field still works
        assertEquals("https://d.uguu.se/abc.png",
            ImageUploader.extractUrl("{\"url\":\"https://d.uguu.se/abc.png\"}", "json:url"));
    }

    @Test
    void extractUrlJsonPathRejectsBadIndexOrType() {
        String body = "{\"files\":[{\"url\":\"https://d.uguu.se/a.png\"}]}";
        assertNull(ImageUploader.extractUrl(body, "json:files[1].url"), "index out of range");
        assertNull(ImageUploader.extractUrl(body, "json:files[x].url"), "non-numeric index");
        assertNull(ImageUploader.extractUrl(body, "json:files[0].size"), "field is a number, not a URL");
        assertNull(ImageUploader.extractUrl("{\"files\":{}}", "json:files[0].url"), "object where array expected");
    }

    @Test
    void defaultHostIsUguuAndRequiresNoExtraFields() {
        assertEquals("https://uguu.se/upload", ImageUploader.DEFAULT_URL);
        assertEquals("files[]", ImageUploader.DEFAULT_FIELD);
        assertEquals("", ImageUploader.DEFAULT_EXTRA);
        assertEquals("json:files[0].url", ImageUploader.DEFAULT_RESPONSE);
    }

    @Test
    void multipartContainsExtraFieldsThenFilePart() {
        byte[] body = ImageUploader.buildMultipart(
            new byte[]{1, 2, 3}, "a b.png", "fileToUpload", "time=72h", "BOUND");
        String s = new String(body, StandardCharsets.UTF_8);
        assertTrue(s.contains("--BOUND\r\n"));
        // extra part first
        int timeIdx = s.indexOf("name=\"time\"");
        int fileIdx = s.indexOf("name=\"fileToUpload\"");
        assertTrue(timeIdx > 0 && fileIdx > timeIdx, "extra fields must precede the file part");
        // filename sanitized (space -> underscore)
        assertTrue(s.contains("filename=\"a_b.png\""), s);
        // binary payload preserved verbatim between head and tail (the file
        // part's own \r\n\r\n after the Content-Disposition header)
        int fileHeadEnd = s.indexOf("\r\n\r\n", fileIdx) + 4;
        assertArrayEquals(new byte[]{1, 2, 3},
            java.util.Arrays.copyOfRange(body, fileHeadEnd, fileHeadEnd + 3));
        assertTrue(s.endsWith("\r\n--BOUND--\r\n"));
    }

    @Test
    void multipartSkipsMalformedExtraPairs() {
        byte[] body = ImageUploader.buildMultipart(
            new byte[]{1}, "x.png", "f", "noeq,key=,=val,good=1", "B");
        String s = new String(body, StandardCharsets.UTF_8);
        assertTrue(s.contains("name=\"good\""));
        assertFalse(s.contains("name=\"noeq\""));
        assertFalse(s.contains("name=\"key\""));
        assertFalse(s.contains("name=\"=val\""));
    }

    @Test
    void extractUrlTextModeAcceptsHttpUrl() {
        assertEquals("https://litter.catbox.moe/abc.png",
            ImageUploader.extractUrl("https://litter.catbox.moe/abc.png\n", "text"));
        assertEquals("http://a.com/x", ImageUploader.extractUrl("http://a.com/x", null));
    }

    @Test
    void extractUrlTextModeRejectsGarbage() {
        assertNull(ImageUploader.extractUrl("ok:123", "text"));
        assertNull(ImageUploader.extractUrl("", "text"));
        assertNull(ImageUploader.extractUrl(null, "text"));
        assertNull(ImageUploader.extractUrl("https://", "text"));
    }

    @Test
    void extractUrlJsonModeReadsField() {
        String body = "{\"success\":true,\"url\":\"https://cdn.example.com/i.png\"}";
        assertEquals("https://cdn.example.com/i.png",
            ImageUploader.extractUrl(body, "json:url"));
    }

    @Test
    void extractUrlJsonModeRejectsMissingFieldOrBadJson() {
        assertNull(ImageUploader.extractUrl("{\"ok\":1}", "json:url"));
        assertNull(ImageUploader.extractUrl("not json", "json:url"));
        assertNull(ImageUploader.extractUrl("{\"url\":123}", "json:url"));
        assertNull(ImageUploader.extractUrl("{\"url\":\"ftp://x\"}", "json:url"));
    }

    @Test
    void extractUrlJsonModeRejectsNonHttpUrl() {
        String body = "{\"url\":\"javascript:alert(1)\"}";
        assertNull(ImageUploader.extractUrl(body, "json:url"));
    }

    @Test
    void localSourceScalesDownLongEdge() {
        java.awt.image.BufferedImage big = new java.awt.image.BufferedImage(4096, 1024, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        LocalImageSource.PreparedImage p = LocalImageSource.fromFile(
            writeTempPng(big, "big.png"));
        assertNotNull(p);
        assertTrue(p.fileName().endsWith(".png"), p.fileName());
        assertTrue(p.bytes().length > 0);
        // decode the uploaded bytes and check the long edge <= MAX_EDGE
        try {
            java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(p.bytes()));
            assertNotNull(decoded);
            assertEquals(2048, decoded.getWidth());
            assertEquals(512, decoded.getHeight());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void localSourceKeepsSmallImagesUnchanged() {
        java.awt.image.BufferedImage small = new java.awt.image.BufferedImage(100, 50, java.awt.image.BufferedImage.TYPE_INT_ARGB);
        LocalImageSource.PreparedImage p = LocalImageSource.fromFile(writeTempPng(small, "s.png"));
        assertNotNull(p);
        try {
            java.awt.image.BufferedImage decoded = javax.imageio.ImageIO.read(new java.io.ByteArrayInputStream(p.bytes()));
            assertEquals(100, decoded.getWidth());
            assertEquals(50, decoded.getHeight());
        } catch (Exception e) {
            fail(e);
        }
    }

    @Test
    void localSourceRejectsMissingFile() {
        assertNull(LocalImageSource.fromFile(new java.io.File("Z:/no/such/file.png")));
    }

    private static java.io.File writeTempPng(java.awt.image.BufferedImage img, String name) {
        try {
            java.io.File f = java.io.File.createTempFile("e33-test-", ".png");
            javax.imageio.ImageIO.write(img, "png", f);
            return f;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}
