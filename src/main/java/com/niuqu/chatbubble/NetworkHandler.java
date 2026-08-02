package com.niuqu.chatbubble;

import com.niuqu.chatbubble.packets.ChatMetaPacket;
import com.niuqu.chatbubble.packets.ConfigSyncPacket;
import com.niuqu.chatbubble.packets.ConfigSyncV2Packet;
import com.niuqu.chatbubble.packets.HistoryPacket;
import com.niuqu.chatbubble.packets.QuoteSyncPacket;
import com.niuqu.chatbubble.packets.ServerConfigSavePacket;
import com.niuqu.chatbubble.packets.ServerConfigScreenPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.simple.SimpleChannel;

public class NetworkHandler {
    private static final String PROTOCOL = "1";
    public static SimpleChannel CHANNEL;

    public static void register() {
        CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(ChatBubbleMod.MODID, "main"),
            () -> PROTOCOL,
            NetworkRegistry.acceptMissingOr(PROTOCOL),
            NetworkRegistry.acceptMissingOr(PROTOCOL)
        );

        CHANNEL.messageBuilder(QuoteSyncPacket.class, 0)
            .encoder(QuoteSyncPacket::encode)
            .decoder(QuoteSyncPacket::decode)
            .consumerMainThread(QuoteSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(ChatMetaPacket.class, 1)
            .encoder(ChatMetaPacket::encode)
            .decoder(ChatMetaPacket::decode)
            .consumerMainThread(ChatMetaPacket::handle)
            .add();

        CHANNEL.messageBuilder(HistoryPacket.class, 2)
            .encoder(HistoryPacket::encode)
            .decoder(HistoryPacket::decode)
            .consumerMainThread(HistoryPacket::handle)
            .add();

        CHANNEL.messageBuilder(ConfigSyncPacket.class, 3)
            .encoder(ConfigSyncPacket::encode)
            .decoder(ConfigSyncPacket::decode)
            .consumerMainThread(ConfigSyncPacket::handle)
            .add();

        CHANNEL.messageBuilder(ConfigSyncV2Packet.class, 4)
            .encoder(ConfigSyncV2Packet::encode)
            .decoder(ConfigSyncV2Packet::decode)
            .consumerMainThread(ConfigSyncV2Packet::handle)
            .add();

        CHANNEL.messageBuilder(ServerConfigScreenPacket.class, 5)
            .encoder(ServerConfigScreenPacket::encode)
            .decoder(ServerConfigScreenPacket::decode)
            .consumerMainThread(ServerConfigScreenPacket::handle)
            .add();

        CHANNEL.messageBuilder(ServerConfigSavePacket.class, 6)
            .encoder(ServerConfigSavePacket::encode)
            .decoder(ServerConfigSavePacket::decode)
            .consumerMainThread(ServerConfigSavePacket::handle)
            .add();
    }
}
