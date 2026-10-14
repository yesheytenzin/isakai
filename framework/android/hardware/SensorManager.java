// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

import android.os.Handler;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;
import org.isakai.bridge.IsakaiSensor;

/**
 * SensorManager. No host sensor backend is wired yet (iio-sensor-proxy over
 * D-Bus would be the host source), so {@link #getDefaultSensor} reports
 * "no such sensor" and {@link #registerListener} returns false: apps must
 * handle absence instead of receiving fabricated readings. Tests can opt in
 * to synthetic accelerometer/light readings with ISAKAI_FAKE_SENSORS=1.
 *
 * <p>Dispatch is via Handler.post when a Handler is supplied, else a
 * per-manager scheduled thread. A registration is marked inactive before
 * the listener is dropped, so a queued or in-flight delivery after
 * {@link #unregisterListener} is suppressed.
 */
public class SensorManager {
    private static final String TAG = "SensorManager";

    public static final int SENSOR_DELAY_FASTEST = 0;
    public static final int SENSOR_DELAY_GAME = 1;
    public static final int SENSOR_DELAY_UI = 2;
    public static final int SENSOR_DELAY_NORMAL = 3;
    public static final int SENSOR_STATUS_ACCURACY_HIGH = 3;
    public static final int SENSOR_STATUS_ACCURACY_MEDIUM = 2;
    public static final int SENSOR_STATUS_ACCURACY_LOW = 1;
    public static final int SENSOR_STATUS_UNRELIABLE = 0;
    public static final int TYPE_ACCELEROMETER = 1;
    public static final int TYPE_MAGNETIC_FIELD = 2;
    public static final int TYPE_GYROSCOPE = 4;
    public static final int TYPE_LIGHT = 5;
    public static final int TYPE_PROXIMITY = 8;
    public static final int TYPE_ALL = -1;

    private static final Sensor ACCEL = new Sensor(TYPE_ACCELEROMETER, "Isakai Accelerometer", "Isakai", "android.sensor.accelerometer", 19.6f, 0.01f, 0.5f, 10000);
    private static final Sensor LIGHT = new Sensor(TYPE_LIGHT, "Isakai Light", "Isakai", "android.sensor.light", 10000f, 1f, 0.1f, 0);

    private static final List<Sensor> ALL_SENSORS;
    static {
        List<Sensor> l = new ArrayList<>();
        l.add(ACCEL); l.add(LIGHT);
        ALL_SENSORS = Collections.unmodifiableList(l);
    }

    private final ScheduledExecutorService exec = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread t = new Thread(r, "SensorManager");
        t.setDaemon(true);
        return t;
    });

    private static class Registration {
        final SensorEventListener listener;
        final Sensor sensor;
        final Handler handler;
        final int rateUs;
        volatile boolean active = true;
        ScheduledFuture<?> future;
        Registration(SensorEventListener l, Sensor s, Handler h, int rateUs) { listener = l; sensor = s; handler = h; this.rateUs = rateUs; }
    }

    private final List<Registration> registrations = new ArrayList<>();

    public Sensor getDefaultSensor(int type) { return getDefaultSensor(type, false); }

    public Sensor getDefaultSensor(int type, boolean wakeUp) {
        if (!IsakaiSensor.available(type)) return null;
        if (type == TYPE_ACCELEROMETER) return ACCEL;
        if (type == TYPE_LIGHT) return LIGHT;
        if (type == TYPE_ALL) return ALL_SENSORS.isEmpty() ? null : ALL_SENSORS.get(0);
        return null;
    }

    public List<Sensor> getSensorList(int type) {
        if (type == TYPE_ALL) {
            List<Sensor> available = new ArrayList<>();
            for (Sensor s : ALL_SENSORS) if (IsakaiSensor.available(s.getType())) available.add(s);
            return Collections.unmodifiableList(available);
        }
        Sensor s = getDefaultSensor(type);
        if (s == null) return Collections.emptyList();
        return Collections.singletonList(s);
    }

    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs) {
        return registerListener(listener, sensor, samplingPeriodUs, null);
    }

    public boolean registerListener(SensorEventListener listener, Sensor sensor, int samplingPeriodUs, Handler handler) {
        if (listener == null || sensor == null) return false;
        if (sensor != ACCEL && sensor != LIGHT) return false;
        // No provider for this type: AOSP returns false instead of
        // registering a listener that never fires or fabricating events.
        if (!IsakaiSensor.available(sensor.getType())) return false;
        // deduplicate
        synchronized (registrations) {
            for (Registration r : registrations) if (r.listener == listener && r.sensor == sensor) return true;
        }
        long periodMs = delayToMs(samplingPeriodUs);
        Registration reg = new Registration(listener, sensor, handler, samplingPeriodUs);
        reg.future = exec.scheduleAtFixedRate(() -> dispatch(reg), 0, periodMs, TimeUnit.MILLISECONDS);
        synchronized (registrations) { registrations.add(reg); }
        return true;
    }

    public void unregisterListener(SensorEventListener listener) {
        List<Registration> toCancel = new ArrayList<>();
        synchronized (registrations) {
            for (Registration r : registrations) if (r.listener == listener) toCancel.add(r);
            registrations.removeAll(toCancel);
        }
        for (Registration r : toCancel) {
            r.active = false;
            if (r.future != null) r.future.cancel(false);
        }
    }

    public void unregisterListener(SensorEventListener listener, Sensor sensor) {
        List<Registration> toCancel = new ArrayList<>();
        synchronized (registrations) {
            for (Registration r : registrations) if (r.listener == listener && r.sensor == sensor) toCancel.add(r);
            registrations.removeAll(toCancel);
        }
        for (Registration r : toCancel) {
            r.active = false;
            if (r.future != null) r.future.cancel(false);
        }
    }

    private void dispatch(Registration reg) {
        if (!reg.active) return;
        IsakaiSensor.Reading reading = IsakaiSensor.poll(reg.sensor.getType());
        SensorEvent ev;
        if (reg.sensor.getType() == TYPE_ACCELEROMETER) {
            ev = new SensorEvent(3);
            if (reading != null && reading.values.length >= 3) {
                ev.values[0] = reading.values[0]; ev.values[1] = reading.values[1]; ev.values[2] = reading.values[2];
            } else {
                // gravity fallback
                ev.values[0] = 0; ev.values[1] = 0; ev.values[2] = 9.81f;
            }
        } else {
            ev = new SensorEvent(1);
            ev.values[0] = reading != null && reading.values.length >= 1 ? reading.values[0] : 0f;
        }
        ev.sensor = reg.sensor;
        ev.accuracy = SENSOR_STATUS_ACCURACY_HIGH;
        ev.timestamp = System.nanoTime();
        Runnable deliver = () -> {
            if (!reg.active) return; // unregistered while queued
            reg.listener.onSensorChanged(ev);
        };
        if (reg.handler != null) reg.handler.post(deliver);
        else deliver.run();
    }

    private static long delayToMs(int delay) {
        switch (delay) {
            case SENSOR_DELAY_FASTEST: return 10;
            case SENSOR_DELAY_GAME: return 20;
            case SENSOR_DELAY_UI: return 60;
            case SENSOR_DELAY_NORMAL: return 200;
            default: return delay <= 0 ? 200 : Math.max(10, delay / 1000);
        }
    }
}
