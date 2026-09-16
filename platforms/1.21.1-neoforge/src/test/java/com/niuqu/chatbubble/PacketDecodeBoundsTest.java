package com.niuqu.chatbubble;

import com.niuqu.chatbubble.packets.ChatMetaPayload;
import com.niuqu.chatbubble.packets.ConfigSyncV2Payload;
import com.niuqu.chatbubble.packets.MediaUploadPayload;
import com.niuqu.chatbubble.packets.ServerConfigDto;
import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Hostile-input contract for decoders that take a length prefix off the wire.
 * Before the caps, a 4-byte count could make the peer preallocate an
 * arbitrarily large collection (a one-packet OOM); these pin the caps and the
 * reject-instead-of-clamp rule for media strings.
 */
class PacketDecodeBoundsTest {

    private static ByteBuf buf() { return Unpooled.buffer(); }

    /** int length prefix (FriendlyByteBuf.readUtf style). */
    private static void utf(ByteBuf b, String s) {
        byte[] bytes = s.getBytes(java.nio.charset.StandardCharsets.UTF_8);
        b.writeInt(bytes.length);
        b.writeBytes(bytes);
    }

    /** varint length prefix (ByteBufCodecs.STRING_UTF8 style). */
    private static void vutf(FriendlyByteBuf b, String s) {
        b.writeUtf(s);
    }

    @Test
    void chatMetaMentionCountIsCapped() {
        // ByteBufCodecs.STRING_UTF8 prefixes each string with a varint length.
        FriendlyByteBuf b = new FriendlyByteBuf(buf());
        vutf(b, new java.util.UUID(0L, 0L).toString());
        vutf(b, "Alex"); vutf(b, "h"); vutf(b, "Steve"); vutf(b, "quoted");
        b.writeVarInt(Integer.MAX_VALUE);   // hostile mention count
        for (int i = 0; i < 200; i++) vutf(b, "t" + i);
        ChatMetaPayload p = ChatMetaPayload.STREAM_CODEC.decode(b);
        assertEquals(200, p.mentionTargets().size(),
            "count must clamp to the cap instead of trusting a hostile length");
    }

    @Test
    void configSyncV2TemplateCountIsCapped() {
        ByteBuf b = buf();
        b.writeBoolean(true);
        b.writeInt(Integer.MAX_VALUE);      // hostile chat-template count
        b.writeInt(0);
        b.writeBoolean(false);
        // The cap keeps the allocation small; running dry mid-list is the
        // acceptable failure for a malformed packet.
        assertDoesNotThrow(() -> {
            try { ConfigSyncV2Payload.STREAM_CODEC.decode(b); }
            catch (RuntimeException expectedEof) { /* fine */ }
        });
    }

    @Test
    void serverConfigDtoTemplateCountIsCapped() {
        ByteBuf b = buf();
        for (int i = 0; i < 7; i++) b.writeBoolean(false);
        FriendlyByteBuf fb = new FriendlyByteBuf(b);
        fb.writeVarInt(Integer.MAX_VALUE);   // hostile chat-template count
        fb.writeVarInt(0);
        assertDoesNotThrow(() -> {
            try { ServerConfigDto.decode(new FriendlyByteBuf(b)); }
            catch (RuntimeException expectedEof) { /* fine */ }
        });
    }

    @Test
    void mediaUploadOversizedStringIsRejectedNotClamped() {
        ByteBuf b = buf();
        b.writeLong(1L); b.writeInt(0); b.writeInt(1); b.writeInt(1);
        b.writeInt(Integer.MAX_VALUE);      // hostile content-type length
        assertThrows(RuntimeException.class, () -> MediaUploadPayload.STREAM_CODEC.decode(b),
            "an out-of-range length must be rejected, not silently clamped");
    }

    @Test
    void mediaUploadOversizedChunkIsRejected() {
        ByteBuf b = buf();
        b.writeLong(1L); b.writeInt(0); b.writeInt(1); b.writeInt(1);
        utf(b, "image/png");
        b.writeInt(Integer.MAX_VALUE);      // hostile chunk length
        assertThrows(RuntimeException.class, () -> MediaUploadPayload.STREAM_CODEC.decode(b));
    }
}
