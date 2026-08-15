package com.niuqu.chatbubble;
import com.niuqu.chatbubble.config.ChatServerConfig;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.server.ChatServerListener;
import com.niuqu.chatbubble.network.NetworkHandler;

import com.mojang.logging.LogUtils;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.config.ModConfig;
import net.neoforged.fml.event.config.ModConfigEvent;
import net.neoforged.neoforge.common.NeoForge;
import org.slf4j.Logger;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Mod(ChatBubbleMod.MODID)
public class ChatBubbleMod {
    public static final String MODID = "e33chat";
    public static final Logger LOGGER = LogUtils.getLogger();

    public ChatBubbleMod(IEventBus modEventBus, ModContainer modContainer, Dist dist) {
        modEventBus.register(NetworkHandler.class);

        if (dist == Dist.CLIENT) {
            migrateClientConfig();
            modContainer.registerConfig(ModConfig.Type.CLIENT,
                ChatBubbleConfig.CLIENT_CONFIG, "e33chat/e33chat-client.toml");
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

    // 2.3.12: client config moved from config/e33chat-client.toml to
    // config/e33chat/e33chat-client.toml (next to the emote pack). Runs before
    // registerConfig so NeoForge loads the migrated file on first start.
    private static void migrateClientConfig() {
        Path configDir = net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get();
        Path oldPath = configDir.resolve("e33chat-client.toml");
        Path newPath = configDir.resolve("e33chat").resolve("e33chat-client.toml");
        if (!Files.exists(oldPath) || Files.exists(newPath)) return;
        try {
            Files.createDirectories(newPath.getParent());
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            LOGGER.info("[e33chat] Migrated config from config/e33chat-client.toml to config/e33chat/e33chat-client.toml");
        } catch (Exception e) {
            LOGGER.warn("[e33chat] Config migration failed", e);
        }
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
