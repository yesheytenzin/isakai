// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

/** Application record from the install registry. */
public class ApplicationInfo extends android.content.pm.PackageItemInfo {
    public static final int FLAG_SYSTEM = 1;
    public static final int FLAG_DEBUGGABLE = 2;
    public static final int FLAG_HAS_CODE = 4;
    public static final int FLAG_PERSISTENT = 8;

    public String packageName;
    public String sourceDir;
    public String dataDir;
    public String nativeLibraryDir;
    public int targetSdkVersion;
    public int minSdkVersion;
    public int flags;
    public int uid;
    public int theme;
}
