package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.server.GroupManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S handshake: the client announces it runs E33Chat right after logging in.
 * The server uses this to route group chat (packet for mod clients, plain
 * formatted line for vanilla ones) and to push the group list immediately.
 *
 * Sent through the mod channel (id 13); a vanilla server just drops the
 * unknown channel payload, so this is safe everywhere.
 */
public class ClientHelloPacket {

    public ClientHelloPacket() {}

    public static void encode(ClientHelloPacket packet, FriendlyByteBuf buf) {}

    public static ClientHelloPacket decode(FriendlyByteBuf buf) {
        return new ClientHelloPacket();
    }

    public static void send() {
        // Client-side send path kept here so callers don't touch connection internals
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        var channel = com.niuqu.chatbubble.network.NetworkHandler.CHANNEL;
        if (mc.player.connection.getConnection() != null && channel.isRemotePresent(mc.player.connection.getConnection())) {
            channel.sendToServer(new ClientHelloPacket());
        }
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                GroupManager.onClientHello(player);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
