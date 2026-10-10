// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Blend-mode enum shell. */
public enum BlendMode {
    CLEAR, SRC, DST, SRC_OVER, DST_OVER, SRC_IN, DST_IN, SRC_OUT, DST_OUT,
    SRC_ATOP, DST_ATOP, XOR, PLUS, MODULATE, SCREEN, OVERLAY, DARKEN, LIGHTEN,
    COLOR_DODGE, COLOR_BURN, HARD_LIGHT, SOFT_LIGHT, DIFFERENCE, EXCLUSION,
    MULTIPLY, HUE, SATURATION, COLOR, LUMINOSITY;
    public static BlendMode defaultMode() { return SRC_OVER; }
}
