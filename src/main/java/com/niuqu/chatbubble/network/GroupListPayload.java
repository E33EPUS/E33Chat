package com.niuqu.chatbubble.network;

import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.List;

/**
 * S2C group directory sync: pushed on hello, login and after every group
 * mutation. The client only shows the tab strip once a payload with
 * enabled=true arrived — on vanilla servers / singleplayer the feature stays
 * hidden entirely.
 */
public record GroupListPayload(boolean enabled, List<String> names,
                               List<Integer> memberCounts, List<String> myGroups)
        implements CustomPayload {

    // Decode-side caps: a hostile server must not balloon client memory
    private static final int MAX_GROUPS = 200;

    public static final CustomPayload.Id<GroupListPayload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "group_list"));

    public static final PacketCodec<PacketByteBuf, GroupListPayload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeBoolean(value.enabled);
            buf.writeCollection(value.names, (b, s) -> b.writeString(s, 64));
            buf.writeCollection(value.memberCounts, (b, c) -> b.writeVarInt(c));
            buf.writeCollection(value.myGroups, (b, s) -> b.writeString(s, 64));
        },
        buf -> new GroupListPayload(
            buf.readBoolean(),
            cap(buf.readList(b -> b.readString(64))),
            cap(buf.readList(PacketByteBuf::readVarInt)),
            cap(buf.readList(b -> b.readString(64)))
        )
    );

    private static <T> List<T> cap(List<T> list) {
        return list.size() > MAX_GROUPS ? list.subList(0, MAX_GROUPS) : list;
    }

    @Override
    public CustomPayload.Id<? extends CustomPayload> getId() {
        return ID;
    }

    /** Client-side hook, invoked from ChatBubbleClientSetup's receiver. */
    public static void handleClient(GroupListPayload payload) {
        com.niuqu.chatbubble.chat.GroupChannelState.enabled = payload.enabled();
        com.niuqu.chatbubble.chat.GroupChannelState.applyDirectory(
            payload.names(), payload.memberCounts(), payload.myGroups());
    }
}
