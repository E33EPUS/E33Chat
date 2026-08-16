package com.niuqu.chatbubble.compat;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;

import javax.swing.SwingUtilities;
import java.awt.FileDialog;
import java.awt.Frame;
import java.io.File;
import java.util.concurrent.atomic.AtomicReference;

/**
 * AWT FileDialog-based image picker, mirroring the modal picker pattern:
 * runs on a worker thread + EDT so the render thread never blocks; MC key/mouse
 * state is released while the dialog owns input and the render thread resumes normally.
 */
public final class NativeFileDialog {
    private static boolean open;

    private NativeFileDialog() {}

    /** Opens a modal image picker; the result (or null on cancel) is delivered on the render thread. */
    public static void pickImage(java.util.function.Consumer<File> callback) {
        if (open) return;
        open = true;
        KeyBinding.unpressAll();
        MinecraftClient mc = MinecraftClient.getInstance();
        // MC keeps thinking the button is held while the dialog grabs input;
        // clear it so release state restores cleanly after the dialog closes
        if (mc.mouse != null) ((com.niuqu.chatbubble.mixin.MouseHandlerAccessor) mc.mouse).e33chat$setActiveButton(0);

        Thread t = new Thread(() -> {
            AtomicReference<File> picked = new AtomicReference<>();
            try {
                SwingUtilities.invokeAndWait(() -> {
                    FileDialog fd = new FileDialog((Frame) null, "Select emote image");
                    fd.setFilenameFilter((dir, name) -> {
                        String n = name.toLowerCase();
                        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg");
                    });
                    fd.setVisible(true);
                    File[] files = fd.getFiles();
                    if (files != null && files.length > 0) picked.set(files[0]);
                    fd.dispose();
                });
            } catch (Exception e) {
                com.mojang.logging.LogUtils.getLogger().warn("[e33chat] File dialog failed", e);
            } finally {
                open = false;
            }
            File result = picked.get();
            mc.execute(() -> callback.accept(result));
        }, "e33chat-file-dialog");
        t.setDaemon(true);
        t.start();
    }
}
