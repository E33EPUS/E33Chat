package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.image.MediaClient;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * Server -> client: media hosting capability (2.3.13). A separate type on
 * purpose: old clients drop unknown payloads harmlessly, so a mixed-version
 * client/server never desyncs. Absent payload = disabled.
 */
public record MediaCapPayload(boolean mediaEnabled) implements CustomPacketPayload {

    public static final Type<MediaCapPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "media_cap"));

    public static final StreamCodec<ByteBuf, MediaCapPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MediaCapPayload decode(ByteBuf buf) {
            return new MediaCapPayload(buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, MediaCapPayload payload) {
            buf.writeBoolean(payload.mediaEnabled());
        }
    };

    @Override
    public Type<MediaCapPayload> type() { return TYPE; }

    public static void handleClient(MediaCapPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> MediaClient.setServerEnabled(payload.mediaEnabled()));
    }
}
