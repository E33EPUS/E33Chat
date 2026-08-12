package com.niuqu.chatbubble.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: media hosting capability (2.3.13). A separate packet id on
 * purpose: old clients drop unknown ids harmlessly (SimpleChannel), so a
 * mixed-version client/server never desyncs. Absent packet = disabled.
 */
public class MediaCapPacket {
    private final boolean mediaEnabled;

    public MediaCapPacket(boolean mediaEnabled) {
        this.mediaEnabled = mediaEnabled;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeBoolean(mediaEnabled);
    }

    public static MediaCapPacket decode(FriendlyByteBuf buf) {
        return new MediaCapPacket(buf.readBoolean());
    }

    public boolean mediaEnabled() { return mediaEnabled; }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.niuqu.chatbubble.image.MediaClient.handleCap(this)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
