// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

import android.content.Context;
import android.database.Cursor;

/**
 * Versioned database holder: opens (creating with {@link #onCreate} at
 * version 0) and migrates with {@link #onUpgrade} / {@link #onDowngrade}.
 * The user version is tracked with {@code PRAGMA user_version}; the helper
 * serializes opens and caches one {@link SQLiteDatabase} per instance.
 */
public abstract class SQLiteOpenHelper {
    private final Context context;
    private final String name;
    private final int version;
    private SQLiteDatabase cached;

    public SQLiteOpenHelper(Context context, String name, Object factory, int version) {
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        this.context = context;
        this.name = name;
        this.version = version;
    }

    public abstract void onCreate(SQLiteDatabase db);

    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new SQLiteException("downgrade from " + oldVersion + " to " + newVersion + " refused");
    }

    public void onOpen(SQLiteDatabase db) {}

    public synchronized SQLiteDatabase getWritableDatabase() {
        if (cached != null && cached.isOpen()) return cached;
        SQLiteDatabase db = SQLiteDatabase.openFromContext(context, name);
        int current = (int) pragmaVersion(db);
        if (current == 0) {
            onCreate(db);
            setPragmaVersion(db, version);
        } else if (current < version) {
            onUpgrade(db, current, version);
            setPragmaVersion(db, version);
        } else if (current > version) {
            onDowngrade(db, current, version);
            setPragmaVersion(db, version);
        }
        onOpen(db);
        cached = db;
        return db;
    }

    public synchronized SQLiteDatabase getReadableDatabase() {
        return getWritableDatabase();
    }

    public synchronized void close() {
        if (cached != null) {
            cached.close();
            cached = null;
        }
    }

    public String getDatabaseName() {
        return name;
    }

    private static long pragmaVersion(SQLiteDatabase db) {
        try (Cursor c = db.rawQuery("PRAGMA user_version;", null)) {
            if (c.moveToFirst()) return c.getLong(0);
        }
        return 0;
    }

    private static void setPragmaVersion(SQLiteDatabase db, int version) {
        db.execSQL("PRAGMA user_version = " + version + ";");
    }
}
