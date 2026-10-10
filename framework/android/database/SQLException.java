// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/**
 * Runtime failure for database errors; the SQLite subclasses carry the
 * native result code via {@link #getResultCode()}.
 */
public class SQLException extends RuntimeException {
    public SQLException() {}

    public SQLException(String message) {
        super(message);
    }

    public SQLException(String message, Throwable cause) {
        super(message, cause);
    }

    /** Native sqlite3 result code, or 0 when not from SQLite. */
    public int getResultCode() {
        return 0;
    }
}
