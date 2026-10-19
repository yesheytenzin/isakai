// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.util.function.IntUnaryOperator;

/**
 * Recording canvas: supplements {@link Canvas} by recording display ops
 * instead of rasterizing. Each op carries the coordinate frame it was
 * drawn in, so {@link Canvas#drawRenderNode} can replay the list under the
 * render node's transform.
 */
public class RecordingCanvas extends Canvas {
    private final DisplayList list;

    /** Record into {@code list} with a logical frame of {@code width x height}. */
    public RecordingCanvas(DisplayList list, int width, int height) {
        super(0, 0, true);
        setLogicalSize(width, height);
        if (list == null) throw new NullPointerException("list");
        this.list = list;
    }

    public RecordingCanvas(DisplayList list) {
        this(list, 0, 0);
    }

    @Override
    protected void emitPixel(float x, float y, int color) {
        list.ops().add(new DisplayList.PixelOp(this, x, y, color));
    }

    @Override
    protected void emitRect(float left, float top, float right, float bottom, int color) {
        if (right < left || bottom < top) return;
        list.ops().add(new DisplayList.RectOp(this, left, top, right, bottom, color));
    }

    @Override
    protected void emitPolygon(float[] pts, int count, int color) {
        if (pts == null || count < 3) return;
        list.ops().add(new DisplayList.PolygonOp(this, pts, count, color));
    }

    @Override
    protected void emitStroke(float x0, float y0, float x1, float y1, int color, int halfWidth) {
        list.ops().add(new DisplayList.StrokeOp(this, x0, y0, x1, y1, color, halfWidth));
    }

    @Override
    protected void emitEllipse(float cx, float cy, float rx, float ry, int color) {
        list.ops().add(new DisplayList.EllipseOp(this, cx, cy, rx, ry, color));
    }

    @Override
    protected void emitBitmap(Bitmap bitmap, float sx0, float sy0, float sx1, float sy1,
            float dx0, float dy0, float dx1, float dy1, IntUnaryOperator transform) {
        if (bitmap == null || bitmap.isRecycled()) return;
        Bitmap src = bitmap;
        if (transform != null) {
            int w = bitmap.getWidth();
            int h = bitmap.getHeight();
            int[] px = new int[w * h];
            bitmap.getPixels(px, 0, w, 0, 0, w, h);
            for (int i = 0; i < px.length; i++) px[i] = transform.applyAsInt(px[i]);
            src = Bitmap.createBitmap(px, w, h, bitmap.getConfig());
        }
        list.ops().add(new DisplayList.BitmapOp(this, src, sx0, sy0, sx1, sy1, dx0, dy0, dx1, dy1));
    }

    @Override
    protected void emitText(String text, float x, float y, float size, int color) {
        if (text == null || text.isEmpty()) return;
        list.ops().add(new DisplayList.TextOp(this, text, x, y, size, color));
    }

    /** Frame-wide fill: recorded as a rect spanning the logical frame. */
    @Override
    public void drawColor(int color) {
        emitRect(0, 0, getWidth(), getHeight(), color);
    }

    @Override
    public int getPixel(int x, int y) {
        return 0;
    }
}
