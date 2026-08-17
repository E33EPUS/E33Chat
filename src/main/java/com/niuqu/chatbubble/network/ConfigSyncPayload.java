package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;

/** Server -> client sync of server-side settings (currently: use_tpa). */
//#if MC >= 12005
public record ConfigSyncPayload(boolean useTpa) implements CustomPayload {
//#else
//$$ public record ConfigSyncPayload(boolean useTpa) {
//#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<ConfigSyncPayload> ID =
        new CustomPayload.Id<>(
            //#if MC >= 12000
            Identifier.of("e33chat", "config_sync")
            //#else
            //$$ new Identifier("e33chat", "config_sync")
            //#endif
        );

    public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> buf.writeBoolean(value.useTpa),
        //#else
        //$$ (value, buf) -> buf.writeBoolean(value.useTpa),
        //#endif
        buf -> new ConfigSyncPayload(buf.readBoolean())
    );

    @Override
    public Id<ConfigSyncPayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "config_sync");
    //#endif

    public static void handle(ConfigSyncPayload payload) {
        ChatMessageStore.setServerUseTpa(payload.useTpa());
    }
}
