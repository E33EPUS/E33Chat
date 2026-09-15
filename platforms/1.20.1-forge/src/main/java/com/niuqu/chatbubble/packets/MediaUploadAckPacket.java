package com.niuqu.chatbubble.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: result of a media upload. mediaId is a 32-hex UUID when
 * successful (URL becomes e33chat://media/<mediaId>); otherwise error holds a
 * short reason and mediaId is null.
 */
public class MediaUploadAckPacket {
    private final long uploadId;
    private final String mediaId;
    private final String error;

    public MediaUploadAckPacket(long uploadId, String mediaId, String error) {
        this.uploadId = uploadId;
        this.mediaId = mediaId;
        this.error = error;
    }

    public void encode(FriendlyByteBuf buf) {
        buf.writeLong(uploadId);
        buf.writeUtf(mediaId != null ? mediaId : "");
        buf.writeUtf(error != null ? error : "");
    }

    public static MediaUploadAckPacket decode(FriendlyByteBuf buf) {
        return new MediaUploadAckPacket(buf.readLong(), nullOrEmpty(buf.readUtf()), nullOrEmpty(buf.readUtf()));
    }

    private static String nullOrEmpty(String s) { return s == null || s.isEmpty() ? null : s; }

    public long uploadId() { return uploadId; }
    public String mediaId() { return mediaId; }
    public String error() { return error; }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                com.niuqu.chatbubble.image.MediaClient.handleAck(this)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
