// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/** Vertical scroll container: single child, offset recorded. */
public class ScrollView extends FrameLayout {
    private int scrollY;

    public ScrollView(Context context) {
        super(context);
    }

    public ScrollView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public ScrollView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public boolean fullScroll(int direction) {
        return false;
    }

    public void scrollTo(int x, int y) {
        this.scrollY = y;
    }

    public int getScrollX() {
        return 0;
    }

    public int getScrollY() {
        return scrollY;
    }

    /** Scroll just enough to show {@code rectangle} (child coordinates), else no-op. */
    public boolean requestChildRectangleOnScreen(
            android.view.View child, android.graphics.Rect rectangle, boolean immediate) {
        if (child == null || rectangle == null) return false;
        rectangle.offset(child.getLeft() - getScrollX(), child.getTop() - getScrollY());
        int screenTop = getScrollY();
        int screenBottom = screenTop + getHeight();
        if (rectangle.top < screenTop) {
            scrollTo(getScrollX(), rectangle.top);
            return true;
        }
        if (rectangle.bottom > screenBottom) {
            scrollTo(getScrollX(), rectangle.bottom - getHeight());
            return true;
        }
        return false;
    }
}
