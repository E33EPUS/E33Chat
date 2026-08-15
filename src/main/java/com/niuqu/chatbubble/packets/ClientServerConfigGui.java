package com.niuqu.chatbubble.packets;
import com.niuqu.chatbubble.network.NetworkHandler;

import com.niuqu.chatbubble.ServerConfigScreen;
import net.minecraft.client.Minecraft;

import java.util.List;

/**
 * Client-only bridge that opens the server-config GUI from a
 * ServerConfigScreenPacket.
 *
 * Deliberately NOT annotated @OnlyIn(CLIENT): it is only ever loaded on the
 * client (the payload handler runs client-side only), and the dist check in the
 * packet handler keeps the reference out of server-side class loading. If this
 * class were @OnlyIn(CLIENT), any eager reference from a dual-side class (e.g. a
 * method reference in NetworkHandler) would trip FML's RuntimeDistCleaner on a
 * dedicated server.
 */
public class ClientServerConfigGui {

    public static void open(boolean useTpa, boolean historyEnabled, boolean templateDebug,
                            boolean mediaEnabled,
                            List<String> chatTemplates, List<String> whisperTemplates) {
        Minecraft.getInstance().setScreen(new ServerConfigScreen(
            Minecraft.getInstance().screen,
            useTpa, historyEnabled, templateDebug, mediaEnabled, chatTemplates, whisperTemplates));
    }
}
