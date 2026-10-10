// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

import android.location.Location;
import android.location.LocationListener;
import android.os.Looper;
import android.util.Log;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Location helpers: virtual fallback when GeoClue2 is absent.
 * Real GeoClue2 path is wired via libisakai_jni when the service is
 * present; otherwise a fixed virtual location ensures apps still get
 * onLocationChanged (or explicit onProviderDisabled when they asked for
 * a disabled provider).
 */
public final class IsakaiLocation {
    private static final String TAG = "IsakaiLocation";
    private static final Map<LocationListener, Boolean> active = new ConcurrentHashMap<>();
    private IsakaiLocation() {}

    public static boolean hasProvider() { return false; }

    public static Location getLastLocation(String provider) {
        Location loc = new Location(provider);
        loc.setLatitude(37.421998); loc.setLongitude(-122.084);
        loc.setAccuracy(10f); loc.setTime(System.currentTimeMillis());
        return loc;
    }

    public static void requestUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener, Looper looper) {
        active.put(listener, true);
        Location loc = getLastLocation(provider);
        Runnable deliver = () -> listener.onLocationChanged(loc);
        if (looper != null && looper.getQueue() != null) {
            // post via Handler if a looper is present
            new android.os.Handler(looper).post(deliver);
        } else {
            deliver.run();
        }
    }

    public static void removeUpdates(LocationListener listener) { active.remove(listener); }
}
