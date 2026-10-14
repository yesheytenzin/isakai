// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

import org.isakai.db.IsakaiSQLite;

/**
 * Compiled reusable statement: bind args, then execute or query.
 * Indexes are 1-based. {@link #close} finalizes the native statement;
 * methods after close throw {@link IllegalStateException}.
 */
public final class SQLiteStatement extends SQLiteProgram {
    private final SQLiteDatabase db;
    private long stmt;

    SQLiteStatement(SQLiteDatabase db, long stmt) {
        db.acquireReference();
        this.db = db;
        this.stmt = stmt;
    }

    private void checkOpen() {
        if (stmt == 0) throw new IllegalStateException("statement closed");
    }

    public void bindNull(int index) {
        db.lockSession();
        try {
        checkOpen();
        int rc = IsakaiSQLite.nBindNull(stmt, index);
        if (rc != 0) throw db.failForStatement(rc);

        } finally { db.unlockSession(); }
    }

    public void bindLong(int index, long value) {
        db.lockSession();
        try {
        checkOpen();
        int rc = IsakaiSQLite.nBindLong(stmt, index, value);
        if (rc != 0) throw db.failForStatement(rc);

        } finally { db.unlockSession(); }
    }

    public void bindDouble(int index, double value) {
        db.lockSession();
        try {
        checkOpen();
        int rc = IsakaiSQLite.nBindDouble(stmt, index, value);
        if (rc != 0) throw db.failForStatement(rc);

        } finally { db.unlockSession(); }
    }

    public void bindString(int index, String value) {
        db.lockSession();
        try {
        checkOpen();
        int rc = value != null
                ? IsakaiSQLite.nBindText(stmt, index, value)
                : IsakaiSQLite.nBindNull(stmt, index);
        if (rc != 0) throw db.failForStatement(rc);

        } finally { db.unlockSession(); }
    }

    public void bindBlob(int index, byte[] value) {
        db.lockSession();
        try {
        checkOpen();
        int rc = value != null
                ? IsakaiSQLite.nBindBlob(stmt, index, value)
                : IsakaiSQLite.nBindNull(stmt, index);
        if (rc != 0) throw db.failForStatement(rc);

        } finally { db.unlockSession(); }
    }

    public void clearBindings() {
        db.lockSession();
        try {
        checkOpen();
        int rc=IsakaiSQLite.nClearBindings(stmt);
        if(rc!=0)throw db.failForStatement(rc);

        } finally { db.unlockSession(); }
    }

    public void execute() {
        db.lockSession();
        try {
        checkOpen();
        int step = IsakaiSQLite.nStep(stmt);
        if (step < 0) throw db.stmtFailForStatement(stmt, step);
        IsakaiSQLite.nReset(stmt);

        } finally { db.unlockSession(); }
    }

    public long executeInsert() {
        db.lockSession();
        try {
        checkOpen();
        int step = IsakaiSQLite.nStep(stmt);
        if (step < 0) throw db.stmtFailForStatement(stmt, step);
        long row = db.getLastRowId();
        IsakaiSQLite.nReset(stmt);
        return row;

        } finally { db.unlockSession(); }
    }

    public int executeUpdateDelete() {
        db.lockSession();
        try { execute();return (int)db.getChanges(); }
        finally { db.unlockSession(); }
    }

    public long simpleQueryForLong() {
        db.lockSession();
        try {
        checkOpen();
        try (android.database.Cursor c = db.drainCursor(stmt,false,null)) {
            if (!c.moveToFirst()) throw new SQLiteException("empty result");
            return c.getLong(0);
        }

        } finally { db.unlockSession(); }
    }

    public String simpleQueryForString() {
        db.lockSession();
        try {
        checkOpen();
        try (android.database.Cursor c = db.drainCursor(stmt,false,null)) {
            if (!c.moveToFirst()) throw new SQLiteException("empty result");
            return c.getString(0);
        }

        } finally { db.unlockSession(); }
    }

    android.database.Cursor queryRows(android.os.CancellationSignal cancellation) {
        checkOpen();return db.drainCursor(stmt,false,cancellation);
    }

    @Override
    protected void onAllReferencesReleased() {
        db.lockSession();
        try {
        if (stmt != 0) {
            IsakaiSQLite.nFinalize(stmt);
            stmt = 0;
        }
        db.releaseReference();

        } finally { db.unlockSession(); }
    }
}
