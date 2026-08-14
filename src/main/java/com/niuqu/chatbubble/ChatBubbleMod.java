package com.niuqu.chatbubble;

import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLClientSetupEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;
import net.minecraftforge.fml.loading.FMLEnvironment;
import net.minecraftforge.fml.loading.FMLPaths;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

@Mod(ChatBubbleMod.MODID)
public class ChatBubbleMod {
    public static final String MODID = "e33chat";

    public ChatBubbleMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            (FMLCommonSetupEvent event) -> event.enqueueWork(NetworkHandler::register));

        // registerConfig(CLIENT) must run from the mod constructor: Forge loads
        // client configs during CONFIG_LOAD (which precedes FMLClientSetupEvent)
        // and only binds the childConfig there — registering later leaves the
        // toml never loaded, so the config screen shows defaults and set() NPEs.
        // ChatBubbleClientSetup still touches client-only classes, so it stays
        // in the FMLClientSetupEvent handler below.
        if (FMLEnvironment.dist.isClient()) {
            migrateClientConfig();
            ModLoadingContext.get().registerConfig(ModConfig.Type.CLIENT,
                ChatBubbleConfig.CLIENT_CONFIG, "e33chat/e33chat-client.toml");
        }

        // FMLClientSetupEvent fires only on the client, so ChatBubbleClientSetup
        // (which loads client-only classes) is never touched on a dedicated
        // server. DistExecutor.safeRunWhenOn with a method reference would eager
        // load the referenced class in dev to validate @OnlyIn — that crashed a
        // dedicated server with "Attempted to load class ChatBubbleClientSetup
        // for invalid dist DEDICATED_SERVER".
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            (FMLClientSetupEvent event) -> ChatBubbleClientSetup.init());

        ModLoadingContext.get().registerConfig(ModConfig.Type.SERVER,
            ChatServerConfig.SERVER_CONFIG, "e33chat-server.toml");

        // /reload or world change reloads the server toml — resync so running
        // clients never drift from the file
        var modBus = FMLJavaModLoadingContext.get().getModEventBus();
        modBus.addListener((ModConfigEvent.Loading event) -> resyncIfServerConfig(event));
        modBus.addListener((ModConfigEvent.Reloading event) -> resyncIfServerConfig(event));

        MinecraftForge.EVENT_BUS.register(new ChatServerListener());
        MinecraftForge.EVENT_BUS.register(new com.niuqu.chatbubble.command.E33ChatCommands());
    }

    // 2.3.12: client config moved from config/e33chat-client.toml to
    // config/e33chat/e33chat-client.toml (next to the emote pack). Runs before
    // registerConfig so Forge loads the migrated file on first start.
    private static void migrateClientConfig() {
        Path configDir = FMLPaths.CONFIGDIR.get();
        Path oldPath = configDir.resolve("e33chat-client.toml");
        Path newPath = configDir.resolve("e33chat").resolve("e33chat-client.toml");
        if (!Files.exists(oldPath) || Files.exists(newPath)) return;
        try {
            Files.createDirectories(newPath.getParent());
            Files.move(oldPath, newPath, StandardCopyOption.REPLACE_EXISTING);
            com.mojang.logging.LogUtils.getLogger().info(
                "[e33chat] Migrated config from config/e33chat-client.toml to config/e33chat/e33chat-client.toml");
        } catch (Exception e) {
            com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Config migration failed", e);
        }
    }

    // Captured at config-load time; the /e33chat template commands use it to
    // persist changes back to the toml file
    private static ModConfig serverConfig;

    public static ModConfig getServerConfig() { return serverConfig; }

    public static void saveServerConfig() {
        ModConfig config = serverConfig;
        if (config != null) config.save();
    }

    // ForgeConfigSpec.set() only writes the in-memory nightconfig — the config
    // screen must explicitly save() or changes vanish on restart
    private static ModConfig clientConfig;

    public static void saveClientConfig() {
        ModConfig config = clientConfig;
        if (config != null) config.save();
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
