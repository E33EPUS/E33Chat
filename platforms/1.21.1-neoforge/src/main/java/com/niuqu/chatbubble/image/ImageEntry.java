package com.niuqu.chatbubble.image;

import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;

/**
 * Loading state of one image URL. Texture upload happens on the render
 * thread; everything else happens on a worker thread.
 */
public final class ImageEntry {
    public enum State { LOADING, LOADED, FAILED }

    private final String url;
    private volatile State state = State.LOADING;
    private volatile ResourceLocation textureId;
    private volatile int width;
    private volatile int height;
    private volatile String failure;
    private volatile long failedAtMillis;

    ImageEntry(String url) {
        this.url = url;
    }

    public String url() { return url; }
    public State state() { return state; }
    public ResourceLocation textureId() { return textureId; }
    public int width() { return width; }
    public int height() { return height; }
    public String failure() { return failure; }
    public long failedAtMillis() { return failedAtMillis; }

    synchronized void markLoaded(ResourceLocation id, NativeImage img) {
        this.textureId = id;
        this.width = img.getWidth();
        this.height = img.getHeight();
        this.state = State.LOADED;
        ImageLoader.VERSION.incrementAndGet();
    }

    synchronized void markFailed(String reason) {
        this.failure = reason;
        this.state = State.FAILED;
        this.failedAtMillis = System.currentTimeMillis();
        ImageLoader.VERSION.incrementAndGet();
    }
}
