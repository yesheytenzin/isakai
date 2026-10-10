// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

/** Color-space shell: identity adapt/convert. */
public class ColorSpace {
    public enum Adaptation { BRADFORD, VON_KRIES, CAT02, CIECAM02 }
    public enum RenderIntent { PERCEPTUAL, RELATIVE, SATURATION, ABSOLUTE }
    public static class Named {
        public static final int SRGB = 0;
        public static final int LINEAR_SRGB = 1;
        public static final int EXTENDED_SRGB = 2;
        public static final int LINEAR_EXTENDED_SRGB = 3;
        public static final int BT709 = 4;
        public static final int BT2020 = 5;
        public static final int DCI_P3 = 6;
        public static final int DISPLAY_P3 = 7;
        public static final int NTSC_1953 = 8;
        public static final int SMPTE_C = 9;
        public static final int ADOBE_RGB = 10;
        public static final int PRO_PHOTO_RGB = 11;
        public static final int ACES = 12;
        public static final int ACESCG = 13;
        public static final int CIE_XYZ = 14;
        public static final int CIE_LAB = 15;
        public static final int OETF_SRGB = 16;
    }
    private final String name;
    private final int id;
    protected ColorSpace(String name, int id) { this.name = name; this.id = id; }
    public static ColorSpace get(int named) {
        switch (named) {
            case Named.LINEAR_SRGB: return new ColorSpace("linear_srgb", named);
            case Named.EXTENDED_SRGB: return new ColorSpace("extended_srgb", named);
            default: return new ColorSpace("srgb", Named.SRGB);
        }
    }
    public int getId() { return id; }
    public String getName() { return name; }
    public boolean isWideGamut() { return false; }
    public float[] toXyz(float[] v) { return v == null ? null : v.clone(); }
    public float[] fromXyz(float[] v) { return v == null ? null : v.clone(); }
    public static float[] connect(ColorSpace src, ColorSpace dst) { return new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f}; }
    public static float[] adapt(float[] xyz, float[] whitepoint) { return xyz == null ? null : xyz.clone(); }
    public static float[] convert(float[] v, ColorSpace src, ColorSpace dst) { return v == null ? null : v.clone(); }
    @Override public boolean equals(Object o) { if (this == o) return true; if (!(o instanceof ColorSpace)) return false; return id == ((ColorSpace) o).id; }
    @Override public int hashCode() { return id; }
    @Override public String toString() { return name; }
}
