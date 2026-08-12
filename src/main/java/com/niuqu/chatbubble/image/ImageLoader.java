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
    // Dedicated daemon pool: ForkJoinPool.commonPool is shared with the whole
    // mod ecosystem and frequently starved/blocked by other mods, which left
    // fetches stuck in LOADING forever. Two threads is plenty for chat images.
    private static final java.util.concurrent.ExecutorService EXEC =
        java.util.concurrent.Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "e33chat-image");
            t.setDaemon(true);
            return t;
        });
    // HTTP/1.1: the JDK's default HTTP/2 path is much slower against some
    // servers (measured 11.7s vs curl's 3.6s on the same image); plain HTTP/1.1
    // matches curl behaviour.
    private static final HttpClient CLIENT = HttpClient.newBuilder()
        .version(HttpClient.Version.HTTP_1_1)
        .connectTimeout(Duration.ofSeconds(8))
        .followRedirects(HttpClient.Redirect.NORMAL)
        .build();

    private static final int MAX_RECEIVE_BYTES = 16 * 1024 * 1024;
    // Direct fetches can be slow (TLS handshake + multi-hundred-KB bodies took
    // ~12s in the user's environment); keep the budget generous so a slow-but-
    // working link lands in LOADED, not FAILED.
    private static final long REQUEST_TIMEOUT_SECONDS = 30;
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
        ImageEntry entry = CACHE.get(url);
        if (entry == null) {
            entry = CACHE.computeIfAbsent(url, ImageLoader::startLoad);
        } else if (entry.state() == ImageEntry.State.FAILED
                && System.currentTimeMillis() - entry.failedAtMillis() > FAILED_RETRY_MS) {
            // Transient failures (DNS hiccup, slow server) should not poison the
            // cache forever — retry after a quiet period. Replacing the entry
            // atomically keeps concurrent renders from seeing a half-built one.
            ImageEntry fresh = new ImageEntry(url);
            if (CACHE.replace(url, entry, fresh)) {
                startLoadInto(url, fresh);
                entry = fresh;
            }
        }
        return entry;
    }

    private static final long FAILED_RETRY_MS = 10_000;

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
        startLoadInto(url, entry);
        return entry;
    }

    private static void startLoadInto(String url, ImageEntry entry) {
        if (!isUsableUrl(url)) {
            entry.markFailed("bad url");
            return;
        }
        CompletableFuture.runAsync(() -> fetchAndDecode(url, entry), EXEC)
            .orTimeout(REQUEST_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .exceptionally(t -> {
                entry.markFailed(String.valueOf(t));
                LOGGER.info("[e33chat] image fetch {} -> timeout after {}s", url, REQUEST_TIMEOUT_SECONDS);
                return null;
            });
    }

    private static void fetchAndDecode(String url, ImageEntry entry) {
        long t0 = System.currentTimeMillis();
        try {
            HttpResponse<byte[]> resp = CLIENT.send(
                HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build(),
                HttpResponse.BodyHandlers.ofByteArray());
            long t1 = System.currentTimeMillis();
            if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                entry.markFailed("http " + resp.statusCode());
                LOGGER.info("[e33chat] image fetch {} -> HTTP {} ({}ms)", url, resp.statusCode(), t1 - t0);
                return;
            }
            byte[] body = resp.body();
            if (body == null || body.length == 0 || body.length > MAX_RECEIVE_BYTES) {
                entry.markFailed("empty or too large");
                LOGGER.info("[e33chat] image fetch {} -> bad body {} bytes ({}ms)", url, body == null ? 0 : body.length, t1 - t0);
                return;
            }
            RasterImageDecoder.DecodedImage decoded = RasterImageDecoder.decode(body);
            if (decoded == null) {
                entry.markFailed("unsupported format");
                LOGGER.info("[e33chat] image fetch {} -> decode failed ({} bytes, {}ms)", url, body.length, t1 - t0);
                return;
            }
            LOGGER.info("[e33chat] image fetch {} -> {}x{} ({} bytes, {}ms)",
                url, decoded.width(), decoded.height(), body.length, t1 - t0);
            // Upload on the render thread, then flip state.
            MinecraftClient.getInstance().execute(() -> {
                if (entry.state() != ImageEntry.State.LOADING) {
                    decoded.image().close();
                    LOGGER.info("[e33chat] image upload SKIPPED (state {}) for {}", entry.state(), url);
                    return;
                }
                // NOTE: getTexture(id) returns the MISSING texture (black/purple)
                // for unregistered ids — never null — so it can't guard registration.
                // Re-register unconditionally (destroy first to avoid leaking the
                // previous NativeImageBackedTexture on cache eviction + reload).
                try {
                    Identifier id = Identifier.of("e33chat", "img/" + hash(url));
                    TextureManager tm = MinecraftClient.getInstance().getTextureManager();
                    tm.destroyTexture(id);
                    tm.registerTexture(id, new NativeImageBackedTexture(decoded.image()));
                    entry.markLoaded(id, decoded.image());
                    LOGGER.info("[e33chat] image upload OK {} -> {}x{} @ {}", url, entry.width(), entry.height(), id);
                } catch (Throwable t) {
                    entry.markFailed("upload: " + t);
                    LOGGER.info("[e33chat] image upload FAILED {}: {}", url, t.toString());
                }
            });
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            entry.markFailed("interrupted");
        } catch (Throwable t) {
            long t1 = System.currentTimeMillis();
            LOGGER.info("[e33chat] image fetch {} -> exception ({}ms): {}", url, t1 - t0, t.toString());
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
