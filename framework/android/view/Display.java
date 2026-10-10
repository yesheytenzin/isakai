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

    public void getMetrics(android.util.DisplayMetrics outMetrics) {}

    public void getSize(android.graphics.Point outSize) {
        if (outSize != null) {
            outSize.x = 0;
            outSize.y = 0;
        }
    }

    public int getRotation() {
        return 0;
    }
}
