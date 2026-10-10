// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Radial gradient shader: records geometry and colors. */
public class RadialGradient extends Shader {
    private final float centerX;
    private final float centerY;
    private final float radius;
    private final int[] colors;
    private final float[] positions;
    private final TileMode tileMode;

    public RadialGradient(float centerX, float centerY, float radius,
            int[] colors, float[] positions, TileMode tile) {
        this.centerX = centerX;
        this.centerY = centerY;
        this.radius = radius;
        this.colors = colors == null ? null : colors.clone();
        this.positions = positions == null ? null : positions.clone();
        this.tileMode = tile;
    }

    public RadialGradient(float centerX, float centerY, float radius,
            int centerColor, int edgeColor, TileMode tileMode) {
        this(centerX, centerY, radius,
                new int[] {centerColor, edgeColor}, null, tileMode);
    }
}
