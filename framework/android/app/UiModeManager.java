// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

/** UI mode (night/car); defaults shell. */
public class UiModeManager {
    public static final int MODE_NIGHT_NO = 1;
    public static final int MODE_NIGHT_YES = 2;
    public static final int MODE_NIGHT_AUTO = 0;
    public static final int DEFAULT_NIGHT_MODE = -1;
    public static final int MODE_TYPE_NORMAL = 1;
    public static final int MODE_TYPE_DESK = 2;
    public static final int MODE_TYPE_CAR = 3;
    public int getNightMode() { return MODE_NIGHT_NO; }
    public void setNightMode(int mode) { }
    public int getCurrentModeType() { return MODE_TYPE_NORMAL; }
    public void enableCarMode(int flags) { }
    public void disableCarMode(int flags) { }
}
