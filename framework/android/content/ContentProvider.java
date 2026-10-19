// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.content.res.AssetFileDescriptor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Map;

/**
 * the CRUD hooks plus {@link #openFile}; the static registry routes
 * {@link ContentResolver} calls by URI authority.
 */
public abstract class ContentProvider {
    private Context context;

    /** Authority -> live provider, populated by attachInfo/register. */
    private static final Map<String, ContentProvider> REGISTRY = new HashMap<>();

    /** Authority roots granted by {@link #grantUriPermissions}; checked on openFile only. */
    private static final java.util.Set<String> GRANTS =
            java.util.Collections.synchronizedSet(new java.util.HashSet<String>());

    public void attachInfo(Context context) {
        this.context = context;
    }

    /**
     * Attach with an explicit authority list (mirrors the manifest
     * provider record). Calls {@link #onCreate} once, then publishes
     * each authority. Re-registration of the same instance is idempotent.
     */
    public void attachInfo(Context context, android.content.pm.ProviderInfo info) {
        this.context = context;
        boolean created = onCreate();
        if (info != null && info.authority != null) {
            for (String auth : info.authority.split(";")) {
                String a = auth.trim();
                if (a.isEmpty()) continue;
                synchronized (REGISTRY) {
                    ContentProvider prev = REGISTRY.get(a);
                    if (prev == null || prev == this) REGISTRY.put(a, this);
                }
            }
        }
        if (!created) {
            // onCreate false: unpublish (fail-closed, provider stays unusable).
            unregister(this);
        }
    }

    /** Test/host hook: publish {@code provider} under {@code authority}. */
    public static void registerForTesting(String authority, ContentProvider provider) {
        if (authority == null || provider == null) throw new NullPointerException("authority/provider");
        synchronized (REGISTRY) {
            REGISTRY.put(authority, provider);
        }
    }

    /** Test/host hook: drop every registration of {@code provider}. */
    public static void unregister(ContentProvider provider) {
        synchronized (REGISTRY) {
            REGISTRY.values().removeIf(p -> p == provider);
        }
    }

    /** Test/host hook: clear the whole registry. */
    public static void clearRegistryForTesting() {
        synchronized (REGISTRY) {
            REGISTRY.clear();
        }
        GRANTS.clear();
    }

    static ContentProvider resolve(String authority) {
        synchronized (REGISTRY) {
            return REGISTRY.get(authority);
        }
    }

    /** Grant read/write access to one URI subtree (checked by openFile). */
    public static void grantUriPermission(String uriPrefix) {
        if (uriPrefix != null) GRANTS.add(uriPrefix);
    }

    static boolean isGranted(String uri) {
        for (String g : GRANTS) {
            if (uri.startsWith(g)) return true;
        }
        return false;
    }

    public Context getContext() {
        return context;
    }

    public boolean onCreate() {
        return true;
    }

    public android.database.Cursor query(android.net.Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return null;
    }

    public android.database.Cursor query(android.net.Uri uri, String[] projection,
            android.os.Bundle queryArgs, CancellationSignal signal) {
        return query(uri, projection, null, null, null);
    }

    public android.net.Uri insert(android.net.Uri uri, ContentValues values) {
        return null;
    }

    public int bulkInsert(android.net.Uri uri, ContentValues[] values) {
        if (values == null) return 0;
        int n = 0;
        for (ContentValues v : values) {
            if (insert(uri, v) != null) n++;
        }
        return n;
    }

    public int update(android.net.Uri uri, ContentValues values, String where, String[] selectionArgs) {
        return 0;
    }

    public int delete(android.net.Uri uri, String where, String[] selectionArgs) {
        return 0;
    }

    public String getType(android.net.Uri uri) {
        return null;
    }

    public String[] getStreamTypes(android.net.Uri uri, String mimeTypeFilter) {
        return null;
    }

    /**
     * AOSP default: providers that host no calls refuse them. Documents-provider
     * emulation routes {@code createDocument} through here.
     */
    public android.os.Bundle call(String method, String arg, android.os.Bundle extras) {
        throw new UnsupportedOperationException("call(" + method + ") not supported");
    }

    public ParcelFileDescriptor openFile(android.net.Uri uri, String mode) throws FileNotFoundException {
        throw new FileNotFoundException("no file for " + uri);
    }

    public ParcelFileDescriptor openFile(android.net.Uri uri, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (signal != null) signal.throwIfCanceled();
        return openFile(uri, mode);
    }

    public AssetFileDescriptor openAssetFile(android.net.Uri uri, String mode) throws FileNotFoundException {
        ParcelFileDescriptor pfd = openFile(uri, mode);
        return new AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
    }

    /** Notify observers registered under {@code uri} (or its ancestors). */
    protected void notifyChange(android.net.Uri uri) {
        ContentResolver.notifyChangeStatic(uri);
    }
}
