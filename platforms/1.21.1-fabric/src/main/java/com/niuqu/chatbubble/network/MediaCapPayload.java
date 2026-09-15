package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.image.MediaClient;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * Server -> client: media hosting capability (2.3.13). A separate type on
 * purpose: old clients drop unknown payloads harmlessly, so a mixed-version
 * client/server never desyncs (an appended field inside ConfigSyncV2 would
 * break old clients decoding a shorter body). Absent payload = disabled.
 */
public record MediaCapPayload(boolean mediaEnabled) implements CustomPayload {

    public static final CustomPayload.Id<MediaCapPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "media_cap"));

    public static final PacketCodec<PacketByteBuf, MediaCapPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeBoolean(value.mediaEnabled),
        buf -> new MediaCapPayload(buf.readBoolean())
    );

    @Override
    public Id<MediaCapPayload> getId() { return ID; }

    public static void handle(MediaCapPayload payload) {
        MediaClient.setServerEnabled(payload.mediaEnabled());
    }
}
