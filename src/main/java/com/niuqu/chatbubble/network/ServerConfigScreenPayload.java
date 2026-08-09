package com.niuqu.chatbubble.network;
import net.minecraft.network.PacketByteBuf;
//#if MC >= 12005
import net.minecraft.network.codec.PacketCodec;
import net.minecraft.network.packet.CustomPayload;
//#endif
import net.minecraft.util.Identifier;
import java.util.ArrayList;
import java.util.List;
public record ServerConfigScreenPayload(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                        List<String> chatTemplates, List<String> whisperTemplates)
        //#if MC >= 12005
        implements CustomPayload {
        //#else
        //$$ {
        //#endif
    //#if MC >= 12005
    public static final CustomPayload.Id<ServerConfigScreenPayload> ID =
        new CustomPayload.Id<>(PayloadIds.of("server_config_screen"));
    public static final PacketCodec<PacketByteBuf, ServerConfigScreenPayload> CODEC = PacketCodec.of(
        //#if MC >= 26000
        (buf, value) -> {
        //#else
        //$$ (value, buf) -> {
        //#endif
            buf.writeBoolean(value.useTpa);
            buf.writeBoolean(value.historyEnabled);
            buf.writeBoolean(value.templateDebug);
            ConfigSyncV2Payload.writeList(buf, value.chatTemplates);
            ConfigSyncV2Payload.writeList(buf, value.whisperTemplates);
        },
        buf -> new ServerConfigScreenPayload(
            buf.readBoolean(),
            buf.readBoolean(),
            buf.readBoolean(),
            ConfigSyncV2Payload.readList(buf),
            ConfigSyncV2Payload.readList(buf)
        )
    );
    @Override
    public Id<ServerConfigScreenPayload> getId() { return ID; }
    //#else
    //$$ public static final Identifier ID = new Identifier("e33chat", "server_config_screen");
    //#endif
}
