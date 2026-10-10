// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Outline descriptor: records the last set shape. */
public class Outline {
    private Rect rect;
    private float radius;
    private Path path;
    private float alpha = 1.0f;
    private boolean empty = true;

    public Outline() {}

    public void setEmpty() {
        empty = true;
        rect = null;
        path = null;
    }

    public boolean isEmpty() { return empty; }

    public boolean canClip() { return !empty; }

    public void setAlpha(float alpha) {
        this.alpha = Math.max(0, Math.min(1, alpha));
    }

    public float getAlpha() { return alpha; }

    public void setRect(Rect rect) {
        this.rect = rect == null ? null : new Rect(rect);
        this.radius = 0;
        this.path = null;
        this.empty = rect == null || rect.isEmpty();
    }

    public void setRect(int left, int top, int right, int bottom) {
        setRect(new Rect(left, top, right, bottom));
    }

    public void setRoundRect(Rect rect, float radius) {
        setRect(rect);
        this.radius = Math.max(0, radius);
    }

    public void setRoundRect(int left, int top, int right, int bottom, float radius) {
        setRect(left, top, right, bottom);
        this.radius = Math.max(0, radius);
    }

    public float getRadius() { return radius; }

    public void setOval(Rect rect) {
        setRect(rect);
    }

    public void setPath(Path path) {
        this.path = path;
        this.rect = null;
        this.empty = path == null || path.isEmpty();
    }

    public void setConvexPath(Path path) {
        setPath(path);
    }

    public Rect getRect() { return rect == null ? null : new Rect(rect); }
}
