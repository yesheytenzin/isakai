// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

import android.database.Cursor;

/** Cursor driver contract. */
public interface SQLiteCursorDriver {
    Cursor query(Object factory, String[] selectionArgs);
    void cursorDeactivated();
    void cursorRequeried(Cursor cursor);
    void cursorClosed();
    void setBindArguments(String[] bindArgs);
}
