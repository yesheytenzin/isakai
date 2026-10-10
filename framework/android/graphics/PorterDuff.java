// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Porter-Duff compositing modes. */
public final class PorterDuff {
    public enum Mode {
        CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN,
        SRC_OUT, DST_OUT, SRC_ATOP, DST_ATOP, XOR, DARKEN,
        LIGHTEN, MULTIPLY, SCREEN, ADD, OVERLAY
    }

    private PorterDuff() {}
}
