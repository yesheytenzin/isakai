// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Integer rectangle with the android.graphics.Rect API surface. */
public final class Rect {
    public int left;
    public int top;
    public int right;
    public int bottom;

    public Rect() {}

    public Rect(int left, int top, int right, int bottom) {
        set(left, top, right, bottom);
    }

    public Rect(Rect r) {
        if (r != null) set(r);
    }

    public void setEmpty() {
        left = top = right = bottom = 0;
    }

    public void set(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public void set(Rect src) {
        if (src != null) set(src.left, src.top, src.right, src.bottom);
    }

    public boolean isEmpty() {
        return left >= right || top >= bottom;
    }

    public int width() { return right - left; }
    public int height() { return bottom - top; }
    public int centerX() { return (left + right) >> 1; }
    public int centerY() { return (top + bottom) >> 1; }
    public float exactCenterX() { return (left + right) * 0.5f; }
    public float exactCenterY() { return (top + bottom) * 0.5f; }

    public void offset(int dx, int dy) {
        left += dx;
        top += dy;
        right += dx;
        bottom += dy;
    }

    public void offsetTo(int x, int y) {
        right += x - left;
        bottom += y - top;
        left = x;
        top = y;
    }

    public void inset(int dx, int dy) {
        left += dx;
        top += dy;
        right -= dx;
        bottom -= dy;
    }

    public boolean contains(int x, int y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    public boolean contains(Rect r) {
        return r != null && left <= r.left && top <= r.top && right >= r.right && bottom >= r.bottom;
    }

    public boolean intersect(int l, int t, int r, int b) {
        if (l < r && t < b && left < r && l < right && top < b && t < bottom) {
            if (left < l) left = l;
            if (top < t) top = t;
            if (right > r) right = r;
            if (bottom > b) bottom = b;
            return true;
        }
        return false;
    }

    public boolean intersect(Rect r) {
        return r != null && intersect(r.left, r.top, r.right, r.bottom);
    }

    public boolean intersects(int l, int t, int r, int b) {
        return l < r && t < b && left < r && l < right && top < b && t < bottom;
    }

    public static boolean intersects(Rect a, Rect b) {
        return a != null && b != null && a.intersects(b.left, b.top, b.right, b.bottom);
    }

    public void union(int l, int t, int r, int b) {
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

    public void union(Rect r) {
        if (r != null) union(r.left, r.top, r.right, r.bottom);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Rect)) return false;
        Rect r = (Rect) o;
        return left == r.left && top == r.top && right == r.right && bottom == r.bottom;
    }

    @Override
    public int hashCode() {
        int h = left;
        h = h * 31 + top;
        h = h * 31 + right;
        h = h * 31 + bottom;
        return h;
    }

    @Override
    public String toString() {
        return "Rect(" + left + ", " + top + " - " + right + ", " + bottom + ")";
    }

    public String toShortString() {
        return "[" + left + "," + top + "][" + right + "," + bottom + "]";
    }
}
