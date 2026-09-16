package com.niuqu.chatbubble;

import com.niuqu.chatbubble.packets.ChatMetaPacket;
import com.niuqu.chatbubble.packets.MediaUploadPacket;
import com.niuqu.chatbubble.packets.ServerConfigDto;
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

    private static FriendlyByteBuf buf() { return new FriendlyByteBuf(Unpooled.buffer()); }

    @Test
    void chatMetaMentionCountIsCapped() {
        FriendlyByteBuf b = buf();
        b.writeUUID(new java.util.UUID(0L, 0L));
        b.writeUtf("Alex"); b.writeUtf("h"); b.writeUtf("Steve"); b.writeUtf("quoted");
        b.writeInt(Integer.MAX_VALUE);   // hostile mention count
        for (int i = 0; i < 200; i++) b.writeUtf("t" + i);
        ChatMetaPacket p = ChatMetaPacket.decode(b);
        assertEquals(200, p.mentionTargets().size(),
            "count must clamp to the cap instead of allocating Integer.MAX_VALUE entries");
    }

    @Test
    void serverConfigDtoTemplateCountIsCapped() {
        FriendlyByteBuf b = buf();
        for (int i = 0; i < 7; i++) b.writeBoolean(false);
        b.writeVarInt(Integer.MAX_VALUE);   // hostile chat-template count
        b.writeVarInt(0);
        assertDoesNotThrow(() -> {
            try { ServerConfigDto.decode(b); }
            catch (RuntimeException expectedEof) { /* fine */ }
        });
    }

    @Test
    void mediaUploadOversizedStringIsRejectedNotClamped() {
        FriendlyByteBuf b = buf();
        b.writeLong(1L); b.writeInt(0); b.writeInt(1); b.writeInt(1);
        b.writeInt(Integer.MAX_VALUE);   // hostile content-type length
        assertThrows(RuntimeException.class, () -> MediaUploadPacket.decode(b),
            "an out-of-range length must be rejected, not silently clamped");
    }

    @Test
    void mediaUploadOversizedChunkIsRejected() {
        FriendlyByteBuf b = buf();
        b.writeLong(1L); b.writeInt(0); b.writeInt(1); b.writeInt(1);
        b.writeUtf("image/png");
        b.writeInt(Integer.MAX_VALUE);   // hostile chunk length
        assertThrows(RuntimeException.class, () -> MediaUploadPacket.decode(b));
    }
}
