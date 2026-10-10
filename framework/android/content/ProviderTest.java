// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.database.Cursor;
import android.database.sqlite.SQLiteCursor;
import android.net.Uri;
import android.os.CancellationSignal;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileNotFoundException;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Host-side unit checks for in-process provider hosting + resolver
 * routing + file-scheme support. Run on the host JVM.
 */
public final class ProviderTest {
    private ProviderTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    /** Tiny table provider: row id -> name, backed by a HashMap. */
    static final class TableProvider extends ContentProvider {
        final Map<Long, String> rows = new HashMap<>();
        long nextId = 1;
        static final String AUTH = "test.table";
        static final Uri BASE = Uri.parse("content://" + AUTH + "/rows");

        @Override
        public Cursor query(Uri uri, String[] projection, String selection,
                String[] selectionArgs, String sortOrder) {
            String[] names = {"_id", "name"};
            List<Object[]> out = new ArrayList<>();
            List<Long> ids = new ArrayList<>(rows.keySet());
            Collections.sort(ids);
            for (Long id : ids) {
                out.add(new Object[] {id, rows.get(id)});
            }
            return new SQLiteCursor(names, out);
        }

        @Override
        public Uri insert(Uri uri, ContentValues values) {
            long id = nextId++;
            rows.put(id, values.getAsString("name"));
            return Uri.parse(BASE + "/" + id);
        }

        @Override
        public int update(Uri uri, ContentValues values, String where, String[] selectionArgs) {
            int n = 0;
            for (Map.Entry<Long, String> e : rows.entrySet()) {
                if (where == null || where.equals(e.getValue())) {
                    e.setValue(values.getAsString("name"));
                    n++;
                }
            }
            return n;
        }

        @Override
        public int delete(Uri uri, String where, String[] selectionArgs) {
            if (where == null) {
                int n = rows.size();
                rows.clear();
                return n;
            }
            int n = 0;
            for (Long id : new ArrayList<>(rows.keySet())) {
                if (where.equals(rows.get(id))) {
                    rows.remove(id);
                    n++;
                }
            }
            return n;
        }

        @Override
        public String getType(Uri uri) {
            return "vnd.android.cursor.dir/vnd.test.rows";
        }

        @Override
        public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
            throw new FileNotFoundException("no files here: " + uri);
        }
    }

    static ContentResolver resolver() {
        return new ContentResolver() {};
    }

    public static void main(String[] args) throws Exception {
        ContentProvider.clearRegistryForTesting();
        TableProvider provider = new TableProvider();
        android.content.pm.ProviderInfo info = new android.content.pm.ProviderInfo();
        info.authority = TableProvider.AUTH;
        provider.attachInfo(null, info);
        ContentResolver cr = resolver();

        // Insert round-trip: two rows, returned URIs carry ids.
        ContentValues a = new ContentValues();
        a.put("name", "alpha");
        Uri ua = cr.insert(TableProvider.BASE, a);
        check(ua != null && ua.toString().startsWith(TableProvider.BASE.toString() + "/"), "insert uri a");
        ContentValues bval = new ContentValues();
        bval.put("name", "beta");
        Uri ub = cr.insert(TableProvider.BASE, bval);
        check(ub != null && !ub.toString().equals(ua.toString()), "insert uri b");

        // Query round-trip: both rows visible in order.
        Cursor c = cr.query(TableProvider.BASE, null, null, null, null);
        check(c != null && c.getCount() == 2, "query count");
        check(c.moveToFirst(), "move first");
        check("alpha".equals(c.getString(1)), "row 0");
        check(c.moveToNext(), "move next");
        check("beta".equals(c.getString(1)), "row 1");
        c.close();

        // Update round-trip.
        ContentValues upd = new ContentValues();
        upd.put("name", "gamma");
        check(cr.update(TableProvider.BASE, upd, "beta", null) == 1, "update count");
        Cursor c2 = cr.query(TableProvider.BASE, null, null, null, null);
        check(c2.moveToFirst() && c2.moveToNext(), "query after update");
        check("gamma".equals(c2.getString(1)), "updated value");
        c2.close();

        // Delete round-trip.
        check(cr.delete(TableProvider.BASE, "alpha", null) == 1, "delete count");
        Cursor c3 = cr.query(TableProvider.BASE, null, null, null, null);
        check(c3.getCount() == 1, "count after delete");
        c3.close();

        // getType + client path.
        check("vnd.android.cursor.dir/vnd.test.rows".equals(cr.getType(TableProvider.BASE)), "getType");
        ContentProviderClient client = cr.acquireContentProviderClient(TableProvider.BASE);
        check(client != null, "client acquired");
        Cursor cc = client.query(TableProvider.BASE, null, null, null, null);
        check(cc != null && cc.getCount() == 1, "client query");
        cc.close();
        client.close();

        // Unknown authority: fail-closed (nulls/zeros, no throw).
        Uri ghost = Uri.parse("content://no.such/in");
        check(cr.query(ghost, null, null, null, null) == null, "ghost query null");
        check(cr.insert(ghost, a) == null, "ghost insert null");
        check(cr.update(ghost, a, null, null) == 0, "ghost update 0");
        check(cr.delete(ghost, null, null) == 0, "ghost delete 0");
        check(cr.acquireContentProviderClient(ghost) == null, "ghost client null");
        boolean threw = false;
        try {
            cr.openFileDescriptor(ghost, "r");
        } catch (FileNotFoundException expected) {
            threw = true;
        }
        check(threw, "ghost open throws");

        // Cancellation honored before dispatch.
        CancellationSignal sig = new CancellationSignal();
        sig.cancel();
        boolean canceled = false;
        try {
            cr.query(TableProvider.BASE, null, null, null, null, sig);
        } catch (CancellationSignal.OperationCanceledException expected) {
            canceled = true;
        }
        check(canceled, "cancel honored");

        // Observers fire on insert.
        final int[] fired = {0};
        android.database.ContentObserver obs = new android.database.ContentObserver() {
            @Override
            public void onChange(boolean selfChange, Uri uri) {
                fired[0]++;
            }
        };
        cr.registerContentObserver(TableProvider.BASE, true, obs);
        ContentValues cv = new ContentValues();
        cv.put("name", "delta");
        cr.insert(TableProvider.BASE, cv);
        check(fired[0] > 0, "observer fired");
        cr.unregisterContentObserver(obs);

        // file:// opens a real file read/write.
        File tmp = File.createTempFile("providertest", ".bin");
        tmp.deleteOnExit();
        Files.write(tmp.toPath(), new byte[] {1, 2, 3});
        Uri fileUri = Uri.parse("file://" + tmp.getAbsolutePath());
        ParcelFileDescriptor pfd = cr.openFileDescriptor(fileUri, "r");
        check(pfd != null && pfd.getStatSize() == 3, "file stat size");
        pfd.close();
        ParcelFileDescriptor pw = cr.openFileDescriptor(fileUri, "w");
        pw.close();
        check(tmp.length() == 0, "file truncated");

        // android.resource:// resolves under isakai.codeDir.
        File codeDir = Files.createTempDirectory("codedir").toFile();
        File resType = new File(codeDir, "res/drawable");
        check(resType.mkdirs(), "mkdir res");
        File icon = new File(resType, "icon.png");
        Files.write(icon.toPath(), new byte[] {9, 9});
        System.setProperty("isakai.codeDir", codeDir.getAbsolutePath());
        Uri resUri = Uri.parse("android.resource://pkg/drawable/icon");
        ParcelFileDescriptor pr = cr.openFileDescriptor(resUri, "r");
        check(pr != null && pr.getStatSize() == 2, "resource resolved");
        pr.close();
        boolean missing = false;
        try {
            cr.openFileDescriptor(Uri.parse("android.resource://pkg/drawable/nope"), "r");
        } catch (FileNotFoundException expected) {
            missing = true;
        }
        check(missing, "missing resource throws");

        ContentProvider.clearRegistryForTesting();
        System.out.println("ProviderTest: all PASS");
    }
}
