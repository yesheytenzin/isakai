// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

/** Inflates each cursor row from a layout resource. */
public abstract class ResourceCursorAdapter extends CursorAdapter {
    private final int layout;

    public ResourceCursorAdapter(Context context, int layout, Cursor cursor, boolean autoRequery) {
        super(context, cursor, autoRequery);
        this.layout = layout;
    }

    @Override
    public View newView(Context context, Cursor cursor, ViewGroup parent) {
        return LayoutInflater.from(context).inflate(layout, parent, false);
    }
}
