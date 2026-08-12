package com.niuqu.chatbubble.image;

import com.mojang.logging.LogUtils;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import net.minecraft.client.texture.NativeImage;
import org.slf4j.Logger;

/**
 * Decodes image bytes to a {@link NativeImage} on a worker thread.
 *
 * PNG goes through NativeImage.read (fast, native). Everything else (JPEG,
 * GIF first frame, BMP) uses ImageIO and is converted pixel by pixel. The
 * reader header is probed BEFORE full decode so oversized images fail fast
 * instead of allocating a huge BufferedImage (decompression-bomb guard).
 */
public final class RasterImageDecoder {
    private static final Logger LOGGER = LogUtils.getLogger();
    public static final int MAX_DIMENSION = 4096;

    public record DecodedImage(NativeImage image, int width, int height) {}

    private RasterImageDecoder() {}

    public static DecodedImage decode(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return null;
        try {
            // PNG fast path (NativeImage.read is PNG-only, no decompression bomb risk here
            // because NativeImage.read applies its own size checks). NOTE: no
            // try-with-resources — the returned image must stay alive until the
            // render thread hands it to NativeImageBackedTexture.
            if (bytes.length > 8 && (bytes[0] & 0xFF) == 0x89 && bytes[1] == 'P' && bytes[2] == 'N' && bytes[3] == 'G') {
                NativeImage img = NativeImage.read(new ByteArrayInputStream(bytes));
                if (img == null) return null;
                return new DecodedImage(img, img.getWidth(), img.getHeight());
            }
            // Header probe for the rest — getWidth/getHeight only reads the header.
            try (ImageInputStream in = ImageIO.createImageInputStream(new ByteArrayInputStream(bytes))) {
                Iterator<ImageReader> readers = ImageIO.getImageReaders(in);
                if (!readers.hasNext()) return null;
                ImageReader reader = readers.next();
                try {
                    reader.setInput(in, true, true);
                    int w = reader.getWidth(0);
                    int h = reader.getHeight(0);
                    if (w <= 0 || h <= 0 || w > MAX_DIMENSION || h > MAX_DIMENSION) return null;
                    BufferedImage bi;
                    try {
                        bi = reader.read(0);
                    } catch (Throwable t) {
                        return null;
                    }
                    if (bi == null) return null;
                    return new DecodedImage(fromBufferedImage(bi), bi.getWidth(), bi.getHeight());
                } finally {
                    reader.dispose();
                }
            }
        } catch (Throwable t) {
            LOGGER.debug("[e33chat] image decode failed: {}", t.toString());
            return null;
        }
    }

    /** AWT BufferedImage → NativeImage (RGBA, ABGR pixel order). */
    private static NativeImage fromBufferedImage(BufferedImage bi) {
        int w = bi.getWidth();
        int h = bi.getHeight();
        NativeImage out = new NativeImage(NativeImage.Format.RGBA, w, h, false);
        // Direct pixel copy. AWT getRGB gives 0xAARRGGBB (big-endian ARGB);
        // NativeImage memory is ABGR32 (byte order B,G,R,A — see FastColor.ABGR32
        // usage in blendPixel), so R and B must swap before the store or JPEG/GIF
        // images render with red and blue exchanged.
        int[] argb = bi.getRGB(0, 0, w, h, null, 0, w);
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int c = argb[y * w + x];
                out.setColor(x, y, (c & 0xFF00FF00) | ((c & 0x00FF0000) >> 16) | ((c & 0x000000FF) << 16));
            }
        }
        return out;
    }
}
