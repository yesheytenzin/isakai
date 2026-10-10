// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.net.Uri;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * In-process ContentResolver: routes {@code content://} URIs to the
 * {@link ContentProvider} registry by authority, serves
 * {@code file://} directly off the filesystem, and resolves
 * {@code android.resource://} against the app's code directory
 * ({@code -Disakai.codeDir}, staged layout {@code res/} +
 * {@code assets/}). Unknown authorities and missing files fail closed
 * (null cursor / FileNotFoundException); observers fire synchronously
 * on notifyChange.
 */
public abstract class ContentResolver {
    public static final String SCHEME_CONTENT = "content";
    public static final String SCHEME_ANDROID_RESOURCE = "android.resource";
    public static final String SCHEME_FILE = "file";

    public ContentResolver() {}

    // --- observer bookkeeping (process-local) ---

    private static final Map<String, List<android.database.ContentObserver>> OBSERVERS = new HashMap<>();

    public void registerContentObserver(Uri uri, boolean notifyForDescendants,
            android.database.ContentObserver observer) {
        if (uri == null || observer == null) throw new NullPointerException("uri/observer");
        String key = uri.toString();
        synchronized (OBSERVERS) {
            OBSERVERS.computeIfAbsent(key, k -> new ArrayList<>()).add(observer);
        }
    }

    public void unregisterContentObserver(android.database.ContentObserver observer) {
        if (observer == null) return;
        synchronized (OBSERVERS) {
            for (List<android.database.ContentObserver> l : OBSERVERS.values()) {
                l.remove(observer);
            }
        }
    }

    public void notifyChange(Uri uri, android.database.ContentObserver observer) {
        notifyChangeStatic(uri);
    }

    static void notifyChangeStatic(Uri uri) {
        if (uri == null) return;
        String target = uri.toString();
        List<android.database.ContentObserver> fire = new ArrayList<>();
        synchronized (OBSERVERS) {
            for (Map.Entry<String, List<android.database.ContentObserver>> e : OBSERVERS.entrySet()) {
                if (target.equals(e.getKey()) || target.startsWith(e.getKey() + "/")) {
                    fire.addAll(e.getValue());
                }
            }
        }
        for (android.database.ContentObserver o : fire) {
            o.onChange(false, uri);
        }
    }

    // --- provider routing ---

    private static String authorityOf(Uri uri) {
        if (uri == null) return null;
        String s = uri.toString();
        String scheme = uri.getScheme();
        if (scheme == null || !scheme.equals(SCHEME_CONTENT)) return null;
        // content://authority[/path...]
        int c = s.indexOf(':');
        if (c < 0) return null;
        int start = c + 1;
        while (start < s.length() && s.charAt(start) == '/') start++;
        int end = s.indexOf('/', start);
        if (end < 0) end = s.length();
        String auth = s.substring(start, end);
        return auth.isEmpty() ? null : auth;
    }

    private ContentProvider resolveProvider(Uri uri) {
        String auth = authorityOf(uri);
        if (auth == null) return null;
        return ContentProvider.resolve(auth);
    }

    public ContentProviderClient acquireUnstableContentProviderClient(Uri uri) {
        return acquireContentProviderClient(uri);
    }

    public ContentProviderClient acquireContentProviderClient(Uri uri) {
        ContentProvider p = resolveProvider(uri);
        if (p == null) return null;
        return new ContentProviderClient(this, p);
    }

    // --- CRUD ---

    public android.database.Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder) {
        return query(uri, projection, selection, selectionArgs, sortOrder, null);
    }

    public android.database.Cursor query(Uri uri, String[] projection, String selection,
            String[] selectionArgs, String sortOrder, CancellationSignal signal) {
        if (signal != null) signal.throwIfCanceled();
        ContentProvider p = resolveProvider(uri);
        if (p == null) return null;
        return p.query(uri, projection, selection, selectionArgs, sortOrder);
    }

    public android.database.Cursor query(Uri uri, String[] projection, Bundle queryArgs,
            CancellationSignal signal) {
        if (signal != null) signal.throwIfCanceled();
        ContentProvider p = resolveProvider(uri);
        if (p == null) return null;
        return p.query(uri, projection, queryArgs, signal);
    }

    public Uri insert(Uri uri, ContentValues values) {
        ContentProvider p = resolveProvider(uri);
        if (p == null) return null;
        Uri out = p.insert(uri, values);
        if (out != null) notifyChangeStatic(out);
        notifyChangeStatic(uri);
        return out;
    }

    public int bulkInsert(Uri uri, ContentValues[] values) {
        ContentProvider p = resolveProvider(uri);
        if (p == null) return 0;
        int n = p.bulkInsert(uri, values);
        if (n > 0) notifyChangeStatic(uri);
        return n;
    }

    public int update(Uri uri, ContentValues values, String where, String[] selectionArgs) {
        ContentProvider p = resolveProvider(uri);
        if (p == null) return 0;
        int n = p.update(uri, values, where, selectionArgs);
        if (n > 0) notifyChangeStatic(uri);
        return n;
    }

    public int delete(Uri uri, String where, String[] selectionArgs) {
        ContentProvider p = resolveProvider(uri);
        if (p == null) return 0;
        int n = p.delete(uri, where, selectionArgs);
        if (n > 0) notifyChangeStatic(uri);
        return n;
    }

    public String getType(Uri uri) {
        ContentProvider p = resolveProvider(uri);
        if (p == null) return null;
        return p.getType(uri);
    }

    public String[] getStreamTypes(Uri uri, String mimeTypeFilter) {
        ContentProvider p = resolveProvider(uri);
        if (p == null) return null;
        return p.getStreamTypes(uri, mimeTypeFilter);
    }

    // --- file access ---

    public ParcelFileDescriptor openFileDescriptor(Uri uri, String mode) throws FileNotFoundException {
        return openFileDescriptor(uri, mode, null);
    }

    public ParcelFileDescriptor openFileDescriptor(Uri uri, String mode, CancellationSignal signal)
            throws FileNotFoundException {
        if (uri == null) throw new FileNotFoundException("null uri");
        if (signal != null) signal.throwIfCanceled();
        String scheme = uri.getScheme();
        if (scheme == null) throw new FileNotFoundException("no scheme: " + uri);
        if (scheme.equals(SCHEME_CONTENT)) {
            ContentProvider p = resolveProvider(uri);
            if (p == null) throw new FileNotFoundException("no providers: " + uri);
            return p.openFile(uri, mode, signal);
        }
        int flags;
        try {
            flags = ParcelFileDescriptor.parseMode(mode);
        } catch (java.io.IOException e) {
            throw new FileNotFoundException("bad mode " + mode);
        }
        File f = resolveLocalFile(uri);
        if (f == null) throw new FileNotFoundException("cannot resolve " + uri);
        try {
            return ParcelFileDescriptor.open(f, flags);
        } catch (java.io.IOException e) {
            throw new FileNotFoundException(e.getMessage());
        }
    }

    /** Map file:// and android.resource:// to a host path; null when unresolvable. */
    private static File resolveLocalFile(Uri uri) {
        String scheme = uri.getScheme();
        if (scheme.equals(SCHEME_FILE)) {
            String path = uri.getPath();
            if (path == null || path.isEmpty()) return null;
            return new File(path);
        }
        if (scheme.equals(SCHEME_ANDROID_RESOURCE)) {
            // android.resource://[package/]<type>/<name>: look under
            // <codeDir>/res/<type>/<name>.* then <codeDir>/assets/<name>.
            String codeDir = System.getProperty("isakai.codeDir", "");
            if (codeDir.isEmpty()) return null;
            String path = uri.getPath();
            if (path == null) return null;
            while (path.startsWith("/")) path = path.substring(1);
            String[] parts = path.split("/");
            File base = new File(codeDir);
            if (parts.length >= 2) {
                File resDir = new File(new File(base, "res"), parts[0]);
                String[] names = resDir.list();
                if (names != null) {
                    for (String n : names) {
                        if (n.equals(parts[1]) || n.startsWith(parts[1] + ".")) {
                            return new File(resDir, n);
                        }
                    }
                }
                File asset = new File(new File(base, "assets"), parts[parts.length - 1]);
                if (asset.exists()) return asset;
                return null;
            }
            if (parts.length == 1 && !parts[0].isEmpty()) {
                File asset = new File(new File(base, "assets"), parts[0]);
                if (asset.exists()) return asset;
            }
            return null;
        }
        return null;
    }
}
