// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Sweep gradient shader: records center, colors, and positions. */
public class SweepGradient extends Shader {
    private final float centerX;
    private final float centerY;
    private final int[] colors;
    private final float[] positions;

    public SweepGradient(float cx, float cy, int[] colors, float[] positions) {
        this.centerX = cx;
        this.centerY = cy;
        this.colors = colors == null ? null : colors.clone();
        this.positions = positions == null ? null : positions.clone();
    }

    public SweepGradient(float cx, float cy, int color0, int color1) {
        this(cx, cy, new int[] {color0, color1}, null);
    }
}
