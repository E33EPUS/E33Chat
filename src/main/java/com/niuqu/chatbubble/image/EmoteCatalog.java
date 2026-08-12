package com.niuqu.chatbubble.image;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.mojang.logging.LogUtils;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import javax.imageio.ImageIO;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * Inline emote catalog: `[:token]` → image.
 *
 * Built-in tokens render from code-generated 16x16 pixel textures (registered
 * at e33chat:emote/<token>, no external dependencies). A user catalog file
 * (config/e33chat-emote.json, a flat {"token": "url"} map) overrides built-ins
 * with network images (loaded through ImageLoader, same anti-flood guards).
 */
public final class EmoteCatalog {
    private static final Logger LOGGER = LogUtils.getLogger();

    /** Built-in tokens (16x16 pixel-art drawn by {@link #drawPixelEmote}). */
    private static final String[] BUILTIN = {
        "happy", "sad", "angry", "love", "thumb", "ok", "cry", "laugh",
        "wow", "sleep", "cool", "fire"
    };

    private static final Map<String, ResourceLocation> BUILTIN_TEXTURES = new HashMap<>();
    private static final Map<String, String> CUSTOM_URLS = new HashMap<>();
    private static boolean registered = false;

    private EmoteCatalog() {}

    /** Registers built-in textures once (render thread). Idempotent. */
    public static void ensureRegistered() {
        if (registered) return;
        registered = true;
        for (String token : BUILTIN) {
            try {
                NativeImage img = drawPixelEmote(token);
                ResourceLocation id = ResourceLocation.fromNamespaceAndPath("e33chat", "emote/" + token);
                Minecraft.getInstance().getTextureManager().register(id,
                    new DynamicTexture(img));
                BUILTIN_TEXTURES.put(token, id);
            } catch (Throwable t) {
                LOGGER.warn("[e33chat] emote {} failed to register: {}", token, t.toString());
            }
        }
        LOGGER.info("[e33chat] emote catalog: {} builtin + {} custom", BUILTIN_TEXTURES.size(), CUSTOM_URLS.size());
    }

    /** Loads config/e33chat-emote.json (flat token → url). Safe to call on any thread. */
    public static void loadCustom(Path configDir) {
        CUSTOM_URLS.clear();
        try {
            Path p = configDir.resolve("e33chat-emote.json");
            if (!Files.isRegularFile(p)) return;
            String json = Files.readString(p);
            JsonObject obj = JsonParser.parseString(json).getAsJsonObject();
            for (var e : obj.entrySet()) {
                if (e.getValue().isJsonPrimitive() && e.getValue().getAsJsonPrimitive().isString()) {
                    String v = e.getValue().getAsString().trim();
                    if (ImageLoader.isUsableUrl(v)) CUSTOM_URLS.put(e.getKey(), v);
                }
            }
            LOGGER.info("[e33chat] emote custom catalog: {} entries", CUSTOM_URLS.size());
        } catch (Throwable t) {
            LOGGER.warn("[e33chat] emote custom catalog failed to load: {}", t.toString());
        }
    }

    /** Resolves a token (without brackets) to a texture id, or null if unknown. */
    public static ResourceLocation resolve(String token) {
        if (token == null || token.isBlank()) return null;
        ensureRegistered();
        ResourceLocation id = BUILTIN_TEXTURES.get(token);
        if (id != null) return id;
        String url = CUSTOM_URLS.get(token);
        if (url != null) {
            // Network emote: go through the shared image loader (cached, scaled).
            ImageEntry entry = ImageLoader.getOrLoad(url);
            if (entry != null && entry.state() == ImageEntry.State.LOADED && entry.textureId() != null) {
                return entry.textureId();
            }
            return null;
        }
        return null;
    }

    /** True when the token is known (built-in or custom) — used to decide placeholder replacement.
     *  Headless-safe (no MC access). */
    public static boolean contains(String token) {
        if (token == null || token.isBlank()) return false;
        for (String b : BUILTIN) {
            if (b.equals(token)) return true;
        }
        return CUSTOM_URLS.containsKey(token);
    }

    /** 16x16 pixel art for the built-in tokens (code-generated, no art assets needed). */
    private static NativeImage drawPixelEmote(String token) throws Exception {
        BufferedImage img = new BufferedImage(16, 16, BufferedImage.TYPE_INT_ARGB);
        java.awt.Graphics2D g = img.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        switch (token) {
            case "happy" -> drawFace(g, Color.YELLOW, new int[]{6, 10}, new int[]{5, 5}, true);
            case "sad" -> drawFace(g, Color.YELLOW, new int[]{6, 10}, new int[]{5, 9}, false);
            case "angry" -> drawFace(g, new Color(255, 140, 0), new int[]{6, 10}, new int[]{5, 5}, false);
            case "love" -> drawHeart(g);
            case "thumb" -> drawThumb(g);
            case "ok" -> drawOk(g);
            case "cry" -> drawFace(g, Color.CYAN, new int[]{6, 10}, new int[]{5, 6}, false);
            case "laugh" -> drawFace(g, Color.YELLOW, new int[]{6, 10}, new int[]{5, 5}, true);
            case "wow" -> drawFace(g, new Color(255, 220, 120), new int[]{5, 11}, new int[]{3, 4}, false);
            case "sleep" -> drawFace(g, new Color(200, 200, 220), new int[]{5, 5}, new int[]{8, 8}, false);
            case "cool" -> drawFace(g, new Color(120, 200, 120), new int[]{5, 11}, new int[]{5, 5}, true);
            case "fire" -> drawFire(g);
            default -> drawFace(g, Color.GRAY, new int[]{6, 10}, new int[]{5, 5}, false);
        }
        g.dispose();
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        NativeImage ni = NativeImage.read(new java.io.ByteArrayInputStream(out.toByteArray()));
        // ABGR32 swap for the pixel buffer
        int[] argb = img.getRGB(0, 0, 16, 16, null, 0, 16);
        for (int y = 0; y < 16; y++) {
            for (int x = 0; x < 16; x++) {
                int c = argb[y * 16 + x];
                ni.setPixelRGBA(x, y, (c & 0xFF00FF00) | ((c & 0x00FF0000) >> 16) | ((c & 0x000000FF) << 16));
            }
        }
        return ni;
    }

    private static void drawFace(java.awt.Graphics2D g, Color skin, int[] eyes, int[] eyeY, boolean smile) {
        g.setColor(skin);
        g.fillOval(1, 1, 14, 14);
        g.setColor(Color.BLACK);
        g.fillOval(eyes[0] - 1, eyeY[0], 3, 4);
        g.fillOval(eyes[1] - 1, eyeY[1], 3, 4);
        if (smile) {
            g.setStroke(new java.awt.BasicStroke(1.5f));
            g.drawArc(4, 8, 8, 6, 20, 140);
        } else {
            g.drawArc(4, 10, 8, 4, 200, 140);
        }
    }

    private static void drawHeart(java.awt.Graphics2D g) {
        g.setColor(new Color(255, 60, 60));
        g.fillOval(2, 3, 6, 6);
        g.fillOval(8, 3, 6, 6);
        int[] xs = {1, 15, 8};
        int[] ys = {8, 8, 15};
        g.fillPolygon(xs, ys, 3);
    }

    private static void drawThumb(java.awt.Graphics2D g) {
        g.setColor(new Color(240, 190, 120));
        g.fillRoundRect(2, 9, 12, 5, 2, 2);
        g.fillRoundRect(6, 4, 4, 7, 2, 2);
        g.setColor(new Color(200, 150, 90));
        g.drawLine(2, 9, 2, 13);
    }

    private static void drawOk(java.awt.Graphics2D g) {
        g.setColor(new Color(60, 180, 60));
        g.fillOval(2, 2, 12, 12);
        g.setColor(Color.WHITE);
        g.setStroke(new java.awt.BasicStroke(2f));
        g.drawArc(4, 6, 8, 6, 200, 140);
    }

    private static void drawFire(java.awt.Graphics2D g) {
        g.setColor(new Color(255, 120, 20));
        g.fillOval(3, 6, 10, 8);
        g.fillPolygon(new int[]{5, 8, 11}, new int[]{2, 6, 2}, 3);
        g.setColor(new Color(255, 220, 60));
        g.fillOval(5, 4, 6, 5);
        g.fillPolygon(new int[]{6, 8, 10}, new int[]{2, 5, 2}, 3);
    }
}
