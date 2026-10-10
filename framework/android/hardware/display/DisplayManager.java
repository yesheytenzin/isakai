// SPDX-License-Identifier: GPL-3.0-or-later
package android.hardware.display;

import android.view.Display;

/** Display enumeration; single default display shell. */
public class DisplayManager {
    public static final String DISPLAY_CATEGORY_PRESENTATION = "android.hardware.display.category.PRESENTATION";
    public Display getDisplay(int displayId) { return null; }
    public Display[] getDisplays() { return new Display[0]; }
    public Display[] getDisplays(String category) { return new Display[0]; }
    public void registerDisplayListener(DisplayListener listener, android.os.Handler handler) { }
    public void unregisterDisplayListener(DisplayListener listener) { }
    public interface DisplayListener {
        void onDisplayAdded(int displayId);
        void onDisplayRemoved(int displayId);
        void onDisplayChanged(int displayId);
    }
}
