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
public class ViewGroup extends View implements ViewParent {
    public static final int FOCUS_BEFORE_DESCENDANTS = 0x20000;
    public static final int FOCUS_AFTER_DESCENDANTS = 0x40000;
    public static final int FOCUS_BLOCK_DESCENDANTS = 0x60000;

    private int descendantFocusability = FOCUS_BEFORE_DESCENDANTS;

    public void setDescendantFocusability(int focusability) {
        this.descendantFocusability = focusability;
    }

    public int getDescendantFocusability() {
        return descendantFocusability;
    }

    public interface OnHierarchyChangeListener {
        void onChildViewAdded(View parent, View child);
        void onChildViewRemoved(View parent, View child);
    }
    private final List<View> children = new ArrayList<View>();
    private View touchTarget;

    /** Layout parameters: absolute position plus fixed size. */
    public static class LayoutParams {
        public static final int MATCH_PARENT = -1;
        public static final int WRAP_CONTENT = -2;

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

        public LayoutParams(int width, int height) {
            this(0, 0, width, height);
        }

        public LayoutParams(LayoutParams source) {
            this(source == null ? 0 : source.x, source == null ? 0 : source.y,
                    source == null ? WRAP_CONTENT : source.width,
                    source == null ? WRAP_CONTENT : source.height);
        }
    }

    /** Layout parameters with margins. */
    public static class MarginLayoutParams extends LayoutParams {
        public int leftMargin;
        public int topMargin;
        public int rightMargin;
        public int bottomMargin;
        public int startMargin;
        public int endMargin;

        public MarginLayoutParams(int width, int height) {
            super(width, height);
        }

        public MarginLayoutParams(LayoutParams source) {
            super(source);
        }

        public MarginLayoutParams(MarginLayoutParams source) {
            super(source);
            if (source != null) {
                leftMargin = source.leftMargin;
                topMargin = source.topMargin;
                rightMargin = source.rightMargin;
                bottomMargin = source.bottomMargin;
            }
        }
    }
    public ViewGroup() {
        super();
    }

    public ViewGroup(android.content.Context context) {
        super(context);
    }

    public ViewGroup(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public ViewGroup(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
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

    public void addView(View child, int index) {
        if (child == null) throw new IllegalArgumentException("child is null");
        child.setParent(this);
        children.add(Math.max(0, Math.min(index, children.size())), child);
        invalidate();
    }

    public void addView(View child, int index, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("child is null");
        if (params == null) throw new IllegalArgumentException("params are null");
        child.setParent(this);
        child.setLayoutParams(params);
        children.add(Math.max(0, Math.min(index, children.size())), child);
        invalidate();
    }

    public void addView(View child, int width, int height) {
        addView(child, new LayoutParams(width, height));
    }

    public void removeViewAt(int index) {
        if (index < 0 || index >= children.size()) return;
        View child = children.remove(index);
        child.setParent(null);
        if (touchTarget == child) touchTarget = null;
        invalidate();
    }

    public int indexOfChild(View child) {
        return children.indexOf(child);
    }

    public boolean checkLayoutParams(LayoutParams params) {
        return params != null;
    }

    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == null || child.getVisibility() != View.VISIBLE) return false;
        child.draw(canvas);
        return true;
    }

    public void setClipToPadding(boolean clip) {}
    public void startViewTransition(View view) {}
    public void endViewTransition(View view) {}
    public android.animation.LayoutTransition getLayoutTransition() { return null; }
    public void setLayoutTransition(android.animation.LayoutTransition transition) {}

    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {}

    public void removeView(View child) {
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

    /**
     * Depth-first id search: self, then each child subtree in order.
     * Mirrors the codegen ViewBindings helper (q0.v.h in OpenCalc).
     */
    @Override
    public View findViewById(int id) {
        if (getId() == id) return this;
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child == null) continue;
            View found = child.findViewById(id);
            if (found != null) return found;
        }
        return null;
    }

    /** Interception hook; always false in M3 (no scrolling groups). */
    public boolean onInterceptTouchEvent(MotionEvent event) {
        return false;
    }

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
        // Single-target routing: a POINTER_DOWN with no current target
        // hit-tests the new pointer (via getActionIndex) like a DOWN.
        if (action == MotionEvent.ACTION_POINTER_DOWN) {
            int idx = event.getActionIndex();
            if (idx >= 0 && idx < event.getPointerCount()) {
                float px = event.getX(idx);
                float py = event.getY(idx);
                for (int i = children.size() - 1; i >= 0; i--) {
                    View child = children.get(i);
                    if (!child.containsPoint(px, py)) continue;
                    if (child.dispatchTouchEvent(event)) {
                        touchTarget = child;
                        return true;
                    }
                }
            }
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

    @Override
    public void requestLayout() {
        invalidate();
    }

    @Override
    public void invalidateChild(View child, android.graphics.Rect r) {
        invalidate();
    }

    @Override
    public boolean isLayoutRequested() {
        return false;
    }
}
