// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.util.ArrayList;
import java.util.List;

/**
 * Path recorder: stores verbs plus points so bounds/measure/transform
 * work; boolean ops and rendering are unsupported.
 */
public class Path {
    public enum Direction { CW, CCW }
    public enum FillType { WINDING, EVEN_ODD, INVERSE_WINDING, INVERSE_EVEN_ODD }
    public enum Op { DIFFERENCE, REVERSE_DIFFERENCE, INTERSECT, UNION, XOR }

    private static final int MOVE = 0;
    private static final int LINE = 1;
    private static final int QUAD = 2;
    private static final int CUBIC = 3;
    private static final int CLOSE = 4;

    private final List<Integer> verbs = new ArrayList<>();
    private final List<Float> points = new ArrayList<>();
    private FillType fillType = FillType.WINDING;
    private float lastX;
    private float lastY;

    public Path() {}

    public Path(Path src) {
        if (src != null) {
            verbs.addAll(src.verbs);
            points.addAll(src.points);
            fillType = src.fillType;
            lastX = src.lastX;
            lastY = src.lastY;
        }
    }

    public void reset() {
        verbs.clear();
        points.clear();
        lastX = 0;
        lastY = 0;
    }

    public void rewind() {
        reset();
    }

    public boolean isEmpty() { return verbs.isEmpty(); }

    public FillType getFillType() { return fillType; }
    public void setFillType(FillType ft) { if (ft != null) fillType = ft; }

    private void add(int verb, float... pts) {
        verbs.add(verb);
        for (float p : pts) points.add(p);
    }

    public void moveTo(float x, float y) {
        add(MOVE, x, y);
        lastX = x;
        lastY = y;
    }

    public void rMoveTo(float dx, float dy) { moveTo(lastX + dx, lastY + dy); }

    public void lineTo(float x, float y) {
        add(LINE, x, y);
        lastX = x;
        lastY = y;
    }

    public void rLineTo(float dx, float dy) { lineTo(lastX + dx, lastY + dy); }

    public void quadTo(float x1, float y1, float x2, float y2) {
        add(QUAD, x1, y1, x2, y2);
        lastX = x2;
        lastY = y2;
    }

    public void rQuadTo(float dx1, float dy1, float dx2, float dy2) {
        quadTo(lastX + dx1, lastY + dy1, lastX + dx2, lastY + dy2);
    }

    public void cubicTo(float x1, float y1, float x2, float y2, float x3, float y3) {
        add(CUBIC, x1, y1, x2, y2, x3, y3);
        lastX = x3;
        lastY = y3;
    }

    public void rCubicTo(float dx1, float dy1, float dx2, float dy2, float dx3, float dy3) {
        cubicTo(lastX + dx1, lastY + dy1, lastX + dx2, lastY + dy2, lastX + dx3, lastY + dy3);
    }

    public void arcTo(RectF oval, float startAngle, float sweepAngle) {
        arcTo(oval, startAngle, sweepAngle, false);
    }

    public void arcTo(RectF oval, float startAngle, float sweepAngle, boolean forceMoveTo) {
        if (oval == null) return;
        float cx = oval.centerX();
        float cy = oval.centerY();
        float rx = oval.width() / 2.0f;
        float ry = oval.height() / 2.0f;
        int steps = Math.max(1, (int) (Math.abs(sweepAngle) / 15));
        for (int i = forceMoveTo || verbs.isEmpty() ? 0 : 1; i <= steps; i++) {
            double a = Math.toRadians(startAngle + sweepAngle * i / steps);
            float x = cx + rx * (float) Math.cos(a);
            float y = cy + ry * (float) Math.sin(a);
            if (i == 0) moveTo(x, y);
            else lineTo(x, y);
        }
    }

    public void addRect(RectF rect, Direction dir) {
        if (rect == null) return;
        moveTo(rect.left, rect.top);
        lineTo(rect.right, rect.top);
        lineTo(rect.right, rect.bottom);
        lineTo(rect.left, rect.bottom);
        close();
    }

    public void addPath(Path src) {
        if (src == null) return;
        verbs.addAll(src.verbs);
        points.addAll(src.points);
        lastX = src.lastX;
        lastY = src.lastY;
    }

    public void addPath(Path src, Matrix matrix) {
        if (src == null) return;
        Path tmp = new Path(src);
        if (matrix != null) tmp.transform(matrix);
        addPath(tmp);
    }

    public void close() {
        add(CLOSE);
    }

    public void transform(Matrix matrix) {
        if (matrix == null || points.isEmpty()) return;
        float[] pts = new float[points.size()];
        for (int i = 0; i < pts.length; i++) pts[i] = points.get(i);
        matrix.mapPoints(pts);
        for (int i = 0; i < pts.length; i++) points.set(i, pts[i]);
    }

    public void computeBounds(RectF bounds, boolean exact) {
        if (bounds == null) return;
        bounds.setEmpty();
        if (points.isEmpty()) return;
        float l = Float.MAX_VALUE, t = Float.MAX_VALUE;
        float r = -Float.MAX_VALUE, b = -Float.MAX_VALUE;
        for (int i = 0; i + 1 < points.size(); i += 2) {
            float x = points.get(i);
            float y = points.get(i + 1);
            if (x < l) l = x;
            if (y < t) t = y;
            if (x > r) r = x;
            if (y > b) b = y;
        }
        bounds.set(l, t, r, b);
    }

    public boolean op(Path path, Op op) { return false; }
    public boolean op(Path path1, Path path2, Op op) { return false; }

    public boolean isConvex() { return false; }

    List<Integer> verbs() { return verbs; }
    List<Float> points() { return points; }
}
