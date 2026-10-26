// SPDX-License-Identifier: GPL-3.0-or-later
package android.content.pm;

/** Activity manifest record. */
public class ActivityInfo extends ComponentInfo {
    public static final int SCREEN_ORIENTATION_UNSPECIFIED = -1;
    public static final int CONFIG_MCC = 0x0001;
    public static final int CONFIG_MNC = 0x0002;
    public static final int CONFIG_LOCALE = 0x0004;
    public static final int CONFIG_ORIENTATION = 0x0080;
    public static final int CONFIG_SCREEN_SIZE = 0x0400;
    public static final int CONFIG_SMALLEST_SCREEN_SIZE = 0x0800;
    public static final int CONFIG_DENSITY = 0x1000;

    public int configChanges;
    public String parentActivityName;
    public String permission;
    public int launchMode;
    public int screenOrientation;
    public int theme;
    public ApplicationInfo applicationInfo;

    public int getThemeResource() {
        return theme != 0 ? theme : applicationInfo == null ? 0 : applicationInfo.theme;
    }
}
