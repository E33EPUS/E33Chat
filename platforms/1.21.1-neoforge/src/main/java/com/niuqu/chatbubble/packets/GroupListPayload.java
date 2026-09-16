package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.chat.GroupChannelState;
import io.netty.buffer.ByteBuf;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.codec.StreamCodec;
import net.minecraft.network.protocol.common.custom.CustomPacketPayload;
import net.minecraft.resources.ResourceLocation;
import net.neoforged.neoforge.network.handling.IPayloadContext;

import java.util.ArrayList;
import java.util.List;

/**
 * S2C group directory sync: pushed on hello, login and after every group
 * mutation. The client only shows the tab strip once a packet with
 * enabled=true arrived — on vanilla servers / singleplayer the feature stays
 * hidden entirely.
 */
public record GroupListPayload(boolean enabled, List<String> names,
                               List<Integer> memberCounts, List<String> myGroups)
        implements CustomPacketPayload {

    // Decode-side caps: a hostile server must not balloon client memory
    private static final int MAX_GROUPS = 200;
    private static final int MAX_PREALLOC = 1024;

    /** Read a length-prefixed list fully (so later fields stay aligned), with a
     *  preallocation cap: the count comes off the wire, so an unclamped
     *  allocation lets one hostile packet OOM the client. Entries beyond the
     *  prealloc cap are still consumed; a padded fake count dies on EOF, which
     *  drops the packet as malformed. The logical cap is applied afterwards,
     *  mirroring the legitimate group_max_count range (up to 500). */
    private static <T> List<T> readList(ByteBuf buf, java.util.function.Function<FriendlyByteBuf, T> reader) {
        int count = Math.max(new FriendlyByteBuf(buf).readVarInt(), 0);
        List<T> out = new ArrayList<>(Math.min(count, MAX_PREALLOC));
        for (int i = 0; i < count; i++) out.add(reader.apply(new FriendlyByteBuf(buf)));
        return out;
    }

    private static <T> List<T> cap(List<T> list) {
        return list.size() > MAX_GROUPS ? new ArrayList<>(list.subList(0, MAX_GROUPS)) : list;
    }

    public static final Type<GroupListPayload> TYPE =
        new Type<>(ResourceLocation.fromNamespaceAndPath("e33chat", "group_list"));

    public static final StreamCodec<ByteBuf, GroupListPayload> STREAM_CODEC = new StreamCodec<>() {
        @Override
        public GroupListPayload decode(ByteBuf buf) {
            boolean enabled = buf.readBoolean();
            // Read every list in full, then cap: capping before reading leaves
            // the surplus entries' bytes in the buffer and desyncs later fields.
            List<String> names = readList(buf, b -> readString(b, 64));
            List<Integer> counts = readList(buf, FriendlyByteBuf::readVarInt);
            List<String> mine = readList(buf, b -> readString(b, 64));
            return new GroupListPayload(enabled, cap(names), cap(counts), cap(mine));
        }

        @Override
        public void encode(ByteBuf buf, GroupListPayload p) {
            buf.writeBoolean(p.enabled());
            new FriendlyByteBuf(buf).writeVarInt(p.names().size());
            for (String n : p.names()) writeString(buf, n, 64);
            new FriendlyByteBuf(buf).writeVarInt(p.memberCounts().size());
            for (int c : p.memberCounts()) new FriendlyByteBuf(buf).writeVarInt(c);
            new FriendlyByteBuf(buf).writeVarInt(p.myGroups().size());
            for (String g : p.myGroups()) writeString(buf, g, 64);
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
    public Type<GroupListPayload> type() { return TYPE; }

    public static void handleClient(GroupListPayload payload, IPayloadContext context) {
        context.enqueueWork(() -> {
            GroupChannelState.enabled = payload.enabled();
            GroupChannelState.applyDirectory(payload.names(), payload.memberCounts(), payload.myGroups());
        });
    }
}
