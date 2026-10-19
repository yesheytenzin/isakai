// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

import java.util.ArrayList;
import java.util.List;

/** Mutable in-memory cursor: rows appended before iteration. */
public class MatrixCursor extends AbstractCursor {
    private final String[] names;
    private final List<Object[]> rows = new ArrayList<>();
    private int position = -1;
    private boolean closed;
    public MatrixCursor(String[] columnNames) { this(columnNames, 0); }
    public MatrixCursor(String[] columnNames, int initialCapacity) {
        if (columnNames == null) throw new IllegalArgumentException("columnNames null");
        names = columnNames.clone();
    }
    public RowBuilder newRow() { Object[] row = new Object[names.length]; rows.add(row); return new RowBuilder(row); }
    public void addRow(Object[] columnValues) {
        if (columnValues == null || columnValues.length != names.length) throw new IllegalArgumentException("bad row");
        rows.add(columnValues.clone());
    }
    public void addRow(Iterable<?> columnValues) {
        Object[] row = new Object[names.length]; int i = 0;
        for (Object v : columnValues) { if (i >= names.length) throw new IllegalArgumentException("too many values"); row[i++] = v; }
        if (i != names.length) throw new IllegalArgumentException("too few values");
        rows.add(row);
    }
    public final class RowBuilder {
        private final Object[] row; private int index;
        RowBuilder(Object[] row) { this.row = row; }
        public RowBuilder add(Object columnValue) {
            if (index >= row.length) throw new CursorIndexOutOfBoundsException(index, row.length);
            row[index++] = columnValue; return this;
        }
        /** AOSP: write by column name; rows may be built out of order. */
        public RowBuilder add(String columnName, Object columnValue) {
            if (columnName == null) throw new IllegalArgumentException("columnName == null");
            row[getColumnIndexOrThrow(columnName)] = columnValue;
            return this;
        }
    }
    private void checkOpen() { if (closed) throw new IllegalStateException("cursor closed"); }
    private Object value(int columnIndex) {
        checkOpen();
        if (position < 0 || position >= rows.size()) throw new CursorIndexOutOfBoundsException(position, rows.size());
        if (columnIndex < 0 || columnIndex >= names.length) throw new CursorIndexOutOfBoundsException(columnIndex, names.length);
        return rows.get(position)[columnIndex];
    }
    @Override public int getCount() { checkOpen(); return rows.size(); }
    @Override public int getColumnCount() { return names.length; }
    @Override public String[] getColumnNames() { return names.clone(); }
    @Override public int getColumnIndex(String columnName) { for (int i = 0; i < names.length; i++) if (names[i].equals(columnName)) return i; return -1; }
    @Override public int getColumnIndexOrThrow(String columnName) { int i = getColumnIndex(columnName); if (i < 0) throw new IllegalArgumentException("no column: " + columnName); return i; }
    @Override public String getColumnName(int columnIndex) { return names[columnIndex]; }
    @Override public boolean moveToFirst() { checkOpen(); if (rows.isEmpty()) { position = -1; return false; } position = 0; return true; }
    @Override public boolean moveToNext() { checkOpen(); if (position + 1 < rows.size()) { position++; return true; } position = rows.size(); return false; }
    @Override public boolean moveToPosition(int p) { checkOpen(); if (p < -1 || p > rows.size()) return false; position = p; return p >= 0 && p < rows.size(); }
    @Override public boolean isBeforeFirst() { return position < 0 && !rows.isEmpty(); }
    @Override public boolean isAfterLast() { return position >= rows.size(); }
    @Override public boolean isFirst() { return position == 0 && !rows.isEmpty(); }
    @Override public boolean isLast() { return position == rows.size() - 1 && !rows.isEmpty(); }
    @Override public boolean isClosed() { return closed; }
    @Override public int getPosition() { return position; }
    @Override public String getString(int c) { Object v = value(c); return v == null ? null : v.toString(); }
    @Override public int getInt(int c) { Object v = value(c); if (v == null) return 0; if (v instanceof Number) return ((Number) v).intValue(); return Integer.parseInt(v.toString()); }
    @Override public long getLong(int c) { Object v = value(c); if (v == null) return 0L; if (v instanceof Number) return ((Number) v).longValue(); return Long.parseLong(v.toString()); }
    @Override public float getFloat(int c) { Object v = value(c); if (v == null) return 0f; if (v instanceof Number) return ((Number) v).floatValue(); return Float.parseFloat(v.toString()); }
    @Override public double getDouble(int c) { Object v = value(c); if (v == null) return 0.0; if (v instanceof Number) return ((Number) v).doubleValue(); return Double.parseDouble(v.toString()); }
    @Override public short getShort(int c) { return (short) getInt(c); }
    @Override public byte[] getBlob(int c) { Object v = value(c); return (byte[]) v; }
    @Override public boolean isNull(int c) { return value(c) == null; }
    @Override public void close() { closed = true; }
}
