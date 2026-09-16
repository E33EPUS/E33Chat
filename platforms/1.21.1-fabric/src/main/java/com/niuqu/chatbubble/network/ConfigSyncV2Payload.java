package com.niuqu.chatbubble.network;

import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
import net.minecraft.util.Identifier;

import java.util.ArrayList;
import java.util.List;

/** Server -> client sync of server-side settings (v2: adds message-format templates). */
public record ConfigSyncV2Payload(boolean useTpa, List<String> chatTemplates,
                                  List<String> whisperTemplates, boolean templateDebug)
        implements CustomPayload {

    public static final CustomPayload.Id<ConfigSyncV2Payload> ID =
        new CustomPayload.Id<>(Identifier.of("e33chat", "config_sync_v2"));

    public static final PacketCodec<PacketByteBuf, ConfigSyncV2Payload> CODEC = PacketCodec.of(
        (value, buf) -> {
            buf.writeBoolean(value.useTpa);
            writeList(buf, value.chatTemplates);
            writeList(buf, value.whisperTemplates);
            buf.writeBoolean(value.templateDebug);
        },
        buf -> new ConfigSyncV2Payload(
            buf.readBoolean(),
            readList(buf),
            readList(buf),
            buf.readBoolean()
        )
    );

    /** Template lists are a handful of entries; anything beyond the cap is a
     *  hostile or corrupt payload — the count comes off the wire, so an
     *  unclamped new ArrayList<>(count) lets one packet OOM the receiver. */
    static final int MAX_LIST_ENTRIES = 256;

    static List<String> readList(PacketByteBuf buf) {
        int count = Math.min(Math.max(buf.readInt(), 0), MAX_LIST_ENTRIES);
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(buf.readString());
        return out;
    }

    static void writeList(PacketByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) buf.writeString(s);
    }

    @Override
    public Id<ConfigSyncV2Payload> getId() { return ID; }

    public static void handle(ConfigSyncV2Payload payload) {
        ChatMessageStore.setServerConfig(
            payload.useTpa(), payload.chatTemplates(), payload.whisperTemplates(), payload.templateDebug());
    }
}
