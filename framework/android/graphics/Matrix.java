// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/**
 * 3x3 affine matrix (row-major float[9]) with the android.graphics.Matrix
 * API surface. Perspective entries are stored but mapPoints treats input
 * as 2D points (matches the common affine use; full perspective divide
 * applies when the last row differs from [0 0 1]).
 */
public class Matrix {
    public static final int MSCALE_X = 0;
    public static final int MSKEW_X = 1;
    public static final int MTRANS_X = 2;
    public static final int MSKEW_Y = 3;
    public static final int MSCALE_Y = 4;
    public static final int MTRANS_Y = 5;
    public static final int MPERSP_0 = 6;
    public static final int MPERSP_1 = 7;
    public static final int MPERSP_2 = 8;

    private final float[] m = new float[9];

    public Matrix() {
        reset();
    }

    public Matrix(Matrix src) {
        set(src);
    }

    public void reset() {
        m[MSCALE_X] = 1; m[MSKEW_X] = 0; m[MTRANS_X] = 0;
        m[MSKEW_Y] = 0; m[MSCALE_Y] = 1; m[MTRANS_Y] = 0;
        m[MPERSP_0] = 0; m[MPERSP_1] = 0; m[MPERSP_2] = 1;
    }

    public void set(Matrix src) {
        if (src == null) {
            reset();
        } else {
            System.arraycopy(src.m, 0, m, 0, 9);
        }
    }

    public boolean isIdentity() {
        return m[MSCALE_X] == 1 && m[MSKEW_X] == 0 && m[MTRANS_X] == 0
                && m[MSKEW_Y] == 0 && m[MSCALE_Y] == 1 && m[MTRANS_Y] == 0
                && m[MPERSP_0] == 0 && m[MPERSP_1] == 0 && m[MPERSP_2] == 1;
    }

    public boolean isAffine() {
        return m[MPERSP_0] == 0 && m[MPERSP_1] == 0 && m[MPERSP_2] == 1;
    }

    public boolean rectStaysRect() {
        return (m[MSKEW_X] == 0 && m[MSKEW_Y] == 0)
                || (m[MSCALE_X] == 0 && m[MSCALE_Y] == 0);
    }

    public void getValues(float[] values) {
        if (values != null && values.length >= 9) System.arraycopy(m, 0, values, 0, 9);
    }

    public void setValues(float[] values) {
        if (values != null && values.length >= 9) System.arraycopy(values, 0, m, 0, 9);
    }

    private void multiply(float[] a) {
        float[] r = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] = m[row * 3] * a[col]
                        + m[row * 3 + 1] * a[3 + col]
                        + m[row * 3 + 2] * a[6 + col];
            }
        }
        System.arraycopy(r, 0, m, 0, 9);
    }

    private void multiplyLeft(float[] a) {
        float[] r = new float[9];
        for (int row = 0; row < 3; row++) {
            for (int col = 0; col < 3; col++) {
                r[row * 3 + col] = a[row * 3] * m[col]
                        + a[row * 3 + 1] * m[3 + col]
                        + a[row * 3 + 2] * m[6 + col];
            }
        }
        System.arraycopy(r, 0, m, 0, 9);
    }

    private static float[] translation(float dx, float dy) {
        return new float[]{1, 0, dx, 0, 1, dy, 0, 0, 1};
    }

    private static float[] scaling(float sx, float sy, float px, float py) {
        return new float[]{sx, 0, px - sx * px, 0, sy, py - sy * py, 0, 0, 1};
    }

    private static float[] rotation(float degrees, float px, float py) {
        double rad = Math.toRadians(degrees);
        float cos = (float) Math.cos(rad);
        float sin = (float) Math.sin(rad);
        return new float[]{cos, -sin, px - cos * px + sin * py,
                sin, cos, py - sin * px - cos * py, 0, 0, 1};
    }

    public void setTranslate(float dx, float dy) {
        reset();
        m[MTRANS_X] = dx;
        m[MTRANS_Y] = dy;
    }

    public void setScale(float sx, float sy, float px, float py) {
        System.arraycopy(scaling(sx, sy, px, py), 0, m, 0, 9);
    }

    public void setScale(float sx, float sy) {
        setScale(sx, sy, 0, 0);
    }

    public boolean preTranslate(float dx, float dy) {
        multiplyLeft(translation(dx, dy));
        return true;
    }

    public boolean preScale(float sx, float sy, float px, float py) {
        multiplyLeft(scaling(sx, sy, px, py));
        return true;
    }

    public boolean preScale(float sx, float sy) {
        return preScale(sx, sy, 0, 0);
    }

    public boolean preRotate(float degrees, float px, float py) {
        multiplyLeft(rotation(degrees, px, py));
        return true;
    }

    public boolean preRotate(float degrees) {
        return preRotate(degrees, 0, 0);
    }

    public boolean preConcat(Matrix other) {
        if (other != null) multiplyLeft(other.m);
        return true;
    }

    public boolean postTranslate(float dx, float dy) {
        multiply(translation(dx, dy));
        return true;
    }

    public boolean postScale(float sx, float sy, float px, float py) {
        multiply(scaling(sx, sy, px, py));
        return true;
    }

    public boolean postScale(float sx, float sy) {
        return postScale(sx, sy, 0, 0);
    }

    public boolean postRotate(float degrees, float px, float py) {
        multiply(rotation(degrees, px, py));
        return true;
    }

    public boolean postRotate(float degrees) {
        return postRotate(degrees, 0, 0);
    }

    public boolean postConcat(Matrix other) {
        if (other != null) multiply(other.m);
        return true;
    }

    public boolean invert(Matrix inverse) {
        float det = m[MSCALE_X] * (m[MSCALE_Y] * m[MPERSP_2] - m[MTRANS_Y] * m[MPERSP_1])
                - m[MSKEW_X] * (m[MSKEW_Y] * m[MPERSP_2] - m[MTRANS_Y] * m[MPERSP_0])
                + m[MTRANS_X] * (m[MSKEW_Y] * m[MPERSP_1] - m[MSCALE_Y] * m[MPERSP_0]);
        if (det == 0) return false;
        float inv = 1.0f / det;
        float[] r = new float[]{
                (m[MSCALE_Y] * m[MPERSP_2] - m[MTRANS_Y] * m[MPERSP_1]) * inv,
                (m[MTRANS_X] * m[MPERSP_1] - m[MSKEW_X] * m[MPERSP_2]) * inv,
                (m[MSKEW_X] * m[MTRANS_Y] - m[MTRANS_X] * m[MSCALE_Y]) * inv,
                (m[MTRANS_Y] * m[MPERSP_0] - m[MSKEW_Y] * m[MPERSP_2]) * inv,
                (m[MSCALE_X] * m[MPERSP_2] - m[MTRANS_X] * m[MPERSP_0]) * inv,
                (m[MTRANS_X] * m[MSKEW_Y] - m[MSCALE_X] * m[MTRANS_Y]) * inv,
                (m[MSKEW_Y] * m[MPERSP_1] - m[MSCALE_Y] * m[MPERSP_0]) * inv,
                (m[MSKEW_X] * m[MPERSP_0] - m[MSCALE_X] * m[MPERSP_1]) * inv,
                (m[MSCALE_X] * m[MSCALE_Y] - m[MSKEW_X] * m[MSKEW_Y]) * inv,
        };
        if (inverse != null) System.arraycopy(r, 0, inverse.m, 0, 9);
        return true;
    }

    public void mapPoints(float[] pts) {
        if (pts != null) mapPoints(pts, 0, pts, 0, pts.length / 2);
    }

    public void mapPoints(float[] dst, float[] src) {
        if (dst != null && src != null) mapPoints(dst, 0, src, 0, Math.min(dst.length, src.length) / 2);
    }

    public void mapPoints(float[] dst, int dstIndex, float[] src, int srcIndex, int pointCount) {
        for (int i = 0; i < pointCount; i++) {
            float x = src[srcIndex + i * 2];
            float y = src[srcIndex + i * 2 + 1];
            float w = m[MPERSP_0] * x + m[MPERSP_1] * y + m[MPERSP_2];
            float nx = m[MSCALE_X] * x + m[MSKEW_X] * y + m[MTRANS_X];
            float ny = m[MSKEW_Y] * x + m[MSCALE_Y] * y + m[MTRANS_Y];
            if (w != 0 && w != 1) {
                nx /= w;
                ny /= w;
            }
            dst[dstIndex + i * 2] = nx;
            dst[dstIndex + i * 2 + 1] = ny;
        }
    }

    public void mapVectors(float[] vecs) {
        if (vecs != null) mapVectors(vecs, 0, vecs, 0, vecs.length / 2);
    }

    public void mapVectors(float[] dst, float[] src) {
        if (dst != null && src != null) mapVectors(dst, 0, src, 0, Math.min(dst.length, src.length) / 2);
    }

    public void mapVectors(float[] dst, int dstIndex, float[] src, int srcIndex, int vectorCount) {
        for (int i = 0; i < vectorCount; i++) {
            float x = src[srcIndex + i * 2];
            float y = src[srcIndex + i * 2 + 1];
            dst[dstIndex + i * 2] = m[MSCALE_X] * x + m[MSKEW_X] * y;
            dst[dstIndex + i * 2 + 1] = m[MSKEW_Y] * x + m[MSCALE_Y] * y;
        }
    }

    public boolean mapRect(RectF dst, RectF src) {
        if (dst == null || src == null) return false;
        float[] pts = {src.left, src.top, src.right, src.top, src.right, src.bottom, src.left, src.bottom};
        mapPoints(pts);
        float l = Math.min(Math.min(pts[0], pts[2]), Math.min(pts[4], pts[6]));
        float t = Math.min(Math.min(pts[1], pts[3]), Math.min(pts[5], pts[7]));
        float r = Math.max(Math.max(pts[0], pts[2]), Math.max(pts[4], pts[6]));
        float b = Math.max(Math.max(pts[1], pts[3]), Math.max(pts[5], pts[7]));
        dst.set(l, t, r, b);
        return true;
    }

    public boolean mapRect(RectF rect) {
        return rect != null && mapRect(rect, rect);
    }

    public float mapRadius(float radius) {
        float sx = (float) Math.hypot(m[MSCALE_X], m[MSKEW_Y]);
        float sy = (float) Math.hypot(m[MSKEW_X], m[MSCALE_Y]);
        return radius * (float) Math.sqrt(sx * sy);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Matrix)) return false;
        Matrix other = (Matrix) o;
        for (int i = 0; i < 9; i++) if (m[i] != other.m[i]) return false;
        return true;
    }

    @Override
    public int hashCode() {
        int h = 0;
        for (float v : m) h = h * 31 + Float.floatToIntBits(v);
        return h;
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Matrix{");
        for (int i = 0; i < 9; i++) {
            if (i > 0) sb.append(", ");
            sb.append(m[i]);
        }
        return sb.append('}').toString();
    }
}
