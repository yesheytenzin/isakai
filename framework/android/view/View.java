// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Rect;

/**
 * Minimal View: size, background, measure/layout/draw plus M3 input.
 * Listeners (click/key/touch/focus), key/touch dispatch, a pressed/click
 * machine in onTouchEvent, focusable/focused state, and a dirty flag that
 * walks up to the root so Activity redraws on demand.
 *
 * <p>AppCompat-style callers also need id/tags, visibility, padding,
 * background drawables, elevation/alpha, layout params, the window-token
 * family, and the saved-state hooks: all recorded, most rendering as
 * no-ops on the software canvas.
 */
public class View {
    public static final int VISIBLE = 0;
    public static final int INVISIBLE = 4;
    public static final int GONE = 8;

    public static final int NO_ID = -1;

    public static final int FOCUS_LEFT = 17;
    public static final int FOCUS_UP = 33;
    public static final int FOCUS_RIGHT = 66;
    public static final int FOCUS_DOWN = 130;
    public static final int FOCUS_FORWARD = 2;
    public static final int FOCUS_BACKWARD = 1;

    public static final int LAYOUT_DIRECTION_LTR = 0;
    public static final int LAYOUT_DIRECTION_RTL = 1;
    public static final int LAYOUT_DIRECTION_INHERIT = 2;
    public static final int LAYOUT_DIRECTION_LOCALE = 3;

    public static final int IMPORTANT_FOR_ACCESSIBILITY_AUTO = 0;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_YES = 1;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO = 2;
    public static final int IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS = 4;

    public static final int IMPORTANT_FOR_AUTOFILL_AUTO = 0;
    public static final int IMPORTANT_FOR_AUTOFILL_YES = 1;
    public static final int IMPORTANT_FOR_AUTOFILL_NO = 2;
    public static final int IMPORTANT_FOR_AUTOFILL_YES_EXCLUDE_DESCENDANTS = 4;

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

    public interface OnLongClickListener {
        boolean onLongClick(View v);
    }

    public interface OnAttachStateChangeListener {
        void onViewAttachedToWindow(View v);
        void onViewDetachedFromWindow(View v);
    }

    public interface OnLayoutChangeListener {
        void onLayoutChange(View v, int left, int top, int right, int bottom,
                int oldLeft, int oldTop, int oldRight, int oldBottom);
    }

    public interface OnApplyWindowInsetsListener {
        WindowInsets onApplyWindowInsets(View v, WindowInsets insets);
    }

    public interface OnUnhandledKeyEventListener {
        boolean onUnhandledKeyEvent(View v, KeyEvent event);
    }

    public interface OnHoverListener {
        boolean onHover(View v, MotionEvent event);
    }

    public interface OnCreateContextMenuListener {
        void onCreateContextMenu(ContextMenu menu, View v, ContextMenu.ContextMenuInfo menuInfo);
    }

    private int measuredWidth;
    private int measuredHeight;
    private int left;
    private int top;
    private int right;
    private int bottom;
    protected int backgroundColor = Color.WHITE;
    private android.graphics.drawable.Drawable background;

    private int id = NO_ID;
    private Object tag;
    private int visibility = VISIBLE;
    private int paddingLeft;
    private int paddingTop;
    private int paddingRight;
    private int paddingBottom;
    private float alpha = 1.0f;
    private float elevation;
    private float translationX;
    private float translationY;
    private float translationZ;
    private float rotation;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private int layoutDirection = LAYOUT_DIRECTION_INHERIT;
    private CharSequence contentDescription;
    private ViewGroup.LayoutParams layoutParams;
    private android.os.IBinder windowToken;

    private ViewGroup parent;
    private boolean dirty;
    private boolean clickable;
    private boolean longClickable;
    private boolean focusable;
    private boolean focused;
    private boolean enabled = true;
    private boolean pressed;
    private boolean attachedToWindow;
    private boolean laidOut;
    private boolean layoutRequested = true;
    private OnClickListener clickListener;
    private OnKeyListener keyListener;
    private OnTouchListener touchListener;
    private OnFocusChangeListener focusListener;
    private OnLongClickListener longClickListener;
    private GestureDetector gestureDetector;
    private final java.util.ArrayList<OnAttachStateChangeListener> attachListeners =
            new java.util.ArrayList<>();
    private final java.util.ArrayList<OnLayoutChangeListener> layoutListeners =
            new java.util.ArrayList<>();

    private android.content.Context viewContext;

    public View() {}

    public View(android.content.Context context) {
        this.viewContext = context;
    }

    public View(android.content.Context context, android.util.AttributeSet attrs) {
        this(context);
    }

    public View(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        this(context);
    }

    public android.content.Context getContext() {
        return viewContext;
    }

    public android.content.res.Resources getResources() {
        if (viewContext != null) return viewContext.getResources();
        return android.content.res.Resources.getSystem();
    }

    public void setId(int id) {
        this.id = id;
    }

    public int getId() {
        return id;
    }

    public void setTag(Object tag) {
        this.tag = tag;
    }

    public Object getTag() {
        return tag;
    }

    public void setTag(int key, Object tag) {
        this.tag = tag;
    }

    public Object getTag(int key) {
        return tag;
    }

    public void setVisibility(int visibility) {
        if (this.visibility != visibility) {
            this.visibility = visibility;
            invalidate();
        }
    }

    public int getVisibility() {
        return visibility;
    }

    public void setPadding(int left, int top, int right, int bottom) {
        this.paddingLeft = left;
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
    }

    public int getPaddingLeft() { return paddingLeft; }
    public int getPaddingTop() { return paddingTop; }
    public int getPaddingRight() { return paddingRight; }
    public int getPaddingBottom() { return paddingBottom; }

    public void setAlpha(float alpha) {
        this.alpha = alpha;
    }

    public float getAlpha() { return alpha; }

    public void setElevation(float elevation) {
        this.elevation = elevation;
    }

    public float getElevation() { return elevation; }

    public void setTranslationX(float tx) { this.translationX = tx; }
    public float getTranslationX() { return translationX; }
    public void setTranslationY(float ty) { this.translationY = ty; }
    public float getTranslationY() { return translationY; }
    public void setTranslationZ(float tz) { this.translationZ = tz; }
    public float getTranslationZ() { return translationZ; }
    public void setRotation(float rotation) { this.rotation = rotation; }
    public float getRotation() { return rotation; }
    public void setScaleX(float sx) { this.scaleX = sx; }
    public float getScaleX() { return scaleX; }
    public void setScaleY(float sy) { this.scaleY = sy; }
    public float getScaleY() { return scaleY; }

    public void setLayoutDirection(int direction) {
        this.layoutDirection = direction;
    }

    public int getLayoutDirection() {
        return layoutDirection == LAYOUT_DIRECTION_INHERIT ? LAYOUT_DIRECTION_LTR : layoutDirection;
    }

    public boolean isLayoutRtl() {
        return getLayoutDirection() == LAYOUT_DIRECTION_RTL;
    }

    public void setContentDescription(CharSequence desc) {
        this.contentDescription = desc;
    }

    public CharSequence getContentDescription() {
        return contentDescription;
    }

    public void setLayoutParams(ViewGroup.LayoutParams params) {
        this.layoutParams = params;
    }

    public ViewGroup.LayoutParams getLayoutParams() {
        return layoutParams;
    }

    public android.os.IBinder getWindowToken() {
        return windowToken;
    }

    public void setBackgroundColor(int color) {
        this.backgroundColor = color;
        this.background = null;
        invalidate();
    }

    public int getBackgroundColor() {
        return backgroundColor;
    }

    public void setBackground(android.graphics.drawable.Drawable background) {
        this.background = background;
        if (background != null) background.setCallback(new BackgroundCallback());
        invalidate();
    }

    public void setBackgroundDrawable(android.graphics.drawable.Drawable background) {
        setBackground(background);
    }

    public android.graphics.drawable.Drawable getBackground() {
        return background;
    }

    public void setBackgroundResource(int resId) {
        setBackground(new android.graphics.drawable.ColorDrawable(0));
    }

    public void setBackgroundTintList(android.content.res.ColorStateList tint) {}
    public void setBackgroundTintMode(android.graphics.PorterDuff.Mode mode) {}

    private final class BackgroundCallback implements android.graphics.drawable.Drawable.Callback {
        @Override
        public void invalidateDrawable(android.graphics.drawable.Drawable who) {
            invalidate();
        }

        @Override
        public void scheduleDrawable(android.graphics.drawable.Drawable who, Runnable what, long when) {}

        @Override
        public void unscheduleDrawable(android.graphics.drawable.Drawable who, Runnable what) {}
    }

    public void setMeasuredDimension(int width, int height) {
        this.measuredWidth = width;
        this.measuredHeight = height;
    }

    public final void measure(int width, int height) {
        onMeasure(width, height);
        if (measuredWidth == 0 && measuredHeight == 0) {
            measuredWidth = MeasureSpec.getSize(width);
            measuredHeight = MeasureSpec.getSize(height);
        }
    }

    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(MeasureSpec.getSize(widthMeasureSpec), MeasureSpec.getSize(heightMeasureSpec));
    }

    public final int getMeasuredWidth() {
        return measuredWidth;
    }

    public final int getMeasuredHeight() {
        return measuredHeight;
    }

    public void requestLayout() {
        layoutRequested = true;
        invalidate();
    }

    public void forceLayout() {}

    public final void layout(int left, int top, int right, int bottom) {
        boolean changed = this.left != left || this.top != top || this.right != right || this.bottom != bottom;
        int oldL = this.left, oldT = this.top, oldR = this.right, oldB = this.bottom;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        laidOut = true;
        layoutRequested = false;
        onLayout(changed, left, top, right, bottom);
        if (changed) {
            for (OnLayoutChangeListener l : layoutListeners.toArray(new OnLayoutChangeListener[0])) {
                l.onLayoutChange(this, left, top, right, bottom, oldL, oldT, oldR, oldB);
            }
        }
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {}

    protected void onLayout(int left, int top, int right, int bottom) {}

    protected void onSizeChanged(int w, int h, int oldW, int oldH) {}

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

    public void getLocationOnScreen(int[] out) {
        if (out != null && out.length >= 2) {
            out[0] = left;
            out[1] = top;
        }
    }

    public void offsetLeftAndRight(int offset) {
        left += offset;
        right += offset;
    }

    public void offsetTopAndBottom(int offset) {
        top += offset;
        bottom += offset;
    }

    final void setParent(ViewGroup parent) {
        this.parent = parent;
    }

    public ViewParent getParent() {
        return parent;
    }

    public View getRootView() {
        View v = this;
        while (v.parent != null) v = v.parent;
        return v;
    }

    /**
     * Id lookup: this view on id match, else null. ViewGroup overrides
     * with a depth-first child search (matches ViewBindings helper {@code q0.v.h}).
     */
    public View findViewById(int id) {
        return this.id == id ? this : null;
    }

    /** Mark this view dirty and walk up so the root is dirty. */
    public void invalidate() {
        dirty = true;
        if (parent != null) parent.invalidate();
    }

    public void postInvalidate() {
        invalidate();
    }

    public boolean post(Runnable action) {
        if (action != null) action.run();
        return true;
    }

    public boolean postDelayed(Runnable action, long delayMillis) {
        return post(action);
    }

    public void postOnAnimation(Runnable action) {
        post(action);
    }

    public void postOnAnimationDelayed(Runnable action, long delayMillis) {
        post(action);
    }

    public void postInvalidateOnAnimation() {
        invalidate();
    }

    public boolean removeCallbacks(Runnable action) {
        return false;
    }


    public void invalidate(Rect dirty) {
        invalidate();
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

    /**
     * Attach a gesture detector to the dispatch path (listener, then
     * detector, then onTouchEvent). Null detaches.
     */
    public void setGestureDetector(GestureDetector detector) {
        this.gestureDetector = detector;
    }

    public GestureDetector getGestureDetector() {
        return gestureDetector;
    }

    public void setOnFocusChangeListener(OnFocusChangeListener l) {
        this.focusListener = l;
    }

    public void setOnLongClickListener(OnLongClickListener l) {
        this.longClickListener = l;
    }

    public void addOnAttachStateChangeListener(OnAttachStateChangeListener l) {
        if (l != null && !attachListeners.contains(l)) attachListeners.add(l);
    }

    public void removeOnAttachStateChangeListener(OnAttachStateChangeListener l) {
        attachListeners.remove(l);
    }

    public void addOnLayoutChangeListener(OnLayoutChangeListener l) {
        if (l != null && !layoutListeners.contains(l)) layoutListeners.add(l);
    }

    public void removeOnLayoutChangeListener(OnLayoutChangeListener l) {
        layoutListeners.remove(l);
    }

    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener l) {}

    public void setOnUnhandledKeyEventListener(OnUnhandledKeyEventListener l) {}

    public void setOnHoverListener(OnHoverListener l) {}

    public void setOnCreateContextMenuListener(OnCreateContextMenuListener l) {}

    public void setClickable(boolean clickable) {
        this.clickable = clickable;
    }

    public boolean isClickable() {
        return clickable;
    }

    public void setLongClickable(boolean longClickable) {
        this.longClickable = longClickable;
    }

    public boolean isLongClickable() {
        return longClickable;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isInEditMode() {
        return false;
    }

    private boolean scrollContainer;

    public void setScrollContainer(boolean isScrollContainer) {
        this.scrollContainer = isScrollContainer;
    }

    public boolean isScrollContainer() {
        return scrollContainer;
    }

    public static final int OVER_SCROLL_ALWAYS = 0;
    public static final int OVER_SCROLL_IF_CONTENT_SCROLLS = 1;
    public static final int OVER_SCROLL_NEVER = 2;

    private int overScrollMode = OVER_SCROLL_ALWAYS;

    public void setOverScrollMode(int mode) {
        this.overScrollMode = mode;
    }

    public int getOverScrollMode() {
        return overScrollMode;
    }

    private boolean willNotDraw;

    public void setWillNotDraw(boolean willNotDraw) {
        this.willNotDraw = willNotDraw;
    }

    public boolean willNotDraw() {
        return willNotDraw;
    }

    public void setWillNotCacheDrawing(boolean willNotCacheDrawing) {}

    public void setDrawingCacheEnabled(boolean enabled) {}

    public boolean isEnabled() {
        return enabled;
    }


    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    public boolean isFocusable() {
        return focusable;
    }

    public void setFocusableInTouchMode(boolean focusable) {
        this.focusable = focusable;
    }

    public boolean isFocused() {
        return focused;
    }

    public boolean hasFocus() {
        return focused;
    }

    public boolean hasWindowFocus() {
        return focused;
    }

    public boolean isInTouchMode() {
        return false;
    }

    public boolean isAttachedToWindow() {
        return attachedToWindow;
    }

    public boolean isLaidOut() {
        return laidOut;
    }

    public boolean isLayoutRequested() {
        return layoutRequested;
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

    public void setSelected(boolean selected) {}

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

    public void onWindowFocusChanged(boolean hasWindowFocus) {}

    protected void onAttachedToWindow() {
        attachedToWindow = true;
        for (OnAttachStateChangeListener l : attachListeners.toArray(new OnAttachStateChangeListener[0])) {
            l.onViewAttachedToWindow(this);
        }
    }

    protected void onDetachedFromWindow() {
        attachedToWindow = false;
        for (OnAttachStateChangeListener l : attachListeners.toArray(new OnAttachStateChangeListener[0])) {
            l.onViewDetachedFromWindow(this);
        }
    }

    protected void onFinishInflate() {}

    public void onConfigurationChanged(android.content.res.Configuration newConfig) {}

    public void onRtlPropertiesChanged(int layoutDirection) {}

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

    /**
     * Touch dispatch: listener first, then the gesture detector (when
     * set via {@link #setGestureDetector}), then onTouchEvent. The
     * detector never consumes the stream alone: dispatch returns true
     * when any stage handled the event.
     */
    public boolean dispatchTouchEvent(MotionEvent event) {
        if (touchListener != null && enabled
                && touchListener.onTouch(this, event)) {
            return true;
        }
        boolean gestureHandled = false;
        if (gestureDetector != null && enabled) {
            gestureHandled = gestureDetector.onTouchEvent(event);
        }
        boolean touchHandled = onTouchEvent(event);
        return gestureHandled || touchHandled;
    }

    /**
     * Default touch handling: clickable views arm on DOWN inside the
     * bounds, fire performClick on UP inside, and release on CANCEL or
     * when the pointer leaves. Long-clickable views fire
     * performLongClick on a long-press handoff from the gesture
     * detector (see {@link #onLongPressFallback}).
     */
    public boolean onTouchEvent(MotionEvent event) {
        if (!enabled) return false;
        if (!clickable && !longClickable) return false;
        int action = event.getActionMasked();
        float x = event.getX();
        float y = event.getY();
        boolean inside = containsPoint(x, y);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!inside) return false;
                setPressed(true);
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
            case MotionEvent.ACTION_MOVE:
                setPressed(inside);
                return true;
            case MotionEvent.ACTION_POINTER_UP:
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

    /**
     * Long-press handoff: views with a GestureDetector route the
     * detector's onLongPress here to fire performLongClick. The
     * default wiring (see {@link #setGestureDetector}) installs this
     * automatically; detached detectors must call it manually.
     */
    protected void onLongPressFallback(MotionEvent event) {
        if (longClickable && (isPressed() || containsPoint(event.getX(), event.getY()))) {
            performLongClick();
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

    public boolean performLongClick() {
        if (longClickListener != null) return longClickListener.onLongClick(this);
        return false;
    }

    public boolean containsPoint(float x, float y) {
        return x >= left && x < right && y >= top && y < bottom;
    }

    /**
     * Draw this view clipped to its own bounds. The canvas is translated
     * so subclasses draw in view-relative coordinates.
     */
    public void draw(Canvas canvas) {
        if (visibility != VISIBLE) return;
        canvas.save();
        canvas.clipRect(left, top, right, bottom);
        if (background != null) {
            background.setBounds(left, top, right, bottom);
            background.draw(canvas);
        }
        onDraw(canvas);
        dispatchDraw(canvas);
        canvas.restore();
    }

    protected void onDraw(Canvas canvas) {
        if (background == null) canvas.drawColor(backgroundColor);
    }

    /** Hook for groups to draw children; plain views draw nothing extra. */
    protected void dispatchDraw(Canvas canvas) {}

    public void drawableStateChanged() {}

    public void jumpDrawablesToCurrentState() {}

    protected boolean setFrame(int left, int top, int right, int bottom) {
        layout(left, top, right, bottom);
        return true;
    }

    public boolean getFocusedRect(Rect r) {
        if (r != null) r.set(left, top, right, bottom);
        return true;
    }

    public ViewTreeObserver getViewTreeObserver() {
        return ViewTreeObserver.getInstance();
    }

    private AccessibilityDelegate accessibilityDelegate;
    private boolean keepScreenOn;

    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        this.accessibilityDelegate = delegate;
    }

    public AccessibilityDelegate getAccessibilityDelegate() {
        return accessibilityDelegate;
    }

    public void setKeepScreenOn(boolean keepScreenOn) {
        this.keepScreenOn = keepScreenOn;
    }

    public boolean getKeepScreenOn() {
        return keepScreenOn;
    }

    public boolean performHapticFeedback(int feedbackConstant) {
        return false;
    }

    public boolean performHapticFeedback(int feedbackConstant, int flags) {
        return false;
    }

    public static class AccessibilityDelegate {}

    public static class BaseSavedState extends AbsSavedState {
        public BaseSavedState(android.os.Parcelable superState) {
            super(superState);
        }
    }

    protected android.os.Parcelable onSaveInstanceState() {
        return AbsSavedState.EMPTY_STATE;
    }

    protected void onRestoreInstanceState(android.os.Parcelable state) {}

    public ViewPropertyAnimator animate() {
        return new ViewPropertyAnimator(this);
    }

    public void setAnimation(android.view.animation.Animation animation) {}
    public void startAnimation(android.view.animation.Animation animation) {}
    public void clearAnimation() {}

    public boolean fitSystemWindows(Rect insets) {
        return false;
    }

    public WindowInsets onApplyWindowInsets(WindowInsets insets) {
        return insets;
    }

    private int importantForAccessibility;
    private int importantForAutofill;

    public void setImportantForAccessibility(int mode) {
        this.importantForAccessibility = mode;
    }

    public int getImportantForAccessibility() {
        return importantForAccessibility;
    }

    public void setImportantForAutofill(int mode) {
        this.importantForAutofill = mode;
    }

    public int getImportantForAutofill() {
        return importantForAutofill;
    }

    public void onInitializeAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {}
    public void onPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}

    public void drawableHotspotChanged(float x, float y) {}

    public boolean hasOverlappingRendering() {
        return true;
    }

    public void saveAttributeDataForStyleable(android.content.Context context, int[] styleable,
            android.util.AttributeSet attrs, android.content.res.TypedArray t, int defStyleAttr,
            int defStyleRes) {}

    public int getBaseline() {
        return -1;
    }
}
