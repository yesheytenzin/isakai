// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Rounded screen corner descriptor: zero radius. */
public final class RoundedCorner {
    public static final int POSITION_TOP_LEFT = 0;
    public static final int POSITION_TOP_RIGHT = 1;
    public static final int POSITION_BOTTOM_RIGHT = 2;
    public static final int POSITION_BOTTOM_LEFT = 3;

    public int getRadius() { return 0; }
    public int getCenterX() { return 0; }
    public int getCenterY() { return 0; }
}
