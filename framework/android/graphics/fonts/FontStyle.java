// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.fonts;

/** Requested font style: weight plus slant. */
public final class FontStyle {
    public static final int FONT_SLANT_UPRIGHT = 0;
    public static final int FONT_SLANT_ITALIC = 1;
    public static final int FONT_SLANT_OBLIQUE = 2;

    private final int weight;
    private final int slant;

    public FontStyle() {
        this(400, FONT_SLANT_UPRIGHT);
    }

    public FontStyle(int weight, int slant) {
        this.weight = weight;
        this.slant = slant;
    }

    public int getWeight() { return weight; }
    public int getSlant() { return slant; }
}
