// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

/** Physical sensor description. */
public final class Sensor {
    public static final int TYPE_ACCELEROMETER = 1;
    public static final int TYPE_MAGNETIC_FIELD = 2;
    public static final int TYPE_GYROSCOPE = 4;
    public static final int TYPE_LIGHT = 5;
    public static final int TYPE_PROXIMITY = 8;
    public static final int TYPE_ALL = -1;
    public static final int REPORTING_MODE_CONTINUOUS = 0;

    private final int type;
    private final String name;
    private final String vendor;
    private final String stringType;
    private final float maxRange;
    private final float resolution;
    private final float power;
    private final int minDelay;

    Sensor(int type, String name, String vendor, String stringType, float maxRange, float resolution, float power, int minDelay) {
        this.type = type;
        this.name = name;
        this.vendor = vendor;
        this.stringType = stringType;
        this.maxRange = maxRange;
        this.resolution = resolution;
        this.power = power;
        this.minDelay = minDelay;
    }

    public int getType() { return type; }
    public String getName() { return name; }
    public String getVendor() { return vendor; }
    public String getStringType() { return stringType; }
    public float getMaximumRange() { return maxRange; }
    public float getResolution() { return resolution; }
    public float getPower() { return power; }
    public int getMinDelay() { return minDelay; }
    public int getReportingMode() { return REPORTING_MODE_CONTINUOUS; }
    public boolean isWakeUpSensor() { return false; }
}
