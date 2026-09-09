package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.server.GroupManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S handshake: the client announces it runs E33Chat right after logging in.
 * The server uses this to route group chat (packet for mod clients, plain
 * formatted line for vanilla ones) and to push the group list immediately.
 */
public record ClientHelloPayload() implements CustomPacketPayload {

    public static final Type<ClientHelloPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "client_hello"));

    public static final StreamCodec<ByteBuf, ClientHelloPayload> STREAM_CODEC =
        new StreamCodec<>() {
            @Override
            public ClientHelloPayload decode(ByteBuf buf) {
                return new ClientHelloPayload();
            }

            @Override
            public void encode(ByteBuf buf, ClientHelloPayload payload) {
            }
        };

    @Override
    public Type<ClientHelloPayload> type() { return TYPE; }

    public static void handleServer(ClientHelloPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GroupManager.onClientHello((ServerPlayer) context.player()));
    }

    /** Client-side dispatch (callers guarantee a live connection). */
    public static void send() {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new ClientHelloPayload());
    }
}
