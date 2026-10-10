// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.graphics.Canvas;

/** Recording canvas shell: no display-list backend. */
public final class DisplayListCanvas extends Canvas {
    public DisplayListCanvas(int width, int height) { super(width, height); }
    public static DisplayListCanvas obtain(int width, int height) { return new DisplayListCanvas(width, height); }
    public void recycle() {}
}
