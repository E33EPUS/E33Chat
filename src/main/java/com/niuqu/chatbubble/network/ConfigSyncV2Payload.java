package com.niuqu.chatbubble.network;
import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
public record ConfigSyncV2Payload(boolean useTpa, List<String> chatTemplates,
                                  List<String> whisperTemplates, boolean templateDebug)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<ConfigSyncV2Payload> ID =
        new CustomPayload.Id<>(PayloadIds.of("config_sync_v2"));
    public static final PacketCodec<PacketByteBuf, ConfigSyncV2Payload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> {
        //#else
        //$$ (value, buf) -> {
        //#endif
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
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "config_sync_v2");
    //#endif
    static List<String> readList(PacketByteBuf buf) {
        int count = buf.readInt();
        List<String> out = new ArrayList<>(count);
        for (int i = 0; i < count; i++) out.add(buf.readString());
        return out;
    }
    static void writeList(PacketByteBuf buf, List<String> list) {
        buf.writeInt(list.size());
        for (String s : list) buf.writeString(s);
    }
    //#if MC >= 12005
    @Override
    public Id<ConfigSyncV2Payload> getId() { return ID; }
    //#endif
    public static void handle(ConfigSyncV2Payload payload) {
        ChatMessageStore.setServerConfig(
            payload.useTpa(), payload.chatTemplates(), payload.whisperTemplates(), payload.templateDebug());
    }
}
