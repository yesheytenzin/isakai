// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.view.View;
import android.view.ViewGroup;

/**
 * Pins {@link TableLayout} row-measure semantics to AOSP: TableRow children are
 * always measured as WRAP_CONTENT (see TableLayout.findLargestCells), so a row
 * with layout_height=MATCH_PARENT can never consume the parent's full height
 * and starve weighted sibling rows to zero height (OpenCalc blank window).
 */
public final class TableLayoutMeasureTest {
    private static void check(boolean value, String message) {
        if (!value) throw new AssertionError(message);
    }

    public static void main(String[] args) {
        android.app.Application app = new android.app.Application();
        TableLayout table = new TableLayout(app);
        // Two WRAP rows with weight, as in OpenCalc's keypad.
        TableRow[] wrapRows = new TableRow[2];
        for (int r = 0; r < 2; r++) {
            TableRow row = new TableRow(app);
            android.widget.LinearLayout.LayoutParams rp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT, 3f);
            row.setLayoutParams(rp);
            for (int i = 0; i < 4; i++) {
                Button b = new Button(app);
                b.setText("x");
                row.addView(b, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            }
            wrapRows[r] = row;
            table.addView(row);
        }
        // Three MATCH_PARENT rows with weight: AOSP forces these to WRAP too.
        for (int r = 0; r < 3; r++) {
            TableRow row = new TableRow(app);
            android.widget.LinearLayout.LayoutParams rp = new android.widget.LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT, 3f);
            row.setLayoutParams(rp);
            Button b = new Button(app);
            b.setText("x");
            row.addView(b, new android.widget.LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            table.addView(row);
        }
        table.measure(View.MeasureSpec.makeMeasureSpec(640, View.MeasureSpec.EXACTLY),
                View.MeasureSpec.makeMeasureSpec(439, View.MeasureSpec.EXACTLY));
        check(table.getMeasuredHeight() == 439, "table height lost: " + table.getMeasuredHeight());
        for (int r = 0; r < 2; r++) {
            int h = wrapRows[r].getMeasuredHeight();
            check(h > 0, "WRAP row " + r + " measured zero height");
        }
        // No single row may consume the whole table.
        for (int i = 0; i < table.getChildCount(); i++) {
            int h = table.getChildAt(i).getMeasuredHeight();
            check(h < table.getMeasuredHeight(), "row " + i + " consumed full table height: " + h);
        }
        System.out.println("PASS: TableLayout rows measure with positive height");
    }
}
