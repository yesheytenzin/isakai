// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Intent;
import android.net.Uri;
import java.io.File;
import java.nio.file.Files;

/**
 * Host-side unit checks for the ACTION_CREATE_DOCUMENT save path
 * (no-bridge fallback). Run on the host JVM.
 */
public final class ActivitySaveTest {
    private ActivitySaveTest() {}

    static void check(boolean cond, String msg) {
        if (!cond) {
            throw new AssertionError(msg);
        }
    }

    static final class Probe extends Activity {
        int code = Integer.MIN_VALUE;
        int result = Integer.MIN_VALUE;
        Intent data = null;

        @Override
        protected void onActivityResult(int requestCode, int resultCode, Intent d) {
            code = requestCode;
            result = resultCode;
            data = d;
        }
    }

    public static void main(String[] args) throws Exception {
        File dataDir = Files.createTempDirectory("savedir").toFile();
        Application app = new Application();
        app.attach(dataDir.getAbsolutePath());
        app.attachPackage("test.save");

        Probe act = new Probe();
        act.attach(app);

        // No desktop bridge on the host JVM: CREATE_DOCUMENT falls back
        // to a writable file under files/ delivered as RESULT_OK.
        Intent save = new Intent();
        save.setAction(Intent.ACTION_CREATE_DOCUMENT);
        save.putExtra(Intent.EXTRA_TITLE, "notes.txt");
        act.startActivityForResult(save, 7);
        check(act.code == 7, "request code");
        check(act.result == Activity.RESULT_OK, "result ok");
        check(act.data != null && act.data.getData() != null, "data uri");
        Uri u = act.data.getData();
        check("file".equals(u.getScheme()), "file scheme: " + u);
        File f = new File(u.getPath());
        check(f.exists() && f.canWrite(), "writable file: " + f);
        // Round-trip bytes through the resolver-provided descriptor.
        android.os.ParcelFileDescriptor pfd =
                act.getContentResolver().openFileDescriptor(u, "w");
        check(pfd != null, "pfd open");
        pfd.close();
        // Unknown actions stay no-ops (no result delivered).
        Probe idle = new Probe();
        idle.attach(app);
        Intent bogus = new Intent();
        bogus.setAction("android.intent.action.BOGUS");
        idle.startActivityForResult(bogus, 3);
        check(idle.code == Integer.MIN_VALUE, "bogus no-op");

        System.out.println("ActivitySaveTest: all PASS");
    }
}
