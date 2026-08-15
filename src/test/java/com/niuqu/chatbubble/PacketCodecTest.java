package com.niuqu.chatbubble;

import com.niuqu.chatbubble.packets.ChatMetaPacket;
import com.niuqu.chatbubble.packets.ConfigSyncPacket;
import com.niuqu.chatbubble.packets.ConfigSyncV2Packet;
import com.niuqu.chatbubble.packets.HistoryPacket;
import com.niuqu.chatbubble.packets.MediaCapPacket;
import com.niuqu.chatbubble.packets.MediaRequestPacket;
import com.niuqu.chatbubble.packets.MediaResponsePacket;
import com.niuqu.chatbubble.packets.MediaUploadAckPacket;
import com.niuqu.chatbubble.packets.MediaUploadPacket;
import com.niuqu.chatbubble.packets.QuoteSyncPacket;
import com.niuqu.chatbubble.packets.ServerConfigSavePacket;
import com.niuqu.chatbubble.packets.ServerConfigScreenPacket;
import io.netty.buffer.Unpooled;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;
import net.minecraft.network.FriendlyByteBuf;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Round-trip tests for every network packet's encode/decode - the wire format
 * is a hard contract, so the codec must stay byte-stable.
 * Assertion style: encode -> decode -> encode again and compare bytes, so the
 * tests do not depend on getters that some packets lack.
 */
class PacketCodecTest {

    private interface Encoder {
        void encode(Object packet, FriendlyByteBuf buf);
    }

    private interface Decoder {
        Object decode(FriendlyByteBuf buf);
    }

    private static void assertStable(Object packet, Encoder enc, Decoder dec) {
        FriendlyByteBuf b1 = new FriendlyByteBuf(Unpooled.buffer());
        enc.encode(packet, b1);
        byte[] original = Arrays.copyOf(b1.array(), b1.writerIndex());
        Object decoded = dec.decode(b1);
        FriendlyByteBuf b2 = new FriendlyByteBuf(Unpooled.buffer());
        enc.encode(decoded, b2);
        byte[] after = Arrays.copyOf(b2.array(), b2.writerIndex());
        assertArrayEquals(original, after, "encode(decode(encode(p))) must equal encode(p)");
    }

    @Test void quoteSyncStable() {
        assertStable(new QuoteSyncPacket("Steve", "hello [quote]", "hash123"),
            (p, buf) -> ((QuoteSyncPacket) p).encode(buf), b -> QuoteSyncPacket.decode(b));
    }

    @Test void chatMetaStable() {
        assertStable(new ChatMetaPacket(
                UUID.fromString("00112233-4455-6677-8899-aabbccddeeff"),
                "Alex", "mh", "Steve", "quoted content", List.of("target1", "target2")),
            (p, buf) -> ((ChatMetaPacket) p).encode(buf), b -> ChatMetaPacket.decode(b));
    }

    @Test void chatMetaEmptyFieldsStable() {
        assertStable(new ChatMetaPacket(new UUID(0, 0), "X", "h", "", "", List.of()),
            (p, buf) -> ((ChatMetaPacket) p).encode(buf), b -> ChatMetaPacket.decode(b));
    }

    @Test void historyStable() {
        assertStable(new HistoryPacket(List.of(
                new HistoryPacket.HistoryEntry(new UUID(1, 1), "Steve", "hi there", 1700000000000L, false, "reply", "sender"),
                new HistoryPacket.HistoryEntry(new UUID(2, 2), "Alex", "system msg", 1700000001000L, true, "", ""))),
            (p, buf) -> HistoryPacket.encode((HistoryPacket) p, buf), b -> HistoryPacket.decode(b));
    }

    @Test void historyEmptyStable() {
        assertStable(new HistoryPacket(List.of()),
            (p, buf) -> HistoryPacket.encode((HistoryPacket) p, buf), b -> HistoryPacket.decode(b));
    }

    @Test void configSyncStable() {
        assertStable(new ConfigSyncPacket(true),
            (p, buf) -> ConfigSyncPacket.encode((ConfigSyncPacket) p, buf), b -> ConfigSyncPacket.decode(b));
    }

    @Test void configSyncV2Stable() {
        assertStable(new ConfigSyncV2Packet(true,
                List.of("{name}: {content}", "{prefix}{name} >> {content}"), List.of("{sender} -> {content}"), false),
            (p, buf) -> ConfigSyncV2Packet.encode((ConfigSyncV2Packet) p, buf), b -> ConfigSyncV2Packet.decode(b));
    }

    @Test void mediaCapStable() {
        assertStable(new MediaCapPacket(true),
            (p, buf) -> ((MediaCapPacket) p).encode(buf), b -> MediaCapPacket.decode(b));
    }

    @Test void mediaRequestStable() {
        assertStable(new MediaRequestPacket("0123456789abcdef0123456789abcdef"),
            (p, buf) -> ((MediaRequestPacket) p).encode(buf), b -> MediaRequestPacket.decode(b));
    }

    @Test void mediaResponseStable() {
        assertStable(new MediaResponsePacket("abc", 2, 5, new byte[]{1, 2, 3}),
            (p, buf) -> ((MediaResponsePacket) p).encode(buf), b -> MediaResponsePacket.decode(b));
    }

    @Test void mediaUploadAckOkStable() {
        assertStable(new MediaUploadAckPacket(42L, "mediaid", null),
            (p, buf) -> ((MediaUploadAckPacket) p).encode(buf), b -> MediaUploadAckPacket.decode(b));
    }

    @Test void mediaUploadAckErrorStable() {
        assertStable(new MediaUploadAckPacket(7L, null, "too large"),
            (p, buf) -> ((MediaUploadAckPacket) p).encode(buf), b -> MediaUploadAckPacket.decode(b));
    }

    @Test void mediaUploadStable() {
        assertStable(new MediaUploadPacket(99L, 0, 3, 3000, "image/png", new byte[]{9, 8, 7}),
            (p, buf) -> ((MediaUploadPacket) p).encode(buf), b -> MediaUploadPacket.decode(b));
    }

    @Test void serverConfigScreenStable() {
        assertStable(new ServerConfigScreenPacket(true, false, true, true,
                List.of("chat tpl"), List.of("whisper tpl")),
            (p, buf) -> ServerConfigScreenPacket.encode((ServerConfigScreenPacket) p, buf), b -> ServerConfigScreenPacket.decode(b));
    }

    @Test void serverConfigSaveStable() {
        assertStable(new ServerConfigSavePacket(false, true, false, false,
                List.of(), List.of("w")),
            (p, buf) -> ServerConfigSavePacket.encode((ServerConfigSavePacket) p, buf), b -> ServerConfigSavePacket.decode(b));
    }
}