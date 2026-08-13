package com.niuqu.chatbubble;

import com.niuqu.chatbubble.image.RasterImageDecoder;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local emote pack: static images in &lt;configDir&gt;/e33chat/emotes/ (png/jpg/gif).
 * Clicking one sends it through the normal image upload path.
 */
public final class EmoteStore {
    private static final Logger LOGGER = com.mojang.logging.LogUtils.getLogger();
    public static final int EMOTE_MAX = 10;
    private static final List<File> emotes = new ArrayList<>();
    private static final Map<File, ResourceLocation> textures = new HashMap<>();
    private static int textureSeq;
    private static boolean scanned;

    private EmoteStore() {}

    public static File dir() {
        return net.neoforged.fml.loading.FMLPaths.CONFIGDIR.get().resolve("e33chat/emotes").toFile();
    }

    private static boolean isImage(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg")
            || n.endsWith(".gif");
    }

    /** Re-scans the emote dir. Called when the panel opens and after edits. */
    public static void refresh() {
        scanned = true;
        emotes.clear();
        File d = dir();
        File[] files = d.listFiles();
        if (files != null) {
            // Sort FIRST, then truncate — listFiles() order is unspecified,
            // truncating unsorted would pick a random subset when over the cap.
            for (File f : files) {
                if (isImage(f)) emotes.add(f);
            }
            emotes.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
            while (emotes.size() > EMOTE_MAX) emotes.remove(emotes.size() - 1);
        }
        textures.keySet().removeIf(f -> !emotes.contains(f));
    }

    public static List<File> list() {
        if (!scanned) refresh();
        return emotes;
    }

    /** Copies the file into the emote dir. False when full or invalid. */
    public static boolean add(File f) {
        if (!isImage(f)) return false;
        if (emotes.size() >= EMOTE_MAX) return false;
        try {
            File d = dir();
            if (!d.isDirectory() && !d.mkdirs()) return false;
            File dest = new File(d, f.getName());
            Files.copy(f.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean remove(File f) {
        if (f.delete()) {
            refresh();
            return true;
        }
        return false;
    }

    /** Saves clipboard/pasted image bytes into the emote dir. False when full or IO fails. */
    public static boolean addBytes(byte[] png, String name) {
        if (png == null || png.length == 0) return false;
        if (emotes.size() >= EMOTE_MAX) return false;
        try {
            File d = dir();
            if (!d.isDirectory() && !d.mkdirs()) return false;
            String safe = name.replaceAll("[^A-Za-z0-9._-]", "_");
            if (!safe.endsWith(".png")) safe += ".png";
            File dest = new File(d, safe);
            Files.write(dest.toPath(), png);
            refresh();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    public static boolean isFull() {
        return emotes.size() >= EMOTE_MAX;
    }

    /** Lazily loads the file into a registered texture; null when it fails.
     * Decoding goes through RasterImageDecoder (PNG fast path + ImageIO
     * fallback for jpg/gif). */
    public static ResourceLocation texture(File f) {
        ResourceLocation id = textures.get(f);
        if (id != null) return id;
        try {
            RasterImageDecoder.DecodedImage dec =
                RasterImageDecoder.decode(Files.readAllBytes(f.toPath()));
            if (dec == null) {
                LOGGER.warn("[e33chat] emote decode failed: {}", f.getName());
                return null;
            }
            // NativeImage ownership transfers to the texture; never close it here.
            // Monotonic id: textures.size() reuses ids after removals, which
            // makes register return a stale texture for the new file.
            ResourceLocation tex = ResourceLocation.fromNamespaceAndPath("e33chat",
                "emote_" + (textureSeq++));
            Minecraft.getInstance().getTextureManager().register(tex,
                new DynamicTexture(dec.image()));
            textures.put(f, tex);
            return tex;
        } catch (IOException e) {
            LOGGER.warn("[e33chat] emote read failed: {}", f.getName(), e);
            return null;
        }
    }
}
