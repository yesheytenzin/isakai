// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/**
 * RelativeLayout: rules recorded per child, laid out as a vertical stack
 * (headless: rule solving is out of scope; children stay findable).
 */
public class RelativeLayout extends ViewGroup {
    public static final int TRUE = -1;
    public static final int ALIGN_PARENT_LEFT = 9;
    public static final int ALIGN_PARENT_TOP = 10;
    public static final int ALIGN_PARENT_RIGHT = 11;
    public static final int ALIGN_PARENT_BOTTOM = 12;
    public static final int CENTER_IN_PARENT = 13;
    public static final int CENTER_HORIZONTAL = 14;
    public static final int CENTER_VERTICAL = 15;

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        private final int[] rules = new int[24];

        public LayoutParams(Context c, android.util.AttributeSet attrs) {
            super(WRAP_CONTENT, WRAP_CONTENT);
        }

        public LayoutParams(int w, int h) {
            super(w, h);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }

        public void addRule(int verb) {
            addRule(verb, TRUE);
        }

        public void addRule(int verb, int anchor) {
            if (verb >= 0 && verb < rules.length) rules[verb] = anchor;
        }

        public int[] getRules() {
            return rules.clone();
        }
    }

    public RelativeLayout(Context context) {
        super(context);
    }

    public RelativeLayout(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public RelativeLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int y = t + getPaddingTop();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == View.GONE) continue;
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            child.layout(l + getPaddingLeft(), y, l + getPaddingLeft() + w, y + h);
            y += h;
        }
    }
}
