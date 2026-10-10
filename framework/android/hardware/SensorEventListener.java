// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware;

import android.os.Handler;

/** Listener for sensor changes. */
public interface SensorEventListener {
    void onSensorChanged(SensorEvent event);
    void onAccuracyChanged(Sensor sensor, int accuracy);
}
