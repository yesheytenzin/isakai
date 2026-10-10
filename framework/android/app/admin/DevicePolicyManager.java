// SPDX-License-Identifier: GPL-3.0-or-later
package android.app.admin;

import android.content.ComponentName;

/** Device admin policy; nothing enforced. */
public class DevicePolicyManager {
    public static final String ACTION_ADD_DEVICE_ADMIN = "android.app.action.ADD_DEVICE_ADMIN";
    public static final String EXTRA_DEVICE_ADMIN = "android.app.extra.DEVICE_ADMIN";
    public static final int PASSWORD_QUALITY_UNSPECIFIED = 0;
    public static final int PASSWORD_QUALITY_SOMETHING = 65536;
    public static final int PASSWORD_QUALITY_NUMERIC = 131072;
    public boolean isAdminActive(ComponentName admin) { return false; }
    public boolean isDeviceOwnerApp(String packageName) { return false; }
    public boolean isProfileOwnerApp(String packageName) { return false; }
    public void removeActiveAdmin(ComponentName admin) { }
    public void lockNow() { }
    public void reboot(ComponentName admin) { }
    public long getPasswordExpiration(ComponentName admin) { return 0; }
    public int getPasswordQuality(ComponentName admin) { return PASSWORD_QUALITY_UNSPECIFIED; }
    public boolean isCameraDisabled(ComponentName admin) { return false; }
}
