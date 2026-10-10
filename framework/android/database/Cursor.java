// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/**
 * Minimal forward-only Cursor over an in-memory row table. Positions start
 * at -1 (before first); {@link #moveToNext} advances, {@link #moveToFirst}
 * rewinds. Column lookup by name is case-sensitive; unknown names throw
 * {@link IllegalArgumentException}. Type getters coerce like AOSP (numeric
 * strings parse, null yields 0/0.0/false/null).
 */
public interface Cursor extends AutoCloseable {
    int getCount();

    int getColumnCount();

    String[] getColumnNames();

    int getColumnIndex(String columnName);

    int getColumnIndexOrThrow(String columnName) throws IllegalArgumentException;

    String getColumnName(int columnIndex);

    boolean moveToFirst();

    boolean moveToNext();

    boolean moveToPosition(int position);

    boolean isBeforeFirst();

    boolean isAfterLast();

    boolean isFirst();

    boolean isLast();

    boolean isClosed();

    int getPosition();

    String getString(int columnIndex);

    int getInt(int columnIndex);

    long getLong(int columnIndex);

    float getFloat(int columnIndex);

    double getDouble(int columnIndex);

    short getShort(int columnIndex);

    byte[] getBlob(int columnIndex);

    boolean isNull(int columnIndex);

    @Override
    void close();
}
