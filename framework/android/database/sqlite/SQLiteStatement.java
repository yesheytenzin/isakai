// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

import org.isakai.db.IsakaiSQLite;

/**
 * Compiled one-shot statement: bind args, then execute or query.
 * Indexes are 1-based. {@link #close} finalizes the native statement;
 * methods after close throw {@link IllegalStateException}.
 */
public final class SQLiteStatement implements AutoCloseable {
    private final SQLiteDatabase db;
    private long stmt;

    SQLiteStatement(SQLiteDatabase db, long stmt) {
        this.db = db;
        this.stmt = stmt;
    }

    private void checkOpen() {
        if (stmt == 0) throw new IllegalStateException("statement closed");
    }

    public void bindNull(int index) {
        checkOpen();
        int rc = IsakaiSQLite.nBindNull(stmt, index);
        if (rc != 0) throw db.failForStatement(rc);
    }

    public void bindLong(int index, long value) {
        checkOpen();
        int rc = IsakaiSQLite.nBindLong(stmt, index, value);
        if (rc != 0) throw db.failForStatement(rc);
    }

    public void bindDouble(int index, double value) {
        checkOpen();
        int rc = IsakaiSQLite.nBindDouble(stmt, index, value);
        if (rc != 0) throw db.failForStatement(rc);
    }

    public void bindString(int index, String value) {
        checkOpen();
        int rc = value != null
                ? IsakaiSQLite.nBindText(stmt, index, value)
                : IsakaiSQLite.nBindNull(stmt, index);
        if (rc != 0) throw db.failForStatement(rc);
    }

    public void bindBlob(int index, byte[] value) {
        checkOpen();
        int rc = value != null
                ? IsakaiSQLite.nBindBlob(stmt, index, value)
                : IsakaiSQLite.nBindNull(stmt, index);
        if (rc != 0) throw db.failForStatement(rc);
    }

    public void clearBindings() {
        checkOpen();
        int cols = IsakaiSQLite.nColumnCount(stmt);
        for (int i = 1; i <= cols + 8; i++) {
            IsakaiSQLite.nBindNull(stmt, i);
        }
    }

    public void execute() {
        checkOpen();
        int step = IsakaiSQLite.nStep(stmt);
        if (step < 0) throw db.stmtFailForStatement(stmt, step);
        IsakaiSQLite.nReset(stmt);
    }

    public long executeInsert() {
        checkOpen();
        int step = IsakaiSQLite.nStep(stmt);
        if (step < 0) throw db.stmtFailForStatement(stmt, step);
        long row = db.getLastRowId();
        IsakaiSQLite.nReset(stmt);
        return row;
    }

    public long simpleQueryForLong() {
        checkOpen();
        try (android.database.Cursor c = db.drainCursor(stmt)) {
            stmt = 0; // drained+finalized by drainCursor
            if (!c.moveToFirst()) throw new SQLiteException("empty result");
            return c.getLong(0);
        }
    }

    public String simpleQueryForString() {
        checkOpen();
        try (android.database.Cursor c = db.drainCursor(stmt)) {
            stmt = 0; // drained+finalized by drainCursor
            if (!c.moveToFirst()) throw new SQLiteException("empty result");
            return c.getString(0);
        }
    }

    @Override
    public void close() {
        if (stmt != 0) {
            IsakaiSQLite.nFinalize(stmt);
            stmt = 0;
        }
    }
}
