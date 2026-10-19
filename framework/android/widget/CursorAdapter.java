// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.database.Cursor;
import android.view.View;
import android.view.ViewGroup;

/** Synchronous adapter for explicitly supplied cursor snapshots. */
public abstract class CursorAdapter extends BaseAdapter {
    private final Context context;
    private Cursor cursor;
    private int idColumn = -1;

    public CursorAdapter(Context context, Cursor cursor, boolean autoRequery) {
        if (autoRequery) throw new UnsupportedOperationException("automatic cursor requery");
        this.context = context;
        this.cursor = cursor;
        idColumn = cursor == null ? -1 : cursor.getColumnIndexOrThrow("_id");
    }

    public Cursor getCursor() { return cursor; }

    @Override
    public int getCount() { return cursor == null ? 0 : cursor.getCount(); }

    @Override
    public Object getItem(int position) {
        return cursor != null && cursor.moveToPosition(position) ? cursor : null;
    }

    @Override
    public long getItemId(int position) {
        return cursor != null && cursor.moveToPosition(position) ? cursor.getLong(idColumn) : 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        if (cursor == null || !cursor.moveToPosition(position)) {
            throw new IllegalStateException("no cursor row at " + position);
        }
        View view = convertView == null ? newView(context, cursor, parent) : convertView;
        bindView(view, context, cursor);
        return view;
    }

    public abstract View newView(Context context, Cursor cursor, ViewGroup parent);
    public abstract void bindView(View view, Context context, Cursor cursor);

    public Cursor swapCursor(Cursor next) {
        if (next == cursor) return null;
        int nextId = next == null ? -1 : next.getColumnIndexOrThrow("_id");
        Cursor previous = cursor;
        cursor = next;
        idColumn = nextId;
        if (next == null) notifyDataSetInvalidated();
        else notifyDataSetChanged();
        return previous;
    }

    public void changeCursor(Cursor next) {
        Cursor previous = swapCursor(next);
        if (previous != null) previous.close();
    }
}
