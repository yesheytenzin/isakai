// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;

/** Table layout: vertical rows, stretch flags recorded. */
public class TableLayout extends LinearLayout {
    public TableLayout(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public TableLayout(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
    }

    public TableLayout(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(VERTICAL);
    }
    @Override
    protected void onMeasure(int widthSpec, int heightSpec) {
        // AOSP TableLayout.measureVertical forces every TableRow child's height to
        // WRAP_CONTENT before measuring, so rows never consume the full parent height.
        for (int i = 0; i < getChildCount(); i++) {
            android.view.View child = getChildAt(i);
            if (child instanceof TableRow && child.getLayoutParams() != null) {
                child.getLayoutParams().height = android.view.ViewGroup.LayoutParams.WRAP_CONTENT;
            }
        }
        super.onMeasure(widthSpec, heightSpec);
    }

    public void setStretchAllColumns(boolean stretch) {}
    public void setShrinkAllColumns(boolean shrink) {}
    public void setColumnStretchable(int columnIndex, boolean isStretchable) {}
    public void setColumnShrinkable(int columnIndex, boolean isShrinkable) {}
    public void setColumnCollapsed(int columnIndex, boolean isCollapsed) {}
}
