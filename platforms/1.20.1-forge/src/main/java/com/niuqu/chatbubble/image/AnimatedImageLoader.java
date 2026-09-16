package com.niuqu.chatbubble.image;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.resources.ResourceLocation;
import org.slf4j.Logger;

import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.metadata.IIOMetadata;
import javax.imageio.metadata.IIOMetadataNode;
import javax.imageio.stream.ImageInputStream;
import java.awt.AlphaComposite;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Animated GIF/WebP/APNG loader for chat images and emotes (2.4.10).
 *
 * Ported from the CoreChat fork's AnimatedEmoteLoader (shared understanding of
 * ImageIO pitfalls: seek-forward readers, GIF frame deltas composed on the
 * logical canvas, disposal methods) with E33Chat transports: plain HTTP via the
 * shared client and server-hosted e33chat://media via MediaClient — no
 * external plugin involved.
 *
 * One GPU texture per decoded frame: Forge/Mohist texture caches reliably
 * switch between ResourceLocations, while re-uploading pixels into one
 * DynamicTexture can stick on frame zero. Frame advance is wall-clock driven
 * (tick + render-side fallback) so GIFs keep moving when screens change.
 */
public final class AnimatedImageLoader {
    private static final Logger LOGGER = LogUtils.getLogger();
    /**
     * Decoded animations are the heaviest objects in the mod: one GPU texture
     * per frame, up to the 8M-pixel budget each. The cache is therefore
     * access-ordered LRU with a hard ceiling on entry count and total decoded
     * frames; eviction releases the textures (ImageLoader's 64-entry still
     * cache was the precedent). MAX_TOTAL_FRAMES must stay >= MAX_FRAMES so a
     * single full-length animation always fits.
     */
    private static final int MAX_ENTRIES = 24;
    private static final int MAX_TOTAL_FRAMES = 192;
    private static final Map<String, Entry> CACHE =
        Collections.synchronizedMap(new LinkedHashMap<>(16, 0.75f, true));
    private static final int MAX_FRAMES = 120;
    private static final int MAX_DIMENSION = 512;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
    /**
     * Decoded-pixel budget for one animation (~8M px, about 32 MB of GPU memory
     * at 4 bytes per pixel). Animations longer than the budget keep their first
     * frames instead of being rejected: a truncated GIF still plays and still
     * says what it is, while a rejection is a dead end the user cannot act on.
     * This is what keeps 120 frames affordable — at 512px square, 120 frames
     * would otherwise be ~125 MB. Same numbers AtomChat uses.
     */
    private static final long MAX_PIXELS_PER_IMAGE = 8L * 1024L * 1024L;
    private static final long FAILED_RETRY_MS = 60_000;

    // Separate small pool: a GIF must never occupy ImageLoader's static-image
    // workers, or one animated download can starve regular images (fork lesson).
    private static final ExecutorService EXEC =
        Executors.newFixedThreadPool(2, r -> {
            Thread t = new Thread(r, "e33chat-animated");
            t.setDaemon(true);
            return t;
        });

    private AnimatedImageLoader() {}

    /** Structural facts about an animated file, read without decoding pixels. */
    public record Probe(int frames, int width, int height, String format) {}

    /** Why an animated source cannot be rendered by the receiver as-is. */
    public enum OverBudget { TOO_MANY_FRAMES, TOO_LARGE_DIMENSION, TOO_LARGE_BYTES }

    /**
     * Reader-level probe: frame count + canvas size, no pixel decode. Null when
     * the bytes are not a readable multi-frame image, i.e. whenever the static
     * path should handle the file instead.
     *
     * <p>This is what lets the upload side tell "animated, send as-is" from
     * "still image, re-encode"; decoding with {@code ImageIO.read} returns only
     * the first frame, which is how every outgoing GIF used to lose its
     * animation before it ever left the client.
     */
    public static Probe probe(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(input, false, false);
                int count = reader.getNumImages(true);
                if (count < 2) return null;
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                // A GIF's logical screen can be larger than the first frame's
                // own bounds; the canvas is what actually gets rendered.
                if ("gif".equalsIgnoreCase(reader.getFormatName())) {
                    IIOMetadata stream = reader.getStreamMetadata();
                    if (stream != null) {
                        try {
                            var root = stream.getAsTree("javax_imageio_gif_stream_1.0");
                            if (root instanceof IIOMetadataNode node) {
                                var d = node.getElementsByTagName("LogicalScreenDescriptor");
                                if (d.getLength() > 0 && d.item(0) instanceof IIOMetadataNode n) {
                                    w = Math.max(w, parseInt(n.getAttribute("logicalScreenWidth"), w));
                                    h = Math.max(h, parseInt(n.getAttribute("logicalScreenHeight"), h));
                                }
                            }
                        } catch (Throwable ignored) {}
                    }
                }
                return new Probe(count, w, h, reader.getFormatName().toLowerCase(java.util.Locale.ROOT));
            } finally {
                reader.dispose();
            }
        } catch (Throwable t) {
            return null;
        }
    }

    /**
     * Null when the receiver can render this source; otherwise the first limit it
     * breaks. The budget is deliberately the receiver's, not a new one: sending
     * something bigger would be accepted by the upload and then silently fall
     * back to a still frame on arrival — the failure this whole path exists to
     * prevent.
     */
    public static OverBudget checkBudget(Probe probe, long byteCount) {
        if (probe == null) return null;
        if (probe.frames() > MAX_FRAMES) return OverBudget.TOO_MANY_FRAMES;
        if (probe.width() > MAX_DIMENSION || probe.height() > MAX_DIMENSION) {
            return OverBudget.TOO_LARGE_DIMENSION;
        }
        if (byteCount > MAX_BYTES) return OverBudget.TOO_LARGE_BYTES;
        return null;
    }

    /** MIME type for a probed format name, so passthrough uploads keep theirs. */
    public static String mimeType(String format) {
        if (format == null) return "application/octet-stream";
        return switch (format.toLowerCase(java.util.Locale.ROOT)) {
            case "gif" -> "image/gif";
            case "png" -> "image/png";   // APNG reports as png
            case "jpeg", "jpg" -> "image/jpeg";
            case "webp" -> "image/webp";
            default -> "application/octet-stream";
        };
    }

    /** URL looks animated by extension / query hint — the cheap path. */
    public static boolean looksAnimated(String url, String nameHint) {
        String lower = (url + " " + (nameHint == null ? "" : nameHint))
            .toLowerCase(java.util.Locale.ROOT);
        boolean formatHint = lower.contains("format=gif") || lower.contains("format=webp");
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".gif") || lower.endsWith(".webp")
            || lower.endsWith(".apng") || formatHint;
    }

    /**
     * The URL/name says a format that is never animated, so a content probe can
     * only come back negative. The probe costs a download against the server's
     * per-player quota, and paying that for every ordinary JPEG is what made the
     * sender's own images the first to fail.
     *
     * <p>{@code .png} is deliberately excluded: APNG is animated and shares the
     * extension, so a PNG still has to be probed.
     */
    public static boolean definitivelyStill(String url, String nameHint) {
        String lower = (url + " " + (nameHint == null ? "" : nameHint))
            .toLowerCase(java.util.Locale.ROOT);
        int query = lower.indexOf('?');
        if (query >= 0) lower = lower.substring(0, query);
        return lower.endsWith(".jpg") || lower.endsWith(".jpeg") || lower.endsWith(".bmp");
    }

    /** Extension-gated entry; null when the URL gives no animation hint. */
    public static Entry getOrLoad(String url, String nameHint) {
        if (url == null || url.isBlank() || !looksAnimated(url, nameHint)) return null;
        return cachedOrStart(url);
    }

    /** Content-probe path for extension-less transports (e33chat://media). */
    public static Entry getOrLoadAny(String url, String nameHint) {
        if (url == null || url.isBlank()) return null;
        return cachedOrStart(url);
    }

    /** Local file (custom emotes); the filename extension gates the probe. */
    public static Entry getOrLoadFile(java.io.File file) {
        if (file == null || !file.isFile()) return null;
        return getOrLoad(file.toURI().toString(), file.getName());
    }

    /** Client tick: advance all live entries. */
    public static void tick() {
        long now = System.currentTimeMillis();
        synchronized (CACHE) {
            for (Entry entry : CACHE.values()) entry.advance(now);
        }
    }

    /** Drop every cached animation and release its textures (disconnect / world
     *  switch). Entries still decoding are marked retired first: otherwise their
     *  load task would register a full frame set after the map is cleared and
     *  nothing would ever release it. */
    public static void resetAll() {
        synchronized (CACHE) {
            for (Entry entry : CACHE.values()) {
                entry.retired = true;
                releaseTextures(entry);
            }
            CACHE.clear();
        }
    }

    private static Entry cachedOrStart(String url) {
        Entry current = CACHE.get(url);
        if (current != null && current.failed
                && System.currentTimeMillis() - current.failedAt > FAILED_RETRY_MS) {
            Entry retry = new Entry(url);
            if (CACHE.replace(url, current, retry)) {
                EXEC.execute(() -> load(retry));
                evictOverBudget(retry);
                return retry;
            }
        }
        Entry entry = CACHE.computeIfAbsent(url, AnimatedImageLoader::start);
        evictOverBudget(entry);
        return entry;
    }

    /** Evict least-recently-used entries while over the size/frame budget.
     *  Still-loading entries are kept (evicting them would orphan the
     *  in-flight download); failed and static-image markers are cheap and
     *  evictable at any time. Must run while holding CACHE's monitor (the
     *  access-ordered map is not weakly consistent). */
    private static void evictOverBudget(Entry keep) {
        synchronized (CACHE) {
            int totalFrames = 0;
            for (Entry entry : CACHE.values()) totalFrames += entry.frameCount();
            if (CACHE.size() <= MAX_ENTRIES && totalFrames <= MAX_TOTAL_FRAMES) return;
            Iterator<Entry> it = CACHE.values().iterator();
            while (it.hasNext()
                    && (CACHE.size() > MAX_ENTRIES || totalFrames > MAX_TOTAL_FRAMES)) {
                Entry entry = it.next();
                if (entry == keep) continue;
                if (!entry.ready && !entry.failed && !entry.staticImage) continue;
                it.remove();
                totalFrames -= entry.frameCount();
                releaseTextures(entry);
            }
        }
    }

    /** Free one entry's per-frame GPU textures; safe from any thread (the
     *  release itself is marshalled to the render thread). */
    private static void releaseTextures(Entry entry) {
        ResourceLocation[] ids = entry.frames;
        entry.frames = null;
        entry.ready = false;
        if (ids == null || ids.length == 0) return;
        Minecraft.getInstance().execute(() -> {
            var textureManager = Minecraft.getInstance().getTextureManager();
            for (ResourceLocation id : ids) textureManager.release(id);
        });
    }

    private static Entry start(String url) {
        Entry entry = new Entry(url);
        EXEC.execute(() -> load(entry));
        return entry;
    }

    private static void load(Entry entry) {
        try {
            byte[] bytes;
            if (entry.url.startsWith("file:")) {
                bytes = java.nio.file.Files.readAllBytes(
                    java.nio.file.Path.of(URI.create(entry.url)));
            } else if (entry.url.startsWith("e33chat://media/")) {
                bytes = MediaClient.fetch(entry.url.substring("e33chat://media/".length()));
                if (bytes == null) {
                    entry.markFailed();
                    return;
                }
            } else {
                // Raw non-ASCII (Chinese filenames) must go through the ASCII
                // URI form or HttpClient can fail before any network I/O.
                URI requestUri = URI.create(URI.create(entry.url).toASCIIString());
                HttpResponse<byte[]> response = ImageLoader.client().send(
                    HttpRequest.newBuilder(requestUri)
                        .timeout(Duration.ofSeconds(30)).build(),
                    HttpResponse.BodyHandlers.ofByteArray());
                bytes = response.statusCode() >= 200 && response.statusCode() < 300
                    ? response.body() : null;
                if (bytes == null) {
                    entry.markFailed();
                    LOGGER.info("[e33chat] animated image fetch failed: {} (HTTP {})",
                        entry.url, response.statusCode());
                    return;
                }
            }
            if (bytes.length == 0 || bytes.length > MAX_BYTES) {
                entry.markFailed();
                LOGGER.info("[e33chat] animated image rejected: {} ({} bytes)",
                    entry.url, bytes.length);
                return;
            }
            entry.sizeBytes = bytes.length;
            Decoded decoded = decode(bytes);
            if (decoded == null || decoded.frames().size() < 2) {
                // Real single-frame file: stop retrying, let the static
                // ImageLoader take over permanently.
                entry.staticImage = true;
                return;
            }
            Minecraft.getInstance().execute(() -> {
                if (entry.retired) {
                    // The cache was cleared while this decode was in flight;
                    // registering now would leak the whole frame set.
                    closeFrames(decoded.frames());
                    return;
                }
                try {
                    ResourceLocation[] ids = new ResourceLocation[decoded.frames().size()];
                    for (int i = 0; i < decoded.frames().size(); i++) {
                        ResourceLocation id = ResourceLocation.fromNamespaceAndPath("e33chat",
                            "anim/" + Integer.toHexString(entry.url.hashCode()) + "_" + i);
                        Minecraft.getInstance().getTextureManager().register(
                            id, new DynamicTexture(decoded.frames().get(i)));
                        ids[i] = id;
                    }
                    entry.frames = ids;
                    entry.delays = decoded.delays();
                    entry.width = decoded.width();
                    entry.height = decoded.height();
                    entry.ready = true;
                    entry.frameStart = System.currentTimeMillis();
                    ImageLoader.VERSION.incrementAndGet();
                } catch (Throwable t) {
                    entry.markFailed();
                    LOGGER.debug("[e33chat] animated image texture upload failed: {}", t.toString());
                }
            });
        } catch (Throwable t) {
            entry.markFailed();
            LOGGER.info("[e33chat] animated image load failed: {} -> {}", entry.url, t.toString());
        }
    }

    /** How many frames of this size fit the decoded-pixel budget (at least one). */
    private static int framesWithinBudget(int w, int h) {
        long per = (long) Math.max(1, w) * Math.max(1, h);
        return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, MAX_PIXELS_PER_IMAGE / per));
    }

    private static Decoded decode(byte[] bytes) {
        try (ImageInputStream input = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
            if (input == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(input);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                // seek-forward-only readers leave the stream at the end after
                // getNumImages(true), breaking later metadata reads — reseat.
                reader.setInput(input, false, false);
                int count = Math.min(MAX_FRAMES, reader.getNumImages(true));
                if (count < 2) return null;
                if ("gif".equalsIgnoreCase(reader.getFormatName())) {
                    return decodeGif(reader, count);
                }
                // Trim to the pixel budget before decoding, so an oversized
                // animation costs neither the CPU nor the GPU memory.
                int budget = framesWithinBudget(reader.getWidth(0), reader.getHeight(0));
                if (budget < count) {
                    LOGGER.info("[e33chat] animated image trimmed to {} of {} frames (pixel budget)",
                        budget, count);
                    count = budget;
                }
                ArrayList<NativeImage> frames = new ArrayList<>();
                int[] delays = new int[count];
                int width = 0, height = 0;
                try {
                    for (int i = 0; i < count; i++) {
                        var frame = reader.read(i);
                        if (frame == null || frame.getWidth() <= 0 || frame.getHeight() <= 0
                            || frame.getWidth() > MAX_DIMENSION || frame.getHeight() > MAX_DIMENSION) break;
                        width = frame.getWidth();
                        height = frame.getHeight();
                        frames.add(RasterImageDecoder.fromBufferedImage(frame));
                        delays[i] = frameDelay(reader.getImageMetadata(i));
                    }
                } catch (Throwable t) {
                    // Mirror the GIF path: a mid-stream failure must not leak
                    // the frames decoded so far.
                    closeFrames(frames);
                    throw t;
                }
                if (frames.size() < 2) {
                    for (NativeImage image : frames) image.close();
                    return null;
                }
                for (int i = 0; i < frames.size(); i++) if (delays[i] <= 0) delays[i] = 100;
                if (frames.size() != delays.length) delays = java.util.Arrays.copyOf(delays, frames.size());
                return new Decoded(frames, delays, width, height);
            } finally {
                reader.dispose();
            }
        } catch (Throwable t) {
            LOGGER.info("[e33chat] animated image decode failed ({}): {}", formatHint(bytes), t.toString());
            return null;
        }
    }

    private static String formatHint(byte[] bytes) {
        if (bytes == null || bytes.length < 6) return "unknown";
        if (bytes[0] == 'G' && bytes[1] == 'I' && bytes[2] == 'F') return "gif";
        if ((bytes[0] & 0xFF) == 0x52 && (bytes[1] & 0xFF) == 0x49
            && (bytes[2] & 0xFF) == 0x46 && (bytes[3] & 0xFF) == 0x46) return "webp";
        return "animated-image";
    }

    /** GIF frames are commonly cropped deltas; compose them on the logical canvas. */
    private static Decoded decodeGif(ImageReader reader, int count) throws Exception {
        int canvasW = Math.max(1, reader.getWidth(0));
        int canvasH = Math.max(1, reader.getHeight(0));
        IIOMetadata stream = reader.getStreamMetadata();
        if (stream != null) {
            try {
                var root = stream.getAsTree("javax_imageio_gif_stream_1.0");
                if (root instanceof IIOMetadataNode node) {
                    var descriptors = node.getElementsByTagName("LogicalScreenDescriptor");
                    if (descriptors.getLength() > 0 && descriptors.item(0) instanceof IIOMetadataNode d) {
                        canvasW = parseInt(d.getAttribute("logicalScreenWidth"), canvasW);
                        canvasH = parseInt(d.getAttribute("logicalScreenHeight"), canvasH);
                    }
                }
            } catch (Throwable ignored) {}
        }
        if (canvasW > MAX_DIMENSION || canvasH > MAX_DIMENSION) return null;
        // Same pixel budget as the generic path: keep the first frames rather
        // than refusing the whole animation.
        int budget = framesWithinBudget(canvasW, canvasH);
        if (budget < count) {
            LOGGER.info("[e33chat] animated image trimmed to {} of {} frames (pixel budget)",
                budget, count);
            count = budget;
        }
        BufferedImage canvas = new BufferedImage(canvasW, canvasH, BufferedImage.TYPE_INT_ARGB);
        ArrayList<NativeImage> frames = new ArrayList<>();
        int[] delays = new int[count];
        FrameInfo previous = null;
        BufferedImage restore = null;
        try {
            for (int i = 0; i < count; i++) {
                if (previous != null) {
                    if (previous.disposal() == 2) {
                        clear(canvas, previous.left(), previous.top(), previous.width(), previous.height());
                    } else if (previous.disposal() == 3 && restore != null) {
                        copyInto(restore, canvas);
                    }
                }
                IIOMetadata metadata = reader.getImageMetadata(i);
                FrameInfo info = frameInfo(reader, metadata, i);
                BufferedImage frame = reader.read(i);
                if (frame == null) break;
                BufferedImage before = info.disposal() == 3 ? copy(canvas) : null;
                Graphics2D graphics = canvas.createGraphics();
                try {
                    graphics.setComposite(AlphaComposite.SrcOver);
                    // Some encoders emit full-canvas frames with a crop-sized
                    // descriptor — trust the raster size over the descriptor.
                    boolean logical = frame.getWidth() == canvasW && frame.getHeight() == canvasH
                        && (info.width() != canvasW || info.height() != canvasH);
                    graphics.drawImage(frame, logical ? 0 : info.left(), logical ? 0 : info.top(), null);
                } finally {
                    graphics.dispose();
                }
                frames.add(RasterImageDecoder.fromBufferedImage(canvas));
                delays[i] = info.delay();
                previous = info;
                restore = before;
            }
            if (frames.size() < 2) {
                closeFrames(frames);
                return null;
            }
            for (int i = 0; i < frames.size(); i++) if (delays[i] <= 0) delays[i] = 100;
            return new Decoded(frames, java.util.Arrays.copyOf(delays, frames.size()), canvasW, canvasH);
        } catch (Throwable t) {
            closeFrames(frames);
            throw t;
        }
    }

    private static FrameInfo frameInfo(ImageReader reader, IIOMetadata metadata, int index) throws Exception {
        int left = 0, top = 0, width = reader.getWidth(index), height = reader.getHeight(index), delay = 100, disposal = 0;
        if (metadata != null) {
            var root = metadata.getAsTree("javax_imageio_gif_image_1.0");
            if (root instanceof IIOMetadataNode node) {
                var descriptors = node.getElementsByTagName("ImageDescriptor");
                if (descriptors.getLength() > 0 && descriptors.item(0) instanceof IIOMetadataNode d) {
                    left = parseInt(d.getAttribute("imageLeftPosition"), 0);
                    top = parseInt(d.getAttribute("imageTopPosition"), 0);
                    width = parseInt(d.getAttribute("imageWidth"), width);
                    height = parseInt(d.getAttribute("imageHeight"), height);
                }
                var controls = node.getElementsByTagName("GraphicControlExtension");
                if (controls.getLength() > 0 && controls.item(0) instanceof IIOMetadataNode c) {
                    delay = Math.max(40, parseInt(c.getAttribute("delayTime"), 10) * 10);
                    String method = c.getAttribute("disposalMethod");
                    disposal = "restoreToBackgroundColor".equals(method) ? 2
                        : "restoreToPrevious".equals(method) ? 3 : 0;
                }
            }
        }
        return new FrameInfo(left, top, Math.max(1, width), Math.max(1, height), delay, disposal);
    }

    private static BufferedImage copy(BufferedImage source) {
        BufferedImage target = new BufferedImage(source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_ARGB);
        copyInto(source, target);
        return target;
    }

    private static void copyInto(BufferedImage source, BufferedImage target) {
        Graphics2D graphics = target.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Src);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
    }

    private static void clear(BufferedImage image, int x, int y, int width, int height) {
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setComposite(AlphaComposite.Clear);
            graphics.fillRect(x, y, width, height);
        } finally {
            graphics.dispose();
        }
    }

    private static int parseInt(String value, int fallback) {
        try {
            return value == null || value.isBlank() ? fallback : Integer.parseInt(value);
        } catch (NumberFormatException ignored) {
            return fallback;
        }
    }

    private static void closeFrames(ArrayList<NativeImage> frames) {
        for (NativeImage image : frames) {
            try { image.close(); } catch (Throwable ignored) {}
        }
        frames.clear();
    }

    private static int frameDelay(IIOMetadata metadata) {
        if (metadata == null) return 100;
        try {
            for (String format : metadata.getMetadataFormatNames()) {
                var root = metadata.getAsTree(format);
                if (!(root instanceof IIOMetadataNode node)) continue;
                var controls = node.getElementsByTagName("GraphicControlExtension");
                if (controls.getLength() > 0 && controls.item(0) instanceof IIOMetadataNode control) {
                    String value = control.getAttribute("delayTime");
                    if (!value.isBlank()) return Math.max(40, Integer.parseInt(value) * 10);
                }
            }
        } catch (Throwable ignored) {}
        return 100;
    }

    public static final class Entry {
        private final String url;
        private volatile ResourceLocation[] frames;
        private volatile int[] delays;
        private volatile int width;
        private volatile int height;
        private volatile boolean ready;
        private volatile boolean failed;
        private volatile long failedAt;
        private volatile boolean staticImage;
        private volatile long sizeBytes;
        private volatile long frameStart;
        private volatile int frameIndex;
        /** Set when the cache dropped this entry; a late load must not register. */
        private volatile boolean retired;

        private Entry(String url) {
            this.url = url;
        }

        public boolean ready() {
            return ready && frames != null && frames.length > 1;
        }

        public boolean failed() {
            return failed;
        }

        public boolean staticImage() {
            return staticImage;
        }

        public long sizeBytes() {
            return sizeBytes;
        }

        private int frameCount() {
            ResourceLocation[] current = frames;
            return current == null ? 0 : current.length;
        }

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /** Current frame texture; wall-clock fallback keeps GIFs alive without ticks. */
        public ResourceLocation texture() {
            ResourceLocation[] current = frames;
            if (current == null || current.length == 0) return null;
            advance(System.currentTimeMillis());
            return current[Math.max(0, Math.min(frameIndex, current.length - 1))];
        }

        private synchronized void advance(long now) {
            ResourceLocation[] current = frames;
            int[] currentDelays = delays;
            if (!ready || current == null || current.length == 0
                || currentDelays == null || currentDelays.length != current.length) return;
            long elapsed = Math.max(0L, now - frameStart);
            while (elapsed >= Math.max(1, currentDelays[frameIndex])) {
                elapsed -= Math.max(1, currentDelays[frameIndex]);
                frameIndex = (frameIndex + 1) % current.length;
                frameStart = now - elapsed;
            }
        }

        private void markFailed() {
            failed = true;
            failedAt = System.currentTimeMillis();
        }
    }

    private record Decoded(ArrayList<NativeImage> frames, int[] delays, int width, int height) {}

    /** Draw snapshot: current frame texture + logical canvas size. */
    public record FrameTex(ResourceLocation texture, int width, int height) {}

    private record FrameInfo(int left, int top, int width, int height, int delay, int disposal) {}
}
