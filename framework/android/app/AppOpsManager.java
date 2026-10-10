// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** App-ops checks: everything allowed. */
public class AppOpsManager {
    public static final int MODE_ALLOWED = 0;
    public static final int MODE_IGNORED = 1;
    public static final int MODE_ERRORED = 2;
    public static final int MODE_DEFAULT = 3;

    public static final String OPSTR_COARSE_LOCATION = "android:coarse_location";
    public static final String OPSTR_FINE_LOCATION = "android:fine_location";
    public static final String OPSTR_READ_CONTACTS = "android:read_contacts";
    public static final String OPSTR_POST_NOTIFICATION = "android:post_notification";

    public int checkOpNoThrow(String op, int uid, String packageName) {
        return MODE_ALLOWED;
    }

    public int noteProxyOp(String op, String proxiedPackageName) {
        return MODE_ALLOWED;
    }

    public int noteProxyOpNoThrow(String op, String proxiedPackageName) {
        return MODE_ALLOWED;
    }

    public int noteOp(String op, int uid, String packageName) {
        return MODE_ALLOWED;
    }

    public static String permissionToOp(String permission) {
        return null;
    }
}
