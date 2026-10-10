// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

import android.database.sqlite.SQLiteException;

/**
 * Static helpers over query results: single-cell getters and row counts.
 * Every method closes nothing; callers own the cursor lifecycle.
 */
public final class DatabaseUtils {
    private DatabaseUtils() {}

    /** Number of rows in the cursor; -1 on null. */
    public static int getRowCount(Cursor cursor) {
        return cursor != null ? cursor.getCount() : -1;
    }

    public static long queryNumEntries(Cursor cursor) {
        if (cursor == null) return 0;
        if (!cursor.moveToFirst()) return 0;
        return cursor.getLong(0);
    }

    public static String stringForQuery(Cursor cursor) {
        if (cursor == null || !cursor.moveToFirst()) {
            throw new SQLiteException("empty cursor");
        }
        return cursor.getString(0);
    }

    public static long longForQuery(Cursor cursor) {
        if (cursor == null || !cursor.moveToFirst()) {
            throw new SQLiteException("empty cursor");
        }
        return cursor.getLong(0);
    }

    /** Concatenate all column values of every row (for debugging). */
    public static String dumpCursorToString(Cursor cursor) {
        if (cursor == null) return "null cursor";
        StringBuilder sb = new StringBuilder();
        sb.append("count=").append(cursor.getCount()).append('\n');
        String[] names = cursor.getColumnNames();
        for (int i = 0; i < names.length; i++) {
            if (i > 0) sb.append('|');
            sb.append(names[i]);
        }
        sb.append('\n');
        while (cursor.moveToNext()) {
            for (int i = 0; i < names.length; i++) {
                if (i > 0) sb.append('|');
                sb.append(cursor.isNull(i) ? "NULL" : cursor.getString(i));
            }
            sb.append('\n');
        }
        return sb.toString();
    }
}
