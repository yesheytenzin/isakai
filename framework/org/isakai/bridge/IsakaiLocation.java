// SPDX-License-Identifier: GPL-3.0-or-later
package org.isakai.bridge;

import android.location.Location;
import android.location.LocationListener;
import android.os.Looper;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Location helpers.
 *
 * <p>There is no real provider backend wired yet (GeoClue2 would be the
 * host source), so {@link #hasProvider()} is false and
 * {@link #getLastLocation} returns null in production: apps see
 * "provider unavailable" instead of fabricated coordinates.
 *
 * <p>Synthetic readings exist for tests only and require the explicit
 * switch {@code ISAKAI_FAKE_LOCATION=1} (or {@code -Disakai.fakeLocation=1}).
 *
 * <p>Deliveries are checked against the live registration set, so a
 * listener removed before its queued callback runs receives nothing
 * (the callback may be posted to a looper that runs later).
 */
public final class IsakaiLocation {
    /** listener → registration token; removal invalidates queued deliveries. */
    private static final Map<LocationListener, Long> active = new ConcurrentHashMap<>();
    private static final AtomicLong tokenSeq = new AtomicLong();

    private IsakaiLocation() {}

    /** True when a real host provider backend is reachable. Not wired yet. */
    public static boolean hasProvider() { return false; }

    /**
     * True when location can be served at all on this host: a real
     * provider, or the explicit test switch. {@code LocationManager}
     * reports provider enablement from this, never from a fabricated fix.
     */
    public static boolean providerAvailable() { return hasProvider() || fakeEnabled(); }

    /** Test-only synthetic readings switch. */
    public static boolean fakeEnabled() {
        return "1".equals(System.getenv("ISAKAI_FAKE_LOCATION"))
                || "1".equals(System.getProperty("isakai.fakeLocation"));
    }

    public static Location getLastLocation(String provider) {
        if (!fakeEnabled()) return null;
        Location loc = new Location(provider);
        loc.setLatitude(37.421998);
        loc.setLongitude(-122.084);
        loc.setAccuracy(10f);
        loc.setTime(System.currentTimeMillis());
        return loc;
    }

    public static void requestUpdates(String provider, long minTimeMs, float minDistanceM, LocationListener listener, Looper looper) {
        Location loc = getLastLocation(provider);
        if (loc == null) return; // no provider: nothing to deliver
        final long token = tokenSeq.incrementAndGet();
        active.put(listener, token);
        Runnable deliver = () -> {
            Long current = active.get(listener);
            if (current == null || current.longValue() != token) return; // removed while queued
            listener.onLocationChanged(loc);
        };
        if (looper != null && looper.getQueue() != null) {
            new android.os.Handler(looper).post(deliver);
        } else {
            deliver.run();
        }
    }

    public static void removeUpdates(LocationListener listener) { active.remove(listener); }
}
