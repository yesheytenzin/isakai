// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

import java.util.Collections;
import java.util.List;

/** Sensor queries; empty shell. */
public class SensorManager {
    public static final int SENSOR_DELAY_FASTEST = 0;
    public static final int SENSOR_DELAY_GAME = 1;
    public static final int SENSOR_DELAY_UI = 2;
    public static final int SENSOR_DELAY_NORMAL = 3;
    public static final int SENSOR_STATUS_ACCURACY_HIGH = 3;
    public static final int SENSOR_STATUS_ACCURACY_MEDIUM = 2;
    public static final int SENSOR_STATUS_ACCURACY_LOW = 1;
    public static final int SENSOR_STATUS_UNRELIABLE = 0;
    public static final int TYPE_ACCELEROMETER = 1;
    public static final int TYPE_GYROSCOPE = 4;
    public static final int TYPE_MAGNETIC_FIELD = 2;
    public static final int TYPE_LIGHT = 5;
    public static final int TYPE_PROXIMITY = 8;
    public static final int TYPE_ALL = -1;
    public Object getDefaultSensor(int type) { return null; }
    public Object getDefaultSensor(int type, boolean wakeUp) { return null; }
    public List getSensorList(int type) { return Collections.emptyList(); }
    public boolean registerListener(Object listener, Object sensor, int samplingPeriodUs) { return false; }
    public boolean registerListener(Object listener, Object sensor, int samplingPeriodUs, android.os.Handler handler) { return false; }
    public void unregisterListener(Object listener) { }
    public void unregisterListener(Object listener, Object sensor) { }
}
