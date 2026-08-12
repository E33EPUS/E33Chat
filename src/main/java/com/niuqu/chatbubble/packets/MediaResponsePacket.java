package com.niuqu.chatbubble.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: one chunk of a media download. The special form
 * (index 0, totalChunks 1, empty chunk) signals "not found" so the client can
 * fail the fetch instead of hanging.
 */
public class MediaResponsePacket {
    private final String mediaId;
    private final int index;
    private final int totalChunks;
    private final byte[] chunk;

    public MediaResponsePacket(String mediaId, int index, int totalChunks, byte[] chunk) {
        this.mediaId = mediaId;
        this.index = index;
        this.totalChunks = totalChunks;
        this.chunk = chunk;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeUtf(mediaId);
        buf.writeInt(index);
        buf.writeInt(totalChunks);
        buf.writeByteArray(chunk);
    }

    public static MediaResponsePacket decode(FriendlyByteBuf buf) {
        return new MediaResponsePacket(buf.readUtf(), buf.readInt(), buf.readInt(), buf.readByteArray());
    }

    public String mediaId() { return mediaId; }
    public int index() { return index; }
    public int totalChunks() { return totalChunks; }
    public byte[] chunk() { return chunk; }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.niuqu.chatbubble.image.MediaClient.handleResponse(this)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
