package com.niuqu.chatbubble.packets;

import com.niuqu.chatbubble.store.ChatMessageStore;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * Server -> client: EasyBot compatibility toggle (2.4.3-beta).
 *
 * Registered as a separate id so old clients (which decode lower ids) never
 * desync: SimpleChannel drops the unknown id harmlessly. Absent packet =
 * disabled, matching the server config default.
 */
public class EasyBotConfigPacket {
    private final boolean easyBotCompat;

    public EasyBotConfigPacket(boolean easyBotCompat) {
        this.easyBotCompat = easyBotCompat;
    }

    public static void encode(EasyBotConfigPacket packet, FriendlyByteBuf buf) {
        buf.writeBoolean(packet.easyBotCompat);
    }

    public static EasyBotConfigPacket decode(FriendlyByteBuf buf) {
        return new EasyBotConfigPacket(buf.readBoolean());
    }

    public void handle(Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
            DistExecutor.unsafeRunWhenOn(Dist.CLIENT, () -> () ->
                ChatMessageStore.setEasyBotCompat(easyBotCompat)
            )
        );
        ctx.get().setPacketHandled(true);
    }
}
