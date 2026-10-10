// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;

/** Holds children, drawing only the selected one. */
public class DrawableContainer extends Drawable implements Drawable.Callback {
    private final java.util.ArrayList<Drawable> children = new java.util.ArrayList<>();
    private int current = -1;

    public DrawableContainer() {}

    protected int addChild(Drawable d) {
        if (d == null) return -1;
        d.setCallback(this);
        children.add(d);
        if (current < 0) current = 0;
        return children.size() - 1;
    }

    protected int indexOf(Drawable d) {
        return children.indexOf(d);
    }

    public int getChildCount() { return children.size(); }

    public Drawable getChild(int index) {
        return index >= 0 && index < children.size() ? children.get(index) : null;
    }

    @Override
    public boolean selectDrawable(int index) {
        if (index < -1 || index >= children.size()) return false;
        if (current == index) return false;
        current = index;
        invalidateSelf();
        return true;
    }

    @Override
    public Drawable getCurrent() {
        Drawable d = getChild(current);
        return d == null ? this : d;
    }

    @Override
    public void draw(Canvas canvas) {
        Drawable d = getChild(current);
        if (canvas != null && d != null) {
            d.setBounds(getBounds());
            d.draw(canvas);
        }
    }

    @Override
    public boolean setVisible(boolean visible, boolean restart) {
        boolean changed = super.setVisible(visible, restart);
        for (Drawable d : children) d.setVisible(visible, restart);
        return changed;
    }

    @Override
    public ConstantState getConstantState() {
        return new DrawableContainerState(children);
    }

    @Override
    public void invalidateDrawable(Drawable who) { invalidateSelf(); }

    @Override
    public void scheduleDrawable(Drawable who, Runnable what, long when) { scheduleSelf(what, when); }

    @Override
    public void unscheduleDrawable(Drawable who, Runnable what) { unscheduleSelf(what); }

    public static class DrawableContainerState extends ConstantState {
        private final Drawable[] children;

        DrawableContainerState(java.util.List<Drawable> children) {
            this.children = children.toArray(new Drawable[0]);
        }

        public int getChildCount() { return children.length; }

        public Drawable getChild(int index) {
            return index >= 0 && index < children.length ? children[index] : null;
        }

        @Override
        public Drawable newDrawable() {
            DrawableContainer c = new DrawableContainer();
            for (Drawable d : children) c.addChild(d);
            return c;
        }
    }
}
