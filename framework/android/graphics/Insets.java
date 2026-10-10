// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** System window insets: four zero edges. */
public final class Insets {
    public static final Insets NONE = new Insets(0, 0, 0, 0);

    public final int left;
    public final int top;
    public final int right;
    public final int bottom;

    private Insets(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
    }

    public static Insets of(int left, int top, int right, int bottom) {
        if (left == 0 && top == 0 && right == 0 && bottom == 0) return NONE;
        return new Insets(left, top, right, bottom);
    }

    public static Insets of(Rect rect) {
        if (rect == null) return NONE;
        return of(rect.left, rect.top, rect.right, rect.bottom);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof Insets)) return false;
        Insets i = (Insets) o;
        return left == i.left && top == i.top && right == i.right && bottom == i.bottom;
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
        return "Insets{l=" + left + " t=" + top + " r=" + right + " b=" + bottom + "}";
    }
}
