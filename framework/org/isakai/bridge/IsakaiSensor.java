// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

import android.hardware.Sensor;

/**
 * Sensor helpers.
 *
 * <p>There is no real host sensor backend wired yet (iio-sensor-proxy over
 * D-Bus would be the host source), so {@link #hasProxy()} is false and
 * {@link #available} reports no sensor in production: apps see
 * "no such sensor" instead of fabricated gravity/lux readings.
 *
 * <p>Synthetic readings exist for tests only and require the explicit
 * switch {@code ISAKAI_FAKE_SENSORS=1} (or {@code -Disakai.fakeSensors=1}).
 * {@code ISAKAI_SENSOR_LIGHT} overrides the synthetic lux value.
 */
public final class IsakaiSensor {
    /** Test-only synthetic readings switch. */
    public static boolean fakeEnabled() {
        return "1".equals(System.getenv("ISAKAI_FAKE_SENSORS"))
                || "1".equals(System.getProperty("isakai.fakeSensors"));
    }

    public static final class Reading {
        public final float[] values;
        public final long timestampNs;
        Reading(float[] values, long ts) { this.values = values; this.timestampNs = ts; }
    }

    private IsakaiSensor() {}

    /** True when a real host sensor proxy is reachable. Not wired yet. */
    public static boolean hasProxy() { return false; }

    /** True when this sensor type can produce readings on this host. */
    public static boolean available(int type) {
        if (!fakeEnabled()) return false;
        return type == Sensor.TYPE_ACCELEROMETER || type == Sensor.TYPE_LIGHT;
    }

    /** Poll a sensor type; null when no provider serves it. */
    public static Reading poll(int type) {
        if (!available(type)) return null;
        if (type == Sensor.TYPE_ACCELEROMETER) return new Reading(new float[]{0, 0, 9.81f}, System.nanoTime());
        String v = System.getenv("ISAKAI_SENSOR_LIGHT");
        float lux = 100f;
        if (v != null) try { lux = Float.parseFloat(v); } catch (NumberFormatException ignored) {}
        return new Reading(new float[]{lux}, System.nanoTime());
    }
}
