// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.View;

/** Maps named snapshot columns onto text views in a row layout. */
public class SimpleCursorAdapter extends ResourceCursorAdapter {
    private final String[] from;
    private final int[] to;

    public SimpleCursorAdapter(Context context, int layout, Cursor cursor, String[] from, int[] to) {
        super(context, layout, cursor, false);
        if (from.length != to.length) throw new IllegalArgumentException("column/view count mismatch");
        this.from = from.clone();
        this.to = to.clone();
    }

    @Override
    public void bindView(View view, Context context, Cursor cursor) {
        for (int i = 0; i < from.length; i++) {
            View target = view.findViewById(to[i]);
            if (target == null) continue;
            if (!(target instanceof TextView)) {
                throw new UnsupportedOperationException("cursor binding requires a TextView");
            }
            ((TextView) target).setText(cursor.getString(cursor.getColumnIndexOrThrow(from[i])));
        }
    }
}
