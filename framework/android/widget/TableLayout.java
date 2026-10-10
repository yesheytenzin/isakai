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

    public void setStretchAllColumns(boolean stretch) {}
    public void setShrinkAllColumns(boolean shrink) {}
    public void setColumnStretchable(int columnIndex, boolean isStretchable) {}
    public void setColumnShrinkable(int columnIndex, boolean isShrinkable) {}
    public void setColumnCollapsed(int columnIndex, boolean isCollapsed) {}
}
