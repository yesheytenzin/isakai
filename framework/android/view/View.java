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
    public static final android.util.Property<View, Float> TRANSLATION_Z =
            new android.util.Property<View, Float>(Float.class, "translationZ") {
                @Override
                public void set(View view, Float value) {
                    view.setTranslationZ(value == null ? 0.0f : value.floatValue());
                }

                @Override
                public Float get(View view) {
                    return view.getTranslationZ();
                }
            };

    public interface OnDragListener { boolean onDrag(View view,DragEvent event); }
    private OnDragListener dragListener;
    public void setOnDragListener(OnDragListener listener) {
        dragListener=listener;
        if(listener!=null)org.isakai.runtime.Capabilities.unavailable("android.view.View.hostDragAndDrop");
    }
    public boolean dispatchDragEvent(DragEvent event) {
        return dragListener!=null && dragListener.onDrag(this,event) || onDragEvent(event);
    }
    public boolean onDragEvent(DragEvent event) { return false; }
    public final void cancelPendingInputEvents() { onCancelPendingInputEvents(); }
    public void onCancelPendingInputEvents() { setPressed(false); }

    private CharSequence tooltipText;
    public void setTooltipText(CharSequence text) {
        tooltipText=text;
        if(text!=null && text.length()>0)org.isakai.runtime.Capabilities.unavailable("android.view.View.tooltipRendering");
    }
    public CharSequence getTooltipText() { return tooltipText; }
    protected int computeHorizontalScrollOffset() { return getScrollX(); }
    protected int computeVerticalScrollOffset() { return getScrollY(); }
    protected int computeHorizontalScrollExtent() { return getWidth(); }
    protected int computeVerticalScrollExtent() { return getHeight(); }
    protected int computeHorizontalScrollRange() { return getWidth(); }
    protected int computeVerticalScrollRange() { return getHeight(); }
    private int systemUiVisibility;
    public int getSystemUiVisibility() { return systemUiVisibility; }
    public int getWindowSystemUiVisibility() { return systemUiVisibility; }
    public void setSystemUiVisibility(int flags) {
        systemUiVisibility=flags;
        if(flags!=0)org.isakai.runtime.Capabilities.unavailable("android.view.View.androidSystemBars");
    }
    private boolean fitsSystemWindows;
    public boolean getFitsSystemWindows() { return fitsSystemWindows; }
    public void setFitsSystemWindows(boolean fits) { fitsSystemWindows=fits; requestLayout(); }
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

    public static final int NOT_FOCUSABLE = 0;
    public static final int FOCUSABLE = 1;
    public static final int FOCUSABLE_AUTO = 16;
    public static final int FOCUSABLE_NONE = 0;
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

    public static final int SCROLL_INDICATORS_NONE = 0;
    public static final int SCROLL_INDICATORS_TOP = 2;
    public static final int SCROLL_INDICATORS_BOTTOM = 8;
    public static final int SCROLL_INDICATORS_LEFT = 1;
    public static final int SCROLL_INDICATORS_RIGHT = 4;
    public static final int SCROLL_INDICATORS_START = 1;
    public static final int SCROLL_INDICATORS_END = 4;
    public static final int SCROLL_INDICATORS_ALL = 15;

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
    private android.graphics.drawable.Drawable foreground;

    private int id = NO_ID;
    private final android.view.autofill.AutofillId autofillId =
            new android.view.autofill.AutofillId(generateViewId());
    private Object tag;
    private java.util.HashMap<Integer, Object> keyedTags;
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
    private float rotationX;
    private float rotationY;
    private float scaleX = 1.0f;
    private float scaleY = 1.0f;
    private float pivotX;
    private float pivotY;
    private float cameraDistance = 1280.0f * 8.0f;
    private float transitionAlpha = 1.0f;
    private boolean pivotSet;
    private int layoutDirection = LAYOUT_DIRECTION_INHERIT;
    private CharSequence contentDescription;
    private CharSequence stateDescription;
    private String transitionName;
    private int scrollIndicators;
    private android.content.res.ColorStateList backgroundTintList;
    private android.graphics.PorterDuff.Mode backgroundTintMode;
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
    private boolean selected;
    private boolean longPressFired;
    private float downX;
    private float downY;
    private Runnable pendingLongPress;
    private boolean attachedToWindow;
    private boolean hasTransientState;
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

    public android.window.OnBackInvokedDispatcher findOnBackInvokedDispatcher() {
        android.content.Context context=viewContext;
        java.util.Set<android.content.Context> seen=java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<>());
        while(context!=null && seen.add(context)) {
            if(context instanceof android.app.Activity)return ((android.app.Activity)context).getOnBackInvokedDispatcher();
            if(!(context instanceof android.content.ContextWrapper))break;
            context=((android.content.ContextWrapper)context).getBaseContext();
        }
        return null;
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
        if (keyedTags == null) keyedTags = new java.util.HashMap<Integer, Object>();
        if (tag == null) keyedTags.remove(key);
        else keyedTags.put(key, tag);
    }

    public Object getTag(int key) {
        return keyedTags == null ? null : keyedTags.get(key);
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

    /**
     * Isakai renders into a single host window; its visibility follows the
     * host compositor, which is visible whenever the app window is, so report
     * the honest constant rather than inventing a hidden state.
     */
    public int getWindowVisibility() {
        return VISIBLE;
    }

    /**
     * Isakai composites every app through the software renderer, so there is
     * no hardware acceleration to report. Returning the honest false steers
     * draw paths (notably Compose) onto their software fallbacks instead of
     * RenderNode-based ones this runtime does not implement.
     */
    public boolean isHardwareAccelerated() {
        return false;
    }

    public void setPadding(int left, int top, int right, int bottom) {
        this.paddingLeft = left;
        this.paddingTop = top;
        this.paddingRight = right;
        this.paddingBottom = bottom;
    }

    public int getPaddingStart() { return isLayoutRtl() ? paddingRight : paddingLeft; }
    public int getPaddingEnd() { return isLayoutRtl() ? paddingLeft : paddingRight; }
    public void setPaddingRelative(int start, int top, int end, int bottom) {
        setPadding(isLayoutRtl() ? end : start, top, isLayoutRtl() ? start : end, bottom);
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

    private int outlineAmbientShadowColor = 0xFF000000;
    private int outlineSpotShadowColor = 0xFF000000;
    private android.graphics.RenderEffect renderEffect;

    /** Outline shadow colors are carried; the software renderer draws no shadows. */
    public void setOutlineAmbientShadowColor(int color) { this.outlineAmbientShadowColor = color; }
    public int getOutlineAmbientShadowColor() { return outlineAmbientShadowColor; }
    public void setOutlineSpotShadowColor(int color) { this.outlineSpotShadowColor = color; }
    public int getOutlineSpotShadowColor() { return outlineSpotShadowColor; }

    /** Render effects are carried, not applied: the software canvas has no filters. */
    public void setRenderEffect(android.graphics.RenderEffect renderEffect) {
        this.renderEffect = renderEffect;
    }

    public android.graphics.RenderEffect getRenderEffect() { return renderEffect; }
    private boolean clipToOutline;
    private ViewOutlineProvider outlineProvider = ViewOutlineProvider.BACKGROUND;

    /**
     * Outline clipping is a no-op on the software canvas (CardView calls it
     * unconditionally at inflate); the flag/provider round-trip for callers.
     */
    public void setClipToOutline(boolean clipToOutline) { this.clipToOutline = clipToOutline; }
    public boolean getClipToOutline() { return clipToOutline; }
    public void setOutlineProvider(ViewOutlineProvider provider) {
        if (provider != null) outlineProvider = provider;
    }
    public ViewOutlineProvider getOutlineProvider() { return outlineProvider; }
    public void invalidateOutline() {}


    public void setTranslationX(float tx) { this.translationX = tx; }
    public float getTranslationX() { return translationX; }
    public void setTranslationY(float ty) { this.translationY = ty; }
    public float getTranslationY() { return translationY; }
    public void setTranslationZ(float tz) { this.translationZ = tz; }
    public float getTranslationZ() { return translationZ; }
    /** AOSP: Z is elevation + translationZ; setting Z moves translationZ. */
    public float getZ() { return elevation + translationZ; }
    public void setZ(float z) { this.translationZ = z - elevation; }

    public void setRotation(float rotation) { this.rotation = rotation; }
    public float getRotation() { return rotation; }
    public void setRotationX(float rotationX) { this.rotationX = rotationX; }
    public float getRotationX() { return rotationX; }
    public void setRotationY(float rotationY) { this.rotationY = rotationY; }
    public float getRotationY() { return rotationY; }
    public void setScaleX(float sx) { this.scaleX = sx; }
    public float getScaleX() { return scaleX; }
    public void setScaleY(float sy) { this.scaleY = sy; }
    public float getScaleY() { return scaleY; }
    public void setPivotX(float pivotX) { this.pivotX = pivotX; this.pivotSet = true; }
    public float getPivotX() { return pivotX; }
    public void setPivotY(float pivotY) { this.pivotY = pivotY; this.pivotSet = true; }
    public float getPivotY() { return pivotY; }
    public boolean isPivotSet() { return pivotSet; }
    public void resetPivot() { this.pivotSet = false; }
    public void setCameraDistance(float distance) { this.cameraDistance = distance; }
    public float getCameraDistance() { return cameraDistance; }
    public void setTransitionAlpha(float alpha) { this.transitionAlpha = alpha; }
    public float getTransitionAlpha() { return transitionAlpha; }

    /** AOSP: X/Y are the view's origin in its parent, translation included. */
    public void setX(float x) { this.translationX = x - left; }
    public float getX() { return left + translationX; }
    public void setY(float y) { this.translationY = y - top; }
    public float getY() { return top + translationY; }

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

    /** Tooltip/accessibility state text; stored for introspection, not announced. */
    public void setStateDescription(CharSequence stateDescription) {
        this.stateDescription = stateDescription;
    }

    public CharSequence getStateDescription() {
        return stateDescription;
    }

    /** Transition identity is plain data on this platform: stored, never animated. */
    public void setTransitionName(String transitionName) {
        this.transitionName = transitionName;
    }

    public String getTransitionName() {
        return transitionName;
    }

    public void setScrollIndicators(int indicators) {
        this.scrollIndicators = indicators;
    }

    public void setScrollIndicators(int indicators, int mask) {
        this.scrollIndicators = (this.scrollIndicators & ~mask) | (indicators & mask);
    }

    public int getScrollIndicators() {
        return scrollIndicators;
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

    /** Host display identity for attached views; dimensions come from Resources. */
    public Display getDisplay() {
        return attachedToWindow ? new Display(0) : null;
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
        refreshDrawableState();
    }

    public void setBackgroundDrawable(android.graphics.drawable.Drawable background) {
        setBackground(background);
    }

    public android.graphics.drawable.Drawable getBackground() {
        return background;
    }

    public void setForeground(android.graphics.drawable.Drawable foreground) {
        this.foreground = foreground;
    }

    public android.graphics.drawable.Drawable getForeground() {
        return foreground;
    }

    public void setBackgroundResource(int resId) {
        setBackground(new android.graphics.drawable.ColorDrawable(0));
    }

    public void setBackgroundTintList(android.content.res.ColorStateList tint) {
        this.backgroundTintList = tint;
    }

    public android.content.res.ColorStateList getBackgroundTintList() {
        return backgroundTintList;
    }

    public void setBackgroundTintMode(android.graphics.PorterDuff.Mode mode) {
        this.backgroundTintMode = mode;
    }

    public android.graphics.PorterDuff.Mode getBackgroundTintMode() {
        return backgroundTintMode;
    }

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

    public static class MeasureSpec {
        public static final int UNSPECIFIED = android.view.MeasureSpec.UNSPECIFIED;
        public static final int EXACTLY = android.view.MeasureSpec.EXACTLY;
        public static final int AT_MOST = android.view.MeasureSpec.AT_MOST;
        public static int makeMeasureSpec(int size, int mode) { return android.view.MeasureSpec.makeMeasureSpec(size, mode); }
        public static int getMode(int spec) { return android.view.MeasureSpec.getMode(spec); }
        public static int getSize(int spec) { return android.view.MeasureSpec.getSize(spec); }
    }
    public static int resolveSize(int desired, int spec) {
        int mode = MeasureSpec.getMode(spec), size = MeasureSpec.getSize(spec);
        return mode == MeasureSpec.EXACTLY ? size : mode == MeasureSpec.AT_MOST ? Math.min(desired, size) : desired;
    }
    public static final int MEASURED_SIZE_MASK=0x00ffffff, MEASURED_STATE_MASK=0xff000000;
    public static final int MEASURED_HEIGHT_STATE_SHIFT=16, MEASURED_STATE_TOO_SMALL=0x01000000;
    public static int resolveSizeAndState(int desired, int spec, int state) {
        int result=resolveSize(desired,spec);
        if(MeasureSpec.getMode(spec)==MeasureSpec.AT_MOST && desired>MeasureSpec.getSize(spec)) result|=MEASURED_STATE_TOO_SMALL;
        return result|(state&MEASURED_STATE_MASK);
    }
    public final int getMeasuredWidthAndState() { return measuredWidth; }
    public final int getMeasuredHeightAndState() { return measuredHeight; }
    public final int getMeasuredState() {
        return (measuredWidth&MEASURED_STATE_MASK)|((measuredHeight>>MEASURED_HEIGHT_STATE_SHIFT)&(MEASURED_STATE_MASK>>MEASURED_HEIGHT_STATE_SHIFT));
    }
    public static int combineMeasuredStates(int current, int next) { return current | next; }
    public static int getDefaultSize(int desired, int spec) {
        return MeasureSpec.getMode(spec) == MeasureSpec.UNSPECIFIED ? desired : MeasureSpec.getSize(spec);
    }
    public void setMeasuredDimension(int width, int height) {
        this.measuredWidth = width;
        this.measuredHeight = height;
    }

    public final void measure(int width, int height) {
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) layoutParams).resolveLayoutDirection(getLayoutDirection());
        }
        onMeasure(width, height);
    }

    private int minimumWidth,minimumHeight;
    public void setMinimumWidth(int value) { minimumWidth=Math.max(0,value);requestLayout(); }
    public void setMinimumHeight(int value) { minimumHeight=Math.max(0,value);requestLayout(); }
    public int getMinimumWidth() { return minimumWidth; }
    public int getMinimumHeight() { return minimumHeight; }
    public int getSuggestedMinimumWidth() { return Math.max(minimumWidth,background==null?0:background.getMinimumWidth()); }
    public int getSuggestedMinimumHeight() { return Math.max(minimumHeight,background==null?0:background.getMinimumHeight()); }
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        setMeasuredDimension(getDefaultSize(getSuggestedMinimumWidth(),widthMeasureSpec),
                getDefaultSize(getSuggestedMinimumHeight(),heightMeasureSpec));
    }

    public final int getMeasuredWidth() {
        return measuredWidth & MEASURED_SIZE_MASK;
    }

    public final int getMeasuredHeight() {
        return measuredHeight & MEASURED_SIZE_MASK;
    }

    public void requestLayout() {
        layoutRequested = true;
        if(parent!=null)parent.requestLayout();
        invalidate();
    }

    public void forceLayout() { layoutRequested=true; }

    public final void layout(int left, int top, int right, int bottom) {
        // AOSP View.layout resolves margin direction before positioning so
        // ConstraintLayout-style params copy guide/constraint fields (a/b/c
        // -> m0/n0/o0) into their resolved slots; without this, guidelines
        // stay at 0 and constrained children collapse (OpenCalc display).
        if (layoutParams instanceof ViewGroup.MarginLayoutParams) {
            ((ViewGroup.MarginLayoutParams) layoutParams).resolveLayoutDirection(getLayoutDirection());
        }
        boolean changed = this.left != left || this.top != top || this.right != right || this.bottom != bottom;
        int oldL = this.left, oldT = this.top, oldR = this.right, oldB = this.bottom;
        this.left = left;
        this.top = top;
        this.right = right;
        this.bottom = bottom;
        laidOut = true;
        layoutRequested = false;
        if (right - left != oldR - oldL || bottom - top != oldB - oldT) {
            onSizeChanged(right - left, bottom - top, oldR - oldL, oldB - oldT);
        }
        onLayout(changed, left, top, right, bottom);
        if (changed) {
            for (OnLayoutChangeListener l : layoutListeners.toArray(new OnLayoutChangeListener[0])) {
                l.onLayoutChange(this, left, top, right, bottom, oldL, oldT, oldR, oldB);
            }
        }
    }

    protected void onLayout(boolean changed, int left, int top, int right, int bottom) { onLayout(left, top, right, bottom); }

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

    /** Direct geometry setters (hidden API in AOSP; apps use them for custom layout). */
    public void setLeft(int left) { this.left = left; }
    public void setTop(int top) { this.top = top; }

    public void setRight(int right) { this.right = right; }

    public void setBottom(int bottom) { this.bottom = bottom; }

    /** Cancel a scheduled drawable callback; nothing is scheduled by the software host. */
    public void unscheduleDrawable(android.graphics.drawable.Drawable who) {
        unscheduleDrawable(who, null);
    }

    public void unscheduleDrawable(android.graphics.drawable.Drawable who, Runnable what) {
        if (who != null && what != null) who.unscheduleSelf(what);
    }

    public void scheduleDrawable(android.graphics.drawable.Drawable who, Runnable what, long when) {
        if (who != null && what != null) who.scheduleSelf(what, when);
    }

    public final int getWidth() {
        return right - left;
    }

    public final int getHeight() {
        return bottom - top;
    }

    public void getLocationOnScreen(int[] out) {
        getLocationInWindow(out);
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
        if (action == null) return false;
        return postDelayed(action, 0);
    }

    /** Main-looper handler for this view's actions (Compose recomposer cleanup). */
    public android.os.Handler getHandler() {
        return mainHandler();
    }
    /**
     * Deferred post on the main looper queue (the window loop pumps it every
     * slice). Delays are honoured: long-press arming depends on it. Actions
     * without a delay still land on the queue, never inline.
     */
    public boolean postDelayed(Runnable action, long delayMillis) {
        if (action == null) return false;
        android.os.Handler handler = mainHandler();
        if (handler == null) {
            if (delayMillis <= 0) action.run();
            return true;
        }
        return handler.postDelayed(action, Math.max(0, delayMillis));
    }

    /**
     * One process-wide handler for the main looper: the queue matches
     * messages by their Handler target, so posting and removing must use the
     * same instance.
     */
    private static synchronized android.os.Handler mainHandler() {
        android.os.Handler cached = MAIN_HANDLER;
        if (cached != null) return cached;
        android.os.Looper looper = android.os.Looper.getMainLooper();
        if (looper == null) return null;
        cached = new android.os.Handler(looper);
        MAIN_HANDLER = cached;
        return cached;
    }

    private static volatile android.os.Handler MAIN_HANDLER;

    public void postOnAnimation(Runnable action) {
        post(action);
    }

    public void postOnAnimationDelayed(Runnable action, long delayMillis) {
        if (action == null) return;
        postDelayed(action, Math.max(0, delayMillis));
    }

    public void postInvalidateOnAnimation() {
        invalidate();
    }

    public boolean removeCallbacks(Runnable action) {
        android.os.Handler handler = mainHandler();
        if (handler == null || action == null) return false;
        boolean queued = handler.hasCallbacks(action);
        handler.removeCallbacks(action);
        return queued;
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

    /**
     * AOSP semantics: attaching a click listener makes the view clickable
     * (apps rarely call setClickable themselves; without this the touch
     * path drops the event and the listener never fires).
     */
    public void setOnClickListener(OnClickListener l) {
        if (l != null && !clickable) clickable = true;
        this.clickListener = l;
    }

    public boolean hasOnClickListeners() {
        return clickListener != null;
    }

    public void setOnKeyListener(OnKeyListener l) {
        this.keyListener = l;
    }

    public boolean onCheckIsTextEditor() {
        return false;
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

    /** AOSP semantics: attaching a long-click listener makes the view long-clickable. */
    public void setOnLongClickListener(OnLongClickListener l) {
        if (l != null && !longClickable) longClickable = true;
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

    private OnApplyWindowInsetsListener insetsListener;
    private boolean applyInsetsRequested;
    public void setOnApplyWindowInsetsListener(OnApplyWindowInsetsListener l) { insetsListener=l; }
    public void requestApplyInsets() {
        View root=this;
        while(root.getParent() instanceof View)root=(View)root.getParent();
        root.applyInsetsRequested=true;
        root.requestLayout();
    }
    /** Desktop content has no Android system-bar/IME inset backend. */
    public final void dispatchHostInsets() {
        if(!applyInsetsRequested)return;
        applyInsetsRequested=false;
        dispatchApplyWindowInsets(new WindowInsets());
    }
    public WindowInsets dispatchApplyWindowInsets(WindowInsets insets) {
        return insetsListener==null?onApplyWindowInsets(insets):insetsListener.onApplyWindowInsets(this,insets);
    }

    /**
     * AOSP contract: consume whatever the view wants from {@code insets} and
     * write the remainder into {@code outLocalInsets}. Isakai has no system
     * bars, so nothing is consumed and the local insets stay empty.
     */
    public WindowInsets computeSystemWindowInsets(WindowInsets insets,
            android.graphics.Rect outLocalInsets) {
        if (outLocalInsets != null) outLocalInsets.setEmpty();
        return insets == null ? new WindowInsets() : insets;
    }

    /**
     * Insets animations never run on this platform (there is no IME or
     * system-bar animation backend), so the callback is accepted and never
     * invoked, matching a device whose insets stay still.
     */
    public void setWindowInsetsAnimationCallback(WindowInsetsAnimation.Callback callback) {}

    private WindowInsetsController windowInsetsController;
    public WindowInsetsController getWindowInsetsController() {
        if(windowInsetsController==null)windowInsetsController=new WindowInsetsControllerImpl();
        return windowInsetsController;
    }
    public WindowInsets getRootWindowInsets() { return new WindowInsets(); }

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
        if (this.enabled != enabled) {
            this.enabled = enabled;
            invalidate();
            refreshDrawableState();
        }
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

    private static final int[] EMPTY_STATE = new int[0];

    /**
     * AOSP merges {@code R.attr.state_*} flags from this view and its parents.
     * Isakai tracks no drawable-state sets, so the honest answer is the empty
     * set; state-list drawables fall back to their default item.
     */
    public int[] getDrawableState() {
        return EMPTY_STATE;
    }


    public void setFocusable(boolean focusable) {
        this.focusable = focusable;
    }

    public void setFocusable(int focusable) {
        this.focusable = focusable != FOCUSABLE_NONE && focusable != NOT_FOCUSABLE;
    }

    public boolean isFocusable() {
        return focusable;
    }

    public void setFocusableInTouchMode(boolean focusable) {
        this.focusable = focusable;
    }

    private boolean keyboardNavigationCluster;
    public void setKeyboardNavigationCluster(boolean cluster) { keyboardNavigationCluster = cluster; }
    public boolean isKeyboardNavigationCluster() { return keyboardNavigationCluster; }

    public boolean isFocused() {
        return focused;
    }

    public boolean hasFocus() {
        return focused;
    }

    public View findFocus() { return focused ? this : null; }
    /** AOSP: focusable and enabled (groups override to include descendants). */
    public boolean hasFocusable() {
        return focusable && enabled;
    }


    public boolean hasWindowFocus() {
        return focused;
    }

    public boolean isInTouchMode() {
        return false;
    }

    /** AOSP transient-state flag (RecyclerView tracks recycled views with it). */
    public boolean hasTransientState() {
        return hasTransientState;
    }

    public void setHasTransientState(boolean hasTransientState) {
        this.hasTransientState = hasTransientState;
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
            refreshDrawableState();
        }
    }

    public boolean isSelected() {
        return selected;
    }

    public void setSelected(boolean selected) {
        if (this.selected != selected) {
            this.selected = selected;
            invalidate();
            refreshDrawableState();
        }
    }

    /** Take focus when focusable and enabled; fires onFocusChanged. */
    public boolean requestFocus(int direction) {
        return requestFocus();
    }

    public boolean requestFocus() {
        if (!focusable || !enabled) return false;
        if (parent instanceof ViewGroup) ((ViewGroup) parent).requestChildFocus(this, this);
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
    /**
     * Scrollbars are not rendered on the software canvas; callers only need
     * the lifecycle return values (RecyclerView awakens bars on data change).
     */
    public boolean awakenScrollBars() { return awakenScrollBars(0, true); }
    protected boolean awakenScrollBars(int startDelay, boolean invalidate) { return false; }
    public void setHorizontalScrollBarEnabled(boolean enabled) {}
    public void setVerticalScrollBarEnabled(boolean enabled) {}
    public boolean isHorizontalScrollBarEnabled() { return false; }
    public boolean isVerticalScrollBarEnabled() { return false; }


    /** Host window attachment, separate from overridable application hooks. */
    public final void dispatchHostAttached() {
        if (attachedToWindow) return;
        windowToken = parent instanceof View && ((View) parent).getWindowToken() != null
                ? ((View) parent).getWindowToken() : new android.os.Binder();
        attachedToWindow = true;
        onAttachedToWindow();
        refreshDrawableState();
        requestApplyInsets();
        for (OnAttachStateChangeListener l : attachListeners.toArray(new OnAttachStateChangeListener[0])) {
            l.onViewAttachedToWindow(this);
        }
        if (this instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) this;
            java.util.ArrayList<View> snapshot = new java.util.ArrayList<>();
            for (int i = 0; i < group.getChildCount(); i++) snapshot.add(group.getChildAt(i));
            for (View child : snapshot) if (child.getParent() == group) child.dispatchHostAttached();
        }
    }

    public final void dispatchHostDetached() {
        if (!attachedToWindow) return;
        onDetachedFromWindow();
        for (OnAttachStateChangeListener l : attachListeners.toArray(new OnAttachStateChangeListener[0])) {
            l.onViewDetachedFromWindow(this);
        }
        attachedToWindow = false;
        windowToken = null;
        if (this instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) this;
            java.util.ArrayList<View> snapshot = new java.util.ArrayList<>();
            for (int i = 0; i < group.getChildCount(); i++) snapshot.add(group.getChildAt(i));
            for (View child : snapshot) child.dispatchHostDetached();
        }
    }

    protected void onAttachedToWindow() {}

    protected void onDetachedFromWindow() {}

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
        // ViewGroup.dispatchTouchEvent offsets the event into each child's
        // parent frame before dispatch; bounds are parent-relative, so the
        // event coords hit-test directly.
        float x = event.getX();
        float y = event.getY();
        boolean inside = containsPoint(x, y);
        switch (action) {
            case MotionEvent.ACTION_DOWN:
                if (!inside) return false;
                setPressed(true);
                downX = x;
                downY = y;
                longPressFired = false;
                if (longClickable) armLongPress();
                return true;
            case MotionEvent.ACTION_POINTER_DOWN:
                // Extra pointers end long-press candidacy (AOSP behaviour).
                cancelLongPress();
                return true;
            case MotionEvent.ACTION_MOVE:
                setPressed(inside);
                if (longClickable && Math.abs(x - downX) > touchSlop() || Math.abs(y - downY) > touchSlop()) {
                    cancelLongPress();
                }
                return true;
            case MotionEvent.ACTION_POINTER_UP:
            case MotionEvent.ACTION_UP:
                cancelLongPress();
                if (!pressed) return false;
                setPressed(false);
                if (inside && !longPressFired) {
                    performClick();
                    return true;
                }
                return false;
            case MotionEvent.ACTION_CANCEL:
                cancelLongPress();
                setPressed(false);
                return true;
            default:
                return false;
        }
    }

    private static int touchSlop() {
        return android.view.ViewConfiguration.get(null).getScaledTouchSlop();
    }

    /** Arm the pending long-press check on the main queue. */
    private void armLongPress() {
        cancelLongPress();
        pendingLongPress = () -> {
            pendingLongPress = null;
            if (!pressed || !longClickable) return;
            longPressFired = true;
            performLongClick();
        };
        postDelayed(pendingLongPress, android.view.ViewConfiguration.getLongPressTimeout());
    }

    public void cancelLongPress() {
        if (pendingLongPress != null) {
            removeCallbacks(pendingLongPress);
            pendingLongPress = null;
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
     * Draw this view clipped to its own bounds. Bounds are parent-relative,
     * so each level offsets by its own left/top; the canvas clips in root
     * coordinates and children compose offsets down the tree.
     */
    public void draw(Canvas canvas) {
        drawAt(canvas, 0, 0);
    }

    /** Draw with an accumulated parent offset (root coordinates). */
    public void drawAt(Canvas canvas, int offsetX, int offsetY) {
        if (visibility != VISIBLE) return;
        int absLeft = offsetX + left;
        int absTop = offsetY + top;
        canvas.save();
        if (right > left && bottom > top) {
            // AOSP never clips a view to its own bounds; empty-bounds containers
            // (compose's DrawChildContainer is 0x0 by design) must stay unclipped
            // or they degenerate the shared clip and drop all their children.
            // Float variant: compose positions layer views with canvas.translate,
            // so the bounds clip must map through the current matrix.
            canvas.clipRect((float) absLeft, (float) absTop,
                    (float) (absLeft + (right - left)), (float) (absTop + (bottom - top)));
        }
        if (background != null) {
            background.setBounds(absLeft, absTop, absLeft + (right - left), absTop + (bottom - top));
            background.draw(canvas);
        }
        viewTreeObserver.dispatchOnDraw();
        onDrawAt(canvas, absLeft, absTop);
        if (this instanceof ViewGroup) {
            dispatchDrawAt(canvas, absLeft, absTop);
        } else {
            // Translated leaf views (Compose layers, custom holders) do their
            // custom drawing in the AOSP dispatchDraw hook, which this
            // pipeline never invoked. Framework leaves do not override it,
            // so this is a no-op for them.
            dispatchDraw(canvas);
        }
        canvas.restore();
    }

    /** Subclass content draw with this view's root offset. Default keeps legacy behavior. */
    protected void onDrawAt(Canvas canvas, int offsetX, int offsetY) {
        onDraw(canvas);
    }

    /** Group child dispatch with this view's root offset. Default draws nothing extra. */
    protected void dispatchDrawAt(Canvas canvas, int offsetX, int offsetY) {}

    /** AOSP child/custom dispatch hook. Empty here; translated leaf views
     * override it, and drawAt invokes it for leaves so their custom drawing
     * runs. Groups keep the offset-aware dispatchDrawAt path above. */
    protected void dispatchDraw(Canvas canvas) {}

    protected void onDraw(Canvas canvas) {
        // The fill stands in for a setBackgroundColor and doubles as the
        // intra-frame erase between repeated passes. Only bounded views may
        // fill: a zero-size view gets no clip of its own (drawAt skips the
        // empty-bounds clip), so its fill would paint whole-canvas clips
        // white and wipe content drawn before it (re-added compose hosts
        // draw last with 0x0 bounds).
        if (background == null && right > left && bottom > top) {
            canvas.drawColor(backgroundColor);
        }
    }

    /**
     * AOSP parity: push this view's drawable state into the stateful
     * background/foreground so state-list tints (e.g. the FAB container
     * color) resolve. The empty state resolves their default item.
     */
    public void drawableStateChanged() {
        int[] state = getDrawableState();
        boolean changed = false;
        if (background != null && background.isStateful()) changed |= background.setState(state);
        if (foreground != null && foreground.isStateful()) changed |= foreground.setState(state);
        if (changed) invalidate();
    }

    public void refreshDrawableState() {
        drawableStateChanged();
    }

    public void jumpDrawablesToCurrentState() {}

    protected boolean setFrame(int left, int top, int right, int bottom) {
        layout(left, top, right, bottom);
        return true;
    }

    public void getFocusedRect(Rect r) {
        if (r != null) r.set(left, top, right, bottom);
    }

    private final ViewTreeObserver viewTreeObserver = new ViewTreeObserver();
    public interface OnScrollChangeListener { void onScrollChange(View view,int x,int y,int oldX,int oldY); }
    private OnScrollChangeListener scrollChangeListener;
    private int hostScrollX,hostScrollY;
    public void setOnScrollChangeListener(OnScrollChangeListener listener){scrollChangeListener=listener;}
    public int getScrollX(){return hostScrollX;}
    public int getScrollY(){return hostScrollY;}
    public void scrollTo(int x,int y){
        int oldX=hostScrollX,oldY=hostScrollY;hostScrollX=x;hostScrollY=y;
        if(x!=oldX || y!=oldY){
            onScrollChanged(x,y,oldX,oldY);
            if(scrollChangeListener!=null)scrollChangeListener.onScrollChange(this,x,y,oldX,oldY);
            viewTreeObserver.dispatchOnScrollChanged();invalidate();
        }
    }
    protected void onScrollChanged(int l,int t,int oldl,int oldt) {}
    public void scrollBy(int x,int y){scrollTo(hostScrollX+x,hostScrollY+y);}

    /**
     * Nested-scroll child side: inert defaults (no parent cooperation),
     * matching the ViewParent defaults. Views that need real nested
     * scrolling override these.
     */
    private boolean nestedScrollingEnabled;

    public void setNestedScrollingEnabled(boolean enabled) {
        nestedScrollingEnabled = enabled;
    }

    public boolean isNestedScrollingEnabled() {
        return nestedScrollingEnabled;
    }

    public boolean startNestedScroll(int axes) {
        return startNestedScroll(axes, 0);
    }

    public boolean startNestedScroll(int axes, int type) {
        return false;
    }

    public void stopNestedScroll() {}

    public void stopNestedScroll(int type) {}

    public boolean hasNestedScrollingParent() {
        return false;
    }

    public boolean hasNestedScrollingParent(int type) {
        return false;
    }

    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow) {
        return false;
    }

    public boolean dispatchNestedScroll(int dxConsumed, int dyConsumed,
            int dxUnconsumed, int dyUnconsumed, int[] offsetInWindow, int type) {
        return false;
    }

    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed,
            int[] offsetInWindow) {
        return false;
    }

    public boolean dispatchNestedPreScroll(int dx, int dy, int[] consumed,
            int[] offsetInWindow, int type) {
        return false;
    }

    public boolean dispatchNestedFling(float velocityX, float velocityY, boolean consumed) {
        return false;
    }

    public boolean dispatchNestedPreFling(float velocityX, float velocityY) {
        return false;
    }
    public void dispatchHostLayout() {
        viewTreeObserver.dispatchOnGlobalLayout();
        if(this instanceof ViewGroup){
            ViewGroup group=(ViewGroup)this;
            for(int i=0;i<group.getChildCount();i++)group.getChildAt(i).dispatchHostLayout();
        }
    }
    public boolean dispatchHostPreDraw() {
        boolean cancel=viewTreeObserver.dispatchOnPreDraw();
        if(this instanceof ViewGroup){
            ViewGroup group=(ViewGroup)this;
            for(int i=0;i<group.getChildCount();i++)cancel|=group.getChildAt(i).dispatchHostPreDraw();
        }
        return cancel;
    }
    public ViewTreeObserver getViewTreeObserver() {
        return viewTreeObserver;
    }

    private AccessibilityDelegate accessibilityDelegate;
    private boolean keepScreenOn;

    public void setAccessibilityDelegate(AccessibilityDelegate delegate) {
        this.accessibilityDelegate = delegate;
    }

    public AccessibilityDelegate getAccessibilityDelegate() {
        return accessibilityDelegate;
    }

    /**
     * Fire-and-forget accessibility notification, AOSP-shaped: when the
     * manager reports disabled (the honest state: Isakai has no host
     * accessibility backend), this returns without delivering, exactly as
     * Android does with the service off. Callers such as sliding panels
     * continue normally after the call.
     */
    public void sendAccessibilityEvent(int eventType) {
        Object service = getContext() == null ? null
                : getContext().getSystemService(android.content.Context.ACCESSIBILITY_SERVICE);
        if (!(service instanceof android.view.accessibility.AccessibilityManager)) return;
        android.view.accessibility.AccessibilityManager manager =
                (android.view.accessibility.AccessibilityManager) service;
        if (!manager.isEnabled()) return;
        android.view.accessibility.AccessibilityEvent event =
                android.view.accessibility.AccessibilityEvent.obtain(eventType);
        event.setPackageName(getContext().getPackageName());
        event.setClassName(getClass().getName());
        sendAccessibilityEventUnchecked(event);
    }

    public void sendAccessibilityEventUnchecked(android.view.accessibility.AccessibilityEvent event) {
        if (event == null) return;
        try {
            ViewParent parent = getParent();
            if (parent != null) parent.requestSendAccessibilityEvent(this, event);
        } finally {
            event.recycle();
        }
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

    /**
     * UI click sounds have no host backend (like haptics above); accepted
     * silently so sound-enabled widgets stay clickable.
     */
    public void playSoundEffect(int soundConstant) {}

    public boolean performHapticFeedback(int feedbackConstant, int flags) {
        return false;
    }

    public static class AccessibilityDelegate {}

    public static class BaseSavedState extends AbsSavedState {
        public BaseSavedState(android.os.Parcelable superState) {
            super(superState);
        }
    }

    private boolean saveEnabled=true;
    private boolean saveFromParentEnabled=true;
    public void setSaveFromParentEnabled(boolean enabled) { saveFromParentEnabled=enabled; }
    public boolean isSaveFromParentEnabled() { return saveFromParentEnabled; }
    public void setSaveEnabled(boolean enabled) { saveEnabled=enabled; }
    public boolean isSaveEnabled() { return saveEnabled; }
    public void saveHierarchyState(android.util.SparseArray<android.os.Parcelable> state) { dispatchSaveInstanceState(state); }
    public void restoreHierarchyState(android.util.SparseArray<android.os.Parcelable> state) { dispatchRestoreInstanceState(state); }
    protected void dispatchSaveInstanceState(android.util.SparseArray<android.os.Parcelable> state) {
        if(saveEnabled && id!=NO_ID) {
            android.os.Parcelable saved=onSaveInstanceState();if(saved!=null)state.put(id,saved);
        }
    }
    protected void dispatchRestoreInstanceState(android.util.SparseArray<android.os.Parcelable> state) {
        if(saveEnabled && id!=NO_ID && state.get(id)!=null)onRestoreInstanceState(state.get(id));
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
    private int importantForContentCapture;
    private int accessibilityLiveRegion;

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

    public void setImportantForContentCapture(int mode) {
        this.importantForContentCapture = mode;
    }

    public int getImportantForContentCapture() {
        return importantForContentCapture;
    }

    /**
     * No content-capture service exists on this host, so there is nothing
     * that could consume this view's content: report not-important rather
     * than inviting callers to record content nobody will read.
     */
    public boolean isImportantForContentCapture() {
        return false;
    }

    public void setAccessibilityPaneTitle(CharSequence title) {}

    public void setAccessibilityLiveRegion(int mode) {
        this.accessibilityLiveRegion = mode;
    }

    public int getAccessibilityLiveRegion() {
        return accessibilityLiveRegion;
    }

    public android.view.contentcapture.ContentCaptureSession getContentCaptureSession() {
        return null;
    }

    public void setContentCaptureSession(android.view.contentcapture.ContentCaptureSession session) {}

    public void setDefaultFocusHighlightEnabled(boolean enabled) {}

    public boolean getDefaultFocusHighlightEnabled() {
        return false;
    }

    public android.view.autofill.AutofillId getAutofillId() {
        return autofillId;
    }

    public void setForceDarkAllowed(boolean allowed) {}

    public boolean isForceDarkAllowed() {
        return false;
    }

    public void setViewTranslationCallback(android.view.translation.ViewTranslationCallback callback) {}

    public android.view.translation.ViewTranslationCallback getViewTranslationCallback() {
        return null;
    }

    public long getDrawingTime() {
        return System.currentTimeMillis();
    }

    public long getUniqueDrawingId() {
        return System.identityHashCode(this);
    }

    public boolean requestFocusFromTouch() {
        return requestFocus();
    }

    private int layerType;
    private android.graphics.Paint layerPaint;

    public void setLayerType(int layerType, android.graphics.Paint paint) {
        this.layerType = layerType;
        this.layerPaint = paint;
    }

    public int getLayerType() {
        return layerType;
    }

    private android.graphics.Rect clipBounds;

    public void setClipBounds(android.graphics.Rect clipBounds) {
        this.clipBounds = clipBounds;
    }

    public android.graphics.Rect getClipBounds() {
        return clipBounds;
    }

    private android.animation.StateListAnimator stateListAnimator;

    public void setStateListAnimator(android.animation.StateListAnimator animator) {
        this.stateListAnimator = animator;
    }

    public android.animation.StateListAnimator getStateListAnimator() {
        return stateListAnimator;
    }

    public android.view.ActionMode startActionMode(android.view.ActionMode.Callback callback, int type) {
        return null;
    }

    public android.view.ActionMode startActionMode(android.view.ActionMode.Callback callback) {
        return null;
    }

    public void getLocationInWindow(int[] location) {
        if (location == null || location.length < 2) return;
        int x = left, y = top;
        View p = this;
        while (p.parent != null) {
            p = p.parent;
            x += p.left;
            y += p.top;
        }
        location[0] = x;
        location[1] = y;
    }

    public android.graphics.Matrix getMatrix() {
        return new android.graphics.Matrix();
    }

    public void transformMatrixToLocal(android.graphics.Matrix matrix) {}

    public void transformMatrixToGlobal(android.graphics.Matrix matrix) {}

    public boolean dispatchGenericMotionEvent(android.view.MotionEvent event) {
        return false;
    }

    public boolean dispatchGenericPointerEvent(android.view.MotionEvent event) {
        return false;
    }

    private static final java.util.concurrent.atomic.AtomicInteger sNextViewId =
            new java.util.concurrent.atomic.AtomicInteger(1);

    public static int generateViewId() {
        return sNextViewId.getAndIncrement();
    }
    public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info) {}
    public void onInitializeAccessibilityEvent(android.view.accessibility.AccessibilityEvent event) {}
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
