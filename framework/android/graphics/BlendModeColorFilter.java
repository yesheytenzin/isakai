// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Blend-mode color filter shell: recorded only. */
public class BlendModeColorFilter extends ColorFilter {
    private final int color;
    private final BlendMode mode;
    public BlendModeColorFilter(int color, BlendMode mode) { this.color = color; this.mode = mode; }
    public int getColor() { return color; }
    public BlendMode getMode() { return mode; }
}
