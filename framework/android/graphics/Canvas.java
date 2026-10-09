// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;

/**
 * Software canvas over a packed ARGB int backing store. All drawing is
 * clipped to the canvas bounds intersected with the current clip rect.
 * Deterministic: no anti-aliasing, no fonts.
 *
 * <p>Two backings: a heap {@code int[]} (copy path) or a native-order
 * {@link IntBuffer} view over a direct {@link ByteBuffer} (zero-copy path,
 * via {@code IsakaiWindow.nMapBuffer}). Pixel access always goes through
 * the indexed helpers so both backings behave identically.
 */
public class Canvas {
    private int[] pixels;
    private IntBuffer intBuffer;
    private int width;
    private int height;

    private int clipLeft;
    private int clipTop;
    private int clipRight;
    private int clipBottom;
    private int[] clipStack = new int[16];
    private int clipDepth;

    public Canvas(int width, int height) {
        checkSize(width, height);
        this.width = width;
        this.height = height;
        this.pixels = new int[width * height];
        this.intBuffer = null;
        resetClip();
    }

    public Canvas(int[] pixels, int width, int height) {
        checkSize(width, height);
        if (pixels == null || pixels.length < width * height) throw new IllegalArgumentException("backing too small");
        initCommon(width, height, pixels.clone(), null);
    }

    /**
     * Zero-copy canvas over a direct buffer (must hold at least
     * {@code width*height} ints). The buffer's order is forced to the
     * native order so the int view matches the SHM XRGB layout.
     */
    public Canvas(ByteBuffer direct, int width, int height) {
        checkSize(width, height);
        if (direct == null || !direct.isDirect()) throw new IllegalArgumentException("need a direct buffer");
        if (direct.remaining() < width * height * 4) throw new IllegalArgumentException("backing too small");
        direct.order(ByteOrder.nativeOrder());
        ByteBuffer dup = direct.duplicate();
        dup.order(ByteOrder.nativeOrder());
        dup.limit(dup.position() + width * height * 4);
        initCommon(width, height, null, dup.asIntBuffer());
    }

    private static void checkSize(int width, int height) {
        if (width <= 0 || height <= 0) throw new IllegalArgumentException("size " + width + "x" + height);
    }

    private void initCommon(int width, int height, int[] pixels, IntBuffer intBuffer) {
        this.width = width;
        this.height = height;
        this.pixels = pixels;
        this.intBuffer = intBuffer;
        resetClip();
    }

    /** True when this canvas writes directly into mapped SHM (no copy). */
    public boolean isDirect() {
        return intBuffer != null;
    }

    public int getWidth() {
        return width;
    }

    public int getHeight() {
        return height;
    }

    /** Direct access to the heap backing store (row-major, stride == width). */
    public int[] getPixels() {
        if (pixels == null) throw new IllegalStateException("direct canvas has no int[] backing");
        return pixels;
    }

    /** Direct access to the int view over mapped SHM (zero-copy path). */
    public IntBuffer getIntBuffer() {
        if (intBuffer == null) throw new IllegalStateException("heap canvas has no int buffer");
        return intBuffer;
    }

    private int getIdx(int i) {
        return pixels != null ? pixels[i] : intBuffer.get(i);
    }

    private void setIdx(int i, int color) {
        if (pixels != null) pixels[i] = color;
        else intBuffer.put(i, color);
    }

    private void resetClip() {
        clipLeft = 0;
        clipTop = 0;
        clipRight = width;
        clipBottom = height;
        clipDepth = 0;
    }

    /** Push the current clip rect; pair every call with {@link #restore}. */
    public void save() {
        if (clipDepth * 4 + 4 > clipStack.length) {
            int[] bigger = new int[clipStack.length * 2];
            System.arraycopy(clipStack, 0, bigger, 0, clipStack.length);
            clipStack = bigger;
        }
        clipStack[clipDepth * 4] = clipLeft;
        clipStack[clipDepth * 4 + 1] = clipTop;
        clipStack[clipDepth * 4 + 2] = clipRight;
        clipStack[clipDepth * 4 + 3] = clipBottom;
        clipDepth++;
    }

    /** Pop the clip rect pushed by the last {@link #save}. */
    public void restore() {
        if (clipDepth <= 0) throw new IllegalStateException("restore without save");
        clipDepth--;
        clipLeft = clipStack[clipDepth * 4];
        clipTop = clipStack[clipDepth * 4 + 1];
        clipRight = clipStack[clipDepth * 4 + 2];
        clipBottom = clipStack[clipDepth * 4 + 3];
    }

    /** Intersect the current clip with [left,right) x [top,bottom). */
    public void clipRect(int left, int top, int right, int bottom) {
        if (left > clipLeft) clipLeft = left;
        if (top > clipTop) clipTop = top;
        if (right < clipRight) clipRight = right;
        if (bottom < clipBottom) clipBottom = bottom;
    }

    public void setPixel(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        if (x < clipLeft || y < clipTop || x >= clipRight || y >= clipBottom) return;
        setIdx(y * width + x, color);
    }

    public int getPixel(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return getIdx(y * width + x);
    }

    public void drawColor(int color) {
        fillRect(clipLeft, clipTop, clipRight, clipBottom, color);
    }

    /** Fill [left,right) x [top,bottom), clipped to the canvas and clip rect. */
    public void fillRect(int left, int top, int right, int bottom, int color) {
        if (left < 0) left = 0;
        if (top < 0) top = 0;
        if (right > width) right = width;
        if (bottom > height) bottom = height;
        if (left < clipLeft) left = clipLeft;
        if (top < clipTop) top = clipTop;
        if (right > clipRight) right = clipRight;
        if (bottom > clipBottom) bottom = clipBottom;
        for (int y = top; y < bottom; y++) {
            int base = y * width;
            for (int x = left; x < right; x++) setIdx(base + x, color);
        }
    }

    /** 1px outline of [left,right) x [top,bottom). */
    public void drawRect(int left, int top, int right, int bottom, int color) {
        fillRect(left, top, right, top + 1, color);
        fillRect(left, bottom - 1, right, bottom, color);
        fillRect(left, top, left + 1, bottom, color);
        fillRect(right - 1, top, right, bottom, color);
    }

    /** Filled disc around (cx,cy). */
    public void drawCircle(int cx, int cy, int radius, int color) {
        if (radius < 0) return;
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt((double) (radius * radius - dy * dy));
            fillRect(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    public void drawLine(int x0, int y0, int x1, int y1, int color) {
        int dx = Math.abs(x1 - x0);
        int dy = Math.abs(y1 - y0);
        int sx = x0 < x1 ? 1 : -1;
        int sy = y0 < y1 ? 1 : -1;
        int err = dx - dy;
        int x = x0;
        int y = y0;
        while (true) {
            setPixel(x, y, color);
            if (x == x1 && y == y1) break;
            int e2 = 2 * err;
            if (e2 > -dy) {
                err -= dy;
                x += sx;
            }
            if (e2 < dx) {
                err += dx;
                y += sy;
            }
        }
    }

    /**
     * Draw text with the built-in {@link BitmapFont}. {@code size} is the
     * target cap height in pixels (scaled by size/7, minimum 1x).
     * Supports '\n' (moves to the next line, x resets).
     */
    public void drawText(String text, int x, int y, int size, int color) {
        if (text == null || text.isEmpty()) return;
        int scale = BitmapFont.scaleFor(size);
        int cx = x;
        int cy = y;
        int lineStep = BitmapFont.lineStep(size);
        int cell = BitmapFont.cellWidth(size);
        for (int i = 0; i < text.length(); i++) {
            int cp = text.charAt(i);
            if (cp == '\n') {
                cx = x;
                cy += lineStep;
                continue;
            }
            drawGlyph(cp, cx, cy, scale, color);
            cx += cell;
        }
    }

    /**
     * Width in pixels of {@code text} at {@code size}, using the widest
     * '\n'-separated line. Same scale rule as {@link #drawText}.
     */
    public static int measureText(String text, int size) {
        if (text == null || text.isEmpty()) return 0;
        int cell = BitmapFont.cellWidth(size);
        int best = 0;
        int cur = 0;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') {
                if (cur > best) best = cur;
                cur = 0;
            } else {
                cur += cell;
            }
        }
        if (cur > best) best = cur;
        return best;
    }

    private void drawGlyph(int codePoint, int x, int y, int scale, int color) {
        for (int row = 0; row < BitmapFont.HEIGHT; row++) {
            for (int col = 0; col < BitmapFont.WIDTH; col++) {
                if (BitmapFont.pixel(codePoint, col, row)) {
                    fillRect(x + col * scale, y + row * scale,
                            x + col * scale + scale, y + row * scale + scale, color);
                }
            }
        }
    }
}
