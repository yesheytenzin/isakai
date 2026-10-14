// SPDX-License-Identifier: GPL-3.0-or-later
package android.database.sqlite;

import android.content.Context;

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
    private android.database.DatabaseErrorHandler errorHandler;
    private boolean writeAheadLogging;
    private SQLiteDatabase.CursorFactory factory;

    public SQLiteOpenHelper(Context context, String name, Object factory, int version) {
        if (version < 1) throw new IllegalArgumentException("version must be >= 1");
        this.context = context;
        this.name = name;
        this.version = version;
    }

    public SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory,
            int version, android.database.DatabaseErrorHandler errorHandler) {
        this(context, name, (Object) factory, version);
        this.factory=factory;
        this.errorHandler = errorHandler;
    }

    public SQLiteOpenHelper(Context context, String name, SQLiteDatabase.CursorFactory factory, int version) {
        this(context, name, factory, version, null);
    }

    public void onConfigure(SQLiteDatabase db) {}

    public synchronized void setWriteAheadLoggingEnabled(boolean enabled) {
        if (writeAheadLogging == enabled) return;
        if (cached != null && cached.isOpen()) {
            if (enabled) cached.enableWriteAheadLogging(); else cached.disableWriteAheadLogging();
        }
        writeAheadLogging = enabled;
    }

    public abstract void onCreate(SQLiteDatabase db);

    public abstract void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion);

    public void onDowngrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        throw new SQLiteException("downgrade from " + oldVersion + " to " + newVersion + " refused");
    }

    public void onOpen(SQLiteDatabase db) {}

    public synchronized SQLiteDatabase getWritableDatabase() {
        if (cached != null && cached.isOpen()) return cached;
        String path = name == null ? ":memory:" : context.getDatabasePath(name).getAbsolutePath();
        SQLiteDatabase db = SQLiteDatabase.openDatabase(path, factory,
                SQLiteDatabase.OPEN_READWRITE | SQLiteDatabase.CREATE_IF_NECESSARY, errorHandler);
        try {
            if (writeAheadLogging) db.enableWriteAheadLogging();
            onConfigure(db);
            int current = db.getVersion();
            if (current != version) {
                db.beginTransaction();
                try {
                    if (current == 0) onCreate(db);
                    else if (current < version) onUpgrade(db, current, version);
                    else onDowngrade(db, current, version);
                    db.setVersion(version);
                    db.setTransactionSuccessful();
                } finally {
                    db.endTransaction();
                }
            }
            onOpen(db);
            cached = db;
            return db;
        } catch (RuntimeException | Error failure) {
            db.close();
            throw failure;
        }
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

}
