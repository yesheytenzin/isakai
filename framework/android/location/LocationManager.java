// SPDX-License-Identifier: GPL-3.0-or-later
package android.location;

import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Looper;
import java.util.Collections;
import java.util.List;

/** Location providers; empty shell. */
public class LocationManager {
    public static final String GPS_PROVIDER = "gps";
    public static final String NETWORK_PROVIDER = "network";
    public static final String PASSIVE_PROVIDER = "passive";
    public static final String FUSED_PROVIDER = "fused";
    public static final String MODE_CHANGED_ACTION = "android.location.MODE_CHANGED";
    public static final String PROVIDERS_CHANGED_ACTION = "android.location.PROVIDERS_CHANGED";
    public List getAllProviders() { return Collections.emptyList(); }
    public List getProviders(boolean enabledOnly) { return Collections.emptyList(); }
    public boolean isProviderEnabled(String provider) { return false; }
    public Object getLastKnownLocation(String provider) { return null; }
    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, Object listener) { }
    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, Object listener, Looper looper) { }
    public void requestLocationUpdates(long minTimeMs, float minDistanceM, Object criteria, Object listener, Looper looper) { }
    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, PendingIntent intent) { }
    public void removeUpdates(Object listener) { }
    public void removeUpdates(PendingIntent intent) { }
    public void addProximityAlert(double latitude, double longitude, float radius, long expiration, PendingIntent intent) { }
    public void removeProximityAlert(PendingIntent intent) { }
    public boolean hasProvider(String provider) { return false; }
    public String getBestProvider(Object criteria, boolean enabledOnly) { return null; }
    public Bundle getProviderExtras(String provider) { return null; }
}
