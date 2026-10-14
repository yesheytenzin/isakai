// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.View;

/** Vertical list with dividers and header/footer views. */
public class ListView extends AbsListView {
    private Drawable divider;
    private int dividerHeight = 1;
    private final java.util.ArrayList<View> headers = new java.util.ArrayList<>();
    private final java.util.ArrayList<View> footers = new java.util.ArrayList<>();

    public ListView(Context context) {
        super(context);
    }

    public ListView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public ListView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void addHeaderView(View view, Object data, boolean isSelectable) {
        if (view != null) headers.add(view);
    }

    public void addHeaderView(View view) {
        addHeaderView(view, null, true);
    }

    public void addFooterView(View view) {
        if (view != null) footers.add(view);
    }

    public void setDivider(Drawable divider) {
        this.divider = divider;
    }

    public Drawable getDivider() {
        return divider;
    }

    public void setDividerHeight(int height) {
        this.dividerHeight = height;
    }

    public int getDividerHeight() {
        return dividerHeight;
    }

    @Override
    public ListAdapter getAdapter() {
        return (ListAdapter) super.getAdapter();
    }

    @Override
    public void setAdapter(ListAdapter adapter) {
        super.setAdapter(adapter);
    }

    @Override
    protected void dispatchDraw(Canvas canvas) {
        drawRows(canvas, getLeft(), getTop());
    }

    @Override
    protected void dispatchDrawAt(Canvas canvas, int offsetX, int offsetY) {
        drawRows(canvas, offsetX, offsetY);
    }

    private void drawRows(Canvas canvas, int originX, int originY) {
        ListAdapter adapter = getAdapter();
        if (adapter == null || canvas == null) return;
        int y = 0;
        int rowH = 24;
        int bottom = getHeight();
        for (int i = 0; i < adapter.getCount() && y < bottom; i++) {
            View row = adapter.getView(i, null, this);
            if (row == null) continue;
            row.layout(0, y, getWidth(), y + rowH);
            row.drawAt(canvas, originX, originY);
            y += rowH + dividerHeight;
        }
    }
}
