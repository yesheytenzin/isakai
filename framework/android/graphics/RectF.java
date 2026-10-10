// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Float rectangle with the android.graphics.RectF API surface. */
public final class RectF {
    public float left;
    public float top;
    public float right;
    public float bottom;

    public RectF() {}

    public RectF(float left, float top, float right, float bottom) {
        set(left, top, right, bottom);
    }

    public RectF(RectF r) {
        if (r != null) set(r);
    }

    public RectF(Rect r) {
        if (r != null) set(r.left, r.top, r.right, r.bottom);
    }

    public void setEmpty() {
        left = top = right = bottom = 0;
    }

    public void set(float left, float top, float right, float bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(RectF src) {
        if (src != null) set(src.left, src.top, src.right, src.bottom);
    }

    public void set(Rect src) {
        if (src != null) set(src.left, src.top, src.right, src.bottom);
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    public float width() { return right - left; }
    public float height() { return bottom - top; }
    public float centerX() { return (left + right) * 0.5f; }
    public float centerY() { return (top + bottom) * 0.5f; }

    public void offset(float dx, float dy) {
        left += dx;
        top += dy;
        right += dx;
        bottom += dy;
    }

    public void offsetTo(float x, float y) {
        right += x - left;
        bottom += y - top;
        left = x;
        top = y;
    }

    public void inset(float dx, float dy) {
        left += dx;
        top += dy;
        right -= dx;
        bottom -= dy;
    }

    public boolean contains(float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public boolean contains(RectF r) {
        return r != null && left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom;
    }

    public boolean intersect(float l, float t, float r, float b) {
        if (l < r && t < b && left < r && l < right && top < b && t < bottom) {
            if (left < l) left = l;
            if (top < t) top = t;
            if (right > r) right = r;
            if (bottom > b) bottom = b;
            return true;
        }
        return false;
    }

    public boolean intersect(RectF r) {
        return r != null && intersect(r.left, r.top, r.right, r.bottom);
    }

    public boolean intersects(float l, float t, float r, float b) {
        return l < r && t < b && left < r && l < right && top < b && t < bottom;
    }

    public static boolean intersects(RectF a, RectF b) {
        return a != null && b != null && a.intersects(b.left, b.top, b.right, b.bottom);
    }

    public void union(float l, float t, float r, float b) {
        if (l < r && t < b) {
            if (isEmpty()) {
                set(l, t, r, b);
            } else {
                if (left > l) left = l;
                if (top > t) top = t;
                if (right < r) right = r;
                if (bottom < b) bottom = b;
            }
        }
    }

    public void union(RectF r) {
        if (r != null) union(r.left, r.top, r.right, r.bottom);
    }

    public void round(Rect dst) {
        if (dst != null) dst.set((int) (left + 0.5f), (int) (top + 0.5f),
                (int) (right + 0.5f), (int) (bottom + 0.5f));
    }

    public void roundOut(Rect dst) {
        if (dst != null) dst.set((int) left, (int) top,
                (int) (right + 0.5f), (int) (bottom + 0.5f));
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof RectF)) return false;
        RectF r = (RectF) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override
    public int hashCode() {
        int h = Float.floatToIntBits(left);
        h = h * 31 + Float.floatToIntBits(top);
        h = h * 31 + Float.floatToIntBits(right);
        h = h * 31 + Float.floatToIntBits(bottom);
        return h;
    }

    @Override
    public String toString() {
        return "RectF(" + left + ", " + top + ", " + right + ", " + bottom + ")";
    }

    public String toShortString() {
        return "[" + left + "," + top + "][" + right + "," + bottom + "]";
    }
}
