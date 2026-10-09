// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.graphics.Canvas;
import android.view.KeyEvent;
import android.view.MotionEvent;
import java.util.ArrayList;
import java.util.List;

/**
 * Minimal ViewGroup: absolute children, reverse paint-order hit testing,
 * touch-target routing (with CANCEL on steal), key routing to the focused
 * child, and a focusNext helper. No TAB traversal in M3.
 */
public class ViewGroup extends View {
    private final List<View> children = new ArrayList<View>();
    private View touchTarget;

    /** Layout parameters: absolute position plus fixed size. */
    public static class LayoutParams {
        public int x;
        public int y;
        public int width;
        public int height;

        public LayoutParams(int x, int y, int width, int height) {
            this.x = x;
            this.y = y;
            this.width = width;
            this.height = height;
        }
    }

    public ViewGroup() {
        super();
    }

    public void addView(View child) {
        if (child == null) throw new IllegalArgumentException("child is null");
        child.setParent(this);
        children.add(child);
        invalidate();
    }

    public void addView(View child, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("child is null");
        if (params == null) throw new IllegalArgumentException("params are null");
        child.setParent(this);
        child.measure(params.width, params.height);
        int l = getLeft() + params.x;
        int t = getTop() + params.y;
        child.layout(l, t, l + params.width, t + params.height);
        children.add(child);
        invalidate();
    }

    public void removeView(View child) {
        if (child == null) return;
        if (children.remove(child)) {
            child.setParent(null);
            if (touchTarget == child) touchTarget = null;
            invalidate();
        }
    }

    public void removeAllViews() {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).setParent(null);
        }
        children.clear();
        touchTarget = null;
        invalidate();
    }

    public int getChildCount() {
        return children.size();
    }

    public View getChildAt(int index) {
        return children.get(index);
    }

    /** Interception hook; always false in M3 (no scrolling groups). */
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN) {
            touchTarget = null;
            if (onInterceptTouchEvent(event)) {
                return super.dispatchTouchEvent(event);
            }
            // Reverse paint order: last child is on top.
            for (int i = children.size() - 1; i >= 0; i--) {
                View child = children.get(i);
                if (!child.containsPoint(event.getX(), event.getY())) continue;
                if (child.dispatchTouchEvent(event)) {
                    touchTarget = child;
                    return true;
                }
            }
            return super.dispatchTouchEvent(event);
        }
        if (onInterceptTouchEvent(event)) {
            if (touchTarget != null) {
                MotionEvent cancel = MotionEvent.obtain(event);
                cancel.setAction(MotionEvent.ACTION_CANCEL);
                touchTarget.dispatchTouchEvent(cancel);
                touchTarget = null;
            }
            return super.dispatchTouchEvent(event);
        }
        if (touchTarget != null) {
            boolean handled = touchTarget.dispatchTouchEvent(event);
            if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                touchTarget = null;
            }
            return handled;
        }
        return super.dispatchTouchEvent(event);
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (super.dispatchKeyEvent(event)) return true;
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child.isFocused() && child.dispatchKeyEvent(event)) return true;
        }
        return false;
    }

    /**
     * Move focus to the next focusable child after the focused one,
     * wrapping around. No automatic TAB wiring in M3; callers drive this.
     */
    public boolean focusNext() {
        if (children.isEmpty()) return false;
        int start = -1;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).isFocused()) {
                start = i;
                break;
            }
        }
        for (int step = 1; step <= children.size(); step++) {
            int idx = (start + step) % children.size();
            View child = children.get(idx);
            if (child.isFocusable() && child.isEnabled()) {
                if (start >= 0) children.get(start).clearFocus();
                return child.requestFocus();
            }
        }
        return false;
    }

    @Override
    protected void onLayout(int left, int top, int right, int bottom) {
        // Children keep their absolute layout from addView; nothing to redo.
    }

    @Override
    public boolean isDirtyAndClear() {
        boolean d = super.isDirtyAndClear();
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).isDirtyAndClear()) d = true;
        }
        return d;
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).draw(canvas);
        }
    }
}
