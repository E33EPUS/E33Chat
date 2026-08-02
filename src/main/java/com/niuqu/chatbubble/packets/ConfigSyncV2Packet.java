package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.ChatMessageStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client sync of server-side settings (v2: adds message-format templates).
 * Registered as a separate id so old clients (which decode id 3 as a single boolean)
 * never desync: SimpleChannel drops the unknown id 4 packet harmlessly.
 */
public class ConfigSyncV2Packet {
    private final boolean useTpa;
    private final List<String> chatTemplates;
    private final List<String> whisperTemplates;
    private final boolean templateDebug;

    public ConfigSyncV2Packet(boolean useTpa, List<String> chatTemplates,
                              List<String> whisperTemplates, boolean templateDebug) {
        this.useTpa = useTpa;
        this.chatTemplates = chatTemplates;
        this.whisperTemplates = whisperTemplates;
        this.templateDebug = templateDebug;
    }

    public static void encode(ConfigSyncV2Packet packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.useTpa);
        buf.writeCollection(packet.chatTemplates, FriendlyByteBuf::writeUtf);
        buf.writeCollection(packet.whisperTemplates, FriendlyByteBuf::writeUtf);
        buf.writeBoolean(packet.templateDebug);
    }

    public static ConfigSyncV2Packet decode(FriendlyByteBuf buf) {
        boolean useTpa = buf.readBoolean();
        List<String> chat = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        List<String> whisper = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        boolean debug = buf.readBoolean();
        return new ConfigSyncV2Packet(useTpa, chat, whisper, debug);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ChatMessageStore.setServerConfig(useTpa, chatTemplates, whisperTemplates, templateDebug)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
