// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

/** Installed-package record. */
public class PackageInfo {
    public String packageName;
    public int versionCode;
    public String versionName;
    public long firstInstallTime;
    public long lastUpdateTime;
    public ApplicationInfo applicationInfo;
    public ActivityInfo[] activities;
    public ServiceInfo[] services;
    public ProviderInfo[] providers;
    public Signature[] signatures;
    public String[] requestedPermissions;

    public long getLongVersionCode() {
        return versionCode & 0xFFFFFFFFL;
    }
}
