// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Parent contract: invalidation and layout requests climb here. */
public interface ViewParent {
    void requestLayout();
    void invalidateChild(View child, android.graphics.Rect r);
    ViewParent getParent();
    boolean isLayoutRequested();
    /** Disallow (or re-allow) interception for the current gesture. */
    default void requestDisallowInterceptTouchEvent(boolean disallowIntercept) {}
    /** Bubble an accessibility event toward the root; no host backend consumes it. */
    default boolean requestSendAccessibilityEvent(View child, android.view.accessibility.AccessibilityEvent event) { return false; }
    default boolean onStartNestedScroll(View child, View target, int axes) { return false; }
    default void onNestedScrollAccepted(View child, View target, int axes) {}
    default void onStopNestedScroll(View target) {}
    default void onNestedScroll(View target, int dxConsumed, int dyConsumed, int dxUnconsumed, int dyUnconsumed) {}
    default void onNestedPreScroll(View target, int dx, int dy, int[] consumed) {}
    default boolean onNestedFling(View target, float velocityX, float velocityY, boolean consumed) { return false; }
    default boolean onNestedPreFling(View target, float velocityX, float velocityY) { return false; }
}
