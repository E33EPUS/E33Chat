package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatServerListener;
import com.niuqu.chatbubble.NetworkHandler;
import com.niuqu.chatbubble.server.DiskMediaStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/** Client -> server: request to download a server-hosted media file. */
public class MediaRequestPacket {
    private final String mediaId;

    public MediaRequestPacket(String mediaId) {
        this.mediaId = mediaId;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mediaId);
    }

    public static MediaRequestPacket decode(FriendlyByteBuf buf) {
        return new MediaRequestPacket(buf.readUtf());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            var sender = ctx.get().getSender();
            if (sender == null) return;
            DiskMediaStore store = ChatServerListener.mediaStore();
            long size = store.sizeOf(mediaId);
            if (size < 0) {
                NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
                    new MediaResponsePacket(mediaId, 0, 1, new byte[0]));
                return;
            }
            int total = DiskMediaStore.totalChunksFor(size);
            for (int i = 0; i < total; i++) {
                byte[] chunk = store.readChunk(mediaId, i, total);
                if (chunk == null) {
                    NetworkHandler.CHANNEL.send(
                        net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
                        new MediaResponsePacket(mediaId, 0, 1, new byte[0]));
                    return;
                }
                NetworkHandler.CHANNEL.send(
                    net.minecraftforge.network.PacketDistributor.PLAYER.with(() -> sender),
                    new MediaResponsePacket(mediaId, i, total, chunk));
            }
        });
        ctx.get().setPacketHandled(true);
    }

    public String mediaId() { return mediaId; }
}
