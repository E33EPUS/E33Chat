package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.image.MediaClient;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;

/**
 * Server -> client: media hosting capability (2.3.13). A separate type on
 * purpose: old clients drop unknown payloads harmlessly, so a mixed-version
 * client/server never desyncs (an appended field inside ConfigSyncV2 would
 * break old clients decoding a shorter body). Absent payload = disabled.
 */
//#if MC >= 12005
public record MediaCapPayload(boolean mediaEnabled) implements CustomPayload {
//#else
//$$ public record MediaCapPayload(boolean mediaEnabled) {
//#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<MediaCapPayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "media_cap")
            //#else
            //$$ new Identifier("e33chat", "media_cap")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, MediaCapPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> buf.writeBoolean(value.mediaEnabled),
        //#else
        //$$ (value, buf) -> buf.writeBoolean(value.mediaEnabled),
        //#endif
        buf -> new MediaCapPayload(buf.readBoolean())
    );

    @Override
    public Id<MediaCapPayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "media_cap");
    //#endif

    public static void handle(MediaCapPayload payload) {
        MediaClient.setServerEnabled(payload.mediaEnabled());
    }
}
