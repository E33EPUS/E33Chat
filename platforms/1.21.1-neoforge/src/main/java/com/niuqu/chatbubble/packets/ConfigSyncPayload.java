package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.store.ChatMessageStore;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/** Server -> client sync of server-side settings (currently: use_tpa). */
public record ConfigSyncPayload(boolean useTpa) implements CustomPacketPayload {

    public static final Type<ConfigSyncPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "config_sync"));

    public static final StreamCodec<ByteBuf, ConfigSyncPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public ConfigSyncPayload decode(ByteBuf buf) {
            return new ConfigSyncPayload(buf.readBoolean());
        }

        @Override
        public void encode(ByteBuf buf, ConfigSyncPayload payload) {
            buf.writeBoolean(payload.useTpa());
        }
    };

    @Override
    public Type<ConfigSyncPayload> type() { return TYPE; }

    public static void handleClient(ConfigSyncPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> ChatMessageStore.setServerUseTpa(payload.useTpa()));
    }
}
