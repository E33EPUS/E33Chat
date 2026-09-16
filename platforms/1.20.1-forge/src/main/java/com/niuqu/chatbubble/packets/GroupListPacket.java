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
    private static final int MAX_PREALLOC = 1024;

    /** Read a length-prefixed list fully (so later fields stay aligned), with a
     *  preallocation cap: the count comes off the wire, so an unclamped
     *  allocation lets one hostile packet OOM the client (1.20.1's
     *  readCollection preallocates without a cap). Entries beyond the prealloc
     *  cap are still consumed; a padded fake count dies on EOF, which drops
     *  the packet as malformed. The logical cap is applied afterwards,
     *  mirroring the legitimate group_max_count range (up to 500). */
    private static <T> List<T> readList(FriendlyByteBuf buf, FriendlyByteBuf.Reader<T> reader) {
        int count = Math.max(buf.readVarInt(), 0);
        List<T> out = new ArrayList<>(Math.min(count, MAX_PREALLOC));
        for (int i = 0; i < count; i++) out.add(reader.apply(buf));
        return out;
    }

    private static <T> List<T> cap(List<T> list) {
        return list.size() > MAX_GROUPS ? new ArrayList<>(list.subList(0, MAX_GROUPS)) : list;
    }

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
        // Read every list in full, then cap: capping before reading leaves the
        // surplus entries' bytes in the buffer and desyncs every later field.
        List<String> names = readList(buf, b -> b.readUtf(64));
        List<Integer> counts = readList(buf, FriendlyByteBuf::readVarInt);
        List<String> mine = readList(buf, b -> b.readUtf(64));
        return new GroupListPacket(enabled, cap(names), cap(counts), cap(mine));
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
