// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.db;

/**
 * JNI doorway to the Zig SQLite bridge ({@code libisakai_jni.so},
 * {@code src/bridge/sqlite.zig} against the system libsqlite3).
 *
 * <p>Handles are native {@code sqlite3*} / {@code sqlite3_stmt*} pointers as
 * jlongs (0 = failure). Opens use FULLMUTEX + a 5s busy timeout; database
 * files are created 0600. Native errors surface as 0/negative returns and
 * string messages; {@code SQLiteDatabase} translates them into
 * {@code SQLiteException} (code 5 becomes
 * {@code SQLiteDatabaseLockedException}), so no {@code ThrowNew} here.
 */
public final class IsakaiSQLite {
    static {
        System.loadLibrary("isakai_jni");
    }

    private IsakaiSQLite() {}

    public static native long nOpen(String path, int flags);

    public static native int nClose(long db);

    public static native String nErr(long db, int code);

    public static native int nExec(long db, String sql);

    public static native long nPrepare(long db, String sql);

    public static native int nFinalize(long stmt);

    public static native int nReset(long stmt);
    public static native int nClearBindings(long stmt);

    public static native int nBindNull(long stmt, int index);

    public static native int nBindLong(long stmt, int index, long value);

    public static native int nBindDouble(long stmt, int index, double value);

    public static native int nBindText(long stmt, int index, String value);

    public static native int nBindBlob(long stmt, int index, byte[] value);

    /** 100 = row ready, 101 = done, negative = -errno on error. */
    public static native int nStep(long stmt);

    public static native int nColumnCount(long stmt);

    public static native String nColumnName(long stmt, int col);

    /** 1=null 2=int 3=float 4=text 5=blob. */
    public static native int nColumnType(long stmt, int col);

    public static native long nColumnLong(long stmt, int col);

    public static native double nColumnDouble(long stmt, int col);

    public static native String nColumnText(long stmt, int col);

    public static native byte[] nColumnBlob(long stmt, int col);

    public static native long nLastRowId(long db);

    public static native long nChanges(long db);
}
