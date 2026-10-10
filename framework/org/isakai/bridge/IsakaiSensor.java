// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

import android.hardware.Sensor;

/**
 * Sensor helpers: virtual fallback when iio-sensor-proxy is absent.
 * Real D-Bus path (iio-sensor-proxy) is wired via libisakai_jni when
 * the service is present; otherwise gravity/light virtual values ensure
 * apps still receive onSensorChanged.
 */
public final class IsakaiSensor {
    private static final String TAG = "IsakaiSensor";

    public static final class Reading {
        public final float[] values;
        public final long timestampNs;
        Reading(float[] values, long ts) { this.values = values; this.timestampNs = ts; }
    }

    private IsakaiSensor() {}

    /** Poll a sensor type: virtual fallback (no D-Bus required). */
    public static Reading poll(int type) {
        if (type == Sensor.TYPE_ACCELEROMETER) return new Reading(new float[]{0, 0, 9.81f}, System.nanoTime());
        if (type == Sensor.TYPE_LIGHT) {
            String v = System.getenv("ISAKAI_SENSOR_LIGHT");
            float lux = 100f;
            if (v != null) try { lux = Float.parseFloat(v); } catch (NumberFormatException ignored) {}
            return new Reading(new float[]{lux}, System.nanoTime());
        }
        return null;
    }

    /** Whether a D-Bus sensor proxy is reachable (stub: false on this host). */
    public static boolean hasProxy() { return false; }
}
