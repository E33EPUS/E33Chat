package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatServerListener;
import com.niuqu.chatbubble.server.DiskMediaStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.PacketDistributor;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Client -> server: request to download a server-hosted media file. */
public record MediaRequestPayload(String mediaId) implements CustomPacketPayload {

    public static final Type<MediaRequestPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "media_request"));

    public static final StreamCodec<ByteBuf, MediaRequestPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public MediaRequestPayload decode(ByteBuf buf) {
            return new MediaRequestPayload(
                buf.readCharSequence(buf.readInt(), java.nio.charset.StandardCharsets.UTF_8).toString());
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
            String id = payload.mediaId();
            DiskMediaStore store = ChatServerListener.mediaStore();
            long size = store.sizeOf(id);
            if (size < 0) {
                PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) ctx.player(),
                    new MediaResponsePayload(id, 0, 1, new byte[0]));
                return;
            }
            int total = DiskMediaStore.totalChunksFor(size);
            for (int i = 0; i < total; i++) {
                byte[] chunk = store.readChunk(id, i, total);
                if (chunk == null) {
                    PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) ctx.player(),
                        new MediaResponsePayload(id, 0, 1, new byte[0]));
                    return;
                }
                PacketDistributor.sendToPlayer((net.minecraft.server.level.ServerPlayer) ctx.player(),
                    new MediaResponsePayload(id, i, total, chunk));
            }
        });
    }
}
