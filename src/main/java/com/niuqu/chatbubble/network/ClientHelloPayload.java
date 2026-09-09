package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.util.Identifier;

/**
 * C2S handshake: the client announces it runs E33Chat right after logging in.
 * The server uses this to route group chat (packet for mod clients, plain
 * formatted line for vanilla ones) and to push the group list immediately.
 */
public record ClientHelloPayload() implements CustomPayload {

    public static final CustomPayload.Id<ClientHelloPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "client_hello"));

    public static final PacketCodec<PacketByteBuf, ClientHelloPayload> CODEC = PacketCodec.of(
        (value, buf) -> {},
        buf -> new ClientHelloPayload()
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Client-side dispatch (callers guarantee a live connection). */
    public static void send() {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(new ClientHelloPayload());
    }

    /** Server-side hook, invoked from ChatBubbleMod's receiver. */
    public static void handleServer(ClientHelloPayload payload, ServerPlayerEntity player) {
        com.niuqu.chatbubble.server.GroupManager.onClientHello(player);
    }
}
