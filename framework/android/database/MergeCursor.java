// SPDX-License-Identifier: GPL-3.0-or-later
package android.database;

/** Concatenated view over two cursors. */
public class MergeCursor implements Cursor {
    private final Cursor[] cursors;
    private int active;
    private boolean closed;
    public MergeCursor(Cursor[] cursors) { this.cursors = cursors == null ? new Cursor[0] : cursors.clone(); }
    private void checkOpen() { if (closed) throw new IllegalStateException("cursor closed"); }
    @Override public int getCount() { int n = 0; for (Cursor c : cursors) n += c.getCount(); return n; }
    @Override public int getColumnCount() { return cursors.length == 0 ? 0 : cursors[0].getColumnCount(); }
    @Override public String[] getColumnNames() { return cursors.length == 0 ? new String[0] : cursors[0].getColumnNames(); }
    @Override public int getColumnIndex(String name) { return cursors.length == 0 ? -1 : cursors[0].getColumnIndex(name); }
    @Override public int getColumnIndexOrThrow(String name) { if (cursors.length == 0) throw new IllegalArgumentException("no column: " + name); return cursors[0].getColumnIndexOrThrow(name); }
    @Override public String getColumnName(int i) { return cursors[0].getColumnName(i); }
    @Override public boolean moveToFirst() { checkOpen(); active = 0; for (Cursor c : cursors) c.moveToPosition(-1); for (int i = 0; i < cursors.length; i++) { if (cursors[i].moveToFirst()) { active = i; return true; } } return false; }
    @Override public boolean moveToNext() { checkOpen(); if (cursors.length == 0) return false; if (cursors[active].moveToNext()) return true; for (int i = active + 1; i < cursors.length; i++) { if (cursors[i].moveToFirst()) { active = i; return true; } } return false; }
    @Override public boolean moveToPosition(int p) { checkOpen(); if (p < 0 || p >= getCount()) return false; moveToFirst(); for (int i = 0; i < p; i++) moveToNext(); return true; }
    private boolean empty() { return cursors.length == 0; }
    @Override public boolean isBeforeFirst() { return false; }
    @Override public boolean isAfterLast() { return false; }
    @Override public boolean isFirst() { return active == 0 && !empty() && cursors[0].isFirst(); }
    @Override public boolean isLast() { return !empty() && active == cursors.length - 1 && cursors[active].isLast(); }
    @Override public boolean isClosed() { return closed; }
    @Override public int getPosition() { int n = 0; for (int i = 0; i < active; i++) n += cursors[i].getCount(); return empty() ? -1 : n + cursors[active].getPosition(); }
    @Override public String getString(int c) { return cursors[active].getString(c); }
    @Override public int getInt(int c) { return cursors[active].getInt(c); }
    @Override public long getLong(int c) { return cursors[active].getLong(c); }
    @Override public float getFloat(int c) { return cursors[active].getFloat(c); }
    @Override public double getDouble(int c) { return cursors[active].getDouble(c); }
    @Override public short getShort(int c) { return cursors[active].getShort(c); }
    @Override public byte[] getBlob(int c) { return cursors[active].getBlob(c); }
    @Override public boolean isNull(int c) { return cursors[active].isNull(c); }
    @Override public void close() { closed = true; for (Cursor c : cursors) c.close(); }
}
