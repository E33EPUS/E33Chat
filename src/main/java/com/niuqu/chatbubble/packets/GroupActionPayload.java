package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.server.GroupManager;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.neoforged.neoforge.network.handling.IPayloadContext;

/**
 * C2S group management action from the client's [+] popup.
 * 0=create, 1=join, 2=leave, 3=delete. The server answers with a fresh
 * GroupListPayload; errors go back as plain system messages.
 */
public record GroupActionPayload(int action, String groupName) implements CustomPacketPayload {

    public static final int CREATE = 0;
    public static final int JOIN = 1;
    public static final int LEAVE = 2;
    public static final int DELETE = 3;

    private static final int MAX_NAME = 64;

    public static final Type<GroupActionPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "group_action"));

    public static final StreamCodec<ByteBuf, GroupActionPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GroupActionPayload decode(ByteBuf buf) {
            return new GroupActionPayload(new FriendlyByteBuf(buf).readVarInt(), readString(buf, MAX_NAME));
        }

        @Override
        public void encode(ByteBuf buf, GroupActionPayload p) {
            new FriendlyByteBuf(buf).writeVarInt(p.action());
            writeString(buf, p.groupName(), MAX_NAME);
        }
    };

    // Raw ByteBuf lacks the varint/utf helpers — route through FriendlyByteBuf
    private static void writeString(ByteBuf buf, String s, int cap) {
        new FriendlyByteBuf(buf).writeUtf(s, cap);
    }

    private static String readString(ByteBuf buf, int cap) {
        return new FriendlyByteBuf(buf).readUtf(cap);
    }

    @Override
    public Type<GroupActionPayload> type() { return TYPE; }

    public static void handleServer(GroupActionPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> GroupManager.handleAction((ServerPlayer) context.player(), payload.action(), payload.groupName()));
    }

    /** Client-side dispatch (callers guarantee a live connection). */
    public static void send(int action, String groupName) {
        net.neoforged.neoforge.network.PacketDistributor.sendToServer(new GroupActionPayload(action, groupName));
    }
}
