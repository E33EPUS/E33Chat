package com.niuqu.chatbubble;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

@Mod(ChatBubbleMod.MODID)
public class ChatBubbleMod {
    public static final String MODID = "e33chat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChatBubbleMod(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        modEventBus.register(NetworkHandler.class);

        if (dist == Dist.CLIENT) {
            modContainer.registerConfig(ModConfig.Type.CLIENT,
                ChatBubbleConfig.CLIENT_CONFIG, "e33chat-client.toml");
        }

        modContainer.registerConfig(ModConfig.Type.SERVER,
            ChatServerConfig.SERVER_CONFIG, "e33chat-server.toml");

        // /reload or world change reloads the server toml — resync so running
        // clients never drift from the file
        modEventBus.addListener((ModConfigEvent.Loading event) -> resyncIfServerConfig(event));
        modEventBus.addListener((ModConfigEvent.Reloading event) -> resyncIfServerConfig(event));

        NeoForge.EVENT_BUS.register(new ChatServerListener());
        NeoForge.EVENT_BUS.register(new com.niuqu.chatbubble.command.E33ChatCommands());
    }

    // Captured at config-load time; the /e33chat template commands use it to
    // persist changes back to the toml file
    private static ModConfig serverConfig;

    public static ModConfig getServerConfig() { return serverConfig; }

    public static void saveServerConfig() {
        ModConfig config = serverConfig;
        // NeoForge's ModConfig has no save(); the loaded IConfigSpec does
        if (config != null && config.getLoadedConfig() != null) config.getLoadedConfig().save();
    }

    // ModConfigSpec.set() only writes the in-memory nightconfig — the config
    // screen must explicitly save() or changes vanish on restart
    private static ModConfig clientConfig;

    public static void saveClientConfig() {
        ModConfig config = clientConfig;
        if (config != null && config.getLoadedConfig() != null) config.getLoadedConfig().save();
    }

    private static void resyncIfServerConfig(ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getType() == ModConfig.Type.SERVER) {
            serverConfig = config;
            ChatServerListener.broadcastServerConfig();
        } else if (config.getType() == ModConfig.Type.CLIENT) {
            clientConfig = config;
        }
    }
}
