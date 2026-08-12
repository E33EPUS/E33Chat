package com.niuqu.chatbubble.image;

import com.mojang.logging.LogUtils;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.DataFlavor;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import org.slf4j.Logger;

/**
 * Turns a local file or a clipboard image into uploadable bytes (scaled to
 * MAX_EDGE, re-encoded). Pure-image logic, headless-safe except the AWT
 * clipboard read itself.
 */
public final class LocalImageSource {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_EDGE = 2048;

    public record PreparedImage(byte[] bytes, String fileName) {}

    private LocalImageSource() {}

    /** Loads + scales + re-encodes a local image file. Null if unreadable/unsupported. */
    public static PreparedImage fromFile(File f) {
        if (f == null || !f.isFile()) return null;
        try {
            BufferedImage bi = ImageIO.read(f);
            if (bi == null) {
                LOGGER.info("[e33chat] upload: unsupported file {}", f.getName());
                return null;
            }
            return encode(bi, f.getName());
        } catch (Throwable t) {
            LOGGER.info("[e33chat] upload: read failed {}: {}", f.getName(), t.toString());
            return null;
        }
    }

    /** Reads an image from the system clipboard (AWT). Null if none/error. */
    public static PreparedImage fromClipboard() {
        try {
            Object data = Toolkit.getDefaultToolkit().getSystemClipboard().getData(DataFlavor.imageFlavor);
            if (!(data instanceof BufferedImage bi)) return null;
            return encode(bi, "clipboard.png");
        } catch (Throwable t) {
            LOGGER.debug("[e33chat] upload: clipboard read failed: {}", t.toString());
            return null;
        }
    }

    private static PreparedImage encode(BufferedImage src, String name) {
        int w = src.getWidth();
        int h = src.getHeight();
        if (w <= 0 || h <= 0) return null;
        // Scale down long edge (chat images; keeps upload small and fast).
        double scale = Math.min(1.0, (double) MAX_EDGE / Math.max(w, h));
        BufferedImage img = src;
        if (scale < 1.0) {
            int nw = Math.max(1, (int) (w * scale));
            int nh = Math.max(1, (int) (h * scale));
            BufferedImage scaled = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = scaled.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            img = scaled;
        }
        String lower = name == null ? "" : name.toLowerCase();
        boolean jpeg = lower.endsWith(".jpg") || lower.endsWith(".jpeg");
        byte[] bytes = jpeg ? toJpeg(img) : toPng(img);
        if (bytes == null) return null;
        return new PreparedImage(bytes, jpeg ? sanitize(name) : sanitizePng(name));
    }

    private static byte[] toPng(BufferedImage img) {
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(img, "png", out);
            return out.size() > 0 ? out.toByteArray() : null;
        } catch (IOException e) {
            return null;
        }
    }

    private static byte[] toJpeg(BufferedImage img) {
        try {
            // JPEG has no alpha — composite onto white first.
            BufferedImage rgb = new BufferedImage(img.getWidth(), img.getHeight(), BufferedImage.TYPE_INT_RGB);
            Graphics2D g = rgb.createGraphics();
            g.setColor(java.awt.Color.WHITE);
            g.fillRect(0, 0, rgb.getWidth(), rgb.getHeight());
            g.drawImage(img, 0, 0, null);
            g.dispose();
            Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
            if (!writers.hasNext()) return null;
            ImageWriter writer = writers.next();
            ImageWriteParam param = writer.getDefaultWriteParam();
            param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
            param.setCompressionQuality(0.9f);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ImageOutputStream ios = ImageIO.createImageOutputStream(out)) {
                writer.setOutput(ios);
                writer.write(null, new IIOImage(rgb, null, null), param);
            }
            writer.dispose();
            return out.size() > 0 ? out.toByteArray() : null;
        } catch (Throwable t) {
            LOGGER.debug("[e33chat] upload: jpeg encode failed: {}", t.toString());
            return null;
        }
    }

    private static String sanitize(String name) {
        return name.replaceAll("[^A-Za-z0-9._-]", "_");
    }

    private static String sanitizePng(String name) {
        String n = sanitize(name);
        return n.endsWith(".png") || n.endsWith(".jpg") || n.endsWith(".jpeg") ? n : n + ".png";
    }
}
