package com.niuqu.chatbubble;
import com.niuqu.chatbubble.config.ChatBubbleConfigScreen;
import com.niuqu.chatbubble.render.RoundRectRenderer;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executor;
import net.minecraft.server.packs.resources.PreparableReloadListener;
import net.minecraft.server.packs.resources.ResourceManager;
import net.minecraft.util.profiling.ProfilerFiller;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.fml.ModContainer;
import net.neoforged.fml.common.Mod;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.neoforged.neoforge.client.event.RegisterClientReloadListenersEvent;
import net.neoforged.neoforge.client.gui.IConfigScreenFactory;
import net.neoforged.neoforge.common.NeoForge;

@Mod(value = ChatBubbleMod.MODID, dist = Dist.CLIENT)
public class ChatBubbleClientSetup {
    public ChatBubbleClientSetup(ModContainer container, IEventBus modEventBus) {
        container.registerExtensionPoint(IConfigScreenFactory.class,
            (modContainer, screen) -> new ChatBubbleConfigScreen(screen));
        NeoForge.EVENT_BUS.register(new ChatBubbleClientListener());

        modEventBus.addListener(this::onClientSetup);
        modEventBus.addListener(RoundRectRenderer::registerShaders);
        modEventBus.addListener(this::registerReloadListener);
    }

    private void registerReloadListener(RegisterClientReloadListenersEvent event) {
        event.registerReloadListener(new PreparableReloadListener() {
            @Override
            public CompletableFuture<Void> reload(PreparableReloadListener.PreparationBarrier barrier, ResourceManager resourceManager,
                                                  ProfilerFiller prepProfiler, ProfilerFiller reloadProfiler,
                                                  Executor backgroundExecutor, Executor gameExecutor) {
                // 纹理全部走 blit(RL) 懒加载（getTexture 自动 new SimpleTexture），F3+T 重载后自动重读资源包新 PNG
                return barrier.wait(null);
            }
        });
    }

    private void onClientSetup(FMLClientSetupEvent event) {
        ChatBubbleMod.LOGGER.info("E33Chat client setup done");
    }
}
