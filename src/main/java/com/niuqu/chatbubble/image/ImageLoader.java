package com.niuqu.chatbubble.image;

import com.mojang.logging.LogUtils;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.client.renderer.texture.TextureManager;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

/**
 * URL → memory cache → async HTTP fetch → decode → scale → texture upload.
 *
 * Anti-flood guards (a chat message is an attacker-controlled download trigger):
 *  - sliding window: at most RATE_LIMIT_PER_WINDOW new downloads per window;
 *    excess URLs queue (QUEUE_CAP) and are drained by {@link #tick()} when a
 *    slot frees up; beyond the queue cap the entry fails with "rate limited"
 *    (rendered with a distinct label).
 *  - cache cap: finished entries are LRU-evicted past CACHE_CAP, destroying
 *    their GPU textures.
 *  - decode is always scaled down to CARD_W x CARD_H before upload, so a
 *    hostile 16MB image costs ~230KB of GPU memory.
 */
public final class ImageLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    private static final Map<String, ImageEntry> CACHE = new ConcurrentHashMap<>();
    private static final Deque<String> LRU = new ArrayDeque<>();
    private static final Deque<ImageEntry> PENDING = new ArrayDeque<>();

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

    /** Shared HTTP client (also used by ImageUploader). */
    public static HttpClient client() { return CLIENT; }

    /** Shared worker pool (also used by ImageUploader). */
    public static java.util.concurrent.ExecutorService executor() { return EXEC; }

    private static final int MAX_RECEIVE_BYTES = 16 * 1024 * 1024;
    // Direct fetches can be slow (TLS handshake + multi-hundred-KB bodies took
    // ~12s in the user's environment); keep the budget generous so a slow-but-
    // working link lands in LOADED, not FAILED.
    private static final long REQUEST_TIMEOUT_SECONDS = 30;

    // Anti-flood knobs (grilled with the user, 2026-08-12)
    static final int RATE_LIMIT_PER_WINDOW = 4;
    static final long RATE_WINDOW_MS = 10_000;
    static final int QUEUE_CAP = 32;
    static final int CACHE_CAP = 64;
    static final int CARD_W = 320;
    static final int CARD_H = 180;

    private static final long FAILED_RETRY_MS = 10_000;
    private static final Deque<Long> RECENT_STARTS = new ArrayDeque<>();
    private static volatile boolean enabled = true;

    /** Incremented on every state flip; consumers (chat layout) use it to drop caches. */
    public static final java.util.concurrent.atomic.AtomicInteger VERSION = new java.util.concurrent.atomic.AtomicInteger();

    private ImageLoader() {}

    public static int version() { return VERSION.get(); }

    public static void setEnabled(boolean e) {
        enabled = e;
        if (!e) {
            Minecraft.getInstance().execute(() -> {
                TextureManager tm = Minecraft.getInstance().getTextureManager();
                for (String u : CACHE.keySet()) {
                    tm.release(ResourceLocation.fromNamespaceAndPath("e33chat", "img/" + hash(u)));
                }
                CACHE.clear();
                LRU.clear();
                PENDING.clear();
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
        touchLru(url);
        return entry;
    }

    private static void touchLru(String url) {
        synchronized (LRU) {
            LRU.remove(url);
            LRU.addLast(url);
        }
        evictIfNeeded();
    }

    /** Drops the oldest finished entries past CACHE_CAP, destroying textures. */
    private static void evictIfNeeded() {
        synchronized (LRU) {
            if (LRU.size() <= CACHE_CAP) return;
            Iterator<String> it = LRU.iterator();
            while (it.hasNext() && LRU.size() > CACHE_CAP) {
                String url = it.next();
                ImageEntry e = CACHE.get(url);
                // Never evict in-flight entries (their upload callback would leak).
                if (e == null || e.state() == ImageEntry.State.LOADING) continue;
                it.remove();
                CACHE.remove(url, e);
                if (e.state() == ImageEntry.State.LOADED && e.textureId() != null) {
                    ResourceLocation id = e.textureId();
                    Minecraft.getInstance().execute(() -> {
                        Minecraft.getInstance().getTextureManager().release(id);
                    });
                }
                VERSION.incrementAndGet();
            }
        }
    }

    /** Headless-friendly: parse + validate the URL without touching MC. */
    public static boolean isUsableUrl(String url) {
        if (url == null || url.isBlank()) return false;
        String lower = url.toLowerCase();
        if (lower.startsWith("e33chat://")) {
            // Server-hosted media: e33chat://media/<32-hex id>
            return lower.startsWith("e33chat://media/")
                && com.niuqu.chatbubble.server.DiskMediaStore.isValidMediaId(
                    url.substring("e33chat://media/".length()));
        }
        if (!lower.startsWith("http://") && !lower.startsWith("https://")) return false;
        try {
            URI uri = new URI(url);
            return uri.getHost() != null;
        } catch (Exception e) {
            return false;
        }
    }

    /** Pure size math for scaling (unit-testable). */
    public static int[] scaledSize(int w, int h) {
        if (w <= 0 || h <= 0) return new int[]{1, 1};
        if (w <= CARD_W && h <= CARD_H) return new int[]{w, h};
        double scale = Math.min((double) CARD_W / w, (double) CARD_H / h);
        return new int[]{
            Math.max(1, (int) (w * scale)),
            Math.max(1, (int) (h * scale))
        };
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
        if (tryAcquireSlot()) {
            launchFetch(url, entry);
        } else {
            synchronized (PENDING) {
                if (PENDING.size() < QUEUE_CAP) {
                    PENDING.addLast(entry);
                    // entry stays LOADING; tick() drains when a slot frees
                } else {
                    entry.markFailed("rate limited");
                }
            }
        }
    }

    private static boolean tryAcquireSlot() {
        long now = System.currentTimeMillis();
        synchronized (RECENT_STARTS) {
            while (!RECENT_STARTS.isEmpty() && now - RECENT_STARTS.peekFirst() > RATE_WINDOW_MS) {
                RECENT_STARTS.removeFirst();
            }
            if (RECENT_STARTS.size() >= RATE_LIMIT_PER_WINDOW) return false;
            RECENT_STARTS.addLast(now);
            return true;
        }
    }

    /** Called every client tick; starts queued fetches as slots free up. */
    public static void tick() {
        if (!enabled) return;
        while (true) {
            ImageEntry next;
            synchronized (PENDING) {
                if (PENDING.isEmpty()) return;
                next = PENDING.peekFirst();
            }
            if (next.state() != ImageEntry.State.LOADING) {
                synchronized (PENDING) { PENDING.removeFirst(); }
                continue;
            }
            if (!tryAcquireSlot()) return;
            synchronized (PENDING) { PENDING.removeFirst(); }
            launchFetch(next.url(), next);
        }
    }

    private static void launchFetch(String url, ImageEntry entry) {
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
            byte[] body;
            long t1;
            if (url.startsWith("e33chat://")) {
                body = com.niuqu.chatbubble.image.MediaClient.fetch(
                    url.substring("e33chat://media/".length()));
                t1 = System.currentTimeMillis();
                if (body == null) {
                    entry.markFailed("server media fetch failed");
                    LOGGER.info("[e33chat] image fetch {} -> server media fetch failed ({}ms)",
                        url, t1 - t0);
                    return;
                }
            } else {
                HttpResponse<byte[]> resp = CLIENT.send(
                    HttpRequest.newBuilder(URI.create(url)).timeout(Duration.ofSeconds(REQUEST_TIMEOUT_SECONDS)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
                t1 = System.currentTimeMillis();
                if (resp.statusCode() < 200 || resp.statusCode() >= 300) {
                    entry.markFailed("http " + resp.statusCode());
                    LOGGER.info("[e33chat] image fetch {} -> HTTP {} ({}ms)", url, resp.statusCode(), t1 - t0);
                    return;
                }
                body = resp.body();
                if (body == null || body.length == 0 || body.length > MAX_RECEIVE_BYTES) {
                    entry.markFailed("empty or too large");
                    LOGGER.info("[e33chat] image fetch {} -> bad body {} bytes ({}ms)", url, body == null ? 0 : body.length, t1 - t0);
                    return;
                }
            }
            RasterImageDecoder.DecodedImage decoded = RasterImageDecoder.decode(body);
            if (decoded == null) {
                entry.markFailed("unsupported format");
                LOGGER.info("[e33chat] image fetch {} -> decode failed ({} bytes, {}ms)", url, body.length, t1 - t0);
                return;
            }
            // Scale down before upload: a hostile full-size image costs ~230KB
            // of GPU memory instead of up to 9MB+, and renders identical at
            // card size (the original can be re-downloaded if a full-size
            // viewer is ever added).
            int[] sc = scaledSize(decoded.width(), decoded.height());
            if (sc[0] != decoded.width() || sc[1] != decoded.height()) {
                NativeImage scaled = new NativeImage(NativeImage.Format.RGBA, sc[0], sc[1], false);
                try {
                    decoded.image().resizeSubRectTo(0, 0, decoded.width(), decoded.height(), scaled);
                } catch (Throwable t) {
                    scaled.close();
                    decoded.image().close();
                    entry.markFailed("scale: " + t);
                    LOGGER.info("[e33chat] image fetch {} -> scale failed: {}", url, t.toString());
                    return;
                }
                decoded.image().close();
                decoded = new RasterImageDecoder.DecodedImage(scaled, sc[0], sc[1]);
            }
            LOGGER.info("[e33chat] image fetch {} -> {}x{} ({} bytes, {}ms)",
                url, decoded.width(), decoded.height(), body.length, t1 - t0);

            final RasterImageDecoder.DecodedImage uploadImage = decoded;
            // Upload on the render thread, then flip state.
            Minecraft.getInstance().execute(() -> {
                if (entry.state() != ImageEntry.State.LOADING) {
                    uploadImage.image().close();
                    LOGGER.info("[e33chat] image upload SKIPPED (state {}) for {}", entry.state(), url);
                    return;
                }
                // NOTE: getTexture(id) returns the MISSING texture (black/purple)
                // for unregistered ids — never null — so it can't guard registration.
                // Re-register unconditionally (destroy first to avoid leaking the
                // previous NativeImageBackedTexture on cache eviction + reload).
                try {
                    ResourceLocation id = ResourceLocation.fromNamespaceAndPath("e33chat", "img/" + hash(url));
                    TextureManager tm = Minecraft.getInstance().getTextureManager();
                    tm.release(id);
                    tm.register(id, new net.minecraft.client.renderer.texture.DynamicTexture(uploadImage.image()));
                    entry.markLoaded(id, uploadImage.image());
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
    static void clearCacheForTest() {
        CACHE.clear();
        synchronized (LRU) { LRU.clear(); }
        synchronized (PENDING) { PENDING.clear(); }
        synchronized (RECENT_STARTS) { RECENT_STARTS.clear(); }
    }
    static boolean tryAcquireSlotForTest() { return tryAcquireSlot(); }
}
