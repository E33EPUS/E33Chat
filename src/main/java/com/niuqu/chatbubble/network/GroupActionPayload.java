package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

/**
 * C2S group management action from the client's [+] popup.
 * 0=create, 1=join, 2=leave, 3=delete. The server answers with a fresh
 * GroupListPayload; errors go back as plain system messages.
 */
public record GroupActionPayload(int action, String groupName) implements CustomPayload {

    public static final int CREATE = 0;
    public static final int JOIN = 1;
    public static final int LEAVE = 2;
    public static final int DELETE = 3;

    private static final int MAX_NAME = 64;

    public static final CustomPayload.Id<GroupActionPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "group_action"));

    public static final PacketCodec<PacketByteBuf, GroupActionPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeVarInt(value.action);
            buf.writeString(value.groupName, MAX_NAME);
        },
        buf -> new GroupActionPayload(buf.readVarInt(), buf.readString(MAX_NAME))
    );

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Client-side dispatch (callers guarantee a live connection). */
    public static void send(int action, String groupName) {
        net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking.send(
            new GroupActionPayload(action, groupName));
    }
}
