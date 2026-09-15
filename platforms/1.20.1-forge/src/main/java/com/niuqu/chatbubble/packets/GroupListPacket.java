package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.chat.GroupChannelState;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * S2C group directory sync: pushed on hello, login and after every group
 * mutation. The client only shows the tab strip once a packet with
 * enabled=true arrived — on vanilla servers / singleplayer the feature stays
 * hidden entirely.
 */
public class GroupListPacket {
    // Decode-side caps: a hostile server must not balloon client memory
    private static final int MAX_GROUPS = 200;

    private final boolean enabled;
    private final List<String> names;
    private final List<Integer> memberCounts;
    private final List<String> myGroups;

    public GroupListPacket(boolean enabled, List<String> names,
                           List<Integer> memberCounts, List<String> myGroups) {
        this.enabled = enabled;
        this.names = names;
        this.memberCounts = memberCounts;
        this.myGroups = myGroups;
    }

    public static void encode(GroupListPacket p, FriendlyByteBuf buf) {
        buf.writeBoolean(p.enabled);
        buf.writeCollection(p.names, (b, s) -> b.writeUtf(s, 64));
        buf.writeVarInt(p.memberCounts.size());
        for (int count : p.memberCounts) buf.writeVarInt(count);
        buf.writeCollection(p.myGroups, (b, s) -> b.writeUtf(s, 64));
    }

    public static GroupListPacket decode(FriendlyByteBuf buf) {
        boolean enabled = buf.readBoolean();
        List<String> names = new ArrayList<>(buf.readCollection(ArrayList::new, b -> b.readUtf(64)));
        int countSize = Math.min(buf.readVarInt(), MAX_GROUPS);
        List<Integer> counts = new ArrayList<>(countSize);
        for (int i = 0; i < countSize; i++) counts.add(buf.readVarInt());
        List<String> mine = new ArrayList<>(buf.readCollection(ArrayList::new, b -> b.readUtf(64)));
        if (names.size() > MAX_GROUPS) {
            names = new ArrayList<>(names.subList(0, MAX_GROUPS));
            counts = new ArrayList<>(counts.subList(0, Math.min(counts.size(), MAX_GROUPS)));
        }
        return new GroupListPacket(enabled, names, counts, mine);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () -> {
                GroupChannelState.enabled = enabled;
                GroupChannelState.applyDirectory(names, memberCounts, myGroups);
            })
        );
        ctx.get().setPacketHandled(true);
    }
}
