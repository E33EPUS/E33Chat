package com.niuqu.chatbubble.network;
import com.niuqu.chatbubble.ChatBubbleMod;

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

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.MediaUploadPacket.class, 7)
            .encoder(com.niuqu.chatbubble.packets.MediaUploadPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.MediaUploadPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.MediaUploadPacket::handle)
            .add();

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.MediaRequestPacket.class, 8)
            .encoder(com.niuqu.chatbubble.packets.MediaRequestPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.MediaRequestPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.MediaRequestPacket::handle)
            .add();

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.MediaCapPacket.class, 9)
            .encoder(com.niuqu.chatbubble.packets.MediaCapPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.MediaCapPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.MediaCapPacket::handle)
            .add();

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.MediaUploadAckPacket.class, 10)
            .encoder(com.niuqu.chatbubble.packets.MediaUploadAckPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.MediaUploadAckPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.MediaUploadAckPacket::handle)
            .add();

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.MediaResponsePacket.class, 11)
            .encoder(com.niuqu.chatbubble.packets.MediaResponsePacket::encode)
            .decoder(com.niuqu.chatbubble.packets.MediaResponsePacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.MediaResponsePacket::handle)
            .add();

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.EasyBotConfigPacket.class, 12)
            .encoder(com.niuqu.chatbubble.packets.EasyBotConfigPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.EasyBotConfigPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.EasyBotConfigPacket::handle)
            .add();

        // 2.4.10 group chat: handshake / say / directory / manage (ids 13-16).
        // Old clients safely drop unknown ids; a new client against an old
        // server just never receives id 15, so the tab strip stays hidden.
        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.ClientHelloPacket.class, 13)
            .encoder(com.niuqu.chatbubble.packets.ClientHelloPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.ClientHelloPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.ClientHelloPacket::handle)
            .add();

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.GroupChatPacket.class, 14)
            .encoder(com.niuqu.chatbubble.packets.GroupChatPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.GroupChatPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.GroupChatPacket::handle)
            .add();

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.GroupListPacket.class, 15)
            .encoder(com.niuqu.chatbubble.packets.GroupListPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.GroupListPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.GroupListPacket::handle)
            .add();

        CHANNEL.messageBuilder(com.niuqu.chatbubble.packets.GroupActionPacket.class, 16)
            .encoder(com.niuqu.chatbubble.packets.GroupActionPacket::encode)
            .decoder(com.niuqu.chatbubble.packets.GroupActionPacket::decode)
            .consumerMainThread(com.niuqu.chatbubble.packets.GroupActionPacket::handle)
            .add();
    }
}
