// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import android.os.Parcel;

/**
 * Pixel buffer with an ARGB int backing store. Only the in-memory API
 * used by apps is present; GPU/hardware paths throw.
 */
public class Bitmap {
    public enum Config {
        ALPHA_8, RGB_565, ARGB_4444, ARGB_8888, RGBA_F16, HARDWARE
    }

    public enum CompressFormat {
        JPEG, PNG, WEBP_LOSSY, WEBP_LOSSLESS, WEBP
    }

    private final int width;
    private final int height;
    private final Config config;
    private final int[] pixels;
    private boolean recycled;
    private int density;
    private boolean hasAlpha = true;
    private ColorSpace colorSpace = ColorSpace.get(ColorSpace.Named.SRGB);

    private Bitmap(int width, int height, Config config, int[] pixels) {
        this.width = width;
        this.height = height;
        this.config = config == null ? Config.ARGB_8888 : config;
        this.pixels = pixels;
    }

    public int getWidth() { return width; }
    public int getHeight() { return height; }
    public Config getConfig() { return config; }
    public boolean isRecycled() { return recycled; }
    public int getDensity() { return density; }
    public void setDensity(int density) { this.density = density; }
    public boolean isMutable() { return !recycled; }
    public boolean isPremultiplied() { return true; }
    public boolean hasAlpha() { return hasAlpha; }
    public void setHasAlpha(boolean hasAlpha) { this.hasAlpha = hasAlpha; }
    public void setPremultiplied(boolean premultiplied) {}
    /** No-op: pixels are already in raster-ready form. */
    public void prepareToDraw() {}
    public int getByteCount() { return width * height * 4; }
    public int getAllocationByteCount() { return getByteCount(); }
    public int getRowBytes() { return width * 4; }

    public void recycle() {
        recycled = true;
    }

    private void checkLive() {
        if (recycled) throw new IllegalStateException("recycled");
    }

    /** Package-private shared backing for a software Canvas targeting this bitmap. */
    int[] canvasPixels() { checkLive(); return pixels; }

    public int getPixel(int x, int y) {
        checkLive();
        if (x < 0 || y < 0 || x >= width || y >= height) throw new IllegalArgumentException("coords");
        return pixels[y * width + x];
    }

    public void setPixel(int x, int y, int color) {
        checkLive();
        if (x < 0 || y < 0 || x >= width || y >= height) throw new IllegalArgumentException("coords");
        pixels[y * width + x] = color;
    }

    public void getPixels(int[] out, int offset, int stride, int x, int y, int w, int h) {
        checkLive();
        for (int row = 0; row < h; row++) {
            System.arraycopy(pixels, (y + row) * width + x, out, offset + row * stride, w);
        }
    }

    public void setPixels(int[] in, int offset, int stride, int x, int y, int w, int h) {
        checkLive();
        for (int row = 0; row < h; row++) {
            System.arraycopy(in, offset + row * stride, pixels, (y + row) * width + x, w);
        }
    }

    public void eraseColor(int color) {
        checkLive();
        java.util.Arrays.fill(pixels, color);
    }

    public Bitmap copy(Config config, boolean mutable) {
        checkLive();
        return new Bitmap(width, height, config, pixels.clone());
    }

    public Bitmap extractAlpha() {
        checkLive();
        int[] out = new int[pixels.length];
        for (int i = 0; i < pixels.length; i++) out[i] = (pixels[i] >>> 24) * 0x01010101;
        return new Bitmap(width, height, Config.ALPHA_8, out);
    }

    public boolean compress(CompressFormat format, int quality, java.io.OutputStream stream) {
        return false;
    }

    public void writeToParcel(Parcel dest, int flags) {
        if (dest != null) {
            dest.writeInt(width);
            dest.writeInt(height);
            dest.writeInt(config.ordinal());
            dest.writeIntArray(pixels);
        }
    }

    public static Bitmap createBitmap(int width, int height, Config config) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("size");
        return new Bitmap(width, height, config, new int[width * height]);
    }

    /** Density-aware variant: the bitmap inherits the metrics' density. */
    public static Bitmap createBitmap(android.util.DisplayMetrics metrics, int width, int height, Config config) {
        Bitmap bitmap = createBitmap(width, height, config);
        if (metrics != null) bitmap.setDensity(metrics.densityDpi);
        return bitmap;
    }

    /** Full variant with alpha semantics and an explicit color space. */
    public static Bitmap createBitmap(android.util.DisplayMetrics metrics, int width, int height,
            Config config, boolean hasAlpha, ColorSpace colorSpace) {
        Bitmap bitmap = createBitmap(metrics, width, height, config);
        bitmap.setHasAlpha(hasAlpha);
        if (colorSpace != null) bitmap.setColorSpace(colorSpace);
        return bitmap;
    }

    public ColorSpace getColorSpace() {
        checkLive();
        return colorSpace;
    }

    public void setColorSpace(ColorSpace colorSpace) {
        this.colorSpace = colorSpace == null ? ColorSpace.get(ColorSpace.Named.SRGB) : colorSpace;
    }

    /**
     * Wrap an ARGB_8888 pixel array. The array is copied; {@code null} or a
     * short array fails with IllegalArgumentException.
     */
    public static Bitmap fromPixels(int width, int height, Config config, int[] pixels) {
        if (width <= 0 || height <= 0 || pixels == null || pixels.length < width * height) {
            throw new IllegalArgumentException("pixels");
        }
        int[] copy = new int[width * height];
        System.arraycopy(pixels, 0, copy, 0, width * height);
        return new Bitmap(width, height, config, copy);
    }

    public static Bitmap createBitmap(int[] colors, int width, int height, Config config) {
        if (colors == null || colors.length < width * height) throw new IllegalArgumentException("colors");
        return new Bitmap(width, height, config, colors.clone());
    }

    public static Bitmap createBitmap(Bitmap src) {
        if (src == null) throw new IllegalArgumentException("src");
        src.checkLive();
        return new Bitmap(src.width, src.height, src.config, src.pixels.clone());
    }

    public static Bitmap createBitmap(Bitmap source, int x, int y, int w, int h) {
        return createBitmap(source, x, y, w, h, null, false);
    }

    public static Bitmap createBitmap(Bitmap source, int x, int y, int w, int h, Matrix m, boolean filter) {
        if (source == null) throw new IllegalArgumentException("src");
        source.checkLive();
        if (x < 0 || y < 0 || w <= 0 || h <= 0 || x + w > source.width || y + h > source.height) {
            throw new IllegalArgumentException("bounds");
        }
        if (m == null || m.isIdentity()) {
            int[] out = new int[w * h];
            for (int row = 0; row < h; row++) {
                System.arraycopy(source.pixels, (y + row) * source.width + x, out, row * w, w);
            }
            return new Bitmap(w, h, source.config, out);
        }
        float[] pts = new float[w * h * 2];
        for (int row = 0; row < h; row++) {
            for (int col = 0; col < w; col++) {
                pts[(row * w + col) * 2] = x + col;
                pts[(row * w + col) * 2 + 1] = y + row;
            }
        }
        Matrix inv = new Matrix();
        if (!m.invert(inv)) return createBitmap(w, h, source.config);
        inv.mapPoints(pts);
        int[] out = new int[w * h];
        for (int i = 0; i < w * h; i++) {
            int sx = Math.round(pts[i * 2]);
            int sy = Math.round(pts[i * 2 + 1]);
            if (sx >= 0 && sy >= 0 && sx < source.width && sy < source.height) {
                out[i] = source.pixels[sy * source.width + sx];
            }
        }
        return new Bitmap(w, h, source.config, out);
    }

    public static Bitmap createScaledBitmap(Bitmap src, int w, int h, boolean filter) {
        if (src == null) throw new IllegalArgumentException("src");
        src.checkLive();
        Bitmap out = createBitmap(Math.max(1, w), Math.max(1, h), src.config);
        for (int row = 0; row < out.height; row++) {
            int sy = row * src.height / out.height;
            for (int col = 0; col < out.width; col++) {
                int sx = col * src.width / out.width;
                out.pixels[row * out.width + col] = src.pixels[sy * src.width + sx];
            }
        }
        return out;
    }

    public static Bitmap createBitmap(Picture source) {
        if (source == null) throw new IllegalArgumentException("src");
        return createBitmap(Math.max(1, source.getWidth()), Math.max(1, source.getHeight()), Config.ARGB_8888);
    }
}
