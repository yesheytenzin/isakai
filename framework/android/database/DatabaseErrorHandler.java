// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/** Callback invoked when SQLite reports database corruption. */
public interface DatabaseErrorHandler {
    void onCorruption(android.database.sqlite.SQLiteDatabase dbObj);
}
