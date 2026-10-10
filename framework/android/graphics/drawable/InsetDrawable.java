// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

/** Wraps a drawable with fixed insets. */
public class InsetDrawable extends Drawable implements Drawable.Callback {
    private Drawable inner;
    private final int left;
    private final int top;
    private final int right;
    private final int bottom;

    public InsetDrawable(Drawable drawable, int insetLeft, int insetTop,
            int insetRight, int insetBottom) {
        this.inner = drawable;
        this.left = insetLeft;
        this.top = insetTop;
        this.right = insetRight;
        this.bottom = insetBottom;
        if (drawable != null) drawable.setCallback(this);
    }

    public InsetDrawable(Drawable drawable, int inset) {
        this(drawable, inset, inset, inset, inset);
    }

    public Drawable getDrawable() { return inner; }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null || inner == null) return;
        Rect b = getBounds();
        inner.setBounds(b.left + left, b.top + top, b.right - right, b.bottom - bottom);
        inner.draw(canvas);
    }

    @Override
    public boolean getPadding(Rect padding) {
        boolean innerPad = inner != null && inner.getPadding(padding);
        if (padding != null) {
            padding.left += left;
            padding.top += top;
            padding.right += right;
            padding.bottom += bottom;
        }
        return innerPad || left != 0 || top != 0 || right != 0 || bottom != 0;
    }

    @Override
    public int getIntrinsicWidth() {
        int w = inner == null ? -1 : inner.getIntrinsicWidth();
        return w < 0 ? w : w + left + right;
    }

    @Override
    public int getIntrinsicHeight() {
        int h = inner == null ? -1 : inner.getIntrinsicHeight();
        return h < 0 ? h : h + top + bottom;
    }

    @Override
    public void invalidateDrawable(Drawable who) { invalidateSelf(); }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
}
