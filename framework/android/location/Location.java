// SPDX-License-Identifier: GPL-3.0-or-later
package android.location;

import android.os.Bundle;

/** Minimal Location with lat/lon/accuracy/time/provider. */
public class Location {
    private final String provider;
    private double latitude;
    private double longitude;
    private float accuracy;
    private long time;
    private Bundle extras;

    public Location(String provider) { this.provider = provider; }

    public String getProvider() { return provider; }
    public double getLatitude() { return latitude; }
    public void setLatitude(double latitude) { this.latitude = latitude; }
    public double getLongitude() { return longitude; }
    public void setLongitude(double longitude) { this.longitude = longitude; }
    public float getAccuracy() { return accuracy; }
    public void setAccuracy(float accuracy) { this.accuracy = accuracy; }
    public long getTime() { return time; }
    public void setTime(long time) { this.time = time; }
    public Bundle getExtras() { return extras; }
    public void setExtras(Bundle extras) { this.extras = extras; }
    public boolean hasAccuracy() { return accuracy > 0; }
}
