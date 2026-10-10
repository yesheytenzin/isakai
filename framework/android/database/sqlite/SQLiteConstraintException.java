// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

/** Constraint violation (UNIQUE/CHECK/FOREIGN KEY/NOT NULL); result code 19. */
public class SQLiteConstraintException extends SQLiteException {
    public SQLiteConstraintException() {}

    public SQLiteConstraintException(String message) {
        super(message, 19);
    }

    public SQLiteConstraintException(String message, int resultCode) {
        super(message, resultCode);
    }
}
