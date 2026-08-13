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
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
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

    public static ChatBubbleConfig config() { return config; }

    public static void saveConfig(ChatBubbleConfig newConfig) {
        config = newConfig;
        com.mojang.logging.LogUtils.getLogger().info("[e33chat] Saving config | soundPublic=" + newConfig.soundPublic() + " | soundSystem=" + newConfig.soundSystem());
        ConfigManager.save(configPath, config);
    }

    @Override
    public void onInitializeClient() {
        Path configDir = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/e33chat");
        configPath = configDir.resolve("client.json");
        // Directory move (2.3.12): config/e33chat-client.json -> config/e33chat/client.json.
        // Also carries the older flat config/e33chat.json legacy chain.
        Path legacyPath = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/e33chat-client.json");
        Path oldFlatPath = MinecraftClient.getInstance().runDirectory.toPath().resolve("config/e33chat.json");
        if (!Files.exists(configPath)) {
            if (Files.exists(legacyPath)) {
                try {
                    Files.createDirectories(configDir);
                    Files.move(legacyPath, configPath, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    com.mojang.logging.LogUtils.getLogger().info("[e33chat] Migrated config from config/e33chat-client.json to config/e33chat/client.json");
                } catch (Exception e) {
                    com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Config migration failed", e);
                }
            } else if (Files.exists(oldFlatPath)) {
                config = ConfigManager.load(oldFlatPath);
                ConfigManager.save(configPath, config);
                try {
                    Files.delete(oldFlatPath);
                } catch (Exception e) {
                    com.mojang.logging.LogUtils.getLogger().warn("[e33chat] Legacy config cleanup failed", e);
                }
                com.mojang.logging.LogUtils.getLogger().info("[e33chat] Migrated config from config/e33chat.json to config/e33chat/client.json");
            }
        }
        if (config == null || !Files.exists(configPath)) {
            if (Files.exists(legacyPath)) {
                // Migration move failed earlier (locked/IO) — read in place so
                // settings are never silently replaced by defaults.
                config = ConfigManager.load(legacyPath);
            } else if (Files.exists(oldFlatPath)) {
                config = ConfigManager.load(oldFlatPath);
            } else {
                config = ConfigManager.load(configPath);
            }
        }

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

        HudRenderCallback.EVENT.register((drawContext, tickDelta) -> {
            if (!config.enabled()) return;
            ChatBubbleHudOverlay.render(drawContext);
        });

        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            ImageLoader.tick();
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

        ScreenEvents.BEFORE_INIT.register((client, screen, width, height) ->
            ScreenEvents.afterRender(screen).register((scr, g, mouseX, mouseY, delta) -> {
                if (config.enabled()) ChatBubbleHudOverlay.renderBannerForScreen(g);
            })
        );

        ResourceManagerHelper.get(ResourceType.CLIENT_RESOURCES).registerReloadListener(
            new SimpleSynchronousResourceReloadListener() {
                @Override
                public Identifier getFabricId() {
                    return Identifier.of(ChatBubbleMod.MOD_ID, "shader_reload");
                }
                @Override
                public void reload(ResourceManager manager) {
                    RoundRectRenderer.resetShader();
                }
            }
        );
    }
}
