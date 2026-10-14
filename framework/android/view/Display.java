// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Display descriptor: fixed size holder. */
public class Display {
    private final int displayId;

    Display(int displayId) {
        this.displayId = displayId;
    }

    public int getDisplayId() {
        return displayId;
    }

    public void getMetrics(android.util.DisplayMetrics outMetrics) {
        if (outMetrics == null) return;
        outMetrics.widthPixels = 0;
        outMetrics.heightPixels = 0;
    }

    public void getSize(android.graphics.Point outSize) {
        if (outSize != null) {
            outSize.x = 0;
            outSize.y = 0;
        }
    }

    public void getRealSize(android.graphics.Point outSize) {
        getSize(outSize);
    }

    public void getRealMetrics(android.util.DisplayMetrics outMetrics) {
        getMetrics(outMetrics);
    }

    public int getRotation() {
        return 0;
    }

    public float getRefreshRate() {
        return 60.0f;
    }

    /** Singleton for service lookups; fixed metrics (see class doc). */
    private static final Display DEFAULT = new Display(0);

    public static Display getDefault() {
        return DEFAULT;
    }
}
