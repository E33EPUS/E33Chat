package com.niuqu.chatbubble.packets;

import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: request to download a server-hosted media file. */
public record MediaRequestPayload(String mediaId) implements CustomPacketPayload {

    public static final Type<MediaRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "media_request"));

    public static final StreamCodec<ByteBuf, MediaRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MediaRequestPayload decode(ByteBuf buf) {
            return new MediaRequestPayload(
                MediaUploadPayload.readUtf(buf));
        }

        @Override
        public void encode(ByteBuf buf, MediaRequestPayload payload) {
            buf.writeInt(payload.mediaId().length());
            buf.writeCharSequence(payload.mediaId(), java.nio.charset.StandardCharsets.UTF_8);
        }
    };

    @Override
    public Type<MediaRequestPayload> type() { return TYPE; }

    public static void handleServer(MediaRequestPayload payload, IPayloadContext ctx) {
        ctx.enqueueWork(() -> {
            if (ctx.player() instanceof net.minecraft.server.level.ServerPlayer sender) {
                com.niuqu.chatbubble.server.MediaService.handleRequest(sender, payload.mediaId());
            }
        });
    }
}
