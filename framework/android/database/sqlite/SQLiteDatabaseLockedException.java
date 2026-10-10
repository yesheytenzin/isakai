// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

/** Database-file lock contention (SQLITE_BUSY, result code 5). */
public class SQLiteDatabaseLockedException extends SQLiteException {
    public SQLiteDatabaseLockedException() {}

    public SQLiteDatabaseLockedException(String message) {
        super(message, 5);
    }

    public SQLiteDatabaseLockedException(String message, int resultCode) {
        super(message, resultCode);
    }
}
