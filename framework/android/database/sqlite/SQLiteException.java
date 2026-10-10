// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

/** Base SQLite failure; carries the native sqlite3 result code. */
public class SQLiteException extends android.database.SQLException {
    private final int resultCode;

    public SQLiteException() {
        this.resultCode = 0;
    }

    public SQLiteException(String message) {
        super(message);
        this.resultCode = 0;
    }

    public SQLiteException(String message, int resultCode) {
        super(message + " (code " + resultCode + ")");
        this.resultCode = resultCode;
    }

    @Override
    public int getResultCode() {
        return resultCode;
    }
}
