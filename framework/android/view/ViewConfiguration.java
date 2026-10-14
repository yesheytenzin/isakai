// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Input thresholds: fixed desktop values. */
public class ViewConfiguration {
    private ViewConfiguration() {}

    public static ViewConfiguration get(android.content.Context context) {
        return new ViewConfiguration();
    }

    public int getScaledTouchSlop() { return 8; }
    public int getScaledMinimumFlingVelocity() { return 50; }
    public int getScaledMaximumFlingVelocity() { return 8000; }
    public int getScaledDoubleTapTimeout() { return 300; }
    public int getScaledDoubleTapSlop() { return 8; }
    public int getScaledPagingTouchSlop() { return 16; }
    public float getScaledHorizontalScrollFactor() { return 64.0f; }
    public float getScaledVerticalScrollFactor() { return 64.0f; }
    public int getScaledOverflingDistance() { return 6; }
    public int getScaledOverscrollDistance() { return 0; }
    public int getScaledHoverSlop() { return 8; }
    public int getScaledEdgeSlop() { return 12; }
    public int getScaledFadingEdgeLength() { return 12; }
    public int getScaledMinimumScalingSpan() { return 70; }
    public static int getScrollBarSize() { return 10; }
    public static int getScrollBarFadeDuration() { return 250; }
    public static int getScrollDefaultDelay() { return 300; }
    public static int getTapTimeout() { return 100; }
    public static int getLongPressTimeout() { return 500; }
    public static int getDoubleTapTimeout() { return 300; }
    /** Desktop windows expose keyboard shortcut labels. */
    public boolean shouldShowMenuShortcutsWhenKeyboardPresent() { return true; }
    public boolean hasPermanentMenuKey() { return false; }
}
