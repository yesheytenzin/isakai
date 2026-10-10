// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;

/** Table row: horizontal LinearLayout with column params. */
public class TableRow extends LinearLayout {
    public static class LayoutParams extends LinearLayout.LayoutParams {
        public int column;

        public LayoutParams(int width, int height) {
            super(width, height);
        }

        public LayoutParams(int width, int height, float weight) {
            super(width, height, weight);
        }

        public LayoutParams(ViewGroup.LayoutParams source) {
            super(source);
        }
    }

    public TableRow(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
    }

    public TableRow(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        setOrientation(HORIZONTAL);
    }

    public TableRow(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        setOrientation(HORIZONTAL);
    }
}
