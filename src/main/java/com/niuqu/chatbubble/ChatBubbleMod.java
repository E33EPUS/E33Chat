package com.niuqu.chatbubble;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.fml.ModLoadingContext;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;
import net.minecraftforge.fml.event.lifecycle.FMLCommonSetupEvent;
import net.minecraftforge.fml.javafmlmod.FMLJavaModLoadingContext;

@Mod(ChatBubbleMod.MODID)
public class ChatBubbleMod {
    public static final String MODID = "e33chat";

    public ChatBubbleMod() {
        FMLJavaModLoadingContext.get().getModEventBus().addListener(
            (FMLCommonSetupEvent event) -> event.enqueueWork(NetworkHandler::register));

        DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> ChatBubbleClientSetup::init);

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

    // Captured at config-load time; the /e33chat template commands use it to
    // persist changes back to the toml file
    private static ModConfig serverConfig;

    public static ModConfig getServerConfig() { return serverConfig; }

    public static void saveServerConfig() {
        ModConfig config = serverConfig;
        if (config != null) config.save();
    }

    private static void resyncIfServerConfig(ModConfigEvent event) {
        ModConfig config = event.getConfig();
        if (config.getType() == ModConfig.Type.SERVER) {
            serverConfig = config;
            ChatServerListener.broadcastServerConfig();
        }
    }
}
