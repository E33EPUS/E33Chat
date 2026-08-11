package com.niuqu.chatbubble.image;

import net.minecraft.client.texture.NativeImage;
import net.minecraft.util.Identifier;

/**
 * Loading state of one image URL. Texture upload happens on the render
 * thread; everything else happens on a worker thread.
 */
public final class ImageEntry {
    public enum State { LOADING, LOADED, FAILED }

    private final String url;
    private volatile State state = State.LOADING;
    private volatile Identifier textureId;
    private volatile int width;
    private volatile int height;
    private volatile String failure;

    ImageEntry(String url) {
        this.url = url;
    }

    public String url() { return url; }
    public State state() { return state; }
    public Identifier textureId() { return textureId; }
    public int width() { return width; }
    public int height() { return height; }
    public String failure() { return failure; }

    synchronized void markLoaded(Identifier id, NativeImage img) {
        this.textureId = id;
        this.width = img.getWidth();
        this.height = img.getHeight();
        this.state = State.LOADED;
        ImageLoader.VERSION.incrementAndGet();
    }

    synchronized void markFailed(String reason) {
        this.failure = reason;
        this.state = State.FAILED;
        ImageLoader.VERSION.incrementAndGet();
    }
}
