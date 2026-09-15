package com.niuqu.chatbubble.render;

import net.minecraft.client.texture.NativeImage;
import com.mojang.logging.LogUtils;
import com.niuqu.chatbubble.ChatBubbleClientSetup;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
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
    private static final Identifier ID = Identifier.of("e33chat", "panel_bg_custom");

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
        String raw = ChatBubbleClientSetup.config().panelBgImage();
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
                MinecraftClient.getInstance().execute(() -> apply(forKey, decoded));
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
                NativeImageBackedTexture tex = new NativeImageBackedTexture(decoded);
                MinecraftClient.getInstance().getTextureManager().registerTexture(ID, tex);
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
                MinecraftClient.getInstance().getTextureManager().destroyTexture(ID);
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
                p = FabricLoader.getInstance().getGameDir().resolve(path);
            }
            return p.toFile();
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Where the panel background is looking, in normalized picture coordinates:
     * the point that is centered, plus how tight the view is. Wide pictures over
     * a narrow panel lose their sides under plain cover-cropping, so the user
     * needs a way to choose which part survives — that choice lives here.
     *
     * <p>Expressed as center + zoom rather than an absolute rectangle so it
     * survives the panel changing shape: when the panel's aspect ratio changes,
     * the same center and zoom still describe something valid.
     */
    public record Crop(float centerX, float centerY, float zoom) {
        /** Centered, widest view: exactly the old cover-crop behaviour. */
        public static final Crop DEFAULT = new Crop(0.5f, 0.5f, 1f);
    }

    /**
     * Parses the {@code panel_bg_crop} value ("centerX,centerY,zoom"). Blank or
     * malformed input falls back to {@link Crop#DEFAULT}, so a hand-edited config
     * can never wedge the panel.
     */
    public static Crop parseCrop(String raw) {
        if (raw == null || raw.isBlank()) return Crop.DEFAULT;
        String[] parts = raw.trim().split(",");
        if (parts.length != 3) return Crop.DEFAULT;
        try {
            float cx = Float.parseFloat(parts[0].trim());
            float cy = Float.parseFloat(parts[1].trim());
            float zoom = Float.parseFloat(parts[2].trim());
            if (!Float.isFinite(cx) || !Float.isFinite(cy) || !Float.isFinite(zoom)) return Crop.DEFAULT;
            return new Crop(clamp01(cx), clamp01(cy), Math.max(1f, zoom));
        } catch (NumberFormatException e) {
            return Crop.DEFAULT;
        }
    }

    /** Serializes a crop for the config; the inverse of {@link #parseCrop}. */
    public static String formatCrop(Crop crop) {
        return String.format(java.util.Locale.ROOT, "%.4f,%.4f,%.4f",
            crop.centerX(), crop.centerY(), crop.zoom());
    }

    private static float clamp01(float v) {
        return v < 0f ? 0f : (v > 1f ? 1f : v);
    }

    /**
     * Source rectangle (u, v, width, height) of the picture to draw into a
     * {@code targetW x targetH} area, for the given crop. Pure function so the
     * framing maths can be tested without a GL context.
     *
     * <p>Zoom 1 is the widest rectangle with the target's aspect ratio that still
     * covers the area (the classic cover crop); larger zoom tightens it. The
     * result is always inside the picture.
     */
    public static int[] sourceRect(int texW, int texH, int targetW, int targetH, Crop crop) {
        if (texW <= 0 || texH <= 0 || targetW <= 0 || targetH <= 0) return new int[]{0, 0, 1, 1};
        float targetAspect = (float) targetW / targetH;
        float srcW = texW, srcH = texH;
        // Widest cover rectangle for this aspect ratio.
        if ((float) texW / texH > targetAspect) {
            srcW = texH * targetAspect;
        } else {
            srcH = texW / targetAspect;
        }
        // Tighten by zoom, never past the picture's own bounds.
        float zoom = Math.max(1f, crop.zoom());
        srcW /= zoom;
        srcH /= zoom;
        float minZoom = Math.max(srcW / texW, srcH / texH);
        if (minZoom > 1f) {
            srcW /= minZoom;
            srcH /= minZoom;
        }
        int w = Math.max(1, Math.round(srcW));
        int h = Math.max(1, Math.round(srcH));
        // Center on the chosen point, then keep the rect inside the picture.
        int u = Math.round(crop.centerX() * texW - w / 2f);
        int v = Math.round(crop.centerY() * texH - h / 2f);
        u = Math.max(0, Math.min(u, texW - w));
        v = Math.max(0, Math.min(v, texH - h));
        return new int[]{u, v, w, h};
    }

    /** Decoded picture size, or 0 when nothing is loaded (used by the crop editor). */
    public static int imageWidth() { return texW; }
    public static int imageHeight() { return texH; }

    /** Texture of the loaded picture, or null when nothing is loaded. */
    public static Identifier textureId() { return available() ? ID : null; }

    /**
     * Aspect ratio (w/h) the panel was last drawn at. The crop editor frames the
     * selection box with it, so the picture the user picks is exactly what the
     * panel will show. The chat panel draws every frame, so this is current by
     * the time any settings screen opens; the fallback only matters in the
     * window between mod start and the first frame.
     */
    private static volatile float lastAspect = 0.4f;
    public static float lastTargetAspect() { return lastAspect; }

    /**
     * Draw the custom image over the given rect using the configured framing.
     * Never upsets blend state (the shared colored-texture path restores it).
     */
    public static void draw(net.minecraft.client.gui.DrawContext g,
                            int x, int y, int w, int h, float alpha) {
        if (w > 0 && h > 0) lastAspect = (float) w / h;
        if (!available() || w <= 0 || h <= 0 || alpha <= 0.003f) return;
        int[] src = sourceRect(texW, texH, w, h, parseCrop(ChatBubbleClientSetup.config().panelBgCrop()));
        com.niuqu.chatbubble.texture.ColoredTextureRenderer.drawWithAlpha(
            g, ID, x, y, w, h, src[0], src[1], src[2], src[3], texW, texH, alpha);
    }
}
