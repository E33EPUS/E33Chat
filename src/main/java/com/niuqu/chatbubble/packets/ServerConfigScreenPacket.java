package com.niuqu.chatbubble.packets;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

/**
 * Server -> client: open the server-config GUI with the current server settings
 * snapshot. Triggered by /e33chat gui (OP).
 *
 * Pure data + codec only — the client-side open logic lives in
 * {@link ClientServerConfigGui} so the server (which loads packet classes when
 * NetworkHandler registers them) never verifies a reference to the client-only
 * Screen class. Without this, a dedicated server crashes at startup with
 * "Attempted to load class net/minecraft/client/gui/screens/Screen for invalid
 * dist DEDICATED_SERVER".
 */
public class ServerConfigScreenPacket {
    private final boolean useTpa;
    private final boolean historyEnabled;
    private final boolean templateDebug;
    private final List<String> chatTemplates;
    private final List<String> whisperTemplates;

    public ServerConfigScreenPacket(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                    List<String> chatTemplates, List<String> whisperTemplates) {
        this.useTpa = useTpa;
        this.historyEnabled = historyEnabled;
        this.templateDebug = templateDebug;
        this.chatTemplates = chatTemplates;
        this.whisperTemplates = whisperTemplates;
    }

    public boolean useTpa() { return useTpa; }
    public boolean historyEnabled() { return historyEnabled; }
    public boolean templateDebug() { return templateDebug; }
    public List<String> chatTemplates() { return chatTemplates; }
    public List<String> whisperTemplates() { return whisperTemplates; }

    public static void encode(ServerConfigScreenPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.useTpa);
        buf.writeBoolean(packet.historyEnabled);
        buf.writeBoolean(packet.templateDebug);
        buf.writeCollection(packet.chatTemplates, FriendlyByteBuf::writeUtf);
        buf.writeCollection(packet.whisperTemplates, FriendlyByteBuf::writeUtf);
    }

    public static ServerConfigScreenPacket decode(FriendlyByteBuf buf) {
        boolean useTpa = buf.readBoolean();
        boolean history = buf.readBoolean();
        boolean debug = buf.readBoolean();
        List<String> chat = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        List<String> whisper = new ArrayList<>(buf.readCollection(ArrayList::new, FriendlyByteBuf::readUtf));
        return new ServerConfigScreenPacket(useTpa, history, debug, chat, whisper);
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientServerConfigGui.open(useTpa, historyEnabled, templateDebug, chatTemplates, whisperTemplates)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
