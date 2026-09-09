package com.niuqu.chatbubble.image;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 2.4.11 regression guard for the media fetch de-duplication.
 *
 * Every e33chat://media URL is requested twice (animated probe + static
 * loader); the server rate-limits downloads per player (4 per 10s), so
 * concurrent un-deduplicated fetches burnt the quota and the 4th image failed
 * with "image load failed". {@link MediaClient#fetch} now shares one in-flight
 * request per mediaId — this test pins the helper that decides whether a fetch
 * is even allowed to start.
 */
class MediaClientDedupTest {

    @Test
    void validMediaIdsAccepted() {
        // 32 lowercase hex chars, the DiskMediaStore id shape
        assertTrue(com.niuqu.chatbubble.server.DiskMediaStore.isValidMediaId(
            "277ba77e3fa549948d0f6976c4a9aa08"));
    }

    @Test
    void malformedMediaIdsRejected() {
        assertFalse(com.niuqu.chatbubble.server.DiskMediaStore.isValidMediaId(null));
        assertFalse(com.niuqu.chatbubble.server.DiskMediaStore.isValidMediaId(""));
        assertFalse(com.niuqu.chatbubble.server.DiskMediaStore.isValidMediaId("tooshort"));
        assertFalse(com.niuqu.chatbubble.server.DiskMediaStore.isValidMediaId("../etc/passwd"));
        assertFalse(com.niuqu.chatbubble.server.DiskMediaStore.isValidMediaId(
            "277BA77E3FA549948D0F6976C4A9AA08")); // uppercase not accepted
    }

    @Test
    void fetchRejectsInvalidIdWithoutTouchingNetwork() {
        // No client connection in headless tests: an invalid id must return null
        // before any packet is sent (NetworkHandler.CHANNEL is null here).
        org.junit.jupiter.api.Assertions.assertNull(MediaClient.fetch("not-a-valid-id"));
    }
}
