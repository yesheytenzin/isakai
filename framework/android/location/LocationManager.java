// SPDX-License-Identifier: GPL-3.0-or-later
package android.location;

import android.app.PendingIntent;
import android.os.Bundle;
import android.os.Looper;
import android.util.Log;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import org.isakai.bridge.IsakaiLocation;

/**
 * LocationManager backed by GeoClue2 D-Bus when present, with fail-closed
 * permission semantics. Providers: gps/network/passive/fused (all map to
 * the same GeoClue client when available, else empty).
 */
public class LocationManager {
    private static final String TAG = "LocationManager";

    public static final String GPS_PROVIDER = "gps";
    public static final String NETWORK_PROVIDER = "network";
    public static final String PASSIVE_PROVIDER = "passive";
    public static final String FUSED_PROVIDER = "fused";
    public static final String MODE_CHANGED_ACTION = "android.location.MODE_CHANGED";
    public static final String PROVIDERS_CHANGED_ACTION = "android.location.PROVIDERS_CHANGED";

    private static final List<String> ALL_PROVIDERS = Collections.unmodifiableList(
            java.util.Arrays.asList(GPS_PROVIDER, NETWORK_PROVIDER, PASSIVE_PROVIDER, FUSED_PROVIDER));

    public List<String> getAllProviders() { return ALL_PROVIDERS; }

    public List<String> getProviders(boolean enabledOnly) {
        if (!enabledOnly) return ALL_PROVIDERS;
        List<String> enabled = new ArrayList<>();
        for (String p : ALL_PROVIDERS) if (isProviderEnabled(p)) enabled.add(p);
        return enabled;
    }

    public List<String> getProviders(Criteria criteria, boolean enabledOnly) { return getProviders(enabledOnly); }

    public boolean isProviderEnabled(String provider) {
        if (provider == null) return false;
        // GeoClue present (or the explicit test switch) → providers enabled;
        // absent → disabled (fail-closed, never a fabricated fix).
        return IsakaiLocation.providerAvailable();
    }

    public boolean hasProvider(String provider) { return ALL_PROVIDERS.contains(provider); }

    public String getBestProvider(Criteria criteria, boolean enabledOnly) {
        List<String> provs = getProviders(enabledOnly);
        return provs.isEmpty() ? null : provs.get(0);
    }

    public Location getLastKnownLocation(String provider) {
        if (provider == null) return null;
        if (!isProviderEnabled(provider)) return null;
        return IsakaiLocation.getLastLocation(provider);
    }

    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener) {
        requestLocationUpdates(provider, minTimeMs, minDistanceM, listener, null);
    }

    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener, Looper looper) {
        if (listener == null) throw new IllegalArgumentException("listener == null");
        if (!isProviderEnabled(provider)) {
            // Permission denial parity: notify via onProviderDisabled, don't throw
            if (looper != null) looper.getQueue();
            listener.onProviderDisabled(provider);
            return;
        }
        IsakaiLocation.requestUpdates(provider, minTimeMs, minDistanceM, listener, looper);
    }

    public void requestLocationUpdates(long minTimeMs, float minDistanceM, Criteria criteria, LocationListener listener, Looper looper) {
        requestLocationUpdates(getBestProvider(criteria, true), minTimeMs, minDistanceM, listener, looper);
    }

    public void requestLocationUpdates(String provider, long minTimeMs, float minDistanceM, PendingIntent intent) {
        Log.w(TAG, "PendingIntent location updates not supported");
    }

    public void removeUpdates(LocationListener listener) {
        if (listener != null) IsakaiLocation.removeUpdates(listener);
    }

    public void removeUpdates(PendingIntent intent) { Log.w(TAG, "PendingIntent remove not supported"); }

    public void addProximityAlert(double latitude, double longitude, float radius, long expiration, PendingIntent intent) {
        Log.w(TAG, "proximity alerts not supported");
    }

    public void removeProximityAlert(PendingIntent intent) { Log.w(TAG, "proximity alerts not supported"); }

    public Bundle getProviderExtras(String provider) { return null; }
}
