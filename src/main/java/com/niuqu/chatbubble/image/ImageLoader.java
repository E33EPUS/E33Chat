package com.niuqu.chatbubble.image;

import com.mojang.logging.LogUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImage;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.client.texture.TextureManager;
import net.minecraft.util.Identifier;
import org.slf4j.Logger;

/**
 * URL → memory cache → async HTTP fetch → decode (worker thread) → texture
 * upload (render thread). Entries are keyed by the raw URL; failed entries
 * are evicted so the next reference retries.
 */
public final class ImageLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ImageEntry> CACHE = new ConcurrentHashMap<>();
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final int MAX_RECEIVE_BYTES = 16 * 1024 * 1024;
    private static final long REQUEST_TIMEOUT_SECONDS = 20;
    private static volatile boolean enabled = true;

    /** Incremented on every state flip; consumers (chat layout) use it to drop caches. */
    public static final java.util.concurrent.atomic.AtomicInteger VERSION = new java.util.concurrent.atomic.AtomicInteger();

    private ImageLoader() {}

    public static int version() { return VERSION.get(); }

    public static void setEnabled(boolean e) {
        enabled = e;
        if (!e) {
            MinecraftClient.getInstance().execute(() -> {
                TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                for (String u : CACHE.keySet()) {
                    tm.destroyTexture(Identifier.of("e33chat", "img/" + hash(u)));
                }
                CACHE.clear();
            });
        }
    }

    /** Main entry: returns the entry, kicking off the load if unseen. */
    public static ImageEntry getOrLoad(String url) {
        if (!enabled) return null;
        return CACHE.computeIfAbsent(url, ImageLoader::startLoad);
    }

    /** Headless-friendly: parse + validate the URL without touching MC. */
    public static boolean isUsableUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        try {
            URI uri = new URI(url);
            return uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    private static ImageEntry startLoad(String url) {
        ImageEntry entry = new ImageEntry(url);
        if (!isUsableUrl(url)) {
            entry.markFailed("bad url");
            return entry;
        }
        CompletableFuture.runAsync(() -> fetchAndDecode(url, entry))
            .orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(t -> {
                entry.markFailed(String.valueOf(t));
                return null;
            });
        return entry;
    }

    private static void fetchAndDecode(String url, ImageEntry entry) {
        try {
            HttpResponse<byte[]> resp = CLIENT.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                entry.markFailed("http " + resp.statusCode());
                return;
            }
            byte[] body = resp.body();
            if (body == null || body.length == 0 || body.length > MAX_RECEIVE_BYTES) {
                entry.markFailed("empty or too large");
                return;
            }
            RasterImageDecoder.DecodedImage decoded = RasterImageDecoder.decode(body);
            if (decoded == null) {
                entry.markFailed("unsupported format");
                return;
            }
            // Upload on the render thread, then flip state.
            MinecraftClient.getInstance().execute(() -> {
                if (entry.state() != ImageEntry.State.LOADING) {
                    decoded.image().close();
                    return;
                }
                // NOTE: getTexture(id) returns the MISSING texture (black/purple)
                // for unregistered ids — never null — so it can't guard registration.
                // Re-register unconditionally (destroy first to avoid leaking the
                // previous NativeImageBackedTexture on cache eviction + reload).
                Identifier id = Identifier.of("e33chat", "img/" + hash(url));
                TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                tm.destroyTexture(id);
                tm.registerTexture(id, new NativeImageBackedTexture(decoded.image()));
                entry.markLoaded(id, decoded.image());
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            entry.markFailed("interrupted");
        } catch (Throwable t) {
            LOGGER.debug("[e33chat] image fetch failed for {}: {}", url, t.toString());
            entry.markFailed(String.valueOf(t));
        }
    }

    private static String hash(String url) {
        return Integer.toHexString(url.hashCode());
    }

    /** Package-private hooks for unit tests. */
    static Map<String, ImageEntry> cache() { return CACHE; }
    static void clearCacheForTest() { CACHE.clear(); }
}
