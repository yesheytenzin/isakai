// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/** Grid view: fixed-column list, columns recorded. */
public class GridView extends AbsListView {
    public static final int NO_STRETCH = 0;
    public static final int STRETCH_SPACING = 1;
    public static final int AUTO_FIT = -1;

    private int numColumns = 2;

    public GridView(Context context) {
        super(context);
    }

    public GridView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public GridView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setNumColumns(int numColumns) {
        this.numColumns = numColumns;
    }

    public int getNumColumns() {
        return numColumns;
    }

    public void setColumnWidth(int width) {}
    public void setStretchMode(int mode) {}
    public void setHorizontalSpacing(int spacing) {}
    public void setVerticalSpacing(int spacing) {}
}
