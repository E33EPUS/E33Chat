package com.niuqu.chatbubble;

import com.niuqu.chatbubble.config.ChatBubbleConfig;
import com.niuqu.chatbubble.config.ConfigManager;
import com.niuqu.chatbubble.image.ImageLoader;
import com.niuqu.chatbubble.network.ChatMetaPayload;
import com.niuqu.chatbubble.network.ConfigSyncPayload;
import com.niuqu.chatbubble.network.ConfigSyncV2Payload;
import com.niuqu.chatbubble.network.HistoryPayload;
import com.niuqu.chatbubble.network.MediaCapPayload;
import com.niuqu.chatbubble.network.ServerConfigScreenPayload;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.networking.v1.ClientPlayNetworking;
//#if MC < 26000
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
//#endif
import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.fabricmc.fabric.api.resource.ResourceManagerHelper;
import net.fabricmc.fabric.api.resource.SimpleSynchronousResourceReloadListener;
import net.minecraft.client.MinecraftClient;
import net.minecraft.resource.ResourceManager;
import net.minecraft.resource.ResourceType;
import net.minecraft.util.Identifier;

import java.nio.file.Files;
import java.nio.file.Path;

public class ChatBubbleClientSetup implements ClientModInitializer {
    private static ChatBubbleConfig config = ChatBubbleConfig.defaults();
    private static Path configPath;
    private static boolean leftWasDown;
    private static boolean wasInWorld;

    public static ChatBubbleConfig config() { return config; }

    public static void saveConfig(ChatBubbleConfig newConfig) {
        config = newConfig;
        E33Log.info("[e33chat] Saving config | soundPublic=" + newConfig.soundPublic() + " | soundSystem=" + newConfig.soundSystem());
        ConfigManager.save(configPath, config);
    }

    @Override
    public void onInitializeClient() {
        configPath = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/e33chat-client.json");
        // v2.3.x renamed the file from e33chat.json to e33chat-client.json (aligns with
        // Forge/Neo); migrate an existing old file so users keep their settings
        Path legacyPath = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/e33chat.json");
        if (!Files.exists(configPath) && Files.exists(legacyPath)) {
            config = ConfigManager.load(legacyPath);
            ConfigManager.save(configPath, config);
            E33Log.info("[e33chat] Migrated config from config/e33chat.json to config/e33chat-client.json");
        } else {
            config = ConfigManager.load(configPath);
        }

        //#if MC >= 12005
        ClientPlayNetworking.registerGlobalReceiver(ChatMetaPayload.ID, (payload, context) -> {
            context.client().execute(() -> ChatMessageStore.applyChatMeta(
                payload.senderUUID(), payload.senderName(), payload.messageHash(),
                payload.quoteSender(), payload.quoteContent(), payload.mentionTargets()));
        });
        ClientPlayNetworking.registerGlobalReceiver(HistoryPayload.ID, (payload, context) -> {
            context.client().execute(() -> ChatMessageStore.addHistoryMessages(payload.entries()));
        });
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncPayload.ID, (payload, context) -> {
            context.client().execute(() -> ConfigSyncPayload.handle(payload));
        });
        ClientPlayNetworking.registerGlobalReceiver(ConfigSyncV2Payload.ID, (payload, context) -> {
            context.client().execute(() -> ConfigSyncV2Payload.handle(payload));
        });
        // Server-config GUI: opened on the client only (server never loads the Screen)
        ClientPlayNetworking.registerGlobalReceiver(ServerConfigScreenPayload.ID, (payload, context) -> {
            context.client().execute(() -> MinecraftClient.getInstance().setScreen(new ServerConfigScreen(
                MinecraftClient.getInstance().currentScreen,
                payload.useTpa(), payload.historyEnabled(), payload.templateDebug(),
                payload.chatTemplates(), payload.whisperTemplates())));
        });

        com.niuqu.chatbubble.image.MediaClient.registerReceivers();
        ClientPlayNetworking.registerGlobalReceiver(MediaCapPayload.ID, (payload, context) -> {
            context.client().execute(() -> MediaCapPayload.handle(payload));
        });
        //#endif

        //#if MC < 26000
        //#if MC >= 12000
        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!config.enabled()) return;
            ChatBubbleHudOverlay.render(drawContext);
        });
        //#else
        //$$ HudRenderCallback.EVENT.register((matrices, tickDelta) -> {
        //$$     if (!config.enabled()) return;
        //$$     ChatBubbleHudOverlay.render(new DrawContext(matrices));
        //$$ });
        //#endif
        //#endif

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ImageLoader.tick();

            // Clean up GL resources when leaving a world — BlurRenderer FBOs must
            // be freed to prevent stale framebuffer binding on disconnect.
            boolean inWorld = client.world != null && client.player != null;
            if (wasInWorld && !inWorld) {
                BlurRenderer.cleanup();
            }
            wasInWorld = inWorld;

            // 纹理全部走 drawTexture(Identifier) 懒加载（getTexture 自动 new ResourceTexture），F3+T 重载后自动重读资源包新 PNG
            if (!config.enabled()) return;

            String key;
            if (client.world == null || client.player == null) {
                key = null;
            } else if (client.getServer() != null) {
                key = "SP:" + client.getServer().getSaveProperties().getLevelName();
            } else if (client.getCurrentServerEntry() != null) {
                key = "MP:" + client.getCurrentServerEntry().name;
            } else {
                key = "world";
            }
            ChatMessageStore.setCurrentWorld(key);
            ChatMessageStore.maybeAutoSave();

            if (client.currentScreen == null) {
                boolean leftDown = org.lwjgl.glfw.GLFW.glfwGetMouseButton(
                    client.getWindow().getHandle(), org.lwjgl.glfw.GLFW.GLFW_MOUSE_BUTTON_1) == org.lwjgl.glfw.GLFW.GLFW_PRESS;
                if (leftDown && !leftWasDown) {
                    double mx = client.mouse.getX() * (double)client.getWindow().getScaledWidth() / (double)client.getWindow().getWidth();
                    double my = client.mouse.getY() * (double)client.getWindow().getScaledHeight() / (double)client.getWindow().getHeight();
                    if (ChatBubbleHudOverlay.isMouseOverIcon(mx, my)) {
                        client.setScreen(new ChatBubbleScreen(""));
                    }
                }
                leftWasDown = leftDown;
            } else {
                leftWasDown = false;
            }
        });

        //#if MC < 26000
        //#if MC >= 12000
        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) ->
            ScreenEvents.afterRender(screen).register((scr, g, mouseX, mouseY, delta) -> {
                if (config.enabled()) ChatBubbleHudOverlay.renderBannerForScreen(g);
            })
        );
        //#else
        //$$ ScreenEvents.BEFORE_INIT.register((client, screen, width, height) ->
        //$$     ScreenEvents.afterRender(screen).register((scr, matrices, mouseX, mouseY, delta) -> {
        //$$         if (config.enabled()) ChatBubbleHudOverlay.renderBannerForScreen(new DrawContext(matrices));
        //$$     })
        //$$ );
        //#endif
        //#endif

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    //#if MC >= 12000
                    return Identifier.of(ChatBubbleMod.MOD_ID, "shader_reload");
                    //#else
                    //$$ return new Identifier(ChatBubbleMod.MOD_ID, "shader_reload");
                    //#endif
                }
                @Override
                //#if MC >= 26000
                //$$ public void onResourceManagerReload(ResourceManager manager) {
                //#else
                public void reload(ResourceManager manager) {
                //#endif
                    RoundRectRenderer.resetShader();
                }
            }
        );
    }
}
