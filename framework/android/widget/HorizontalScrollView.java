// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;

/** Horizontal scroll container: single child, offset recorded. */
public class HorizontalScrollView extends FrameLayout {
    private int scrollX;

    public HorizontalScrollView(Context context) {
        super(context);
    }

    public HorizontalScrollView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public HorizontalScrollView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public boolean fullScroll(int direction) {
        return false;
    }

    public void scrollTo(int x, int y) {
        this.scrollX = x;
    }

    public int getScrollX() {
        return scrollX;
    }
}
