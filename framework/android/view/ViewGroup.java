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
    private boolean touchscreenBlocksFocus;

    public void setTouchscreenBlocksFocus(boolean blocksFocus) {
        touchscreenBlocksFocus = blocksFocus;
    }

    public boolean getTouchscreenBlocksFocus() {
        return touchscreenBlocksFocus;
    }

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

        public LayoutParams(android.content.Context context, android.util.AttributeSet attrs) {
            this(WRAP_CONTENT, WRAP_CONTENT);
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs, new int[]{0x010100f4, 0x010100f5});
            width = a.getLayoutDimension(0, WRAP_CONTENT);
            height = a.getLayoutDimension(1, WRAP_CONTENT);
            a.recycle();
        }

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
        public int startMargin = Integer.MIN_VALUE;
        public int endMargin = Integer.MIN_VALUE;

        public MarginLayoutParams(android.content.Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            android.content.res.TypedArray a = context.obtainStyledAttributes(attrs,
                    new int[]{0x010100f6, 0x010100f7, 0x010100f8, 0x010100f9, 0x010100fa});
            int all = a.getDimensionPixelSize(0, 0);
            leftMargin = a.getDimensionPixelSize(1, all); topMargin = a.getDimensionPixelSize(2, all);
            rightMargin = a.getDimensionPixelSize(3, all); bottomMargin = a.getDimensionPixelSize(4, all);
            a.recycle();
        }
        public void setMargins(int left,int top,int right,int bottom) {
            leftMargin=left; topMargin=top; rightMargin=right; bottomMargin=bottom;
        }
        public void setMarginStart(int start) { startMargin=start; }
        public void setMarginEnd(int end) { endMargin=end; }
        public int getMarginStart() { return startMargin == Integer.MIN_VALUE ? leftMargin : startMargin; }
        public int getMarginEnd() { return endMargin == Integer.MIN_VALUE ? rightMargin : endMargin; }
        public void resolveLayoutDirection(int direction) {
            setLayoutDirection(direction);
            if (direction == View.LAYOUT_DIRECTION_RTL) {
                if (endMargin != Integer.MIN_VALUE) leftMargin=endMargin;
                if (startMargin != Integer.MIN_VALUE) rightMargin=startMargin;
            } else {
                if (startMargin != Integer.MIN_VALUE) leftMargin=startMargin;
                if (endMargin != Integer.MIN_VALUE) rightMargin=endMargin;
            }
        }
        private int layoutDirection = View.LAYOUT_DIRECTION_LTR;
        public int getLayoutDirection() { return layoutDirection; }
        public void setLayoutDirection(int direction) { layoutDirection = direction; }

        public MarginLayoutParams(int width, int height) {
            super(width, height);
        }

        public MarginLayoutParams(LayoutParams source) {
            super(source);
            if(source instanceof MarginLayoutParams) {
                MarginLayoutParams margins=(MarginLayoutParams)source;
                leftMargin=margins.leftMargin;topMargin=margins.topMargin;
                rightMargin=margins.rightMargin;bottomMargin=margins.bottomMargin;
                startMargin=margins.startMargin;endMargin=margins.endMargin;
            }
        }

        public MarginLayoutParams(MarginLayoutParams source) {
            super(source);
            if (source != null) {
                leftMargin = source.leftMargin;
                topMargin = source.topMargin;
                rightMargin = source.rightMargin;
                bottomMargin = source.bottomMargin;
                startMargin = source.startMargin; endMargin = source.endMargin;
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

    protected LayoutParams generateDefaultLayoutParams() { return new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT); }
    public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) { return new LayoutParams(getContext(), attrs); }
    protected LayoutParams generateLayoutParams(LayoutParams source) { return new LayoutParams(source); }
    public void onViewAdded(View child) {}
    public void onViewRemoved(View child) {}
    private void prepareChild(View child) {
        if (child.getParent() != null) throw new IllegalStateException("View already has a parent");
        LayoutParams params = child.getLayoutParams();
        if (params == null) params=generateDefaultLayoutParams();
        else if (!checkLayoutParams(params)) params=generateLayoutParams(params);
        child.setLayoutParams(params);
        if (params instanceof MarginLayoutParams)
            ((MarginLayoutParams) params).resolveLayoutDirection(getLayoutDirection());
        child.setParent(this);
    }
    public void addView(View child) {
        if (child == null) throw new IllegalArgumentException("child is null");
        prepareChild(child);
        children.add(child);
        onViewAdded(child);
        if (isAttachedToWindow()) child.dispatchHostAttached();
        invalidate();
    }

    public void addView(View child, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("child is null");
        if (params == null) throw new IllegalArgumentException("params are null");
        child.setLayoutParams(params);
        prepareChild(child);
        children.add(child);
        onViewAdded(child);
        if (isAttachedToWindow()) child.dispatchHostAttached();
        invalidate();
    }

    public void addView(View child, int index) {
        if (child == null) throw new IllegalArgumentException("child is null");
        prepareChild(child);
        children.add(Math.max(0, Math.min(index, children.size())), child);
        onViewAdded(child);
        if (isAttachedToWindow()) child.dispatchHostAttached();
        invalidate();
    }

    public void addView(View child, int index, LayoutParams params) {
        if (child == null) throw new IllegalArgumentException("child is null");
        if (params == null) throw new IllegalArgumentException("params are null");
        child.setLayoutParams(params);
        prepareChild(child);
        children.add(Math.max(0, Math.min(index, children.size())), child);
        onViewAdded(child);
        if (isAttachedToWindow()) child.dispatchHostAttached();
        invalidate();
    }

    public void addView(View child, int width, int height) {
        addView(child, new LayoutParams(width, height));
    }

    public void updateViewLayout(View view, LayoutParams params) {
        if (view == null || view.getParent() != this) {
            throw new IllegalArgumentException("view is not a child");
        }
        if (params == null) throw new IllegalArgumentException("params are null");
        view.setLayoutParams(params);
        requestLayout();
        invalidate();
    }

    protected boolean addViewInLayout(View child, int index, LayoutParams params) {
        addView(child, index, params);
        return true;
    }

    protected boolean addViewInLayout(View child, int index, LayoutParams params, boolean preventRequestLayout) {
        addView(child, index, params);
        return true;
    }

    public void removeViewInLayout(View child) {
        removeChild(child);
    }

    public void cleanupLayoutState(View child) {}

    public void suppressLayout(boolean suppress) {}

    private boolean transitionGroup;

    public void setTransitionGroup(boolean transitionGroup) {
        this.transitionGroup = transitionGroup;
    }

    public boolean isTransitionGroup() {
        return transitionGroup;
    }

    public void removeViewAt(int index) {
        removeChildAt(index);
    }

    private void removeChildAt(int index) {
        if (index < 0 || index >= children.size()) return;
        View child = children.remove(index);
        child.dispatchHostDetached();
        child.setParent(null);
        onViewRemoved(child);
        if (touchTarget == child) touchTarget = null;
        invalidate();
    }

    public int indexOfChild(View child) {
        return children.indexOf(child);
    }

    public void bringChildToFront(View child) {
        if (children.remove(child)) {
            children.add(child);
            requestLayout();
            invalidate();
        }
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
    public void setClipChildren(boolean clipChildren) {}

    /** AOSP layout-bound modes; GridLayout reads this while measuring. */
    public static final int LAYOUT_MODE_UNDEFINED = -1;
    public static final int LAYOUT_MODE_CLIP_BOUNDS = 0;
    public static final int LAYOUT_MODE_OPTICAL_BOUNDS = 1;
    private int layoutMode = LAYOUT_MODE_UNDEFINED;
    public int getLayoutMode() { return layoutMode; }
    public void setLayoutMode(int mode) {
        if (mode < 0) mode = LAYOUT_MODE_UNDEFINED;
        if (mode == layoutMode) return;
        layoutMode = mode;
        requestLayout();
    }

    /**
     * Record whether this group's drawable state merges its children's
     * states. Retained for API shape; child-state merging itself is still
     * unimplemented (View pushes its own state only), so setting the flag
     * currently changes no rendering.
     */
    public void setAddStatesFromChildren(boolean addsStates) {
        addStatesFromChildren = addsStates;
    }

    public boolean addStatesFromChildren() {
        return addStatesFromChildren;
    }

    private boolean addStatesFromChildren;
    public void startViewTransition(View view) {}
    public void endViewTransition(View view) {}
    public android.animation.LayoutTransition getLayoutTransition() { return null; }
    public void setLayoutTransition(android.animation.LayoutTransition transition) {}

    public void setOnHierarchyChangeListener(OnHierarchyChangeListener listener) {}

    public void removeView(View child) {
        removeChild(child);
    }

    private void removeChild(View child) {
        removeChildAt(children.indexOf(child));
    }
    /**
     * Temporary attach/detach used by RecyclerView's scrap cache: detaching
     * removes the child from layout/draw without full remove bookkeeping;
     * attaching re-inserts it. removeDetachedView finishes a detached child.
     */
    protected void attachViewToParent(View child, int index, LayoutParams params) {
        if (child == null) return;
        if (params != null) child.setLayoutParams(params);
        child.setParent(this);
        children.add(Math.max(0, Math.min(index, children.size())), child);
        onViewAdded(child);
        invalidate();
    }

    protected void detachViewFromParent(int index) {
        View child = getChildAt(index);
        if (child != null) detachViewFromParent(child);
    }

    protected void detachViewFromParent(View child) {
        if (child == null || !children.remove(child)) return;
        child.setParent(null);
        if (touchTarget == child) touchTarget = null;
        invalidate();
    }

    protected void removeDetachedView(View child, boolean animate) {
        removeDetachedView(child);
    }

    private void removeDetachedView(View child) {
        if (child == null) return;
        child.dispatchHostDetached();
        onViewRemoved(child);
        invalidate();
    }

    public void addFocusables(java.util.ArrayList<View> views, int direction, int focusableMode) {
        if (views == null) return;
        if (getVisibility() != View.VISIBLE) return;
        if (hasFocusable()) views.add(this);
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child == null || child.getVisibility() != View.VISIBLE) continue;
            if (child instanceof ViewGroup) ((ViewGroup) child).addFocusables(views, direction, focusableMode);
            else if (child.hasFocusable()) views.add(child);
        }
    }

    public View focusSearch(View focused, int direction) {
        return null;
    }

    protected boolean onRequestFocusInDescendants(int direction, android.graphics.Rect previouslyFocusedRect) {
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child != null && child.isFocusable() && child.isEnabled() && child.requestFocus()) return true;
        }
        return false;
    }

    public void requestChildFocus(View child, View focused) {
        super.clearFocus();
        for (View sibling : children) {
            if (sibling != child) sibling.clearFocus();
        }
        if (getParent() instanceof ViewGroup) {
            ((ViewGroup) getParent()).requestChildFocus(this, focused);
        }
    }

    @Override
    public void clearFocus() {
        super.clearFocus();
        for (View child : children) child.clearFocus();
    }

    public void offsetDescendantRectToMyCoords(View descendant, android.graphics.Rect rect) {
        if (descendant == null || rect == null) return;
        ViewParent p = descendant.getParent();
        View c = descendant;
        while (p instanceof ViewGroup && c != this) {
            View v = (View) p;
            rect.offset(c.getLeft() - v.getScrollX(), c.getTop() - v.getScrollY());
            c = v;
            p = v.getParent();
        }
    }

    public void offsetRectIntoDescendantCoords(View descendant, android.graphics.Rect rect) {
        if (descendant == null || rect == null) return;
        java.util.ArrayList<View> chain = new java.util.ArrayList<>();
        ViewParent p = descendant.getParent();
        View c = descendant;
        while (p instanceof ViewGroup && c != this) {
            chain.add(c);
            c = (View) p;
            p = c.getParent();
        }
        for (int i = chain.size() - 1; i >= 0; i--) {
            View v = chain.get(i);
            View parent = (View) v.getParent();
            rect.offset(-(v.getLeft() - parent.getScrollX()), -(v.getTop() - parent.getScrollY()));
        }
    }

    protected int getChildDrawingOrder(int childCount, int i) { return i; }
    private boolean childrenDrawingOrderEnabled;
    public void setChildrenDrawingOrderEnabled(boolean enabled) { childrenDrawingOrderEnabled = enabled; }
    public boolean isChildrenDrawingOrderEnabled() { return childrenDrawingOrderEnabled; }



    public void removeAllViews() {
        for (View child : new ArrayList<>(children)) removeView(child);
        touchTarget = null;
        invalidate();
    }

    public int getChildCount() {
        return children.size();
    }

    @Override
    public View findFocus() {
        View own = super.findFocus();
        if (own != null) return own;
        for (View child : children) {
            View focused = child.findFocus();
            if (focused != null) return focused;
        }
        return null;
    }

    @Override
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        WindowInsets applied=super.dispatchApplyWindowInsets(insets);
        if(applied!=null && applied!=WindowInsets.CONSUMED) {
            for(View child:new ArrayList<>(children))child.dispatchApplyWindowInsets(applied);
        }
        return applied;
    }

    public View getChildAt(int index) {
        return index>=0 && index<children.size()?children.get(index):null;
    }
    /** Direct child holding (or containing) focus, or null. */
    public View getFocusedChild() {
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child != null && child.hasFocus()) return child;
        }
        return null;
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
            // Reverse paint order: topmost child first (drawing-order aware).
            float localX = event.getX() - getLeft();
            float localY = event.getY() - getTop();
            for (int index : hitOrder()) {
                View child = children.get(index);
                if (child.getVisibility() != View.VISIBLE) continue;
                if (!child.containsPoint(localX, localY)) continue;
                event.offsetLocation(-getLeft(), -getTop());
                boolean handled;
                try {
                    handled = child.dispatchTouchEvent(event);
                } finally {
                    event.offsetLocation(getLeft(), getTop());
                }
                if (handled) {
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
                cancel.offsetLocation(-getLeft(), -getTop());
                touchTarget.dispatchTouchEvent(cancel);
                touchTarget = null;
            }
            return super.dispatchTouchEvent(event);
        }
        if (touchTarget != null) {
            event.offsetLocation(-getLeft(), -getTop());
            boolean handled;
            try {
                handled = touchTarget.dispatchTouchEvent(event);
            } finally {
                event.offsetLocation(getLeft(), getTop());
            }
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
                float px = event.getX(idx) - getLeft();
                float py = event.getY(idx) - getTop();
                for (int i = children.size() - 1; i >= 0; i--) {
                    View child = children.get(i);
                    if (child.getVisibility() != View.VISIBLE) continue;
                    if (!child.containsPoint(px, py)) continue;
                    event.offsetLocation(-getLeft(), -getTop());
                    boolean handled;
                    try {
                        handled = child.dispatchTouchEvent(event);
                    } finally {
                        event.offsetLocation(getLeft(), getTop());
                    }
                    if (handled) {
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
            if (child.hasFocus() && child.dispatchKeyEvent(event)) return true;
        }
        return false;
    }

    public boolean dispatchKeyEventPreIme(KeyEvent event) {
        return false;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent event) {
        return super.dispatchGenericMotionEvent(event);
    }

    @Override
    public void getFocusedRect(android.graphics.Rect r) {
        super.getFocusedRect(r);
    }

    @Override
    protected void onAttachedToWindow() {}

    @Override
    protected void onDetachedFromWindow() {}

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {}

    @Override
    public void onWindowFocusChanged(boolean hasWindowFocus) {}

    /** Groups track a focused child; the rect hint shapes the focus highlight. */
    protected void onFocusChanged(boolean gainFocus, int direction,
            android.graphics.Rect previouslyFocusedRect) {}

    /**
     * AOSP returns true so groups can shield children from a press that began
     * outside them; host input dispatches presses to the deepest target, so
     * the default stays true to preserve that filtering expectation.
     */
    public boolean shouldDelayChildPressedState() {
        return true;
    }

    @Override
    public boolean hasFocus() {
        if (super.hasFocus()) return true;
        for (int i = 0; i < children.size(); i++) {
            if (children.get(i).hasFocus()) return true;
        }
        return false;
    }
    @Override
    public boolean hasFocusable() {
        if (super.hasFocusable()) return true;
        for (int i = 0; i < children.size(); i++) {
            View child = children.get(i);
            if (child != null && child.getVisibility() == View.VISIBLE && child.hasFocusable()) return true;
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

    public static int getChildMeasureSpec(int parentSpec, int padding, int dimension) {
        int available = Math.max(0, MeasureSpec.getSize(parentSpec) - padding);
        int mode = MeasureSpec.getMode(parentSpec);
        if (dimension >= 0) return MeasureSpec.makeMeasureSpec(dimension, MeasureSpec.EXACTLY);
        if (dimension == LayoutParams.MATCH_PARENT)
            return MeasureSpec.makeMeasureSpec(available, mode);
        return MeasureSpec.makeMeasureSpec(available, mode == MeasureSpec.UNSPECIFIED ? mode : MeasureSpec.AT_MOST);
    }
    protected void measureChild(View child, int widthSpec, int heightSpec) {
        LayoutParams p = child.getLayoutParams();
        if (p == null) p = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT);
        child.measure(getChildMeasureSpec(widthSpec,getPaddingLeft()+getPaddingRight(),p.width),
                getChildMeasureSpec(heightSpec,getPaddingTop()+getPaddingBottom(),p.height));
    }
    protected void measureChildren(int widthSpec, int heightSpec) {
        for (View child : children) if (child.getVisibility() != GONE) {
            measureChild(child, widthSpec, heightSpec);
        }
    }
    protected void measureChildWithMargins(View child, int widthSpec, int widthUsed, int heightSpec, int heightUsed) {
        LayoutParams raw = child.getLayoutParams();
        MarginLayoutParams p = raw instanceof MarginLayoutParams ? (MarginLayoutParams)raw : new MarginLayoutParams(raw);
        child.measure(getChildMeasureSpec(widthSpec,getPaddingLeft()+getPaddingRight()+p.leftMargin+p.rightMargin+widthUsed,p.width),
                getChildMeasureSpec(heightSpec,getPaddingTop()+getPaddingBottom()+p.topMargin+p.bottomMargin+heightUsed,p.height));
    }
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        measureChildren(widthSpec,heightSpec);
        int w = getPaddingLeft()+getPaddingRight(), h = getPaddingTop()+getPaddingBottom();
        for (View child : children) if (child.getVisibility() != GONE) {
            LayoutParams p=child.getLayoutParams();
            w=Math.max(w,child.getMeasuredWidth()+(p==null?0:Math.max(0,p.x))+getPaddingLeft()+getPaddingRight());
            h=Math.max(h,child.getMeasuredHeight()+(p==null?0:Math.max(0,p.y))+getPaddingTop()+getPaddingBottom());
        }
        setMeasuredDimension(resolveSize(w,widthSpec),resolveSize(h,heightSpec));
    }
    @Override
    protected void onLayout(int left, int top, int right, int bottom) {
        for (View child : children) if (child.getVisibility() != GONE) {
            LayoutParams p=child.getLayoutParams();
            int x=getPaddingLeft()+(p==null?0:p.x), y=getPaddingTop()+(p==null?0:p.y);
            child.layout(x,y,x+child.getMeasuredWidth(),y+child.getMeasuredHeight());
        }
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
    protected void dispatchSaveInstanceState(android.util.SparseArray<android.os.Parcelable> state) {
        super.dispatchSaveInstanceState(state);
        for(View child:children)if(child.isSaveFromParentEnabled())child.dispatchSaveInstanceState(state);
    }
    @Override
    protected void dispatchRestoreInstanceState(android.util.SparseArray<android.os.Parcelable> state) {
        super.dispatchRestoreInstanceState(state);
        for(View child:children)if(child.isSaveFromParentEnabled())child.dispatchRestoreInstanceState(state);
    }
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < children.size(); i++) {
            children.get(i).draw(canvas);
        }
    }

    @Override
    protected void dispatchDrawAt(Canvas canvas, int offsetX, int offsetY) {
        for (int index : drawOrder()) {
            children.get(index).drawAt(canvas, offsetX, offsetY);
        }
    }

    /** Paint order: child index, drawing-order override, then Z elevation. */
    private int[] drawOrder() {
        int n = children.size();
        int[] order = new int[n];
        if (!isChildrenDrawingOrderEnabled()) {
            for (int i = 0; i < n; i++) order[i] = i;
        } else {
            for (int i = 0; i < n; i++) {
                int mapped = getChildDrawingOrder(n, i);
                order[i] = mapped >= 0 && mapped < n ? mapped : i;
            }
        }
        // Stable Z sort: higher elevation paints (and hit-tests) on top,
        // matching CoordinatorLayout's Z-sorted touch dispatch. Children
        // with equal Z keep index order.
        for (int i = 1; i < n; i++) {
            int key = order[i];
            float keyZ = children.get(key).getZ();
            int j = i - 1;
            while (j >= 0 && children.get(order[j]).getZ() > keyZ) {
                order[j + 1] = order[j];
                j--;
            }
            order[j + 1] = key;
        }
        return order;
    }

    /** Hit-test order: reverse paint order (topmost child first). */
    private int[] hitOrder() {
        int[] paint = drawOrder();
        for (int lo = 0, hi = paint.length - 1; lo < hi; lo++, hi--) {
            int tmp = paint[lo];
            paint[lo] = paint[hi];
            paint[hi] = tmp;
        }
        return paint;
    }

    @Override
    public void onCancelPendingInputEvents() {
        super.onCancelPendingInputEvents();for(View child:children)child.cancelPendingInputEvents();
    }
    @Override
    public void requestLayout() {
        super.requestLayout();
    }

    @Override
    public void invalidateChild(View child, android.graphics.Rect r) {
        invalidate();
    }

    @Override
    public boolean isLayoutRequested() {
        return super.isLayoutRequested();
    }
}
