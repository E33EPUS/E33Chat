package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.server.ChatServerListener;
import com.niuqu.chatbubble.network.NetworkHandler;
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
            if (sender != null) com.niuqu.chatbubble.server.MediaService.handleRequest(sender, mediaId);
        });
        ctx.get().setPacketHandled(true);
    }

    public String mediaId() { return mediaId; }
}
