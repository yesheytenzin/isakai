// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Deferred drawing for {@link RenderNode}: the compose software path records
 * display ops into this list through a {@link RecordingCanvas} and replays
 * them into the real canvas with the node's transform.
 *
 * <p>Each op carries the coordinate frame (matrix + clip) it was recorded in,
 * so replay composes the node transform on top. Coordinates stay in user
 * space; replay maps them by {@code nodeMatrix * recordedMatrix}.
 */
public final class DisplayList {
    /** A recorded op, replayed into a raster canvas. */
    public abstract static class Op {
        final float[] matrix;
        final int clipLeft;
        final int clipTop;
        final int clipRight;
        final int clipBottom;

        Op(Canvas canvas) {
            this.matrix = canvas.currentMatrix();
            this.clipLeft = canvas.clipLeft();
            this.clipTop = canvas.clipTop();
            this.clipRight = canvas.clipRight();
            this.clipBottom = canvas.clipBottom();
        }

        /** Draw into {@code target}, placing the recorded frame under the node transform. */
        public abstract void draw(Canvas target, float[] nodeMatrix, int alpha255);

        /** Push the recorded clip, mapped through the combined transform. */
        static void pushClip(Canvas target, float[] combined, int l, int t, int r, int b) {
            float[] out = new float[8];
            float[] p = new float[2];
            mapPoint(combined, l, t, p);
            out[0] = p[0];
            out[1] = p[1];
            mapPoint(combined, r, t, p);
            out[2] = p[0];
            out[3] = p[1];
            mapPoint(combined, r, b, p);
            out[4] = p[0];
            out[5] = p[1];
            mapPoint(combined, l, b, p);
            out[6] = p[0];
            out[7] = p[1];
            float left = Math.min(Math.min(out[0], out[2]), Math.min(out[4], out[6]));
            float top = Math.min(Math.min(out[1], out[3]), Math.min(out[5], out[7]));
            float right = Math.max(Math.max(out[0], out[2]), Math.max(out[4], out[6]));
            float bottom = Math.max(Math.max(out[1], out[3]), Math.max(out[5], out[7]));
            target.clipRect(left, top, right, bottom);
        }
    }

    private final List<Op> ops = new ArrayList<>();

    List<Op> ops() {
        return ops;
    }

    public boolean isEmpty() {
        return ops.isEmpty();
    }

    public void clear() {
        ops.clear();
    }

    public int size() {
        return ops.size();
    }

    static float[] composite(float[] a, float[] b) {
        float[] r = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] = a[row * 3] * b[col]
                        + a[row * 3 + 1] * b[3 + col]
                        + a[row * 3 + 2] * b[6 + col];
            }
        }
        return r;
    }

    static void mapPoint(float[] m, float x, float y, float[] out) {
        float w = m[6] * x + m[7] * y + m[8];
        if (w == 0 || w != w) w = 1;
        out[0] = (m[0] * x + m[1] * y + m[2]) / w;
        out[1] = (m[3] * x + m[4] * y + m[5]) / w;
    }

    static void mapPoints(float[] m, float[] pts) {
        float[] tmp = new float[2];
        for (int i = 0; i + 1 < pts.length; i += 2) {
            mapPoint(m, pts[i], pts[i + 1], tmp);
            pts[i] = tmp[0];
            pts[i + 1] = tmp[1];
        }
    }

    static int withAlpha(int color, int alpha255) {
        int a = (color >>> 24) * alpha255 / 255;
        return (color & 0x00FFFFFF) | (a << 24);
    }

    static float scaleOf(float[] m) {
        float sx = (float) Math.hypot(m[0], m[3]);
        float sy = (float) Math.hypot(m[1], m[4]);
        float s = (float) Math.sqrt(Math.max(0.0001f, sx * sy));
        return Float.isNaN(s) || s <= 0 ? 1f : s;
    }

    static void mapCorners(float[] m, float l, float t, float r, float b, float[] out) {
        float[] p = new float[2];
        mapPoint(m, l, t, p);
        out[0] = p[0];
        out[1] = p[1];
        mapPoint(m, r, t, p);
        out[2] = p[0];
        out[3] = p[1];
        mapPoint(m, r, b, p);
        out[4] = p[0];
        out[5] = p[1];
        mapPoint(m, l, b, p);
        out[6] = p[0];
        out[7] = p[1];
    }

    static final class RectOp extends Op {
        private final float l, t, r, b;
        private final int color;

        RectOp(Canvas canvas, float l, float t, float r, float b, int color) {
            super(canvas);
            this.l = l;
            this.t = t;
            this.r = r;
            this.b = b;
            this.color = color;
        }

        @Override
        public void draw(Canvas target, float[] nodeMatrix, int alpha255) {
            float[] m = composite(nodeMatrix, matrix);
            float[] out = new float[8];
            mapCorners(m, l, t, r, b, out);
            int color = withAlpha(this.color, alpha255);
            target.save();
            pushClip(target, m, clipLeft, clipTop, clipRight, clipBottom);
            target.paintPolygon(out, 4, color);
            target.restore();
        }
    }

    static final class PolygonOp extends Op {
        private final float[] pts;
        private final int count;
        private final int color;

        PolygonOp(Canvas canvas, float[] pts, int count, int color) {
            super(canvas);
            this.pts = pts;
            this.count = count;
            this.color = color;
        }

        @Override
        public void draw(Canvas target, float[] nodeMatrix, int alpha255) {
            float[] m = composite(nodeMatrix, matrix);
            float[] mapped = pts.clone();
            mapPoints(m, mapped);
            int color = withAlpha(this.color, alpha255);
            target.save();
            pushClip(target, m, clipLeft, clipTop, clipRight, clipBottom);
            target.paintPolygon(mapped, count, color);
            target.restore();
        }
    }

    static final class PixelOp extends Op {
        private final float x, y;
        private final int color;

        PixelOp(Canvas canvas, float x, float y, int color) {
            super(canvas);
            this.x = x;
            this.y = y;
            this.color = color;
        }

        @Override
        public void draw(Canvas target, float[] nodeMatrix, int alpha255) {
            float[] m = composite(nodeMatrix, matrix);
            float[] out = new float[2];
            mapPoint(m, x, y, out);
            target.save();
            pushClip(target, m, clipLeft, clipTop, clipRight, clipBottom);
            target.paintPixel(Math.round(out[0]), Math.round(out[1]), withAlpha(color, alpha255));
            target.restore();
        }
    }

    static final class StrokeOp extends Op {
        private final float x0, y0, x1, y1;
        private final int color;
        private final int halfWidth;

        StrokeOp(Canvas canvas, float x0, float y0, float x1, float y1, int color, int halfWidth) {
            super(canvas);
            this.x0 = x0;
            this.y0 = y0;
            this.x1 = x1;
            this.y1 = y1;
            this.color = color;
            this.halfWidth = halfWidth;
        }

        @Override
        public void draw(Canvas target, float[] nodeMatrix, int alpha255) {
            float[] m = composite(nodeMatrix, matrix);
            float[] a = new float[2];
            float[] b = new float[2];
            mapPoint(m, x0, y0, a);
            mapPoint(m, x1, y1, b);
            target.save();
            pushClip(target, m, clipLeft, clipTop, clipRight, clipBottom);
            target.paintStroke(a[0], a[1], b[0], b[1], withAlpha(color, alpha255), halfWidth);
            target.restore();
        }
    }

    static final class EllipseOp extends Op {
        private final float cx, cy, rx, ry;
        private final int color;

        EllipseOp(Canvas canvas, float cx, float cy, float rx, float ry, int color) {
            super(canvas);
            this.cx = cx;
            this.cy = cy;
            this.rx = rx;
            this.ry = ry;
            this.color = color;
        }

        @Override
        public void draw(Canvas target, float[] nodeMatrix, int alpha255) {
            float[] m = composite(nodeMatrix, matrix);
            float[] c = new float[2];
            mapPoint(m, cx, cy, c);
            float scale = scaleOf(m);
            target.save();
            pushClip(target, m, clipLeft, clipTop, clipRight, clipBottom);
            target.paintEllipse(c[0], c[1], rx * scale, ry * scale, withAlpha(color, alpha255));
            target.restore();
        }
    }

    static final class BitmapOp extends Op {
        private final Bitmap bitmap;
        private final float sx0, sy0, sx1, sy1;
        private final float dx0, dy0, dx1, dy1;

        BitmapOp(Canvas canvas, Bitmap bitmap, float sx0, float sy0, float sx1, float sy1,
                float dx0, float dy0, float dx1, float dy1) {
            super(canvas);
            this.bitmap = bitmap;
            this.sx0 = sx0;
            this.sy0 = sy0;
            this.sx1 = sx1;
            this.sy1 = sy1;
            this.dx0 = dx0;
            this.dy0 = dy0;
            this.dx1 = dx1;
            this.dy1 = dy1;
        }

        @Override
        public void draw(Canvas target, float[] nodeMatrix, int alpha255) {
            float[] m = composite(nodeMatrix, matrix);
            float[] out = new float[8];
            mapCorners(m, dx0, dy0, dx1, dy1, out);
            float left = Math.min(Math.min(out[0], out[2]), Math.min(out[4], out[6]));
            float top = Math.min(Math.min(out[1], out[3]), Math.min(out[5], out[7]));
            float right = Math.max(Math.max(out[0], out[2]), Math.max(out[4], out[6]));
            float bottom = Math.max(Math.max(out[1], out[3]), Math.max(out[5], out[7]));
            target.save();
            pushClip(target, m, clipLeft, clipTop, clipRight, clipBottom);
            target.paintBitmap(bitmap, sx0, sy0, sx1, sy1, left, top, right, bottom, alpha255, null);
            target.restore();
        }
    }

    static final class TextOp extends Op {
        private final String text;
        private final float x, y, size;
        private final int color;

        TextOp(Canvas canvas, String text, float x, float y, float size, int color) {
            super(canvas);
            this.text = text;
            this.x = x;
            this.y = y;
            this.size = size;
            this.color = color;
        }

        @Override
        public void draw(Canvas target, float[] nodeMatrix, int alpha255) {
            float[] m = composite(nodeMatrix, matrix);
            float[] out = new float[2];
            mapPoint(m, x, y, out);
            target.save();
            pushClip(target, m, clipLeft, clipTop, clipRight, clipBottom);
            target.paintText(text, out[0], out[1], size * scaleOf(m), withAlpha(color, alpha255));
            target.restore();
        }
    }
}
