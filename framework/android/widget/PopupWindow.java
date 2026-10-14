// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

/** Floating window: content view plus background, dismissed on demand. */
public class PopupWindow {
    public static final int INPUT_METHOD_FROM_FOCUSABLE = 0;
    public static final int INPUT_METHOD_NEEDED = 1;
    public static final int INPUT_METHOD_NOT_NEEDED = 2;

    public interface OnDismissListener {
        void onDismiss();
    }

    private View contentView;
    private Drawable background;
    private int width;
    private int height;
    private boolean focusable;
    private boolean showing;
    private boolean outsideTouchable;
    private boolean touchModal = true;
    private boolean clippedToScreen = true;
    private int inputMethodMode = INPUT_METHOD_FROM_FOCUSABLE;
    private int animationStyle;
    private int windowLayoutType;
    private OnDismissListener dismissListener;
    private View.OnTouchListener touchInterceptor;
    private Rect epicenterBounds;

    public PopupWindow() {}

    public PopupWindow(View contentView) {
        setContentView(contentView);
    }

    public PopupWindow(View contentView, int width, int height) {
        setContentView(contentView);
        setWidth(width);
        setHeight(height);
    }

    public PopupWindow(Context context, android.util.AttributeSet attrs, int defStyleAttr) {}

    public PopupWindow(Context context, android.util.AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {}

    public View getContentView() { return contentView; }

    public void setContentView(View view) {
        this.contentView = view;
    }

    public Drawable getBackground() { return background; }

    public void setBackgroundDrawable(Drawable background) {
        this.background = background;
    }

    public int getWidth() { return width; }
    public void setWidth(int width) { this.width = width; }
    public int getHeight() { return height; }
    public void setHeight(int height) { this.height = height; }

    public void setFocusable(boolean focusable) { this.focusable = focusable; }
    public boolean isFocusable() { return focusable; }

    public boolean isShowing() { return showing; }

    public void showAtLocation(View parent, int gravity, int x, int y) {
        showing = true;
    }

    public void showAsDropDown(View anchor, int xoff, int yoff, int gravity) {
        showing = true;
    }

    public void showAsDropDown(View anchor, int xoff, int yoff) {
        showing = true;
    }

    public void showAsDropDown(View anchor) {
        showing = true;
    }

    public void dismiss() {
        if (!showing) return;
        showing = false;
        if (dismissListener != null) dismissListener.onDismiss();
    }

    public void update(View anchor, int width, int height) {
        setWidth(width);
        setHeight(height);
    }

    public void update(View anchor, int xoff, int yoff, int width, int height) {
        update(anchor, width, height);
    }

    public void setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public void setTouchInterceptor(View.OnTouchListener interceptor) {
        this.touchInterceptor = interceptor;
    }

    public void setOutsideTouchable(boolean touchable) {
        this.outsideTouchable = touchable;
    }

    public void setTouchModal(boolean modal) {
        this.touchModal = modal;
    }

    public void setIsClippedToScreen(boolean clipped) {
        this.clippedToScreen = clipped;
    }

    public void setInputMethodMode(int mode) {
        this.inputMethodMode = mode;
    }

    public int getInputMethodMode() {
        return inputMethodMode;
    }

    public void setAnimationStyle(int style) {
        this.animationStyle = style;
    }

    public void setEnterTransition(android.transition.Transition transition) {}
    public void setExitTransition(android.transition.Transition transition) {}

    public void setEpicenterBounds(Rect bounds) {
        this.epicenterBounds = bounds == null ? null : new Rect(bounds);
    }

    public int getMaxAvailableHeight(View anchor, int yOffset) {
        return 0;
    }

    public int getMaxAvailableHeight(View anchor, int yOffset, boolean ignoreBottomDecorations) {
        return getMaxAvailableHeight(anchor, yOffset);
    }

    /**
     * Retained window type (popups render inside the app window here, so
     * the type changes no placement).
     */
    public void setWindowLayoutType(int layoutType) {
        this.windowLayoutType = layoutType;
    }

    public void draw(Canvas canvas) {
        if (!showing || contentView == null) return;
        contentView.draw(canvas);
    }

    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }
}
