// SPDX-License-Identifier: GPL-3.0-or-later
package android.location;

/** Minimal LocationListener: 4 callbacks matching AOSP. */
public interface LocationListener {
    void onLocationChanged(Location location);
    void onStatusChanged(String provider, int status, android.os.Bundle extras);
    void onProviderEnabled(String provider);
    void onProviderDisabled(String provider);
}
