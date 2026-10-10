// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

/** Battery state query stub: always unplugged, unknown status. */
public class BatteryManager {
    public static final int BATTERY_STATUS_UNKNOWN = 1;
    public static final int BATTERY_STATUS_CHARGING = 2;
    public static final int BATTERY_STATUS_DISCHARGING = 3;
    public static final int BATTERY_STATUS_NOT_CHARGING = 4;
    public static final int BATTERY_STATUS_FULL = 5;
    public static final int BATTERY_PLUGGED_AC = 1;
    public static final int BATTERY_PLUGGED_USB = 2;
    public static final int BATTERY_PLUGGED_WIRELESS = 4;
    public static final int BATTERY_PLUGGED_DOCK = 8;
    public static final String EXTRA_STATUS = "status";
    public static final String EXTRA_PLUGGED = "plugged";
    public static final String EXTRA_LEVEL = "level";
    public static final String EXTRA_SCALE = "scale";

    public BatteryManager() {
    }

    public boolean isCharging() {
        return false;
    }

    public int getIntProperty(int id) {
        return Integer.MIN_VALUE;
    }

    public long getLongProperty(int id) {
        return Long.MIN_VALUE;
    }
}
