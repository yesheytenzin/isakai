// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

/** Native prepared query with typed parameters, eagerly copied into a cursor. */
public final class SQLiteQuery extends SQLiteProgram {
    private final SQLiteStatement statement;
    private final String sql;
    private final android.os.CancellationSignal cancellation;
    SQLiteQuery(SQLiteDatabase db,String sql,android.os.CancellationSignal cancellation) {
        this.sql=sql;this.cancellation=cancellation;statement=db.compileStatement(sql);
    }
    @Override public void bindNull(int index) { statement.bindNull(index); }
    @Override public void bindLong(int index,long value) { statement.bindLong(index,value); }
    @Override public void bindDouble(int index,double value) { statement.bindDouble(index,value); }
    @Override public void bindString(int index,String value) { statement.bindString(index,value); }
    @Override public void bindBlob(int index,byte[] value) { statement.bindBlob(index,value); }
    @Override public void clearBindings() { statement.clearBindings(); }
    SQLiteCursor execute() { return (SQLiteCursor)statement.queryRows(cancellation); }
    @Override protected void onAllReferencesReleased() { statement.close(); }
    @Override public String toString() { return sql; }
}
