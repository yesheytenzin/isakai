// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

import android.database.Cursor;
import java.util.Collections;
import java.util.List;

/**
 * Eager cursor: all rows are buffered at query time (the native statement
 * is already stepped to DONE and finalized). Position starts before first.
 */
public final class SQLiteCursor implements Cursor {
    private final String[] names;
    private final List<Object[]> rows;
    private int position = -1;
    private boolean closed;

    public SQLiteCursor(String[] names, List<Object[]> rows) {
        this.names = names.clone();
        this.rows = Collections.unmodifiableList(rows);
    }

    private void checkOpen() {
        if (closed) throw new IllegalStateException("cursor closed");
    }

    private Object value(int columnIndex) {
        checkOpen();
        if (position < 0 || position >= rows.size()) {
            throw new android.database.CursorIndexOutOfBoundsException(position, rows.size());
        }
        if (columnIndex < 0 || columnIndex >= names.length) {
            throw new android.database.CursorIndexOutOfBoundsException(columnIndex, names.length);
        }
        return rows.get(position)[columnIndex];
    }

    @Override
    public int getCount() {
        checkOpen();
        return rows.size();
    }

    @Override
    public int getColumnCount() {
        return names.length;
    }

    @Override
    public String[] getColumnNames() {
        return names.clone();
    }

    @Override
    public int getColumnIndex(String columnName) {
        for (int i = 0; i < names.length; i++) {
            if (names[i].equals(columnName)) return i;
        }
        return -1;
    }

    @Override
    public int getColumnIndexOrThrow(String columnName) {
        int idx = getColumnIndex(columnName);
        if (idx < 0) throw new IllegalArgumentException("no column: " + columnName);
        return idx;
    }

    @Override
    public String getColumnName(int columnIndex) {
        return names[columnIndex];
    }

    @Override
    public boolean moveToFirst() {
        checkOpen();
        if (rows.isEmpty()) {
            position = -1;
            return false;
        }
        position = 0;
        return true;
    }

    @Override
    public boolean moveToNext() {
        checkOpen();
        if (position + 1 < rows.size()) {
            position++;
            return true;
        }
        position = rows.size();
        return false;
    }

    @Override
    public boolean moveToPosition(int position) {
        checkOpen();
        if (position < -1 || position > rows.size()) return false;
        this.position = position;
        return position >= 0 && position < rows.size();
    }

    @Override
    public boolean isBeforeFirst() {
        return position < 0 && !rows.isEmpty();
    }

    @Override
    public boolean isAfterLast() {
        return position >= rows.size();
    }

    @Override
    public boolean isFirst() {
        return position == 0 && !rows.isEmpty();
    }

    @Override
    public boolean isLast() {
        return position == rows.size() - 1 && !rows.isEmpty();
    }

    @Override
    public boolean isClosed() {
        return closed;
    }

    @Override
    public int getPosition() {
        return position;
    }

    private static long toLong(Object v) {
        if (v == null) return 0;
        if (v instanceof Number) return ((Number) v).longValue();
        if (v instanceof byte[]) return 0;
        try {
            return Long.parseLong(v.toString());
        } catch (NumberFormatException e) {
            try {
                return (long) Double.parseDouble(v.toString());
            } catch (NumberFormatException e2) {
                return 0;
            }
        }
    }

    private static double toDouble(Object v) {
        if (v == null) return 0.0;
        if (v instanceof Number) return ((Number) v).doubleValue();
        if (v instanceof byte[]) return 0.0;
        try {
            return Double.parseDouble(v.toString());
        } catch (NumberFormatException e) {
            return 0.0;
        }
    }

    @Override
    public String getString(int columnIndex) {
        Object v = value(columnIndex);
        if (v == null) return null;
        if (v instanceof byte[]) return new String((byte[]) v, java.nio.charset.StandardCharsets.UTF_8);
        return v.toString();
    }

    @Override
    public int getInt(int columnIndex) {
        return (int) toLong(value(columnIndex));
    }

    @Override
    public long getLong(int columnIndex) {
        return toLong(value(columnIndex));
    }

    @Override
    public float getFloat(int columnIndex) {
        return (float) toDouble(value(columnIndex));
    }

    @Override
    public double getDouble(int columnIndex) {
        return toDouble(value(columnIndex));
    }

    @Override
    public short getShort(int columnIndex) {
        return (short) toLong(value(columnIndex));
    }

    @Override
    public byte[] getBlob(int columnIndex) {
        Object v = value(columnIndex);
        if (v == null) return null;
        if (v instanceof byte[]) return ((byte[]) v).clone();
        return v.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8);
    }

    @Override
    public boolean isNull(int columnIndex) {
        return value(columnIndex) == null;
    }

    @Override
    public void close() {
        closed = true;
    }
}
