// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

/** Clips its child to a level fraction along one axis. */
public class ClipDrawable extends Drawable implements Drawable.Callback {
    public static final int HORIZONTAL = 1;
    public static final int VERTICAL = 2;

    private final Drawable inner;
    private final int gravity;
    private final int orientation;

    public ClipDrawable(Drawable drawable, int gravity, int orientation) {
        this.inner = drawable;
        this.gravity = gravity;
        this.orientation = orientation;
        if (drawable != null) drawable.setCallback(this);
    }

    public Drawable getDrawable() { return inner; }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null || inner == null) return;
        Rect b = getBounds();
        float frac = Math.max(0, Math.min(getLevel(), 10000)) / 10000.0f;
        int w = b.width();
        int h = b.height();
        int cw = orientation == VERTICAL ? w : (int) (w * frac);
        int ch = orientation == VERTICAL ? (int) (h * frac) : h;
        canvas.save();
        canvas.clipRect(b.left, b.top, b.left + cw, b.top + ch);
        inner.setBounds(b.left, b.top, b.right, b.bottom);
        inner.draw(canvas);
        canvas.restore();
    }

    @Override
    public void invalidateDrawable(Drawable who) { invalidateSelf(); }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
}
