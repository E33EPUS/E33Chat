package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/** Server -> client sync of server-side settings (currently: use_tpa). */
public record ConfigSyncPayload(boolean useTpa) implements CustomPayload {

    public static final CustomPayload.Id<ConfigSyncPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "config_sync"));

    public static final PacketCodec<PacketByteBuf, ConfigSyncPayload> CODEC = PacketCodec.of(
        (value, buf) -> buf.writeBoolean(value.useTpa),
        buf -> new ConfigSyncPayload(buf.readBoolean())
    );

    @Override
    public Id<ConfigSyncPayload> getId() { return ID; }

    public static void handle(ConfigSyncPayload payload) {
        ChatMessageStore.setServerUseTpa(payload.useTpa());
    }
}
