// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.graphics.Canvas;
import android.graphics.Color;

/**
 * Minimal View: size, background, measure/layout/draw plus M3 input.
 * Listeners (click/key/touch/focus), key/touch dispatch, a pressed/click
 * machine in onTouchEvent, focusable/focused state, and a dirty flag that
 * walks up to the root so Activity redraws on demand.
 */
public class View {
    /** Click callback. */
    public interface OnClickListener {
        void onClick(View v);
    }

    /** Key callback, fired from dispatchKeyEvent before onKeyDown/onKeyUp. */
    public interface OnKeyListener {
        boolean onKey(View v, int keyCode, KeyEvent event);
    }

    /** Touch callback, fired from dispatchTouchEvent before onTouchEvent. */
    public interface OnTouchListener {
        boolean onTouch(View v, MotionEvent event);
    }

    /** Focus-change callback. */
    public interface OnFocusChangeListener {
        void onFocusChange(View v, boolean hasFocus);
    }

    private int measuredWidth;
    private int measuredHeight;
    private int left;
    private int top;
    private int right;
    private int bottom;
    protected int backgroundColor = Color.WHITE;

    private ViewGroup parent;
    private boolean dirty;
    private boolean clickable;
    private boolean focusable;
    private boolean focused;
    private boolean enabled = true;
    private boolean pressed;
    private OnClickListener clickListener;
    private OnKeyListener keyListener;
    private OnTouchListener touchListener;
    private OnFocusChangeListener focusListener;

    public View() {}

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public final void measure(int width, int height) {
        this.measuredWidth = width;
        this.measuredHeight = height;
        onMeasure(width, height);
    }

    protected void onMeasure(int width, int height) {}

    public final int getMeasuredWidth() {
        return measuredWidth;
    }

    public final int getMeasuredHeight() {
        return measuredHeight;
    }

    public final void layout(int left, int top, int right, int bottom) {
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        onLayout(left, top, right, bottom);
    }

    protected void onLayout(int left, int top, int right, int bottom) {}

    public final int getLeft() {
        return left;
    }

    public final int getTop() {
        return top;
    }

    public final int getRight() {
        return right;
    }

    public final int getBottom() {
        return bottom;
    }

    public final int getWidth() {
        return right - left;
    }

    public final int getHeight() {
        return bottom - top;
    }

    final void setParent(ViewGroup parent) {
        this.parent = parent;
    }

    public final ViewGroup getParent() {
        return parent;
    }

    /** Mark this view dirty and walk up so the root is dirty. */
    public void invalidate() {
        dirty = true;
        if (parent != null) parent.invalidate();
    }

    /**
     * Root check used by Activity: true when this view (or, for groups,
     * any descendant) was invalidated. Clears the flag.
     */
    public boolean isDirtyAndClear() {
        boolean d = dirty;
        dirty = false;
        return d;
    }

    public void setOnClickListener(OnClickListener l) {
        this.clickListener = l;
    }

    public void setOnKeyListener(OnKeyListener l) {
        this.keyListener = l;
    }

    public void setOnTouchListener(OnTouchListener l) {
        this.touchListener = l;
    }

    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        this.focusListener = l;
    }

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }

    public boolean isClickable() {
        return clickable;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    public boolean isFocusable() {
        return focusable;
    }

    public final boolean isFocused() {
        return focused;
    }

    public boolean isPressed() {
        return pressed;
    }

    public void setPressed(boolean pressed) {
        if (this.pressed != pressed) {
            this.pressed = pressed;
            invalidate();
        }
    }

    /** Take focus when focusable and enabled; fires onFocusChanged. */
    public boolean requestFocus() {
        if (!focusable || !enabled) return false;
        if (focused) return true;
        focused = true;
        onFocusChanged(true);
        if (focusListener != null) focusListener.onFocusChange(this, true);
        invalidate();
        return true;
    }

    /** Drop focus; fires onFocusChanged. */
    public void clearFocus() {
        if (!focused) return;
        focused = false;
        onFocusChanged(false);
        if (focusListener != null) focusListener.onFocusChange(this, false);
        invalidate();
    }

    protected void onFocusChanged(boolean gainFocus) {}

    /** Key dispatch: listener first, then onKeyDown/onKeyUp. */
    public boolean dispatchKeyEvent(KeyEvent event) {
        if (keyListener != null && enabled
                && keyListener.onKey(this, event.getKeyCode(), event)) {
            return true;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            return onKeyDown(event.getKeyCode(), event);
        } else {
            return onKeyUp(event.getKeyCode(), event);
        }
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    /** Touch dispatch: listener first, then onTouchEvent. */
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (touchListener != null && enabled
                && touchListener.onTouch(this, event)) {
            return true;
        }
        return onTouchEvent(event);
    }

    /**
     * Default touch handling: clickable views arm on DOWN inside the
     * bounds, fire performClick on UP inside, and release on CANCEL or
     * when the pointer leaves.
     */
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled || !clickable) return false;
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        boolean inside = containsPoint(x, y);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!inside) return false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_MOVE:
                setPressed(inside);
                return true;
            case MotionEvent.ACTION_UP:
                if (!pressed) return false;
                setPressed(false);
                if (inside) {
                    performClick();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                setPressed(false);
                return true;
            default:
                return false;
        }
    }

    /** Fire the click listener. */
    public boolean performClick() {
        if (clickListener != null) {
            clickListener.onClick(this);
            return true;
        }
        return false;
    }

    public boolean containsPoint(float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    /**
     * Draw this view clipped to its own bounds. The canvas is translated
     * so subclasses draw in view-relative coordinates.
     */
    public final void draw(Canvas canvas) {
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        onDraw(canvas);
        dispatchDraw(canvas);
        canvas.restore();
    }

    protected void onDraw(Canvas canvas) {
        canvas.drawColor(backgroundColor);
    }

    /** Hook for groups to draw children; plain views draw nothing extra. */
    protected void dispatchDraw(Canvas canvas) {}
}
