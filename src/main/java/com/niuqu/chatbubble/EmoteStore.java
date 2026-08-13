package com.niuqu.chatbubble;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Local emote pack: static images in &lt;runDir&gt;/e33chat/emotes/ (png/jpg).
 * Clicking one sends it through the normal image upload path.
 */
public final class EmoteStore {
    public static final int EMOTE_MAX = 10;
    private static final List<File> emotes = new ArrayList<>();
    private static final Map<File, Identifier> textures = new HashMap<>();
    private static boolean scanned;

    private EmoteStore() {}

    public static File dir() {
        return new File(MinecraftClient.getInstance().runDirectory, "e33chat/emotes");
    }

    private static boolean isImage(File f) {
        String n = f.getName().toLowerCase();
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
    }

    /** Re-scans the emote dir. Called when the panel opens and after edits. */
    public static void refresh() {
        scanned = true;
        emotes.clear();
        File d = dir();
        File[] files = d.listFiles();
        if (files != null) {
            for (File f : files) {
                if (isImage(f) && emotes.size() < EMOTE_MAX) emotes.add(f);
            }
            emotes.sort((a, b) -> a.getName().compareToIgnoreCase(b.getName()));
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

    public static boolean isFull() {
        return emotes.size() >= EMOTE_MAX;
    }

    /** Lazily loads the file into a registered texture; null when it fails. */
    public static Identifier texture(File f) {
        Identifier id = textures.get(f);
        if (id != null) return id;
        try (NativeImage img = NativeImage.read(Files.newInputStream(f.toPath()))) {
            Identifier tex = MinecraftClient.getInstance().getTextureManager()
                .registerDynamicTexture("e33chat_emote_" + textures.size(),
                    new NativeImageBackedTexture(img));
            textures.put(f, tex);
            return tex;
        } catch (IOException e) {
            return null;
        }
    }
}
