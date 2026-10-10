// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

/** Compiled-statement bind shell. */
public abstract class SQLiteProgram implements AutoCloseable {
    protected SQLiteProgram() { }
    public void bindNull(int index) { }
    public void bindLong(int index, long value) { }
    public void bindDouble(int index, double value) { }
    public void bindString(int index, String value) { }
    public void bindBlob(int index, byte[] value) { }
    public void clearBindings() { }
    @Override public void close() { }
}
