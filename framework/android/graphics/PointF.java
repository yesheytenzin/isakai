// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Float point. */
public class PointF {
    public float x;
    public float y;

    public PointF() {}

    public PointF(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public PointF(Point src) {
        if (src != null) {
            this.x = src.x;
            this.y = src.y;
        }
    }

    public void set(float x, float y) {
        this.x = x;
        this.y = y;
    }

    public void offset(float dx, float dy) {
        x += dx;
        y += dy;
    }

    public static float length(float x, float y) {
        return (float) Math.hypot(x, y);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PointF)) return false;
        PointF p = (PointF) o;
        return x == p.x && y == p.y;
    }

    @Override
    public int hashCode() {
        return Float.floatToIntBits(x) * 31 + Float.floatToIntBits(y);
    }

    @Override
    public String toString() {
        return "PointF(" + x + ", " + y + ")";
    }
}
