// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;
import android.graphics.Rect;

/** Stack of drawables drawn in order with per-layer insets. */
public class LayerDrawable extends Drawable implements Drawable.Callback {
    private Drawable[] layers;
    private int[] ids;
    private int[] insetLeft;
    private int[] insetTop;
    private int[] insetRight;
    private int[] insetBottom;

    public LayerDrawable(Drawable[] layers) {
        setLayers(layers);
    }

    private void setLayers(Drawable[] layers) {
        this.layers = layers == null ? new Drawable[0] : layers.clone();
        int n = this.layers.length;
        ids = new int[n];
        insetLeft = new int[n];
        insetTop = new int[n];
        insetRight = new int[n];
        insetBottom = new int[n];
        for (int i = 0; i < n; i++) {
            ids[i] = -1;
            if (this.layers[i] != null) this.layers[i].setCallback(this);
        }
    }

    public int getNumberOfLayers() { return layers.length; }

    public Drawable getDrawable(int index) {
        return index >= 0 && index < layers.length ? layers[index] : null;
    }

    public boolean setDrawableByLayerId(int id, Drawable drawable) {
        int i = findIndexById(id);
        if (i < 0 || drawable == null) return false;
        if (layers[i] != null) layers[i].setCallback(null);
        layers[i] = drawable;
        drawable.setCallback(this);
        return true;
    }

    public Drawable findDrawableByLayerId(int id) {
        int i = findIndexById(id);
        return i < 0 ? null : layers[i];
    }

    private int findIndexById(int id) {
        for (int i = 0; i < ids.length; i++) if (ids[i] == id) return i;
        return -1;
    }

    public void setId(int index, int id) {
        if (index >= 0 && index < ids.length) ids[index] = id;
    }

    public int getId(int index) {
        return index >= 0 && index < ids.length ? ids[index] : -1;
    }

    public void setLayerInset(int index, int l, int t, int r, int b) {
        if (index < 0 || index >= layers.length) return;
        insetLeft[index] = l;
        insetTop[index] = t;
        insetRight[index] = r;
        insetBottom[index] = b;
    }

    public void setLayerInsetLeft(int index, int l) { setLayerInset(index, l, getLayerInsetTop(index), getLayerInsetRight(index), getLayerInsetBottom(index)); }
    public void setLayerInsetTop(int index, int t) { setLayerInset(index, getLayerInsetLeft(index), t, getLayerInsetRight(index), getLayerInsetBottom(index)); }
    public void setLayerInsetRight(int index, int r) { setLayerInset(index, getLayerInsetLeft(index), getLayerInsetTop(index), r, getLayerInsetBottom(index)); }
    public void setLayerInsetBottom(int index, int b) { setLayerInset(index, getLayerInsetLeft(index), getLayerInsetTop(index), getLayerInsetRight(index), b); }
    public void setLayerInsetStart(int index, int s) { setLayerInsetLeft(index, s); }
    public void setLayerInsetEnd(int index, int e) { setLayerInsetRight(index, e); }

    public int getLayerInsetLeft(int index) { return index >= 0 && index < layers.length ? insetLeft[index] : 0; }
    public int getLayerInsetTop(int index) { return index >= 0 && index < layers.length ? insetTop[index] : 0; }
    public int getLayerInsetRight(int index) { return index >= 0 && index < layers.length ? insetRight[index] : 0; }
    public int getLayerInsetBottom(int index) { return index >= 0 && index < layers.length ? insetBottom[index] : 0; }
    public int getLayerInsetStart(int index) { return getLayerInsetLeft(index); }
    public int getLayerInsetEnd(int index) { return getLayerInsetRight(index); }

    public void setLayerSize(int index, int w, int h) {}
    public void setLayerWidth(int index, int w) {}
    public void setLayerHeight(int index, int h) {}
    public void setLayerGravity(int index, int gravity) {}

    public int getLayerWidth(int index) { return -1; }
    public int getLayerHeight(int index) { return -1; }
    public int getLayerGravity(int index) { return 0; }

    @Override
    public void draw(Canvas canvas) {
        if (canvas == null) return;
        Rect b = getBounds();
        for (int i = 0; i < layers.length; i++) {
            Drawable d = layers[i];
            if (d == null || !d.isVisible()) continue;
            d.setBounds(b.left + insetLeft[i], b.top + insetTop[i],
                    b.right - insetRight[i], b.bottom - insetBottom[i]);
            d.draw(canvas);
        }
    }

    @Override
    public boolean isStateful() {
        for (Drawable d : layers) if (d != null && d.isStateful()) return true;
        return false;
    }

    @Override
    protected boolean onStateChange(int[] state) {
        boolean changed = false;
        for (Drawable d : layers) if (d != null) changed |= d.setState(state);
        return changed;
    }

    @Override
    public void invalidateDrawable(Drawable who) { invalidateSelf(); }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }
}
