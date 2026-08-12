package com.niuqu.chatbubble.server;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.util.Arrays;

import static org.junit.jupiter.api.Assertions.*;

/** Headless tests for the server media store (pure Java, no MC classes). */
class DiskMediaStoreTest {

    private static byte[] payload(int size) {
        byte[] b = new byte[size];
        for (int i = 0; i < size; i++) b[i] = (byte) (i * 31);
        return b;
    }

    @Test
    void uploadFlowRoundTrips() throws IOException {
        Path dir = Files.createTempDirectory("e33media");
        DiskMediaStore store = new DiskMediaStore(dir);
        byte[] data = payload(100_000);
        int chunks = DiskMediaStore.totalChunksFor(data.length);
        long uploadId = 1;
        assertNull(store.beginUpload(uploadId, "alice", chunks, data.length, "image/png"));
        String mediaId = null;
        for (int i = 0; i < chunks; i++) {
            int from = i * DiskMediaStore.CHUNK_BYTES;
            int len = Math.min(DiskMediaStore.CHUNK_BYTES, data.length - from);
            String r = store.acceptChunk(uploadId, i, Arrays.copyOfRange(data, from, from + len));
            if (i == chunks - 1) mediaId = r;
            else assertNull(r, "mid-upload must return null");
        }
        assertNotNull(mediaId);
        assertTrue(DiskMediaStore.isValidMediaId(mediaId));
        assertEquals(data.length, store.sizeOf(mediaId));

        // read back in chunks and reassemble
        int total = DiskMediaStore.totalChunksFor(data.length);
        byte[] out = new byte[data.length];
        int off = 0;
        for (int i = 0; i < total; i++) {
            byte[] c = store.readChunk(mediaId, i, total);
            assertNotNull(c);
            System.arraycopy(c, 0, out, off, c.length);
            off += c.length;
        }
        assertArrayEquals(data, out);
    }

    @Test
    void rejectsOversizedFile() {
        DiskMediaStore store = new DiskMediaStore(Path.of("nonexistent"),
            DiskMediaStore.MAX_SINGLE_BYTES, DiskMediaStore.QUOTA_BYTES, 0);
        String r = store.beginUpload(1, "alice", 100, DiskMediaStore.MAX_SINGLE_BYTES + 1, "image/png");
        assertEquals("too large", r);
    }

    @Test
    void rejectsQuotaExceeded() throws IOException {
        Path dir = Files.createTempDirectory("e33media");
        DiskMediaStore store = new DiskMediaStore(dir, 1024 * 1024, 10_000, 0);
        String r = store.beginUpload(1, "alice", 1, 20_000, "image/png");
        assertEquals("quota exceeded", r);
        // within quota passes
        assertNull(store.beginUpload(2, "alice", 1, 1_000, "image/png"));
    }

    @Test
    void rejectsOutOfOrderChunk() throws IOException {
        Path dir = Files.createTempDirectory("e33media");
        DiskMediaStore store = new DiskMediaStore(dir);
        assertNull(store.beginUpload(1, "alice", 2, 100, "image/png"));
        String r = store.acceptChunk(1, 1, new byte[50]); // skip index 0
        assertEquals("chunk out of order", r);
        assertEquals(-1, store.sizeOf("whatever"));
        // session discarded: a valid 0-chunk no longer accepted
        assertNull(store.acceptChunk(1, 0, new byte[50]));
    }

    @Test
    void rejectsSizeMismatch() throws IOException {
        Path dir = Files.createTempDirectory("e33media");
        DiskMediaStore store = new DiskMediaStore(dir);
        assertNull(store.beginUpload(1, "alice", 2, 100, "image/png"));
        String r = store.acceptChunk(1, 0, new byte[50]);
        assertNull(r);
        r = store.acceptChunk(1, 1, new byte[60]); // 110 != 100
        assertNotNull(r);
        assertNotEquals("", r);
        assertEquals("size mismatch", r);
    }

    @Test
    void readMissingReturnsNull() {
        DiskMediaStore store = new DiskMediaStore(Path.of("nonexistent"));
        assertNull(store.readChunk("00000000000000000000000000000000", 0, 1));
        assertEquals(-1, store.sizeOf("00000000000000000000000000000000"));
    }

    @Test
    void mediaIdValidation() {
        assertTrue(DiskMediaStore.isValidMediaId("0123456789abcdef0123456789abcdef"));
        assertFalse(DiskMediaStore.isValidMediaId(null));
        assertFalse(DiskMediaStore.isValidMediaId(""));
        assertFalse(DiskMediaStore.isValidMediaId("0123456789abcdef0123456789abcde"));
        assertFalse(DiskMediaStore.isValidMediaId("0123456789abcdef0123456789ABCDEF"));
        assertFalse(DiskMediaStore.isValidMediaId("../../etc/passwd"));
        assertFalse(DiskMediaStore.isValidMediaId("0123456789abcdef-123456789abcdef"));
    }

    @Test
    void cleanupExpiredOnlyRemovesOld() throws IOException {
        Path dir = Files.createTempDirectory("e33media");
        DiskMediaStore store = new DiskMediaStore(dir, DiskMediaStore.MAX_SINGLE_BYTES,
            DiskMediaStore.QUOTA_BYTES, 1_000);
        long id = 1;
        assertNull(store.beginUpload(id, "alice", 1, 10, "image/png"));
        String mediaId = store.acceptChunk(id, 0, new byte[10]);
        assertNotNull(mediaId);
        long old = System.currentTimeMillis() - 5_000;
        Files.setLastModifiedTime(dir.resolve(mediaId), FileTime.fromMillis(old));
        // fresh upload stays
        assertNull(store.beginUpload(id + 1, "alice", 1, 10, "image/png"));
        String fresh = store.acceptChunk(id + 1, 0, new byte[10]);
        assertNotNull(fresh);

        int removed = store.cleanupExpired();
        assertEquals(1, removed);
        assertEquals(-1, store.sizeOf(mediaId));
        assertEquals(10, store.sizeOf(fresh));
    }

    @Test
    void urlValidationMatchesStore() {
        // isUsableUrl-style check for the e33chat protocol (logic lives in ImageLoader;
        // here we only pin the mediaId contract used by both sides)
        assertTrue(DiskMediaStore.isValidMediaId("abcdef0123456789abcdef0123456789"));
        assertTrue(DiskMediaStore.isValidMediaId(DiskMediaStore.newMediaId()));
    }
}
