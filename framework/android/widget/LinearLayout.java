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
        public int gravity=-1;

        public LayoutParams(Context context, android.util.AttributeSet attrs) {
            super(context, attrs);
            android.content.res.TypedArray a=context.obtainStyledAttributes(attrs,new int[]{0x01010181});
            weight=a.getFloat(0,0); a.recycle();
        }
        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height);
            this.weight = weight;
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
            if (source instanceof LayoutParams) { weight=((LayoutParams)source).weight;gravity=((LayoutParams)source).gravity; }
        }
    }

    private int orientation = VERTICAL;
    private int gravity=0x00800033;
    private float weightSum=-1;

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

    public void setGravity(int gravity) { this.gravity=gravity;requestLayout(); }
    public int getGravity() { return gravity; }
    public void setWeightSum(float sum) { weightSum=Math.max(0,sum);requestLayout(); }
    public float getWeightSum() { return weightSum; }

    @Override
    protected ViewGroup.LayoutParams generateDefaultLayoutParams() {
        return new LayoutParams(orientation==HORIZONTAL?LayoutParams.WRAP_CONTENT:LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
    }
    @Override public ViewGroup.LayoutParams generateLayoutParams(android.util.AttributeSet attrs) { return new LayoutParams(getContext(),attrs); }
    @Override protected ViewGroup.LayoutParams generateLayoutParams(ViewGroup.LayoutParams source) { return new LayoutParams(source); }
    @Override public boolean checkLayoutParams(ViewGroup.LayoutParams params) { return params instanceof LayoutParams; }
    @Override
    protected void onMeasure(int widthSpec,int heightSpec) {
        int used=0,cross=0;float totalWeight=0;
        for(int i=0;i<getChildCount();i++) {
            View child=getChildAt(i); if(child.getVisibility()==GONE)continue;
            LayoutParams p=(LayoutParams)child.getLayoutParams();
            totalWeight+=Math.max(0,p.weight);
            int childW=getChildMeasureSpec(widthSpec,getPaddingLeft()+getPaddingRight()+p.leftMargin+p.rightMargin,p.width);
            int childH=getChildMeasureSpec(heightSpec,getPaddingTop()+getPaddingBottom()+p.topMargin+p.bottomMargin,p.height);
            child.measure(childW,childH);
            used+=(orientation==HORIZONTAL?child.getMeasuredWidth()+p.leftMargin+p.rightMargin:child.getMeasuredHeight()+p.topMargin+p.bottomMargin);
            cross=Math.max(cross,orientation==HORIZONTAL?child.getMeasuredHeight()+p.topMargin+p.bottomMargin:child.getMeasuredWidth()+p.leftMargin+p.rightMargin);
        }
        int padding=orientation==HORIZONTAL?getPaddingLeft()+getPaddingRight():getPaddingTop()+getPaddingBottom();
        int wanted=used+padding;
        int mainSpec=orientation==HORIZONTAL?widthSpec:heightSpec;
        int main=resolveSize(wanted,mainSpec);
        int remaining=main-padding-used;
        if(totalWeight>0 && remaining!=0) {
            float remainingWeight=weightSum>0?weightSum:totalWeight;
            for(int i=0;i<getChildCount();i++) {
                View child=getChildAt(i); if(child.getVisibility()==GONE)continue;
                LayoutParams p=(LayoutParams)child.getLayoutParams(); if(p.weight<=0)continue;
                int share=(int)(remaining*p.weight/remainingWeight);
                remaining-=share;remainingWeight-=p.weight;
                int w=child.getMeasuredWidth(),h=child.getMeasuredHeight();
                if(orientation==HORIZONTAL)w=Math.max(0,w+share);else h=Math.max(0,h+share);
                child.measure(MeasureSpec.makeMeasureSpec(w,MeasureSpec.EXACTLY),MeasureSpec.makeMeasureSpec(h,MeasureSpec.EXACTLY));
            }
        }
        int crossPadding=orientation==HORIZONTAL?getPaddingTop()+getPaddingBottom():getPaddingLeft()+getPaddingRight();
        setMeasuredDimension(orientation==HORIZONTAL?main:resolveSize(cross+crossPadding,widthSpec),
                orientation==HORIZONTAL?resolveSize(cross+crossPadding,heightSpec):main);
    }
    @Override
    protected void onLayout(boolean changed, int left, int top, int right, int bottom) {
        // AOSP: child bounds are parent-relative (parent's left/top excluded).
        // Translated containers (Toolbar, ActionMenuView) already do this;
        // drawing and touch dispatch compose offsets down the tree.
        int x = getPaddingLeft(), y = getPaddingTop();
        for (int i = 0; i < getChildCount(); i++) {
            View child = getChildAt(i);
            if (child == null || child.getVisibility() == GONE) continue;
            int w = child.getMeasuredWidth();
            int h = child.getMeasuredHeight();
            LayoutParams p=(LayoutParams)child.getLayoutParams();
            int cx=x+p.leftMargin,cy=y+p.topMargin;
            int alignment=p.gravity<0?gravity:p.gravity;
            if(orientation==HORIZONTAL) {
                int vertical=alignment&0x70;
                if(vertical==0x10)cy=getPaddingTop()+(bottom-top-getPaddingTop()-getPaddingBottom()-h+p.topMargin-p.bottomMargin)/2;
                else if(vertical==0x50)cy=bottom-top-getPaddingBottom()-h-p.bottomMargin;
            } else {
                int horizontal=alignment&7;
                if((alignment&0x00800000)!=0 && getLayoutDirection()==LAYOUT_DIRECTION_RTL) {
                    if(horizontal==3)horizontal=5;else if(horizontal==5)horizontal=3;
                }
                if(horizontal==1)cx=getPaddingLeft()+(right-left-getPaddingLeft()-getPaddingRight()-w+p.leftMargin-p.rightMargin)/2;
                else if(horizontal==5)cx=right-left-getPaddingRight()-w-p.rightMargin;
            }
            child.layout(cx,cy,cx+w,cy+h);
            if (orientation == HORIZONTAL) x += w+p.leftMargin+p.rightMargin;
            else y += h+p.topMargin+p.bottomMargin;
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
