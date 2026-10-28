// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.IntBuffer;
import java.util.ArrayList;
import java.util.List;

/**
 * Software canvas over a packed ARGB int backing store. All drawing is
 * clipped to the canvas bounds intersected with the current clip rect,
 * and geometry passes through the current affine transform. Geometry is
 * software rasterized; text uses the host Java2D font/shaping backend.
 *
 * <p>Two backings: a heap {@code int[]} (copy path) or a native-order
 * {@link IntBuffer} view over a direct {@link ByteBuffer} (zero-copy path,
 * via {@code IsakaiWindow.nMapBuffer}). Pixel access always goes through
 * the indexed helpers so both backings behave identically.
 *
 * <p>Drawing funnels through the emit* hooks (matrix applied) into the
 * paint* device-space rasterizers. {@link RecordingCanvas} overrides the
 * emit hooks to record ops into a {@link DisplayList}, which
 * {@link #drawRenderNode} replays onto this canvas.
 */
public class Canvas {
    /** Edge filtering hint for quickReject, recorded only. */
    public enum EdgeType { BW, AA }

    /** Vertex drawing mode, accepted for linkage; drawVertices is a no-op. */
    public enum VertexMode { TRIANGLES, TRIANGLE_FAN, TRIANGLE_STRIP }

    private int[] pixels;
    private IntBuffer intBuffer;
    private int width;
    private int height;

    private int clipLeft;
    private int clipTop;
    private int clipRight;
    private int clipBottom;

    /** Current affine transform, row-major [m00 m01 m02 m10 m11 m12 m20 m21 m22]. */
    private final float[] matrix = {1, 0, 0, 0, 1, 0, 0, 0, 1};
    private float[] savedMatrices = new float[16 * 9];
    private int[] clipStack = new int[16 * 4];
    private int clipDepth;

    private int density = 160;
    private DrawFilter drawFilter;

    public Canvas(Bitmap bitmap) {
        if (bitmap == null) throw new NullPointerException("bitmap");
        if (!bitmap.isMutable()) throw new IllegalStateException("bitmap is not mutable");
        initCommon(bitmap.getWidth(), bitmap.getHeight(), bitmap.canvasPixels(), null);
    }

    /** Empty canvas: draws nowhere until setBitmap; Compose probes it. */
    public Canvas() {
        this.width = 0;
        this.height = 0;
        this.pixels = new int[0];
        this.intBuffer = null;
        resetClip();
    }

    protected Canvas(int width, int height, boolean unused) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.pixels = new int[Math.max(1, this.width * this.height)];
        this.intBuffer = null;
        resetClip();
    }

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
        resetMatrix();
        resetClip();
    }

    /** Size the logical frame without allocating pixels (recording canvases). */
    protected void setLogicalSize(int width, int height) {
        this.width = Math.max(0, width);
        this.height = Math.max(0, height);
        this.pixels = new int[1];
        this.intBuffer = null;
        clipLeft = 0;
        clipTop = 0;
        clipRight = this.width;
        clipBottom = this.height;
        clipDepth = 0;
        resetMatrix();
    }

    /** Snapshot of the current matrix (recording ops compose it at replay). */
    float[] currentMatrix() {
        return matrix.clone();
    }

    int clipLeft() { return clipLeft; }
    int clipTop() { return clipTop; }
    int clipRight() { return clipRight; }
    int clipBottom() { return clipBottom; }

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

    /** Retarget to a bitmap (or detach with null). */
    public void setBitmap(Bitmap bitmap) {
        if (bitmap == null) {
            this.width = 0;
            this.height = 0;
            this.pixels = new int[0];
            this.intBuffer = null;
            resetMatrix();
            resetClip();
            return;
        }
        initCommon(bitmap.getWidth(), bitmap.getHeight(), bitmap.canvasPixels(), null);
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

    /** Src-over store: opaque colors overwrite, translucent colors blend. */
    private void setIdx(int i, int color) {
        int a = color >>> 24;
        if (a == 0xFF) {
            if (pixels != null) pixels[i] = color;
            else intBuffer.put(i, color);
            return;
        }
        if (a == 0) return;
        int dst = pixels != null ? pixels[i] : intBuffer.get(i);
        int out = blend(color, dst);
        if (pixels != null) pixels[i] = out;
        else intBuffer.put(i, out);
    }

    /** Raw store bypassing blending (drawColor SRC/CLEAR). */
    private void setIdxRaw(int i, int color) {
        if (pixels != null) pixels[i] = color;
        else intBuffer.put(i, color);
    }

    private static int blend(int src, int dst) {
        int a = src >>> 24;
        if (a == 0xFF) return src;
        if (a == 0) return dst;
        int ia = 255 - a;
        int rb = (((src >>> 16) & 0xFF) * a + ((dst >>> 16) & 0xFF) * ia + 127) / 255;
        int gb = (((src >>> 8) & 0xFF) * a + ((dst >>> 8) & 0xFF) * ia + 127) / 255;
        int bb = ((src & 0xFF) * a + (dst & 0xFF) * ia + 127) / 255;
        return 0xFF000000 | (rb << 16) | (gb << 8) | bb;
    }

    private void resetClip() {
        clipLeft = 0;
        clipTop = 0;
        clipRight = width;
        clipBottom = height;
    }

    private void resetMatrix() {
        System.arraycopy(new float[]{1, 0, 0, 0, 1, 0, 0, 0, 1}, 0, matrix, 0, 9);
    }

    private void ensureStack(int depth) {
        if (depth * 4 > clipStack.length) {
            int[] biggerClip = new int[clipStack.length * 2];
            System.arraycopy(clipStack, 0, biggerClip, 0, clipStack.length);
            clipStack = biggerClip;
            float[] biggerMatrix = new float[savedMatrices.length * 2];
            System.arraycopy(savedMatrices, 0, biggerMatrix, 0, savedMatrices.length);
            savedMatrices = biggerMatrix;
        }
    }

    /**
     * Save the current matrix and clip. Matches the AOSP contract: returns
     * the value to pass to {@link #restoreToCount} to balance this call,
     * with the initial save count being 1.
     */
    public int save() {
        ensureStack(clipDepth + 1);
        System.arraycopy(matrix, 0, savedMatrices, clipDepth * 9, 9);
        int b = clipDepth * 4;
        clipStack[b] = clipLeft;
        clipStack[b + 1] = clipTop;
        clipStack[b + 2] = clipRight;
        clipStack[b + 3] = clipBottom;
        clipDepth++;
        return clipDepth;
    }

    /** Pop the matrix and clip pushed by the last {@link #save}. */
    public void restore() {
        if (clipDepth <= 0) throw new IllegalStateException("restore without save");
        clipDepth--;
        int b = clipDepth * 4;
        clipLeft = clipStack[b];
        clipTop = clipStack[b + 1];
        clipRight = clipStack[b + 2];
        clipBottom = clipStack[b + 3];
        System.arraycopy(savedMatrices, clipDepth * 9, matrix, 0, 9);
    }

    /** Unwind every save down to {@code saveCount}, AOSP contract. */
    public void restoreToCount(int saveCount) {
        if (saveCount < 0) throw new IllegalArgumentException("saveCount must be >= 0");
        while (clipDepth > 0 && clipDepth >= saveCount) restore();
    }

    /** Number of matrix/clip states in the stack; initially 1. */
    public int getSaveCount() {
        return clipDepth + 1;
    }

    // ---------------------------------------------------------------- transforms

    private boolean isIdentity() {
        return matrix[0] == 1 && matrix[1] == 0 && matrix[2] == 0
                && matrix[3] == 0 && matrix[4] == 1 && matrix[5] == 0
                && matrix[6] == 0 && matrix[7] == 0 && matrix[8] == 1;
    }

    /** Map a user-space point through the current transform. */
    private void mapPoint(float x, float y, float[] out) {
        float w = matrix[6] * x + matrix[7] * y + matrix[8];
        if (w == 0) w = 1;
        out[0] = (matrix[0] * x + matrix[1] * y + matrix[2]) / w;
        out[1] = (matrix[3] * x + matrix[4] * y + matrix[5]) / w;
    }

    /** Average scale factor of the current transform (for text/stroke sizes). */
    private float scaleFactor() {
        float sx = (float) Math.hypot(matrix[0], matrix[3]);
        float sy = (float) Math.hypot(matrix[1], matrix[4]);
        float s = (float) Math.sqrt(Math.max(0.0001f, sx * sy));
        return !Float.isNaN(s) && s > 0 ? s : 1f;
    }

    /** Concat the current matrix with a translation: M' = M * T. */
    public void translate(float dx, float dy) {
        float m0 = matrix[0], m1 = matrix[1], m2 = matrix[2];
        float m3 = matrix[3], m4 = matrix[4], m5 = matrix[5];
        float m6 = matrix[6], m7 = matrix[7];
        matrix[2] = m0 * dx + m1 * dy + m2;
        matrix[5] = m3 * dx + m4 * dy + m5;
        matrix[8] = m6 * dx + m7 * dy + matrix[8];
    }

    /** Concat the current matrix with a scale about (px,py): M' = M * S. */
    public void scale(float sx, float sy, float px, float py) {
        float m0 = matrix[0], m1 = matrix[1], m2 = matrix[2];
        float m3 = matrix[3], m4 = matrix[4], m5 = matrix[5];
        float m6 = matrix[6], m7 = matrix[7], m8 = matrix[8];
        float tx = px - sx * px;
        float ty = py - sy * py;
        matrix[0] = m0 * sx;
        matrix[1] = m1 * sy;
        matrix[2] = m0 * tx + m1 * ty + m2;
        matrix[3] = m3 * sx;
        matrix[4] = m4 * sy;
        matrix[5] = m3 * tx + m4 * ty + m5;
        matrix[6] = m6 * sx;
        matrix[7] = m7 * sy;
        matrix[8] = m6 * tx + m7 * ty + m8;
    }

    public void scale(float sx, float sy) {
        scale(sx, sy, 0, 0);
    }

    /** Concat the current matrix with a rotation about (px,py): M' = M * R. */
    public void rotate(float degrees, float px, float py) {
        double rad = Math.toRadians(degrees);
        float c = (float) Math.cos(rad);
        float s = (float) Math.sin(rad);
        float m0 = matrix[0], m1 = matrix[1], m2 = matrix[2];
        float m3 = matrix[3], m4 = matrix[4], m5 = matrix[5];
        float m6 = matrix[6], m7 = matrix[7], m8 = matrix[8];
        float tx = px - c * px + s * py;
        float ty = py - s * px - c * py;
        matrix[0] = m0 * c + m1 * s;
        matrix[1] = -m0 * s + m1 * c;
        matrix[2] = m0 * tx + m1 * ty + m2;
        matrix[3] = m3 * c + m4 * s;
        matrix[4] = -m3 * s + m4 * c;
        matrix[5] = m3 * tx + m4 * ty + m5;
        matrix[6] = m6 * c + m7 * s;
        matrix[7] = -m6 * s + m7 * c;
        matrix[8] = m6 * tx + m7 * ty + m8;
    }

    public void rotate(float degrees) {
        rotate(degrees, 0, 0);
    }

    /** Concat the current matrix with a skew: M' = M * K. */
    public void skew(float sx, float sy) {
        float m0 = matrix[0], m1 = matrix[1];
        float m3 = matrix[3], m4 = matrix[4];
        float m6 = matrix[6], m7 = matrix[7];
        matrix[0] = m0 + m1 * sy;
        matrix[1] = m0 * sx + m1;
        matrix[3] = m3 + m4 * sy;
        matrix[4] = m3 * sx + m4;
        matrix[6] = m6 + m7 * sy;
        matrix[7] = m6 * sx + m7;
    }

    /** Concat the current matrix with {@code other}: M' = M * other. */
    public void concat(Matrix other) {
        if (other == null) return;
        float[] o = new float[9];
        other.getValues(o);
        float[] r = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] = matrix[row * 3] * o[col]
                        + matrix[row * 3 + 1] * o[3 + col]
                        + matrix[row * 3 + 2] * o[6 + col];
            }
        }
        System.arraycopy(r, 0, matrix, 0, 9);
    }

    /** Replace the current matrix (null resets to identity). */
    public void setMatrix(Matrix m) {
        if (m == null) {
            resetMatrix();
            return;
        }
        m.getValues(matrix);
    }

    /** Copy the current matrix into {@code c}. */
    public void getMatrix(Matrix c) {
        if (c != null) c.setValues(matrix);
    }

    // ---------------------------------------------------------------------- clip

    /** Intersect the clip with [left,right) x [top,bottom). */
    public boolean clipRect(int left, int top, int right, int bottom) {
        if (left > clipLeft) clipLeft = left;
        if (top > clipTop) clipTop = top;
        if (right < clipRight) clipRight = right;
        if (bottom < clipBottom) clipBottom = bottom;
        return true;
    }

    /** Float variant: transformed, then tracked as an axis-aligned rect. */
    public boolean clipRect(float left, float top, float right, float bottom) {
        if (isIdentity()) {
            clipRect((int) Math.floor(left), (int) Math.floor(top),
                    (int) Math.ceil(right), (int) Math.ceil(bottom));
            return true;
        }
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        float[] p = new float[2];
        mapPoint(left, top, p);
        l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
        mapPoint(right, top, p);
        l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
        mapPoint(right, bottom, p);
        l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
        mapPoint(left, bottom, p);
        l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
        clipRect((int) Math.floor(l), (int) Math.floor(t), (int) Math.ceil(r), (int) Math.ceil(b));
        return true;
    }

    public boolean clipRect(Rect rect) {
        if (rect == null) return false;
        return clipRect((float) rect.left, rect.top, rect.right, rect.bottom);
    }

    public boolean clipRect(RectF rect) {
        if (rect == null) return false;
        return clipRect(rect.left, rect.top, rect.right, rect.bottom);
    }

    public boolean clipRect(float left, float top, float right, float bottom, Region.Op op) {
        return clipRectOp(left, top, right, bottom, op);
    }

    public boolean clipRect(Rect rect, Region.Op op) {
        if (rect == null) return false;
        return clipRectOp(rect.left, rect.top, rect.right, rect.bottom, op);
    }

    public boolean clipRect(RectF rect, Region.Op op) {
        if (rect == null) return false;
        return clipRectOp(rect.left, rect.top, rect.right, rect.bottom, op);
    }

    private boolean clipRectOp(float left, float top, float right, float bottom, Region.Op op) {
        if (op == null || op == Region.Op.INTERSECT) {
            return clipRect(left, top, right, bottom);
        }
        if (op == Region.Op.REPLACE) {
            float l = left, t = top, r = right, b = bottom;
            if (!isIdentity()) {
                l = Float.MAX_VALUE; t = Float.MAX_VALUE; r = -Float.MAX_VALUE; b = -Float.MAX_VALUE;
                float[] p = new float[2];
                mapPoint(left, top, p);
                l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
                mapPoint(right, top, p);
                l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
                mapPoint(right, bottom, p);
                l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
                mapPoint(left, bottom, p);
                l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            }
            clipLeft = clamp((int) Math.floor(l), 0, width);
            clipTop = clamp((int) Math.floor(t), 0, height);
            clipRight = clamp((int) Math.ceil(r), 0, width);
            clipBottom = clamp((int) Math.ceil(b), 0, height);
            return true;
        }
        if (op == Region.Op.UNION) {
            float l = left, t = top, r = right, b = bottom;
            if (!isIdentity()) {
                l = Float.MAX_VALUE; t = Float.MAX_VALUE; r = -Float.MAX_VALUE; b = -Float.MAX_VALUE;
                float[] p = new float[2];
                mapPoint(left, top, p);
                l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
                mapPoint(right, top, p);
                l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
                mapPoint(right, bottom, p);
                l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
                mapPoint(left, bottom, p);
                l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            }
            clipLeft = clamp((int) Math.floor(Math.min(clipLeft, l)), 0, width);
            clipTop = clamp((int) Math.floor(Math.min(clipTop, t)), 0, height);
            clipRight = clamp((int) Math.ceil(Math.max(clipRight, r)), 0, width);
            clipBottom = clamp((int) Math.ceil(Math.max(clipBottom, b)), 0, height);
            return true;
        }
        // DIFFERENCE/REVERSE_DIFFERENCE/XOR need a non-rectangular clip region.
        return false;
    }

    private static int clamp(int v, int lo, int hi) {
        return v < lo ? lo : Math.min(v, hi);
    }

    public boolean clipPath(Path path) {
        return clipPath(path, Region.Op.INTERSECT);
    }

    public boolean clipPath(Path path, Region.Op op) {
        if (path == null) return false;
        RectF b = new RectF();
        path.computeBounds(b, true);
        return clipRectOp(b.left, b.top, b.right, b.bottom, op);
    }

    /** Subtract a path from the clip: unsupported, reports unchanged. */
    public boolean clipOutPath(Path path) {
        return false;
    }

    /** Subtract rects from the clip: unsupported, reports unchanged. */
    public boolean clipOutRect(RectF rect) {
        return false;
    }

    /** Subtract rects from the clip: unsupported, reports unchanged. */
    public boolean clipOutRect(Rect rect) {
        return false;
    }

    /** Subtract rects from the clip: unsupported, reports unchanged. */
    public boolean clipOutRect(float left, float top, float right, float bottom) {
        return false;
    }

    /** Subtract rects from the clip: unsupported, reports unchanged. */
    public boolean clipOutRect(int left, int top, int right, int bottom) {
        return false;
    }

    /** Copy the current clip into {@code bounds}. */
    public boolean getClipBounds(Rect bounds) {
        if (bounds != null) bounds.set(clipLeft, clipTop, clipRight, clipBottom);
        return clipRight > clipLeft && clipBottom > clipTop;
    }

    // ------------------------------------------------------------------- emitters

    /** Point draw through the current transform. */
    protected void emitPixel(float x, float y, int color) {
        if (isIdentity()) {
            paintPixel(Math.round(x), Math.round(y), color);
            return;
        }
        float[] p = new float[2];
        mapPoint(x, y, p);
        paintPixel(Math.round(p[0]), Math.round(p[1]), color);
    }

    /** Filled rectangle through the current transform. */
    protected void emitRect(float left, float top, float right, float bottom, int color) {
        if (right < left || bottom < top) return;
        if (isIdentity()) {
            paintRect(left, top, right, bottom, color);
            return;
        }
        float[] q = new float[]{left, top, right, top, right, bottom, left, bottom};
        emitPolygon(q, 4, color);
    }

    /** Filled polygon through the current transform (pts x0,y0,x1,y1,...). */
    protected void emitPolygon(float[] pts, int count, int color) {
        if (pts == null || count < 3) return;
        if (!isIdentity()) {
            float[] mapped = pts.clone();
            float[] p = new float[2];
            for (int i = 0; i + 1 < mapped.length; i += 2) {
                mapPoint(mapped[i], mapped[i + 1], p);
                mapped[i] = p[0];
                mapped[i + 1] = p[1];
            }
            paintPolygon(mapped, count, color);
            return;
        }
        paintPolygon(pts, count, color);
    }

    /** Stroked segment through the current transform. */
    protected void emitStroke(float x0, float y0, float x1, float y1, int color, int halfWidth) {
        if (isIdentity()) {
            paintStroke(x0, y0, x1, y1, color, halfWidth);
            return;
        }
        float[] a = new float[2];
        float[] b = new float[2];
        mapPoint(x0, y0, a);
        mapPoint(x1, y1, b);
        paintStroke(a[0], a[1], b[0], b[1], color, halfWidth);
    }

    /** Filled ellipse through the current transform. */
    protected void emitEllipse(float cx, float cy, float rx, float ry, int color) {
        if (isIdentity()) {
            paintEllipse(cx, cy, rx, ry, color);
            return;
        }
        float[] p = new float[2];
        mapPoint(cx, cy, p);
        float s = scaleFactor();
        paintEllipse(p[0], p[1], rx * s, ry * s, color);
    }

    /** Bitmap blit through the current transform (source/dest in user space). */
    protected void emitBitmap(Bitmap bitmap, float sx0, float sy0, float sx1, float sy1,
            float dx0, float dy0, float dx1, float dy1) {
        emitBitmap(bitmap, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, null);
    }

    protected void emitBitmap(Bitmap bitmap, float sx0, float sy0, float sx1, float sy1,
            float dx0, float dy0, float dx1, float dy1,
            java.util.function.IntUnaryOperator transform) {
        if (bitmap == null || bitmap.isRecycled() || dx1 <= dx0 || dy1 <= dy0) return;
        if (!isIdentity()) {
            float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
            float[] p = new float[2];
            mapPoint(dx0, dy0, p);
            l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            mapPoint(dx1, dy0, p);
            l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            mapPoint(dx1, dy1, p);
            l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            mapPoint(dx0, dy1, p);
            l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            paintBitmap(bitmap, sx0, sy0, sx1, sy1, l, t, r, b, 255, transform);
            return;
        }
        paintBitmap(bitmap, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1, 255, transform);
    }

    /** Text draw through the current transform. */
    protected void emitText(String text, float x, float y, float size, int color) {
        if (text == null || text.isEmpty()) return;
        if (isIdentity()) {
            paintText(text, x, y, size, color);
            return;
        }
        float[] p = new float[2];
        mapPoint(x, y, p);
        paintText(text, p[0], p[1], size * scaleFactor(), color);
    }

    // ------------------------------------------------------------ device raster

    /** Device-space point, clipped, src-over blended. */
    void paintPixel(int x, int y, int color) {
        if (x < 0 || y < 0 || x >= width || y >= height) return;
        if (x < clipLeft || y < clipTop || x >= clipRight || y >= clipBottom) return;
        setIdx(y * width + x, color);
    }

    /** Device-space rectangle fill, clipped, src-over blended. */
    void paintRect(float left, float top, float right, float bottom, int color) {
        int l = (int) Math.floor(Math.max(left, 0));
        int t = (int) Math.floor(Math.max(top, 0));
        int r = (int) Math.ceil(Math.min(right, width));
        int b = (int) Math.ceil(Math.min(bottom, height));
        if (l < clipLeft) l = clipLeft;
        if (t < clipTop) t = clipTop;
        if (r > clipRight) r = clipRight;
        if (b > clipBottom) b = clipBottom;
        for (int y = t; y < b; y++) {
            int base = y * width;
            for (int x = l; x < r; x++) setIdx(base + x, color);
        }
    }

    /** Raw fill bypassing alpha blending (SRC/CLEAR modes). */
    private void paintRectRaw(float left, float top, float right, float bottom, int color) {
        int l = (int) Math.floor(Math.max(left, 0));
        int t = (int) Math.floor(Math.max(top, 0));
        int r = (int) Math.ceil(Math.min(right, width));
        int b = (int) Math.ceil(Math.min(bottom, height));
        if (l < clipLeft) l = clipLeft;
        if (t < clipTop) t = clipTop;
        if (r > clipRight) r = clipRight;
        if (b > clipBottom) b = clipBottom;
        for (int y = t; y < b; y++) {
            int base = y * width;
            for (int x = l; x < r; x++) setIdxRaw(base + x, color);
        }
    }

    /** Scanline even-odd polygon fill; pts holds x0,y0,x1,y1,... */
    void paintPolygon(float[] pts, int count, int color) {
        if (count < 3) return;
        float minY = Float.MAX_VALUE;
        float maxY = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            float y = pts[i * 2 + 1];
            if (y < minY) minY = y;
            if (y > maxY) maxY = y;
        }
        int y0 = Math.max(clipTop, (int) Math.floor(minY));
        int y1 = Math.min(clipBottom, (int) Math.ceil(maxY));
        if (y1 <= y0) return;
        float[] xs = new float[count];
        for (int y = y0; y < y1; y++) {
            float yc = y + 0.5f;
            int n = 0;
            for (int i = 0; i < count; i++) {
                int j = i + 1 == count ? 0 : i + 1;
                float yi = pts[i * 2 + 1];
                float yj = pts[j * 2 + 1];
                if ((yi <= yc && yj > yc) || (yj <= yc && yi > yc)) {
                    float xi = pts[i * 2];
                    float xj = pts[j * 2];
                    xs[n++] = xi + (yc - yi) * (xj - xi) / (yj - yi);
                }
            }
            if (n < 2) continue;
            java.util.Arrays.sort(xs, 0, n);
            for (int k = 0; k + 1 < n; k += 2) {
                int xa = (int) Math.ceil(xs[k] - 0.5f);
                int xb = (int) Math.ceil(xs[k + 1] - 0.5f);
                if (xa < clipLeft) xa = clipLeft;
                if (xb > clipRight) xb = clipRight;
                if (xb <= xa) continue;
                int base = y * width;
                for (int x = xa; x < xb; x++) setIdx(base + x, color);
            }
        }
    }

    /** Bresenham segment with optional half-width thickening (square brush). */
    void paintStroke(float x0, float y0, float x1, float y1, int color, int hw) {
        int ix0 = Math.round(x0);
        int iy0 = Math.round(y0);
        int ix1 = Math.round(x1);
        int iy1 = Math.round(y1);
        int dx = Math.abs(ix1 - ix0);
        int dy = Math.abs(iy1 - iy0);
        int sx = ix0 < ix1 ? 1 : -1;
        int sy = iy0 < iy1 ? 1 : -1;
        int err = dx - dy;
        int x = ix0;
        int y = iy0;
        while (true) {
            if (hw <= 0) {
                paintPixel(x, y, color);
            } else {
                paintRect(x - hw, y - hw, x + hw + 1, y + hw + 1, color);
            }
            if (x == ix1 && y == iy1) break;
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

    /** Filled axis-aligned ellipse in device space. */
    void paintEllipse(float cx, float cy, float rx, float ry, int color) {
        if (rx <= 0 || ry <= 0) return;
        int t = Math.max(clipTop, (int) Math.floor(cy - ry));
        int b = Math.min(clipBottom, (int) Math.ceil(cy + ry));
        for (int y = t; y < b; y++) {
            float dy = (y + 0.5f - cy) / ry;
            float inside = 1 - dy * dy;
            if (inside < 0) continue;
            float dx = rx * (float) Math.sqrt(inside);
            int xa = Math.max(clipLeft, (int) Math.ceil(cx - dx - 0.5f));
            int xb = Math.min(clipRight, (int) Math.ceil(cx + dx - 0.5f));
            int base = y * width;
            for (int x = xa; x < xb; x++) setIdx(base + x, color);
        }
    }

    /** Nearest-neighbor bitmap blit in device space with optional tint. */
    void paintBitmap(Bitmap bitmap, float sx0, float sy0, float sx1, float sy1,
            float dx0, float dy0, float dx1, float dy1, int alpha255,
            java.util.function.IntUnaryOperator transform) {
        if (bitmap == null || bitmap.isRecycled()) return;
        int sw = bitmap.getWidth();
        int sh = bitmap.getHeight();
        if (sw <= 0 || sh <= 0) return;
        float dw = dx1 - dx0;
        float dh = dy1 - dy0;
        if (dw <= 0 || dh <= 0) return;
        int x0 = Math.max((int) Math.floor(dx0), Math.max(0, clipLeft));
        int y0 = Math.max((int) Math.floor(dy0), Math.max(0, clipTop));
        int x1 = Math.min((int) Math.ceil(dx1), Math.min(width, clipRight));
        int y1 = Math.min((int) Math.ceil(dy1), Math.min(height, clipBottom));
        float sxs = (sx1 - sx0) / dw;
        float sys = (sy1 - sy0) / dh;
        for (int dy = y0; dy < y1; dy++) {
            int sy = (int) (sy0 + (dy + 0.5f - dy0) * sys);
            if (sy < 0) sy = 0;
            if (sy >= sh) sy = sh - 1;
            int base = dy * width;
            for (int dx = x0; dx < x1; dx++) {
                int sx = (int) (sx0 + (dx + 0.5f - dx0) * sxs);
                if (sx < 0) sx = 0;
                if (sx >= sw) sx = sw - 1;
                int color = bitmap.getPixel(sx, sy);
                if (transform != null) color = transform.applyAsInt(color);
                if (alpha255 != 0xFF) {
                    int a = (color >>> 24) * alpha255 / 255;
                    color = (color & 0x00FFFFFF) | (a << 24);
                }
                setIdx(base + dx, color);
            }
        }
    }

    /** Host text raster in device space. */
    void paintText(String text, float x, float y, float size, int color) {
        HostText.draw(this, text, Math.round(x), Math.round(y), Math.max(1, Math.round(size)), color);
    }

    // ------------------------------------------------------------------ pixels

    public void setPixel(int x, int y, int color) {
        // Device-space write: callers pass pixels already mapped through the
        // canvas transform (HostText rasters, VectorDrawable), so this must
        // not re-apply the matrix the way drawPoint/emitPixel do.
        paintPixel(x, y, color);
    }

    public int getPixel(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return getIdx(y * width + x);
    }

    /** Fill the entire clip with {@code color}, src-over blended. */
    public void drawColor(int color) {
        paintRect(clipLeft, clipTop, clipRight, clipBottom, color);
    }

    /** Fill the clip honoring the mode: SRC/CLEAR replace, others blend. */
    public void drawColor(int color, PorterDuff.Mode mode) {
        if (mode == PorterDuff.Mode.SRC) {
            paintRectRaw(clipLeft, clipTop, clipRight, clipBottom, color);
        } else if (mode == PorterDuff.Mode.CLEAR) {
            paintRectRaw(clipLeft, clipTop, clipRight, clipBottom, 0);
        } else {
            drawColor(color);
        }
    }

    /** Color-long variant: sRGB colors pass through the low 32 bits. */
    public void drawColor(long color) {
        drawColor((int) color);
    }

    public void drawColor(long color, BlendMode mode) {
        drawColor((int) color);
    }

    public void drawColor(int color, BlendMode mode) {
        drawColor(color);
    }

    public void drawARGB(int a, int r, int g, int b) {
        drawColor(((a & 0xFF) << 24) | ((r & 0xFF) << 16) | ((g & 0xFF) << 8) | (b & 0xFF));
    }

    public void drawRGB(int r, int g, int b) {
        drawARGB(0xFF, r, g, b);
    }

    /** Fill [left,right) x [top,bottom), clipped to the canvas and clip rect. */
    public void fillRect(int left, int top, int right, int bottom, int color) {
        emitRect(left, top, right, bottom, color);
    }

    /** 1px outline of [left,right) x [top,bottom). */
    public void drawRect(int left, int top, int right, int bottom, int color) {
        emitRect(left, top, right, top + 1, color);
        emitRect(left, bottom - 1, right, bottom, color);
        emitRect(left, top, left + 1, bottom, color);
        emitRect(right - 1, top, right, bottom, color);
    }

    // ------------------------------------------------------------- paint state

    /** Effective color of a paint: its color with the paint alpha folded in. */
    protected static int paintColor(Paint paint) {
        int c = paint.getColor();
        int a = (c >>> 24) * paint.getAlpha() / 255;
        return (c & 0x00FFFFFF) | (a << 24);
    }

    private static int halfWidth(Paint paint) {
        float w = paint.getStrokeWidth();
        return w <= 1.5f ? 0 : Math.max(0, Math.round(w / 2f) - 1);
    }

    // ------------------------------------------------------- paint-based draws

    public void drawRect(float left, float top, float right, float bottom, Paint paint) {
        if (paint == null) return;
        int color = paintColor(paint);
        Paint.Style style = paint.getStyle();
        if (style == Paint.Style.STROKE) {
            int hw = halfWidth(paint);
            emitStroke(left, top, right, top, color, hw);
            emitStroke(right, top, right, bottom, color, hw);
            emitStroke(right, bottom, left, bottom, color, hw);
            emitStroke(left, bottom, left, top, color, hw);
            return;
        }
        emitRect(left, top, right, bottom, color);
        if (style == Paint.Style.FILL_AND_STROKE) {
            emitStroke(left, top, right, top, color, 0);
            emitStroke(right, top, right, bottom, color, 0);
            emitStroke(right, bottom, left, bottom, color, 0);
            emitStroke(left, bottom, left, top, color, 0);
        }
    }

    public void drawRect(Rect rect, Paint paint) {
        if (rect == null) return;
        drawRect(rect.left, rect.top, rect.right, rect.bottom, paint);
    }

    public void drawRect(RectF rect, Paint paint) {
        if (rect == null) return;
        drawRect(rect.left, rect.top, rect.right, rect.bottom, paint);
    }

    /**
     * Rounded-rect fill: rect body plus corner discs of radius min(rx,ry).
     * STROKE draws a 1px rounded outline. Under a non-identity transform the
     * rect degenerates to its corner quad (no corner radii).
     */
    public void drawRoundRect(RectF rect, float rx, float ry, Paint paint) {
        if (rect == null || paint == null) return;
        drawRoundRect(rect.left, rect.top, rect.right, rect.bottom, rx, ry, paint);
    }

    public void drawRoundRect(float left, float top, float right, float bottom,
            float rx, float ry, Paint paint) {
        if (paint == null || right <= left || bottom <= top) return;
        int color = paintColor(paint);
        Paint.Style style = paint.getStyle();
        if (!isIdentity()) {
            // Map to device space and rasterize the same rounded shape there:
            // stroking the user-space quad would drop the corner radii.
            float[] a = new float[2], b = new float[2];
            mapPoint(left, top, a);
            mapPoint(right, bottom, b);
            float s = scaleFactor();
            float w = paint.getStrokeWidth() * s;
            int hw = w <= 1.5f ? 0 : Math.max(0, Math.round(w / 2f) - 1);
            drawRoundRectDevice(a[0], a[1], b[0], b[1], rx * s, ry * s, color, style, hw);
            return;
        }
        int l = Math.round(left);
        int t = Math.round(top);
        int r = Math.round(right);
        int b = Math.round(bottom);
        int radius = (int) Math.max(0, Math.min(rx, ry));
        if (style == Paint.Style.STROKE) {
            int rad = Math.min(radius, Math.min((r - l) / 2, (b - t) / 2));
            emitRect(l + rad, t, r - rad, t + 1, color);
            emitRect(l + rad, b - 1, r - rad, b, color);
            emitRect(l, t + rad, l + 1, b - rad, color);
            emitRect(r - 1, t + rad, r, b - rad, color);
            if (rad > 0) {
                arcOutline(l + rad, t + rad, rad, color, 0);
                arcOutline(r - rad - 1, t + rad, rad, color, 1);
                arcOutline(l + rad, b - rad - 1, rad, color, 2);
                arcOutline(r - rad - 1, b - rad - 1, rad, color, 3);
            }
            return;
        }
        int rad = Math.min(radius, Math.min((r - l) / 2, (b - t) / 2));
        emitRect(l + rad, t, r - rad, b, color);
        emitRect(l, t + rad, l + rad, b - rad, color);
        emitRect(r - rad, t + rad, r, b - rad, color);
        if (rad > 0) {
            disc(l + rad, t + rad, rad, color);
            disc(r - rad - 1, t + rad, rad, color);
            disc(l + rad, b - rad - 1, rad, color);
            disc(r - rad - 1, b - rad - 1, rad, color);
        }
    }

    /**
     * Device-space rounded-rect rasterizer shared by the transformed path.
     * Coordinates and radii arrive already mapped; device writes go straight
     * to the raster (no re-transform). Mirrors the identity branch below.
     */
    private void drawRoundRectDevice(float left, float top, float right, float bottom,
            float rx, float ry, int color, Paint.Style style, int hw) {
        if (right <= left || bottom <= top) return;
        int l = Math.round(left);
        int t = Math.round(top);
        int r = Math.round(right);
        int b = Math.round(bottom);
        int radius = (int) Math.max(0, Math.min(rx, ry));
        if (style == Paint.Style.STROKE) {
            int rad = Math.min(radius, Math.min((r - l) / 2, (b - t) / 2));
            paintRect(l + rad, t, r - rad, t + 1, color);
            paintRect(l + rad, b - 1, r - rad, b, color);
            paintRect(l, t + rad, l + 1, b - rad, color);
            paintRect(r - 1, t + rad, r, b - rad, color);
            if (rad > 0) {
                devArcOutline(l + rad, t + rad, rad, color, 0);
                devArcOutline(r - rad - 1, t + rad, rad, color, 1);
                devArcOutline(l + rad, b - rad - 1, rad, color, 2);
                devArcOutline(r - rad - 1, b - rad - 1, rad, color, 3);
            }
            return;
        }
        int rad = Math.min(radius, Math.min((r - l) / 2, (b - t) / 2));
        paintRect(l + rad, t, r - rad, b, color);
        paintRect(l, t + rad, l + rad, b - rad, color);
        paintRect(r - rad, t + rad, r, b - rad, color);
        if (rad > 0) {
            devDisc(l + rad, t + rad, rad, color);
            devDisc(r - rad - 1, t + rad, rad, color);
            devDisc(l + rad, b - rad - 1, rad, color);
            devDisc(r - rad - 1, b - rad - 1, rad, color);
        }
    }

    /** Device-space arc outline pixel plot (no transform). */
    private void devArcOutline(int cx, int cy, int radius, int color, int quadrant) {
        for (int a = 0; a <= 90; a += 2) {
            double rad = Math.toRadians(a);
            int dx = (int) Math.round(radius * Math.cos(rad));
            int dy = (int) Math.round(radius * Math.sin(rad));
            int x = quadrant == 0 || quadrant == 2 ? cx - dx : cx + dx;
            int y = quadrant < 2 ? cy - dy : cy + dy;
            paintPixel(x, y, color);
        }
    }

    /** Device-space filled disc (no transform). */
    private void devDisc(int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt((double) (radius * radius - dy * dy));
            paintRect(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** Filled disc helper for rounded corners (radius >= 0). */
    private void disc(int cx, int cy, int radius, int color) {
        for (int dy = -radius; dy <= radius; dy++) {
            int dx = (int) Math.sqrt((double) (radius * radius - dy * dy));
            emitRect(cx - dx, cy + dy, cx + dx + 1, cy + dy + 1, color);
        }
    }

    /** 1px quarter-arc outline; quadrant 0=TL 1=TR 2=BL 3=BR. */
    private void arcOutline(int cx, int cy, int radius, int color, int quadrant) {
        for (int a = 0; a <= 90; a += 2) {
            double rad = Math.toRadians(a);
            int dx = (int) Math.round(radius * Math.cos(rad));
            int dy = (int) Math.round(radius * Math.sin(rad));
            int x = quadrant == 0 || quadrant == 2 ? cx - dx : cx + dx;
            int y = quadrant < 2 ? cy - dy : cy + dy;
            emitPixel(x, y, color);
        }
    }

    /** Filled disc around (cx,cy). */
    public void drawCircle(int cx, int cy, int radius, int color) {
        emitEllipse(cx, cy, radius, radius, color);
    }

    public void drawLine(int x0, int y0, int x1, int y1, int color) {
        emitStroke(x0, y0, x1, y1, color, 0);
    }

    public void drawCircle(float cx, float cy, float radius, Paint paint) {
        if (paint == null || radius <= 0) return;
        int color = paintColor(paint);
        if (paint.getStyle() == Paint.Style.STROKE) {
            int steps = Math.max(24, (int) (radius * 6));
            int hw = halfWidth(paint);
            float px = cx + radius;
            float py = cy;
            for (int i = 1; i <= steps; i++) {
                double a = 2 * Math.PI * i / steps;
                float x = cx + radius * (float) Math.cos(a);
                float y = cy + radius * (float) Math.sin(a);
                emitStroke(px, py, x, y, color, hw);
                px = x;
                py = y;
            }
            return;
        }
        emitEllipse(cx, cy, radius, radius, color);
    }

    public void drawOval(RectF oval, Paint paint) {
        if (oval == null) return;
        drawOval(oval.left, oval.top, oval.right, oval.bottom, paint);
    }

    public void drawOval(float left, float top, float right, float bottom, Paint paint) {
        if (paint == null) return;
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        float rx = (right - left) / 2f;
        float ry = (bottom - top) / 2f;
        if (paint.getStyle() == Paint.Style.STROKE) {
            int steps = Math.max(24, (int) (Math.max(rx, ry) * 6));
            int hw = halfWidth(paint);
            float px = cx + rx;
            float py = cy;
            for (int i = 1; i <= steps; i++) {
                double a = 2 * Math.PI * i / steps;
                float x = cx + rx * (float) Math.cos(a);
                float y = cy + ry * (float) Math.sin(a);
                emitStroke(px, py, x, y, paintColor(paint), hw);
                px = x;
                py = y;
            }
            return;
        }
        emitEllipse(cx, cy, rx, ry, paintColor(paint));
    }

    public void drawArc(RectF oval, float startAngle, float sweepAngle,
            boolean useCenter, Paint paint) {
        if (oval == null) return;
        drawArc(oval.left, oval.top, oval.right, oval.bottom, startAngle, sweepAngle, useCenter, paint);
    }

    public void drawArc(float left, float top, float right, float bottom,
            float startAngle, float sweepAngle, boolean useCenter, Paint paint) {
        if (paint == null || sweepAngle == 0) return;
        int color = paintColor(paint);
        float cx = (left + right) / 2f;
        float cy = (top + bottom) / 2f;
        float rx = (right - left) / 2f;
        float ry = (bottom - top) / 2f;
        if (rx <= 0 || ry <= 0) return;
        int steps = Math.max(8, (int) (Math.abs(sweepAngle) / 4) + 1);
        if (paint.getStyle() == Paint.Style.STROKE) {
            int hw = halfWidth(paint);
            double a0 = Math.toRadians(startAngle);
            float px = cx + rx * (float) Math.cos(a0);
            float py = cy + ry * (float) Math.sin(a0);
            for (int i = 1; i <= steps; i++) {
                double a = Math.toRadians(startAngle + sweepAngle * i / steps);
                float x = cx + rx * (float) Math.cos(a);
                float y = cy + ry * (float) Math.sin(a);
                emitStroke(px, py, x, y, color, hw);
                px = x;
                py = y;
            }
            return;
        }
        float[] pts = new float[(steps + 2) * 2];
        int n = 0;
        for (int i = 0; i <= steps; i++) {
            double a = Math.toRadians(startAngle + sweepAngle * i / steps);
            pts[n++] = cx + rx * (float) Math.cos(a);
            pts[n++] = cy + ry * (float) Math.sin(a);
        }
        if (useCenter) {
            pts[n++] = cx;
            pts[n++] = cy;
        }
        emitPolygon(pts, n / 2, color);
    }

    public void drawLine(float x0, float y0, float x1, float y1, Paint paint) {
        if (paint == null) return;
        emitStroke(x0, y0, x1, y1, paintColor(paint), halfWidth(paint));
    }

    public void drawLines(float[] pts, Paint paint) {
        if (pts == null) return;
        drawLines(pts, 0, pts.length, paint);
    }

    public void drawLines(float[] pts, int offset, int count, Paint paint) {
        if (pts == null || paint == null) return;
        int end = Math.min(pts.length, offset + count);
        for (int i = offset; i + 3 < end; i += 4) {
            drawLine(pts[i], pts[i + 1], pts[i + 2], pts[i + 3], paint);
        }
    }

    public void drawPoint(float x, float y, Paint paint) {
        if (paint == null) return;
        emitPixel(x, y, paintColor(paint));
    }

    public void drawPoints(float[] pts, Paint paint) {
        if (pts == null) return;
        drawPoints(pts, 0, pts.length, paint);
    }

    public void drawPoints(float[] pts, int offset, int count, Paint paint) {
        if (pts == null || paint == null) return;
        int end = Math.min(pts.length, offset + count);
        for (int i = offset; i + 1 < end; i += 2) {
            drawPoint(pts[i], pts[i + 1], paint);
        }
    }

    /** Walk the recorded path, honoring the transform. */
    public void drawPath(Path path, Paint paint) {
        if (path == null || paint == null) return;
        List<Integer> verbs = path.verbs();
        List<Float> points = path.points();
        List<float[]> contours = new ArrayList<>();
        float[] current = new float[64];
        int n = 0;
        int pi = 0;
        float lastX = 0;
        float lastY = 0;
        float startX = 0;
        float startY = 0;
        for (int v : verbs) {
            switch (v) {
                case 0: {
                    if (n >= 2) contours.add(copyOf(current, n));
                    n = 0;
                    lastX = points.get(pi);
                    lastY = points.get(pi + 1);
                    pi += 2;
                    current = ensure(current, n + 2);
                    current[n++] = lastX;
                    current[n++] = lastY;
                    startX = lastX;
                    startY = lastY;
                    break;
                }
                case 1: {
                    lastX = points.get(pi);
                    lastY = points.get(pi + 1);
                    pi += 2;
                    current = ensure(current, n + 2);
                    current[n++] = lastX;
                    current[n++] = lastY;
                    break;
                }
                case 2: {
                    float x0 = lastX;
                    float y0 = lastY;
                    float qx = points.get(pi);
                    float qy = points.get(pi + 1);
                    lastX = points.get(pi + 2);
                    lastY = points.get(pi + 3);
                    pi += 4;
                    if (n > 0) {
                        current = ensure(current, n + 16);
                        for (int s = 1; s <= 8; s++) {
                            float tt = s / 8f;
                            float it = 1 - tt;
                            current[n++] = it * it * x0 + 2 * it * tt * qx + tt * tt * lastX;
                            current[n++] = it * it * y0 + 2 * it * tt * qy + tt * tt * lastY;
                        }
                    }
                    break;
                }
                case 3: {
                    float x0 = lastX;
                    float y0 = lastY;
                    float c1x = points.get(pi);
                    float c1y = points.get(pi + 1);
                    float c2x = points.get(pi + 2);
                    float c2y = points.get(pi + 3);
                    lastX = points.get(pi + 4);
                    lastY = points.get(pi + 5);
                    pi += 6;
                    if (n > 0) {
                        current = ensure(current, n + 24);
                        for (int s = 1; s <= 12; s++) {
                            float tt = s / 12f;
                            float it = 1 - tt;
                            current[n++] = it * it * it * x0 + 3 * it * it * tt * c1x
                                    + 3 * it * tt * tt * c2x + tt * tt * tt * lastX;
                            current[n++] = it * it * it * y0 + 3 * it * it * tt * c1y
                                    + 3 * it * tt * tt * c2y + tt * tt * tt * lastY;
                        }
                    }
                    break;
                }
                case 4: {
                    current = ensure(current, n + 2);
                    current[n++] = startX;
                    current[n++] = startY;
                    lastX = startX;
                    lastY = startY;
                    break;
                }
                default:
                    break;
            }
        }
        if (n >= 2) contours.add(copyOf(current, n));
        if (contours.isEmpty()) return;
        int color = paintColor(paint);
        Paint.Style style = paint.getStyle();
        if (style == Paint.Style.STROKE) {
            int hw = halfWidth(paint);
            for (float[] c : contours) {
                for (int i = 0; i + 3 < c.length; i += 2) {
                    emitStroke(c[i], c[i + 1], c[i + 2], c[i + 3], color, hw);
                }
            }
            return;
        }
        int total = 0;
        for (float[] c : contours) total += c.length;
        float[] all = new float[total];
        int at = 0;
        for (float[] c : contours) {
            System.arraycopy(c, 0, all, at, c.length);
            at += c.length;
        }
        emitPolygon(all, total / 2, color);
        if (style == Paint.Style.FILL_AND_STROKE) {
            int hw = halfWidth(paint);
            for (float[] c : contours) {
                for (int i = 0; i + 3 < c.length; i += 2) {
                    emitStroke(c[i], c[i + 1], c[i + 2], c[i + 3], color, hw);
                }
            }
        }
    }

    private static float[] ensure(float[] a, int size) {
        if (size <= a.length) return a;
        float[] bigger = new float[Math.max(size, a.length * 2)];
        System.arraycopy(a, 0, bigger, 0, a.length);
        return bigger;
    }

    private static float[] copyOf(float[] a, int len) {
        float[] out = new float[len];
        System.arraycopy(a, 0, out, 0, len);
        return out;
    }

    /** Fill the clip with the paint's color. */
    public void drawPaint(Paint paint) {
        if (paint == null) return;
        drawColor(paintColor(paint));
    }

    // ------------------------------------------------------------------- misc

    /** Picture replay is not modeled; pictures draw nothing. */
    public void drawPicture(Picture picture) {
    }

    public void drawPicture(Picture picture, Rect dst) {
    }

    public void drawPicture(Picture picture, RectF dst) {
    }

    /**
     * Replay a render node's recorded display list. The node transform
     * (position, translation, scale, rotation) is composed into each op;
     * node alpha folds into every color.
     */
    public void drawRenderNode(RenderNode node) {
        if (node == null) return;
        java.util.List<DisplayList.Op> ops = node.displayListOps();
        if (ops.isEmpty()) return;
        float[] nodeMatrix = node.renderMatrix();
        int alpha = Math.round(Math.max(0f, Math.min(1f, node.getAlpha())) * 255);
        for (DisplayList.Op op : ops) {
            op.draw(this, nodeMatrix, alpha);
        }
    }

    /** Render nodes need a compositor surface; recorded draws are skipped. */
    public void drawRenderNodePlaceholder(RenderNode node) {
    }

    public boolean quickReject(float left, float top, float right, float bottom, EdgeType type) {
        float l = left, t = top, r = right, b = bottom;
        if (!isIdentity()) {
            l = Float.MAX_VALUE;
            t = Float.MAX_VALUE;
            r = -Float.MAX_VALUE;
            b = -Float.MAX_VALUE;
            float[] p = new float[2];
            mapPoint(left, top, p);
            l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            mapPoint(right, top, p);
            l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            mapPoint(right, bottom, p);
            l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
            mapPoint(left, bottom, p);
            l = Math.min(l, p[0]); t = Math.min(t, p[1]); r = Math.max(r, p[0]); b = Math.max(b, p[1]);
        }
        return r <= clipLeft || l >= clipRight || b <= clipTop || t >= clipBottom;
    }

    public boolean quickReject(float left, float top, float right, float bottom) {
        return quickReject(left, top, right, bottom, EdgeType.BW);
    }

    public boolean quickReject(RectF rect, EdgeType type) {
        if (rect == null) return true;
        return quickReject(rect.left, rect.top, rect.right, rect.bottom, type);
    }

    public boolean quickReject(RectF rect) {
        return quickReject(rect, EdgeType.BW);
    }

    public boolean quickReject(Path path, EdgeType type) {
        if (path == null) return true;
        RectF b = new RectF();
        path.computeBounds(b, true);
        return quickReject(b, type);
    }

    public boolean quickReject(Path path) {
        return quickReject(path, EdgeType.BW);
    }

    /** Layer saves are approximated by a plain save (no offscreen buffer). */
    public int saveLayer(RectF bounds, Paint paint, int saveFlags) {
        return save();
    }

    public int saveLayer(RectF bounds, Paint paint) {
        return save();
    }

    public int saveLayer(float left, float top, float right, float bottom, Paint paint, int saveFlags) {
        return save();
    }

    public int saveLayer(float left, float top, float right, float bottom, Paint paint) {
        return save();
    }

    public int saveLayerAlpha(RectF bounds, int alpha, int saveFlags) {
        return save();
    }

    public int saveLayerAlpha(RectF bounds, int alpha) {
        return save();
    }

    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha, int saveFlags) {
        return save();
    }

    public int saveLayerAlpha(float left, float top, float right, float bottom, int alpha) {
        return save();
    }

    /** Isakai composites through the software renderer, so never accelerated. */
    public boolean isHardwareAccelerated() {
        return false;
    }

    /** Conservative: the backing store may hold translucent pixels. */
    public boolean isOpaque() {
        return false;
    }

    public int getDensity() {
        return density;
    }

    public void setDensity(int density) {
        this.density = density;
    }

    public DrawFilter getDrawFilter() {
        return drawFilter;
    }

    public void setDrawFilter(DrawFilter filter) {
        this.drawFilter = filter;
    }

    public int getMaximumBitmapWidth() {
        return 32766;
    }

    public int getMaximumBitmapHeight() {
        return 32767;
    }

    // ------------------------------------------------------------------ bitmaps

    /** Blit a bitmap scaled to [x,x+w) x [y,y+h), clipped. */
    public void drawBitmap(Bitmap bitmap, int x, int y, int w, int h) {
        drawBitmapFiltered(bitmap, x, y, w, h, null);
    }

    /** Internal software drawable path, with tint/filter applied before presentation. */
    public void drawBitmapFiltered(Bitmap bitmap, int x, int y, int w, int h,
            java.util.function.IntUnaryOperator transform) {
        emitBitmap(bitmap, 0, 0, bitmap == null ? 0 : bitmap.getWidth(),
                bitmap == null ? 0 : bitmap.getHeight(), x, y, x + w, y + h, transform);
    }

    public void drawBitmap(Bitmap bitmap, float x, float y, Paint paint) {
        if (bitmap == null) return;
        float w = bitmap.getWidth();
        float h = bitmap.getHeight();
        emitBitmap(bitmap, 0, 0, w, h, x, y, x + w, y + h);
    }

    public void drawBitmap(Bitmap bitmap, Rect src, Rect dst, Paint paint) {
        if (src == null || dst == null) return;
        emitBitmap(bitmap, src.left, src.top, src.right, src.bottom,
                dst.left, dst.top, dst.right, dst.bottom);
    }

    public void drawBitmap(Bitmap bitmap, Rect src, RectF dst, Paint paint) {
        if (src == null || dst == null) return;
        emitBitmap(bitmap, src.left, src.top, src.right, src.bottom,
                dst.left, dst.top, dst.right, dst.bottom);
    }

    public void drawBitmap(Bitmap bitmap, Matrix m, Paint paint) {
        if (bitmap == null) return;
        float[] saved = currentMatrix();
        if (m != null) {
            float[] o = new float[9];
            m.getValues(o);
            float[] r = new float[9];
            for (int row = 0; row < 3; row++) {
                for (int col = 0; col < 3; col++) {
                    r[row * 3 + col] = matrix[row * 3] * o[col]
                            + matrix[row * 3 + 1] * o[3 + col]
                            + matrix[row * 3 + 2] * o[6 + col];
                }
            }
            System.arraycopy(r, 0, matrix, 0, 9);
        }
        float w = bitmap.getWidth();
        float h = bitmap.getHeight();
        emitBitmap(bitmap, 0, 0, w, h, 0, 0, w, h);
        System.arraycopy(saved, 0, matrix, 0, 9);
    }

    public void drawBitmap(int[] colors, int offset, int stride, float x, float y,
            int width, int height, boolean hasAlpha, Paint paint) {
        if (colors == null || width <= 0 || height <= 0) return;
        int[] copy = new int[width * height];
        for (int row = 0; row < height; row++) {
            int src = offset + row * stride;
            if (src < 0 || src + width > colors.length) break;
            System.arraycopy(colors, src, copy, row * width, width);
        }
        Bitmap b = Bitmap.createBitmap(copy, width, height, Bitmap.Config.ARGB_8888);
        drawBitmap(b, x, y, paint);
    }

    public void drawBitmap(int[] colors, int offset, int stride, int x, int y,
            int width, int height, boolean hasAlpha, Paint paint) {
        drawBitmap(colors, offset, stride, (float) x, (float) y, width, height, hasAlpha, paint);
    }

    public void drawBitmapMesh(Bitmap bitmap, int meshWidth, int meshHeight, float[] verts,
            int vertOffset, int[] colors, int colorOffset, Paint paint) {
        if (bitmap == null || verts == null) return;
        int count = (meshWidth + 1) * (meshHeight + 1);
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE, r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (int i = 0; i < count; i++) {
            int vi = vertOffset + i * 2;
            if (vi + 1 >= verts.length) break;
            l = Math.min(l, verts[vi]);
            t = Math.min(t, verts[vi + 1]);
            r = Math.max(r, verts[vi]);
            b = Math.max(b, verts[vi + 1]);
        }
        if (r <= l || b <= t) return;
        emitBitmap(bitmap, 0, 0, bitmap.getWidth(), bitmap.getHeight(), l, t, r, b);
    }

    public void drawVertices(VertexMode mode, int vertexCount, float[] verts, int vertOffset,
            float[] texs, int texOffset, int[] colors, int colorOffset, short[] indices,
            int indexOffset, int indexCount, Paint paint) {
        // Vertex meshes are recorded but not rasterized by the software canvas.
    }

    /** Double rounded rect (text backgrounds): approximated by the outer round rect. */
    public void drawDoubleRoundRect(RectF outer, float[] outerRadii, RectF inner,
            float[] innerRadii, Paint paint) {
        if (outer == null || paint == null) return;
        float r = outerRadii != null ? outerRadii[0] : (innerRadii != null ? innerRadii[0] : 0);
        drawRoundRect(outer, r, r, paint);
    }

    public void drawDoubleRoundRect(RectF outer, float rx1, float ry1, RectF inner,
            float rx2, float ry2, Paint paint) {
        if (outer == null || paint == null) return;
        drawRoundRect(outer, rx1, ry1, paint);
    }

    // --------------------------------------------------------------------- text

    /**
     * Glyph runs arrive as font-internal ids with no text the host renderer
     * can shape; the pixel-font host draws text through {@link #drawText}.
     */
    public void drawGlyphs(int[] glyphIds, int glyphIdsOffset, float[] positions,
            int positionsOffset, int glyphCount, android.graphics.fonts.Font font, Paint paint) {
    }

    private void drawTextImpl(String text, float x, float y, Paint paint) {
        if (text == null || text.isEmpty() || paint == null) return;
        int color = paintColor(paint);
        float size = paint.getTextSize();
        if (size <= 0) size = 1;
        float ax = x;
        Paint.Align align = paint.getTextAlign();
        if (align == Paint.Align.CENTER) {
            ax = x - HostText.measure(text, size) / 2f;
        } else if (align == Paint.Align.RIGHT) {
            ax = x - HostText.measure(text, size);
        }
        emitText(text, ax, y, size, color);
    }

    public void drawText(String text, float x, float y, Paint paint) {
        drawTextImpl(text, x, y, paint);
    }

    public void drawText(String text, int start, int end, float x, float y, Paint paint) {
        if (text == null) return;
        int s = Math.max(0, start);
        int e = Math.min(text.length(), end);
        drawTextImpl(e <= s ? "" : text.substring(s, e), x, y, paint);
    }

    public void drawText(CharSequence text, int start, int end, float x, float y, Paint paint) {
        if (text == null) return;
        int s = Math.max(0, start);
        int e = Math.min(text.length(), end);
        drawTextImpl(e <= s ? "" : text.subSequence(s, e).toString(), x, y, paint);
    }

    public void drawText(char[] text, int index, int count, float x, float y, Paint paint) {
        if (text == null) return;
        int i = Math.max(0, index);
        int c = Math.min(count, text.length - i);
        drawTextImpl(c <= 0 ? "" : new String(text, i, c), x, y, paint);
    }

    public void drawTextRun(char[] text, int index, int count, int contextIndex,
            int contextCount, float x, float y, boolean isRtl, Paint paint) {
        drawText(text, index, count, x, y, paint);
    }

    public void drawTextRun(CharSequence text, int start, int end, int contextStart,
            int contextEnd, float x, float y, boolean isRtl, Paint paint) {
        drawText(text, start, end, x, y, paint);
    }

    public void drawPosText(String text, float[] pos, Paint paint) {
        if (text == null || pos == null || paint == null) return;
        for (int i = 0; i < text.length(); i++) {
            if (i * 2 + 1 >= pos.length) break;
            drawText(String.valueOf(text.charAt(i)), pos[i * 2], pos[i * 2 + 1], paint);
        }
    }

    public void drawPosText(char[] text, int index, int count, float[] pos, Paint paint) {
        if (text == null || pos == null) return;
        int i = Math.max(0, index);
        int c = Math.min(count, text.length - i);
        for (int k = 0; k < c; k++) {
            if (k * 2 + 1 >= pos.length) break;
            drawText(String.valueOf(text[i + k]), pos[k * 2], pos[k * 2 + 1], paint);
        }
    }

    public void drawTextOnPath(String text, Path path, float hOffset, float vOffset, Paint paint) {
        if (text == null || path == null || paint == null) return;
        RectF b = new RectF();
        path.computeBounds(b, true);
        drawText(text, b.left + hOffset, b.bottom - vOffset, paint);
    }

    public void drawTextOnPath(char[] text, int index, int count, Path path,
            float hOffset, float vOffset, Paint paint) {
        if (text == null) return;
        int i = Math.max(0, index);
        int c = Math.min(count, text.length - i);
        drawTextOnPath(c <= 0 ? "" : new String(text, i, c), path, hOffset, vOffset, paint);
    }

    /**
     * Draw text with the built-in {@link BitmapFont}. {@code size} is the
     * target cap height in pixels. Supports '\n' (moves to the next line,
     * x resets).
     */
    public void drawText(String text, int x, int y, int size, int color) {
        emitText(text, x, y, size, color);
    }

    /**
     * Width in pixels of {@code text} at {@code size}, using the widest
     * '\n'-separated line. Same scale rule as {@link #drawText}.
     */
    public static int measureText(String text, int size) {
        return (int) Math.ceil(HostText.measure(text, size));
    }

    private void drawGlyph(int codePoint, int x, int y, int scale, int color) {
        for (int row = 0; row < BitmapFont.HEIGHT; row++) {
            for (int col = 0; col < BitmapFont.WIDTH; col++) {
                if (BitmapFont.pixel(codePoint, col, row)) {
                    emitRect(x + col * scale, y + row * scale,
                            x + col * scale + scale, y + row * scale + scale, color);
                }
            }
        }
    }

    /**
     * Draw a nine-patch bitmap into {@code dst} (patch edges are not
     * stretched; the bitmap scales as one image).
     */
    public void drawPatch(NinePatch patch, Rect dst, Paint paint) {
        if (patch == null || dst == null) return;
        Bitmap b = patch.getBitmap();
        if (b == null) return;
        emitBitmap(b, 0, 0, b.getWidth(), b.getHeight(), dst.left, dst.top, dst.right, dst.bottom);
    }

    public void drawPatch(NinePatch patch, RectF dst, Paint paint) {
        if (dst == null) return;
        drawPatch(patch, new Rect((int) dst.left, (int) dst.top, (int) dst.right, (int) dst.bottom), paint);
    }
}
