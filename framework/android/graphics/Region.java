// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Region: bounds-rect union, path/ops record-only. */
public class Region {
    public enum Op {
        DIFFERENCE, REVERSE_DIFFERENCE, INTERSECT, UNION, XOR, REPLACE
    }

    private final Rect bounds = new Rect();

    public Region() {}

    public Region(Rect rect) {
        if (rect != null) bounds.set(rect);
    }

    public Region(Region other) {
        if (other != null) bounds.set(other.bounds);
    }

    public boolean set(Rect rect) {
        bounds.setEmpty();
        if (rect != null) bounds.set(rect);
        return true;
    }

    public boolean set(Region other) {
        bounds.setEmpty();
        if (other != null) bounds.set(other.bounds);
        return true;
    }

    public boolean setPath(Path path, Region clip) {
        bounds.setEmpty();
        if (path == null) return false;
        RectF f = new RectF();
        path.computeBounds(f, true);
        f.roundOut(bounds);
        if (clip != null) bounds.intersect(clip.bounds);
        return true;
    }

    public boolean op(Rect rect, Op op) {
        if (rect == null) return false;
        return apply(rect, op);
    }

    public boolean op(Region other, Op op) {
        if (other == null) return false;
        return apply(other.bounds, op);
    }

    private boolean apply(Rect rect, Op op) {
        if (op == null) return false;
        switch (op) {
            case REPLACE:
                bounds.set(rect);
                return true;
            case UNION:
                bounds.union(rect);
                return true;
            case INTERSECT:
                return bounds.intersect(rect);
            case DIFFERENCE:
            case REVERSE_DIFFERENCE:
            case XOR:
            default:
                return false;
        }
    }

    public Rect getBounds() { return new Rect(bounds); }

    public boolean getBounds(Rect out) {
        if (out != null) out.set(bounds);
        return true;
    }

    public boolean isEmpty() { return bounds.isEmpty(); }
    public boolean contains(int x, int y) { return bounds.contains(x, y); }
}
