package com.niuqu.chatbubble.image;

import net.minecraft.client.texture.NativeImage;
import com.mojang.logging.LogUtils;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.texture.NativeImageBackedTexture;
import net.minecraft.util.Identifier;
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
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
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
    private static final Map<String, Entry> CACHE = new ConcurrentHashMap<>();
    private static final int MAX_FRAMES = 48;
    private static final int MAX_DIMENSION = 512;
    private static final long MAX_BYTES = 8L * 1024 * 1024;
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
        for (Entry entry : CACHE.values()) entry.advance(now);
    }

    private static Entry cachedOrStart(String url) {
        Entry current = CACHE.get(url);
        if (current != null && current.failed
                && System.currentTimeMillis() - current.failedAt > FAILED_RETRY_MS) {
            Entry retry = new Entry(url);
            if (CACHE.replace(url, current, retry)) {
                EXEC.execute(() -> load(retry));
                return retry;
            }
        }
        return CACHE.computeIfAbsent(url, AnimatedImageLoader::start);
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
            MinecraftClient.getInstance().execute(() -> {
                try {
                    Identifier[] ids = new Identifier[decoded.frames().size()];
                    for (int i = 0; i < decoded.frames().size(); i++) {
                        Identifier id = Identifier.of("e33chat",
                        "anim/" + Integer.toHexString(entry.url.hashCode()) + "_" + i);
                        MinecraftClient.getInstance().getTextureManager().registerTexture(
                            id, new NativeImageBackedTexture(decoded.frames().get(i)));
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
                ArrayList<NativeImage> frames = new ArrayList<>();
                int[] delays = new int[count];
                int width = 0, height = 0;
                for (int i = 0; i < count; i++) {
                    var frame = reader.read(i);
                    if (frame == null || frame.getWidth() <= 0 || frame.getHeight() <= 0
                        || frame.getWidth() > MAX_DIMENSION || frame.getHeight() > MAX_DIMENSION) break;
                    width = frame.getWidth();
                    height = frame.getHeight();
                    frames.add(RasterImageDecoder.fromBufferedImage(frame));
                    delays[i] = frameDelay(reader.getImageMetadata(i));
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
        private volatile Identifier[] frames;
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

        public int width() {
            return width;
        }

        public int height() {
            return height;
        }

        /** Current frame texture; wall-clock fallback keeps GIFs alive without ticks. */
        public Identifier texture() {
            Identifier[] current = frames;
            if (current == null || current.length == 0) return null;
            advance(System.currentTimeMillis());
            return current[Math.max(0, Math.min(frameIndex, current.length - 1))];
        }

        private synchronized void advance(long now) {
            Identifier[] current = frames;
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
    public record FrameTex(Identifier texture, int width, int height) {}

    private record FrameInfo(int left, int top, int width, int height, int delay, int disposal) {}
}
