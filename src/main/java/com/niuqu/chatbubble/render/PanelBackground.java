package com.niuqu.chatbubble.render;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.config.ChatBubbleConfig;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.File;
import java.io.FileInputStream;
import java.nio.file.Path;

/**
 * Custom chat-panel background image (2.4.10, client-only).
 *
 * The user picks a picture (config `panel_bg_image`); it is decoded off-thread,
 * uploaded once as a DynamicTexture and drawn stretched over the panel area
 * ("cover": aspect preserved, center-cropped). Missing/broken files fall back
 * to the default PANEL_BG texture (logged once per path). An empty path or a
 * failed load always renders the stock panel, so a bad config can never break
 * the chat screen.
 */
public final class PanelBackground {

    private static final Logger LOGGER = LogUtils.getLogger();
    private static final ResourceLocation ID = ResourceLocation.fromNamespaceAndPath("e33chat", "panel_bg_custom");

    private static final Object LOCK = new Object();
    private static String loadedKey = null;
    private static boolean registered;
    private static boolean failed;
    private static boolean warnedPath;
    private static int texW, texH;

    private PanelBackground() {}

    /** True when a custom image is loaded and ready to draw. */
    public static boolean available() {
        return registered && !failed && texW > 0 && texH > 0;
    }

    /** Main-thread: (re)load when the configured path changed; no-op otherwise. */
    public static void ensureLoaded() {
        String raw = ChatBubbleConfig.PANEL_BG_IMAGE.get();
        String key = raw == null ? "" : raw.trim();
        synchronized (LOCK) {
            if (key.equals(loadedKey)) return;
            // Path changed: drop the old texture synchronously so the very next
            // frame draws the fallback instead of a stale picture.
            unloadLocked();
            loadedKey = key;
            failed = false;
            warnedPath = false;
            if (key.isEmpty()) return;
        }
        File file = resolve(key);
        if (file == null || !file.isFile()) {
            synchronized (LOCK) {
                failed = true;
                if (!warnedPath) {
                    warnedPath = true;
                    LOGGER.info("[e33chat] panel background not found: {}", key);
                }
            }
            return;
        }
        final String forKey = key;
        com.niuqu.chatbubble.image.ImageLoader.executor().execute(() -> {
            NativeImage img = null;
            try (FileInputStream in = new FileInputStream(file)) {
                img = NativeImage.read(in);
                if (img.getWidth() <= 0 || img.getHeight() <= 0) throw new IllegalStateException("empty image");
                final NativeImage decoded = img;
                img = null;
                Minecraft.getInstance().execute(() -> apply(forKey, decoded));
            } catch (Throwable t) {
                if (img != null) img.close();
                synchronized (LOCK) {
                    failed = true;
                    if (!warnedPath) {
                        warnedPath = true;
                        LOGGER.info("[e33chat] panel background load failed: {} -> {}", forKey, t.toString());
                    }
                }
            }
        });
    }

    /** Render thread: upload + register; skipped if the path changed meanwhile. */
    private static void apply(String forKey, NativeImage decoded) {
        synchronized (LOCK) {
            if (!forKey.equals(loadedKey)) {
                decoded.close();
                return;
            }
            try {
                DynamicTexture tex = new DynamicTexture(decoded);
                Minecraft.getInstance().getTextureManager().register(ID, tex);
                texW = decoded.getWidth();
                texH = decoded.getHeight();
                registered = true;
                failed = false;
            } catch (Throwable t) {
                decoded.close();
                failed = true;
                LOGGER.info("[e33chat] panel background upload failed: {}", t.toString());
            }
        }
    }

    private static void unloadLocked() {
        if (registered) {
            try {
                Minecraft.getInstance().getTextureManager().release(ID);
            } catch (Throwable ignored) {}
        }
        registered = false;
        texW = 0;
        texH = 0;
    }

    /** Relative paths resolve against the game directory, absolute pass through. */
    private static File resolve(String path) {
        try {
            Path p = Path.of(path);
            if (!p.isAbsolute()) {
                p = Minecraft.getInstance().gameDirectory.toPath().resolve(path);
            }
            return p.toFile();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Draw the custom image over the given rect, "cover" style: aspect ratio
     * preserved, centered, edges cropped. Never upsets blend state (the shared
     * colored-texture path restores it).
     */
    public static void draw(net.minecraft.client.gui.GuiGraphics g,
                            int x, int y, int w, int h, float alpha) {
        if (!available() || w <= 0 || h <= 0 || alpha <= 0.003f) return;
        float scale = Math.max((float) w / texW, (float) h / texH);
        int srcW = Math.max(1, Math.round(w / scale));
        int srcH = Math.max(1, Math.round(h / scale));
        int u = (texW - srcW) / 2;
        int v = (texH - srcH) / 2;
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(
            g, ID, x, y, w, h, u, v, srcW, srcH, texW, texH, alpha);
    }
}
