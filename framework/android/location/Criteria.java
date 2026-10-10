// SPDX-License-Identifier: GPL-3.0-or-later
package android.location;

/** Minimal Criteria: accuracy/power/altitude/speed/bearing/cost. */
public class Criteria {
    public static final int ACCURACY_FINE = 1;
    public static final int ACCURACY_COARSE = 2;
    public static final int ACCURACY_LOW = 3;
    public static final int POWER_HIGH = 3;
    public static final int POWER_MEDIUM = 2;
    public static final int POWER_LOW = 1;
    public static final int NO_REQUIREMENT = 0;

    private int accuracy = NO_REQUIREMENT;
    private int powerRequirement = NO_REQUIREMENT;
    private boolean altitudeRequired;
    private boolean bearingRequired;
    private boolean speedRequired;
    private boolean costAllowed;

    public int getAccuracy() { return accuracy; }
    public void setAccuracy(int accuracy) { this.accuracy = accuracy; }
    public int getPowerRequirement() { return powerRequirement; }
    public void setPowerRequirement(int level) { this.powerRequirement = level; }
    public boolean isAltitudeRequired() { return altitudeRequired; }
    public void setAltitudeRequired(boolean v) { this.altitudeRequired = v; }
    public boolean isBearingRequired() { return bearingRequired; }
    public void setBearingRequired(boolean v) { this.bearingRequired = v; }
    public boolean isSpeedRequired() { return speedRequired; }
    public void setSpeedRequired(boolean v) { this.speedRequired = v; }
    public boolean isCostAllowed() { return costAllowed; }
    public void setCostAllowed(boolean v) { this.costAllowed = v; }
}
