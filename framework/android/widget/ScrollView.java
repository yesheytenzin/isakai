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
}
