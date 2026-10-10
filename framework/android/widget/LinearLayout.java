// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

/** Lays children in a row or column with weights. */
public class LinearLayout extends ViewGroup {
    public static final int HORIZONTAL = 0;
    public static final int VERTICAL = 1;

    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public float weight;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    private int orientation = VERTICAL;

    public LinearLayout(Context context) {
        super(context);
    }

    public LinearLayout(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public LinearLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setOrientation(int orientation) {
        this.orientation = orientation;
    }

    public int getOrientation() {
        return orientation;
    }

    public void setGravity(int gravity) {}
    public void setWeightSum(float sum) {}

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        int x = left, y = top;
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            child.layout(x, y, x + w, y + h);
            if (orientation == HORIZONTAL) x += w;
            else y += h;
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null && child.getVisibility() == VISIBLE) child.draw(canvas);
        }
    }
}
