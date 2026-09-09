package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.server.GroupManager;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * C2S group management action from the client's [+] popup.
 * 0=create, 1=join, 2=leave, 3=delete. The server answers with a fresh
 * GroupListPacket; errors go back as plain system messages.
 */
public class GroupActionPacket {
    public static final int CREATE = 0;
    public static final int JOIN = 1;
    public static final int LEAVE = 2;
    public static final int DELETE = 3;

    private static final int MAX_NAME = 64;

    private final int action;
    private final String groupName;

    public GroupActionPacket(int action, String groupName) {
        this.action = action;
        this.groupName = groupName;
    }

    /** Client-side dispatch (no-op when the server has no E33Chat channel). */
    public static void send(int action, String groupName) {
        var mc = net.minecraft.client.Minecraft.getInstance();
        if (mc.player == null || mc.player.connection == null) return;
        var channel = com.niuqu.chatbubble.network.NetworkHandler.CHANNEL;
        if (mc.player.connection.getConnection() != null
                && channel.isRemotePresent(mc.player.connection.getConnection())) {
            channel.sendToServer(new GroupActionPacket(action, groupName));
        }
    }

    public static void encode(GroupActionPacket p, FriendlyByteBuf buf) {
        buf.writeVarInt(p.action);
        buf.writeUtf(p.groupName, MAX_NAME);
    }

    public static GroupActionPacket decode(FriendlyByteBuf buf) {
        return new GroupActionPacket(buf.readVarInt(), buf.readUtf(MAX_NAME));
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer player = ctx.get().getSender();
            if (player != null) {
                GroupManager.handleAction(player, action, groupName);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
