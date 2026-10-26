// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;
import android.view.ViewGroup;

/** Stacks children at the top-left, honoring gravity. */
public class FrameLayout extends ViewGroup {
    public static class LayoutParams extends ViewGroup.MarginLayoutParams {
        public static final int UNSPECIFIED_GRAVITY = -1;

        public int gravity = UNSPECIFIED_GRAVITY;

        public LayoutParams(Context context, android.util.AttributeSet attrs) {
            super(context,attrs);
            android.content.res.TypedArray values=context.obtainStyledAttributes(attrs,new int[]{0x010100b3});
            gravity=values.getInt(0,UNSPECIFIED_GRAVITY);values.recycle();
        }

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, int gravity) {
            super(width, height);
            this.gravity = gravity;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    @Override public LayoutParams generateLayoutParams(android.util.AttributeSet attrs) {
        return new LayoutParams(getContext(),attrs);
    }
    @Override protected LayoutParams generateLayoutParams(ViewGroup.LayoutParams source) {
        return new LayoutParams(source);
    }
    @Override protected LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(LayoutParams.MATCH_PARENT,LayoutParams.MATCH_PARENT);
    }
    @Override public boolean checkLayoutParams(ViewGroup.LayoutParams params) { return params instanceof LayoutParams; }

    public FrameLayout(Context context) {
        super(context);
    }

    public FrameLayout(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public FrameLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            child.layout(getPaddingLeft(), getPaddingTop(), getPaddingLeft() + w, getPaddingTop() + h);
        }
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null && child.getVisibility() == VISIBLE) child.draw(canvas);
        }
    }

    @Override
    protected void dispatchDrawAt(Canvas canvas, int offsetX, int offsetY) {
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child != null && child.getVisibility() == VISIBLE) child.drawAt(canvas, offsetX, offsetY);
        }
    }
}
