// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Display cutout descriptor: empty bounds. */
public final class DisplayCutout {
    public DisplayCutout(android.graphics.Rect safeInsets, java.util.List<android.graphics.Rect> boundRects) {}

    public int getSafeInsetLeft() { return 0; }
    public int getSafeInsetTop() { return 0; }
    public int getSafeInsetRight() { return 0; }
    public int getSafeInsetBottom() { return 0; }
}
