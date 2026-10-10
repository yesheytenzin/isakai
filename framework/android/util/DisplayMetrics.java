// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

/**
 * DisplayMetrics: density/scale snapshot from the compositor output.
 *
 * <p>Field layout mirrors the native int[8] snapshot carried over
 * {@code IsakaiWindow.nPollMetrics}: {@code [w, h, densityDpi,
 * density*1000, scaled*1000, xdpi*1000, ydpi*1000, scale]}.
 * {@link #setToDefaults} fills the scale=1 fallback the native side
 * publishes when no wl_output is bound; {@link #setFromSnapshot} applies
 * a fresh polled snapshot. Both keep every density field mutually
 * consistent so dimension helpers ({@code TypedValue}) agree.
 */
public class DisplayMetrics {
    public static final int DENSITY_DEFAULT = 160;
    public static final int DENSITY_LOW = 120;
    public static final int DENSITY_MEDIUM = 160;
    public static final int DENSITY_HIGH = 240;
    public static final int DENSITY_XHIGH = 320;
    public static final int DENSITY_XXHIGH = 480;
    public static final int DENSITY_XXXHIGH = 640;

    /** Native snapshot length: w,h,densityDpi,density,scaled,xdpi,ydpi,scale. */
    public static final int SNAPSHOT_SIZE = 8;

    public int widthPixels;
    public int heightPixels;
    public float density;
    public int densityDpi;
    public float scaledDensity;
    public float xdpi;
    public float ydpi;

    /** Scale=1 fallback (no compositor output bound). */
    public DisplayMetrics() {
        setToDefaults();
    }

    /** Scale=1 fallback: 160dpi, density 1, xdpi/ydpi 160. */
    public final void setToDefaults() {
        widthPixels = 0;
        heightPixels = 0;
        densityDpi = DENSITY_DEFAULT;
        density = 1.0f;
        scaledDensity = 1.0f;
        xdpi = DENSITY_DEFAULT;
        ydpi = DENSITY_DEFAULT;
    }

    /**
     * Apply a native int[8] snapshot. Null or short arrays leave the
     * metrics untouched; returns true when applied.
     */
    public final boolean setFromSnapshot(int[] m) {
        if (m == null || m.length < SNAPSHOT_SIZE) return false;
        int scale = m[7] <= 0 ? 1 : m[7];
        widthPixels = m[0];
        heightPixels = m[1];
        densityDpi = m[2] <= 0 ? DENSITY_DEFAULT * scale : m[2];
        density = m[3] / 1000.0f;
        if (density <= 0) density = scale;
        scaledDensity = m[4] / 1000.0f;
        if (scaledDensity <= 0) scaledDensity = density;
        xdpi = m[5] / 1000.0f;
        if (xdpi <= 0) xdpi = densityDpi;
        ydpi = m[6] / 1000.0f;
        if (ydpi <= 0) ydpi = densityDpi;
        return true;
    }

    /** Set all density fields from one scale factor (densityDpi = 160*scale). */
    public final void setDensityScale(int scale) {
        if (scale <= 0) scale = 1;
        densityDpi = DENSITY_DEFAULT * scale;
        density = scale;
        scaledDensity = scale;
        xdpi = densityDpi;
        ydpi = densityDpi;
    }
}
