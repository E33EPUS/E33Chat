package com.niuqu.chatbubble;

import com.niuqu.chatbubble.packets.ChatMetaPayload;
import com.niuqu.chatbubble.packets.ClientServerConfigGui;
import com.niuqu.chatbubble.packets.ConfigSyncPayload;
import com.niuqu.chatbubble.packets.ConfigSyncV2Payload;
import com.niuqu.chatbubble.packets.HistoryPayload;
import com.niuqu.chatbubble.packets.QuoteSyncPayload;
import com.niuqu.chatbubble.packets.ServerConfigSavePayload;
import com.niuqu.chatbubble.packets.ServerConfigScreenPayload;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.neoforge.network.event.RegisterPayloadHandlersEvent;
import net.neoforged.neoforge.network.registration.PayloadRegistrar;

public class NetworkHandler {

    @SubscribeEvent
    public static void registerPayloads(RegisterPayloadHandlersEvent event) {
        // optional: joining servers without this mod must not be rejected
        PayloadRegistrar registrar = event.registrar("1").optional();
        registrar.playToServer(QuoteSyncPayload.TYPE, QuoteSyncPayload.STREAM_CODEC, QuoteSyncPayload::handleServer);
        registrar.playToClient(ChatMetaPayload.TYPE, ChatMetaPayload.STREAM_CODEC, ChatMetaPayload::handleClient);
        registrar.playToClient(HistoryPayload.TYPE, HistoryPayload.STREAM_CODEC, HistoryPayload::handleClient);
        registrar.playToClient(ConfigSyncPayload.TYPE, ConfigSyncPayload.STREAM_CODEC, ConfigSyncPayload::handleClient);
        registrar.playToClient(ConfigSyncV2Payload.TYPE, ConfigSyncV2Payload.STREAM_CODEC, ConfigSyncV2Payload::handleClient);
        // Lambda (not a method reference): a method reference resolves its target
        // class eagerly at registration, which would load the client-only GUI class
        // on a dedicated server and trip FML's RuntimeDistCleaner. The lambda body
        // runs only when a client actually receives the packet, so the client-side
        // reference never touches server class loading.
        registrar.playToClient(ServerConfigScreenPayload.TYPE, ServerConfigScreenPayload.STREAM_CODEC,
            (payload, ctx) -> {
                if (net.neoforged.fml.loading.FMLEnvironment.dist == net.neoforged.api.distmarker.Dist.CLIENT) {
                    ClientServerConfigGui.open(payload, ctx);
                }
            });
        registrar.playToServer(ServerConfigSavePayload.TYPE, ServerConfigSavePayload.STREAM_CODEC, ServerConfigSavePayload::handleServer);
    }
}
