// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.isakai.db.IsakaiSQLite;

/**
 * SQLite database handle over the native bridge. Open via
 * {@link #openOrCreateDatabase} / {@link #openDatabase}, or hold one through
 * {@link SQLiteOpenHelper}. Statements compile through
 * {@link #compileStatement}; queries return an eager {@link SQLiteCursor}
 * (all rows buffered at query time).
 *
 * <p>Errors: native failures throw {@link SQLiteException}; result code 5
 * (BUSY) throws {@link SQLiteDatabaseLockedException}, code 19 throws
 * {@link SQLiteConstraintException}.
 */
public final class SQLiteDatabase extends SQLiteClosable {
    public static final int OPEN_READONLY = 1;
    public static final int OPEN_READWRITE = 2;
    public static final int CREATE_IF_NECESSARY = 4;

    public interface CursorFactory {
        Cursor newCursor(SQLiteDatabase db, SQLiteCursorDriver driver, String editTable, SQLiteQuery query);
    }

    private android.database.DatabaseErrorHandler errorHandler;
    private CursorFactory defaultFactory;
    private final String path;
    private volatile long handle;
    private final java.util.concurrent.locks.ReentrantLock sessionLock=new java.util.concurrent.locks.ReentrantLock(true);
    private static final class Transaction { boolean successful;boolean childFailed; }
    private final ThreadLocal<java.util.ArrayDeque<Transaction>> transactions=ThreadLocal.withInitial(java.util.ArrayDeque::new);
    void lockSession() { sessionLock.lock(); }
    void unlockSession() { sessionLock.unlock(); }

    private SQLiteDatabase(String path, long handle) {
        this.path = path;
        this.handle = handle;
    }

    private void checkOpen() {
        if (handle == 0) throw new IllegalStateException("database closed: " + path);
    }

    private RuntimeException fail(int negCode) {
        int code = -negCode;
        String msg = IsakaiSQLite.nErr(handle, code);
        if ((code & 255) == 11 || (code & 255) == 26) {
            if (errorHandler != null) errorHandler.onCorruption(this);
            else close();
        }
        if (code == 5 || code == 261 || code == 517 || code == 773) {
            return new SQLiteDatabaseLockedException(msg, code);
        }
        if (code == 19) {
            return new SQLiteConstraintException(msg, code);
        }
        return new SQLiteException(msg, code);
    }

    public static SQLiteDatabase openOrCreateDatabase(String path, Object factory) {
        return openDatabase(path, null, OPEN_READWRITE | CREATE_IF_NECESSARY);
    }

    public static SQLiteDatabase openOrCreateDatabase(java.io.File file, Object factory) {
        return openOrCreateDatabase(file.getAbsolutePath(), factory);
    }

    public static SQLiteDatabase openDatabase(
            String path, Object factory, int flags) {
        long h = IsakaiSQLite.nOpen(path, flags);
        if (h == 0) {
            String msg = IsakaiSQLite.nErr(0, 14);
            if ((flags & 5) == 5) {
                throw new SQLiteException("cannot open " + path + ": " + msg, 14);
            }
            throw new SQLiteException("cannot open " + path + ": " + msg, 14);
        }
        return new SQLiteDatabase(path, h);
    }

    public static SQLiteDatabase openDatabase(String path, CursorFactory factory, int flags,
            android.database.DatabaseErrorHandler errorHandler) {
        SQLiteDatabase db = openDatabase(path, (Object) null, flags);
        db.errorHandler = errorHandler;
        db.defaultFactory = factory;
        return db;
    }

    public boolean enableWriteAheadLogging() {
        try (Cursor cursor = rawQuery("PRAGMA journal_mode=WAL", null)) {
            return cursor.moveToFirst() && "wal".equalsIgnoreCase(cursor.getString(0));
        }
    }

    public boolean isWriteAheadLoggingEnabled() {
        try(Cursor cursor=rawQuery("PRAGMA journal_mode",null)) {
            return cursor.moveToFirst() && "wal".equalsIgnoreCase(cursor.getString(0));
        }
    }

    public void disableWriteAheadLogging() {
        try (Cursor cursor = rawQuery("PRAGMA journal_mode=DELETE", null)) { cursor.moveToFirst(); }
    }

    public void setMaxSqlCacheSize(int size) {
        if (size < 0 || size > 100) throw new IllegalStateException("cache size must be between 0 and 100");
        org.isakai.runtime.Capabilities.unavailable("SQLiteDatabase.setMaxSqlCacheSize: prepared statements are not cached");
    }

    public int getVersion() {
        try (Cursor cursor = rawQuery("PRAGMA user_version", null)) {
            return cursor.moveToFirst() ? cursor.getInt(0) : 0;
        }
    }

    public void setVersion(int version) { execSQL("PRAGMA user_version=" + version); }

    public String getPath() {
        return path;
    }

    public boolean isOpen() {
        return handle != 0;
    }

    public boolean isReadOnly() {
        return false;
    }

    public boolean inTransaction() { return !transactions.get().isEmpty(); }

    public void beginTransactionNonExclusive() { beginTransaction("IMMEDIATE"); }
    public void beginTransaction() { beginTransaction("EXCLUSIVE"); }
    private void beginTransaction(String mode) {
        acquireReference();
        lockSession();
        boolean started=false;
        try {
            checkOpen();
            java.util.ArrayDeque<Transaction> stack=transactions.get();
            if(!stack.isEmpty() && stack.peek().successful)throw new IllegalStateException("transaction already marked successful");
            if(stack.isEmpty())execSQL("BEGIN "+mode);
            stack.push(new Transaction());started=true;
        } finally {
            if(!started) { unlockSession();releaseReference(); }
        }
    }
    public void setTransactionSuccessful() {
        Transaction transaction=transactions.get().peek();
        if(transaction==null || transaction.successful)throw new IllegalStateException("no unmarked transaction");
        transaction.successful=true;
    }
    public void endTransaction() {
        java.util.ArrayDeque<Transaction> stack=transactions.get();
        if(stack.isEmpty())throw new IllegalStateException("not in transaction on this thread");
        Transaction transaction=stack.pop();
        boolean success=transaction.successful && !transaction.childFailed;
        try {
            if(stack.isEmpty())execSQL(success?"COMMIT":"ROLLBACK");
            else if(!success)stack.peek().childFailed=true;
        } finally {
            unlockSession();releaseReference();
        }
    }

    public void execSQL(String sql) {
        lockSession();
        try {
        checkOpen();
        int rc = IsakaiSQLite.nExec(handle, sql);
        if (rc != 0) throw fail(rc);

        } finally { unlockSession(); }
    }

    public void execSQL(String sql, Object[] bindArgs) {
        lockSession();
        try {
        checkOpen();
        long stmt = IsakaiSQLite.nPrepare(handle, sql);
        if (stmt == 0) throw fail(-1);
        try {
            bindAll(stmt, bindArgs);
            int step = IsakaiSQLite.nStep(stmt);
            if (step < 0) throw stmtFail(stmt, step);
        } finally {
            IsakaiSQLite.nFinalize(stmt);
        }

        } finally { unlockSession(); }
    }

    public long insert(String table, String nullColumnHack, ContentValues values) {
        return insertWithOnConflict(table, nullColumnHack, values, 0);
    }

    public long insertOrThrow(String table, String nullColumnHack, ContentValues values) {
        long row = insert(table, nullColumnHack, values);
        if (row < 0) throw new SQLiteException("insert failed: " + table);
        return row;
    }

    public long insertWithOnConflict(String table, String nullColumnHack, ContentValues values, int conflictAlgorithm) {
        lockSession();
        try {
        checkOpen();
        String conflict = switch (conflictAlgorithm) {
            case 1 -> " OR ROLLBACK";
            case 2 -> " OR ABORT";
            case 3 -> " OR FAIL";
            case 4 -> " OR IGNORE";
            case 5 -> " OR REPLACE";
            default -> "";
        };
        StringBuilder sql = new StringBuilder("INSERT").append(conflict).append(" INTO ").append(table);
        Object[] args;
        if (values == null || values.size() == 0) {
            if (nullColumnHack == null) {
                throw new IllegalArgumentException("nullColumnHack required for empty values");
            }
            sql.append(" (").append(nullColumnHack).append(") VALUES (NULL)");
            args = new Object[0];
        } else {
            List<String> keys = new ArrayList<>(values.keySet());
            sql.append(" (");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sql.append(",");
                sql.append(keys.get(i));
            }
            sql.append(") VALUES (");
            for (int i = 0; i < keys.size(); i++) {
                if (i > 0) sql.append(",");
                sql.append("?");
            }
            sql.append(")");
            args = new Object[keys.size()];
            for (int i = 0; i < keys.size(); i++) args[i] = values.get(keys.get(i));
        }
        long stmt = IsakaiSQLite.nPrepare(handle, sql.toString());
        if (stmt == 0) throw fail(-1);
        try {
            bindAll(stmt, args);
            int step = IsakaiSQLite.nStep(stmt);
            if (step == 101) return IsakaiSQLite.nLastRowId(handle);
            if (step < 0) throw stmtFail(stmt, step);
            return -1;
        } finally {
            IsakaiSQLite.nFinalize(stmt);
        }

        } finally { unlockSession(); }
    }

    public int update(String table, ContentValues values, String whereClause, String[] whereArgs) {
        lockSession();
        try {
        return updateWithOnConflict(table, values, whereClause, whereArgs, 0);

        } finally { unlockSession(); }
    }

    public int updateWithOnConflict(
            String table, ContentValues values, String whereClause, String[] whereArgs, int conflictAlgorithm) {
        checkOpen();
        if (values == null || values.size() == 0) {
            throw new IllegalArgumentException("empty values");
        }
        String conflict = switch (conflictAlgorithm) {
            case 4 -> " OR IGNORE";
            case 5 -> " OR REPLACE";
            default -> "";
        };
        List<String> keys = new ArrayList<>(values.keySet());
        StringBuilder sql = new StringBuilder("UPDATE").append(conflict).append(" ").append(table).append(" SET ");
        for (int i = 0; i < keys.size(); i++) {
            if (i > 0) sql.append(",");
            sql.append(keys.get(i)).append("=?");
        }
        List<Object> args = new ArrayList<>();
        for (String k : keys) args.add(values.get(k));
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
            if (whereArgs != null) args.addAll(Arrays.asList(whereArgs));
        }
        long stmt = IsakaiSQLite.nPrepare(handle, sql.toString());
        if (stmt == 0) throw fail(-1);
        try {
            bindAll(stmt, args.toArray());
            int step = IsakaiSQLite.nStep(stmt);
            if (step < 0) throw stmtFail(stmt, step);
            return (int) IsakaiSQLite.nChanges(handle);
        } finally {
            IsakaiSQLite.nFinalize(stmt);
        }
    }

    public int delete(String table, String whereClause, String[] whereArgs) {
        lockSession();
        try {
        checkOpen();
        StringBuilder sql = new StringBuilder("DELETE FROM ").append(table);
        if (whereClause != null && !whereClause.isEmpty()) {
            sql.append(" WHERE ").append(whereClause);
        }
        long stmt = IsakaiSQLite.nPrepare(handle, sql.toString());
        if (stmt == 0) throw fail(-1);
        try {
            bindAll(stmt, whereArgs != null ? (Object[]) whereArgs : new Object[0]);
            int step = IsakaiSQLite.nStep(stmt);
            if (step < 0) throw stmtFail(stmt, step);
            return (int) IsakaiSQLite.nChanges(handle);
        } finally {
            IsakaiSQLite.nFinalize(stmt);
        }

        } finally { unlockSession(); }
    }

    public SQLiteStatement compileStatement(String sql) {
        lockSession();
        try {
        checkOpen();
        long stmt = IsakaiSQLite.nPrepare(handle, sql);
        if (stmt == 0) throw fail(-1);
        return new SQLiteStatement(this, stmt);

        } finally { unlockSession(); }
    }

    public Cursor query(
            String table, String[] columns, String selection, String[] selectionArgs,
            String groupBy, String having, String orderBy) {
        return query(table, columns, selection, selectionArgs, groupBy, having, orderBy, null);
    }

    public Cursor query(
            String table, String[] columns, String selection, String[] selectionArgs,
            String groupBy, String having, String orderBy, String limit) {
        StringBuilder sql = new StringBuilder("SELECT ");
        if (columns == null || columns.length == 0) {
            sql.append("*");
        } else {
            for (int i = 0; i < columns.length; i++) {
                if (i > 0) sql.append(",");
                sql.append(columns[i]);
            }
        }
        sql.append(" FROM ").append(table);
        if (selection != null && !selection.isEmpty()) sql.append(" WHERE ").append(selection);
        if (groupBy != null && !groupBy.isEmpty()) sql.append(" GROUP BY ").append(groupBy);
        if (having != null && !having.isEmpty()) sql.append(" HAVING ").append(having);
        if (orderBy != null && !orderBy.isEmpty()) sql.append(" ORDER BY ").append(orderBy);
        if (limit != null && !limit.isEmpty()) sql.append(" LIMIT ").append(limit);
        return rawQuery(sql.toString(), selectionArgs);
    }

    public Cursor rawQuery(String sql, String[] selectionArgs) {
        return rawQueryWithFactory(null,sql,selectionArgs,null,null);
    }
    public Cursor rawQuery(String sql,String[] args,android.os.CancellationSignal cancellation) {
        return rawQueryWithFactory(null,sql,args,null,cancellation);
    }
    public Cursor rawQueryWithFactory(CursorFactory factory,String sql,String[] args,String editTable) {
        lockSession();
        try {
        return rawQueryWithFactory(factory,sql,args,editTable,null);

        } finally { unlockSession(); }
    }
    public Cursor rawQueryWithFactory(CursorFactory factory,String sql,String[] args,String editTable,
            android.os.CancellationSignal cancellation) {
        lockSession();
        try {
        checkOpen();
        if(cancellation!=null)cancellation.throwIfCanceled();
        SQLiteQuery query=new SQLiteQuery(this,sql,cancellation);
        SQLiteCursorDriver driver=new SQLiteCursorDriver() {
            public Cursor query(CursorFactory requested,String[] bindings) {
                query.bindAllArgsAsStrings(bindings);
                CursorFactory selected=requested==null?defaultFactory:requested;
                return selected==null?new SQLiteCursor(this,editTable,query):selected.newCursor(SQLiteDatabase.this,this,editTable,query);
            }
            public void cursorDeactivated() {}
            public void cursorRequeried(Cursor cursor) {}
            public void cursorClosed() {}
            public void setBindArguments(String[] bindings) { query.bindAllArgsAsStrings(bindings); }
        };
        try {
            Cursor result=driver.query(factory,args);
            if(result==null)throw new IllegalStateException("cursor factory returned null");
            return result;
        } finally {
            // Results are eagerly buffered; release the native query even if a custom
            // factory returns a wrapper that does not own the original SQLiteCursor.
            query.close();
        }

        } finally { unlockSession(); }
    }

    /** Eagerly step the statement to DONE, buffering every row. Finalizes stmt. */
    Cursor drainCursor(long stmt) { return drainCursor(stmt,true,null); }
    Cursor drainCursor(long stmt,boolean finalize,android.os.CancellationSignal cancellation) {
        try {
        int cols = IsakaiSQLite.nColumnCount(stmt);
        String[] names = new String[Math.max(cols, 0)];
        for (int i = 0; i < names.length; i++) {
            String n = IsakaiSQLite.nColumnName(stmt, i);
            names[i] = n != null ? n : "c" + i;
        }
        List<Object[]> rows = new ArrayList<>();
        List<Integer> types = new ArrayList<>();
        boolean typed = false;
        while (true) {
            if(cancellation!=null)cancellation.throwIfCanceled();
            int step = IsakaiSQLite.nStep(stmt);
            if (step == 101) break;
            if (step < 0) {
                RuntimeException err = stmtFail(stmt, step);
                throw err;
            }
            Object[] row = new Object[names.length];
            for (int i = 0; i < names.length; i++) {
                int t = IsakaiSQLite.nColumnType(stmt, i);
                // sqlite3.h: INTEGER=1, FLOAT=2, TEXT=3, BLOB=4, NULL=5.
                if (!typed) types.add(t == 5 ? 0 : t == 1 ? 1 : t == 2 ? 2 : t == 3 ? 3 : 4);
                switch (t) {
                    case 1:
                        row[i] = IsakaiSQLite.nColumnLong(stmt, i);
                        break;
                    case 2:
                        row[i] = IsakaiSQLite.nColumnDouble(stmt, i);
                        break;
                    case 3:
                        row[i] = IsakaiSQLite.nColumnText(stmt, i);
                        break;
                    case 4:
                        row[i] = IsakaiSQLite.nColumnBlob(stmt, i);
                        break;
                    default:
                        row[i] = null;
                        break;
                }
            }
            typed = true;
            rows.add(row);
        }
        return new SQLiteCursor(names, rows);
        } finally {
            if(finalize)IsakaiSQLite.nFinalize(stmt);else IsakaiSQLite.nReset(stmt);
        }
    }

    private RuntimeException stmtFail(long stmt, int negCode) {
        int code = -negCode;
        String msg;
        try {
            msg = IsakaiSQLite.nErr(handle, code);
        } catch (Throwable t) {
            msg = "statement failed (code " + code + ")";
        }
        if (code == 5 || code == 261 || code == 517 || code == 773) {
            return new SQLiteDatabaseLockedException(msg, code);
        }
        if (code == 19) {
            return new SQLiteConstraintException(msg, code);
        }
        return new SQLiteException(msg, code);
    }

    void bindAll(long stmt, Object[] args) {
        if (args == null) return;
        for (int i = 0; i < args.length; i++) {
            Object a = args[i];
            int idx = i + 1;
            int rc;
            if (a == null) {
                rc = IsakaiSQLite.nBindNull(stmt, idx);
            } else if (a instanceof Long || a instanceof Integer || a instanceof Short || a instanceof Byte) {
                rc = IsakaiSQLite.nBindLong(stmt, idx, ((Number) a).longValue());
            } else if (a instanceof Double || a instanceof Float) {
                rc = IsakaiSQLite.nBindDouble(stmt, idx, ((Number) a).doubleValue());
            } else if (a instanceof Boolean) {
                rc = IsakaiSQLite.nBindLong(stmt, idx, (Boolean) a ? 1 : 0);
            } else if (a instanceof byte[]) {
                rc = IsakaiSQLite.nBindBlob(stmt, idx, (byte[]) a);
            } else {
                rc = IsakaiSQLite.nBindText(stmt, idx, a.toString());
            }
            if (rc != 0) throw new SQLiteException("bind " + idx + " failed (code " + rc + ")", rc);
        }
    }

    long handleForStatement() {
        checkOpen();
        return handle;
    }

    RuntimeException failForStatement(int negCode) {
        return fail(negCode);
    }

    RuntimeException stmtFailForStatement(long stmt, int negCode) {
        return stmtFail(stmt, negCode);
    }

    public long getLastRowId() {
        lockSession();
        try {
        checkOpen();
        return IsakaiSQLite.nLastRowId(handle);

        } finally { unlockSession(); }
    }

    public long getChanges() {
        lockSession();
        try {
        checkOpen();
        return IsakaiSQLite.nChanges(handle);

        } finally { unlockSession(); }
    }

    @Override
    protected void onAllReferencesReleased() {
        lockSession();
        try {
        if (handle != 0) {
            IsakaiSQLite.nClose(handle);
            handle = 0;
        }

        } finally { unlockSession(); }
    }

    /** Convenience: open (or create) a database file inside this context's databases dir. */
    public static SQLiteDatabase openFromContext(Context context, String name) {
        java.io.File f = context.getDatabasePath(name);
        return openOrCreateDatabase(f.getAbsolutePath(), null);
    }
}
