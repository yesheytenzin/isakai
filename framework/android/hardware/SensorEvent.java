// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

/** Sensor event carrying timestamped values. */
public final class SensorEvent {
    public Sensor sensor;
    public int accuracy;
    public long timestamp;
    public final float[] values;

    SensorEvent(int valueCount) { this.values = new float[valueCount]; }
}
