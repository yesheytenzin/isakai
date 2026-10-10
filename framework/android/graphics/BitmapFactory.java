// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

/**
 * Software image decoder: PNG/JPEG via {@code javax.imageio} into
 * ARGB_8888 bitmaps. No native code, no Binder.
 *
 * <p>Fail-closed: corrupt, truncated, or oversized input returns
 * {@code null} (or throws {@code IOException} only where the signature
 * requires a stream read); a partially decoded image is never returned.
 */
public final class BitmapFactory {
    /** Largest width or height accepted; larger images fail to decode. */
    public static final int MAX_DECODE_DIMENSION = 4096;

    /** Decode options; mirrors the subset of AOSP options the runtime honors. */
    public static class Options {
        /** When true, only {@link #outWidth}/{@link #outHeight} are set. */
        public boolean inJustDecodeBounds;
        public int outWidth = -1;
        public int outHeight = -1;
        public String outMimeType;
        public Bitmap.Config inPreferredConfig = Bitmap.Config.ARGB_8888;
    }

    private BitmapFactory() {}

    public static Bitmap decodeFile(String pathName) {
        return decodeFile(pathName, null);
    }

    public static Bitmap decodeFile(String pathName, Options opts) {
        if (pathName == null) return null;
        try (InputStream in = new FileInputStream(pathName)) {
            return decodeStream(in, opts);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static Bitmap decodeStream(InputStream is) {
        return decodeStream(is, null);
    }

    public static Bitmap decodeStream(InputStream is, Options opts) {
        if (is == null) return null;
        try {
            return decodeLocked(is, opts);
        } catch (IOException | RuntimeException e) {
            return null;
        }
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length) {
        return decodeByteArray(data, offset, length, null);
    }

    public static Bitmap decodeByteArray(byte[] data, int offset, int length, Options opts) {
        if (data == null || offset < 0 || length < 0 || offset + length > data.length) return null;
        return decodeStream(new ByteArrayInputStream(data, offset, length), opts);
    }

    private static Bitmap decodeLocked(InputStream is, Options opts) throws IOException {
        try (ImageInputStream iis = ImageIO.createImageInputStream(is)) {
            if (iis == null) return null;
            Iterator<ImageReader> readers = ImageIO.getImageReaders(iis);
            if (!readers.hasNext()) return null;
            ImageReader reader = readers.next();
            try {
                reader.setInput(iis, true, true);
                int w = reader.getWidth(0);
                int h = reader.getHeight(0);
                if (w <= 0 || h <= 0 || w > MAX_DECODE_DIMENSION || h > MAX_DECODE_DIMENSION) {
                    return null;
                }
                if (opts != null) {
                    opts.outWidth = w;
                    opts.outHeight = h;
                    try {
                        opts.outMimeType = reader.getOriginatingProvider().getMIMETypes()[0];
                    } catch (RuntimeException e) {
                        opts.outMimeType = null;
                    }
                    if (opts.inJustDecodeBounds) return null;
                }
                BufferedImage img;
                try {
                    img = reader.read(0);
                } catch (RuntimeException e) {
                    return null;
                }
                if (img == null) return null;
                if (img.getWidth() != w || img.getHeight() != h) {
                    w = img.getWidth();
                    h = img.getHeight();
                    if (w <= 0 || h <= 0 || w > MAX_DECODE_DIMENSION || h > MAX_DECODE_DIMENSION) {
                        return null;
                    }
                }
                int[] pixels = new int[w * h];
                img.getRGB(0, 0, w, h, pixels, 0, w);
                return Bitmap.fromPixels(w, h, Bitmap.Config.ARGB_8888, pixels);
            } finally {
                reader.dispose();
            }
        }
    }
}
