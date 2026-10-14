// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Looper;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Sensor/location capability honesty and cancellation:
 *
 * <ul>
 *  <li>without a provider, no sensor is advertised, no listener registers
 *      and no fabricated reading is delivered;</li>
 *  <li>with the explicit test switch, readings flow;</li>
 *  <li>a listener removed before its queued delivery runs receives
 *      nothing (regression: the location delivery was posted
 *      unconditionally and the sensor dispatch did not re-check).</li>
 * </ul>
 */
public final class SensorLocationTest {
    private static void check(boolean ok, String message) {
        if (!ok) throw new AssertionError(message);
    }

    private static final class CountingSensorListener implements SensorEventListener {
        final AtomicInteger events = new AtomicInteger();
        @Override public void onSensorChanged(SensorEvent event) { events.incrementAndGet(); }
        @Override public void onAccuracyChanged(Sensor sensor, int accuracy) {}
    }

    private static final class CountingLocationListener implements LocationListener {
        final AtomicInteger events = new AtomicInteger();
        @Override public void onLocationChanged(Location location) { events.incrementAndGet(); }
        @Override public void onProviderDisabled(String provider) {}
        @Override public void onProviderEnabled(String provider) {}
        @Override public void onStatusChanged(String provider, int status, Bundle extras) {}
    }

    public static void main(String[] args) throws Exception {
        System.clearProperty("isakai.fakeSensors");
        System.clearProperty("isakai.fakeLocation");
        // --- production default: no provider, no fabrication ---
        SensorManager sensors = new SensorManager();
        check(sensors.getDefaultSensor(SensorManager.TYPE_ACCELEROMETER) == null,
                "accelerometer must not be advertised without a provider");
        check(sensors.getSensorList(SensorManager.TYPE_ALL).isEmpty(),
                "sensor list must be empty without a provider");
        CountingSensorListener quiet = new CountingSensorListener();
        check(!sensors.registerListener(quiet, new Sensor(SensorManager.TYPE_ACCELEROMETER, "fake", "test",
                        "android.sensor.accelerometer", 19.6f, 0.01f, 0.5f, 10000),
                SensorManager.SENSOR_DELAY_FASTEST),
                "registerListener must refuse an unsupported sensor");
        Thread.sleep(60);
        check(quiet.events.get() == 0, "silent sensor delivered " + quiet.events.get());

        LocationManager location = new LocationManager();
        check(!location.isProviderEnabled(LocationManager.GPS_PROVIDER), "gps must be disabled without a provider");
        check(location.getLastKnownLocation(LocationManager.GPS_PROVIDER) == null, "fabricated last-known location");
        CountingLocationListener noFix = new CountingLocationListener();
        location.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, noFix, null);
        check(noFix.events.get() == 0, "disabled provider delivered a fix");

        // --- explicit test switch: synthetic values flow ---
        System.setProperty("isakai.fakeSensors", "1");
        System.setProperty("isakai.fakeLocation", "1");
        try {
            Sensor accel = sensors.getDefaultSensor(SensorManager.TYPE_ACCELEROMETER);
            check(accel != null, "fake mode must advertise the accelerometer");
            CountingSensorListener active = new CountingSensorListener();
            check(sensors.registerListener(active, accel, SensorManager.SENSOR_DELAY_FASTEST),
                    "fake mode must accept a registration");
            long deadline = System.nanoTime() + 2_000_000_000L;
            while (active.events.get() == 0 && System.nanoTime() < deadline) Thread.sleep(5);
            check(active.events.get() > 0, "fake accelerometer produced no events");
            sensors.unregisterListener(active);
            int seen = active.events.get();
            Thread.sleep(120);
            check(active.events.get() == seen, "unregistered sensor listener kept firing: "
                    + active.events.get() + " > " + seen);

            // Cancellation before the queued delivery runs: the post goes to
            // this thread's queue, and removeUpdates happens before it drains.
            Looper.prepare();
            CountingLocationListener queued = new CountingLocationListener();
            location.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, queued, Looper.myLooper());
            location.removeUpdates(queued);
            Looper.myLooper().dispatchPending();
            check(queued.events.get() == 0, "removed location listener still received a queued fix");
            // Sanity: while registered, the same path delivers.
            CountingLocationListener live = new CountingLocationListener();
            location.requestLocationUpdates(LocationManager.GPS_PROVIDER, 0, 0, live, Looper.myLooper());
            Looper.myLooper().dispatchPending();
            check(live.events.get() == 1, "registered location listener missed its fix: " + live.events.get());
            location.removeUpdates(live);
            Looper.myLooper().quit();
        } finally {
            System.clearProperty("isakai.fakeSensors");
            System.clearProperty("isakai.fakeLocation");
        }
        System.out.println("PASS: sensor/location honesty (no provider ⇒ no sensor, no fabricated fix)"
                + " and unregister/removeUpdates suppress queued deliveries");
    }
}
