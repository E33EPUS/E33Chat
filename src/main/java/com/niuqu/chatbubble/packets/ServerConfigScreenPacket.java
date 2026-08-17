package com.niuqu.chatbubble.packets;
import com.niuqu.chatbubble.network.NetworkHandler;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

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
    private final ServerConfigDto dto;

    public ServerConfigScreenPacket(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                                    boolean mediaEnabled, boolean mediaAutoClean,
                                    List<String> chatTemplates, List<String> whisperTemplates) {
        this.dto = new ServerConfigDto(useTpa, historyEnabled, templateDebug, mediaEnabled,
            mediaAutoClean, chatTemplates, whisperTemplates);
    }

    public boolean useTpa() { return dto.useTpa(); }
    public boolean historyEnabled() { return dto.historyEnabled(); }
    public boolean templateDebug() { return dto.templateDebug(); }
    public boolean mediaEnabled() { return dto.mediaEnabled(); }
    public boolean mediaAutoClean() { return dto.mediaAutoClean(); }
    public List<String> chatTemplates() { return dto.chatTemplates(); }
    public List<String> whisperTemplates() { return dto.whisperTemplates(); }

    public static void encode(ServerConfigScreenPacket packet, FriendlyByteBuf buf) {
        ServerConfigDto.encode(packet.dto, buf);
    }

    public static ServerConfigScreenPacket decode(FriendlyByteBuf buf) {
        ServerConfigDto d = ServerConfigDto.decode(buf);
        return new ServerConfigScreenPacket(d.useTpa(), d.historyEnabled(), d.templateDebug(),
            d.mediaEnabled(), d.mediaAutoClean(), d.chatTemplates(), d.whisperTemplates());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ClientServerConfigGui.open(dto.useTpa(), dto.historyEnabled(), dto.templateDebug(),
                    dto.mediaEnabled(), dto.mediaAutoClean(), dto.chatTemplates(), dto.whisperTemplates())
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
