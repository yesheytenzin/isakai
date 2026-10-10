// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import android.os.RemoteException;
import java.io.FileNotFoundException;

/**
 * Thin client over one registered {@link ContentProvider}: every call
 * forwards in-process (CancellationSignal honored before dispatch).
 */
public class ContentProviderClient implements AutoCloseable {
    private final ContentResolver resolver;
    private final ContentProvider provider;
    private boolean released;

    /** Framework only: created by {@link ContentResolver#acquireContentProviderClient}. */
    public ContentProviderClient(ContentResolver resolver, ContentProvider provider) {
        if (provider == null) throw new NullPointerException("provider");
        this.resolver = resolver;
        this.provider = provider;
    }

    /** Legacy package-private construction kept for link shape; unusable without a provider. */
    ContentProviderClient() {
        this.resolver = null;
        this.provider = null;
        this.released = true;
    }

    private ContentProvider target() throws RemoteException {
        if (released || provider == null) throw new RemoteException("released");
        return provider;
    }

    public android.database.Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) throws RemoteException {
        return target().query(uri, projection, selection, selectionArgs, sortOrder);
    }

    public android.database.Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder, CancellationSignal signal) throws RemoteException {
        if (signal != null) signal.throwIfCanceled();
        return target().query(uri, projection, selection, selectionArgs, sortOrder);
    }

    public android.database.Cursor query(Uri uri, String[] projection, Bundle queryArgs,
            CancellationSignal signal) throws RemoteException {
        if (signal != null) signal.throwIfCanceled();
        return target().query(uri, projection, queryArgs, signal);
    }

    public Uri insert(Uri uri, ContentValues values) throws RemoteException {
        return target().insert(uri, values);
    }

    public int bulkInsert(Uri uri, ContentValues[] values) throws RemoteException {
        return target().bulkInsert(uri, values);
    }

    public int update(Uri uri, ContentValues values, String where, String[] selectionArgs)
            throws RemoteException {
        return target().update(uri, values, where, selectionArgs);
    }

    public int delete(Uri uri, String where, String[] selectionArgs) throws RemoteException {
        return target().delete(uri, where, selectionArgs);
    }

    public String getType(Uri uri) throws RemoteException {
        return target().getType(uri);
    }

    public ParcelFileDescriptor openFile(Uri uri, String mode) throws RemoteException, FileNotFoundException {
        return target().openFile(uri, mode);
    }

    public ParcelFileDescriptor openFile(Uri uri, String mode, CancellationSignal signal)
            throws RemoteException, FileNotFoundException {
        if (signal != null) signal.throwIfCanceled();
        return target().openFile(uri, mode, signal);
    }

    public boolean release() {
        released = true;
        return true;
    }

    @Override
    public void close() {
        release();
    }
}
