// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.DoubleUnaryOperator;

/**
 * Color-space model: named spaces are stable singletons, {@link Rgb} carries
 * primaries/white point/transfer functions. Conversions are identity passes;
 * the software rasterizer composites in sRGB.
 */
public class ColorSpace {
    public enum Adaptation { BRADFORD, VON_KRIES, CAT02, CIECAM02 }
    public enum RenderIntent { PERCEPTUAL, RELATIVE, SATURATION, ABSOLUTE }

    /** Named color spaces, AOSP ordering (the ordinal doubles as the id). */
    public enum Named {
        SRGB, LINEAR_SRGB, EXTENDED_SRGB, LINEAR_EXTENDED_SRGB, BT709, BT2020,
        DCI_P3, DISPLAY_P3, NTSC_1953, SMPTE_C, ADOBE_RGB, PRO_PHOTO_RGB,
        ACES, ACESCG, CIE_XYZ, CIE_LAB, BT2020_HLG, BT2020_PQ
    }

    private static final AtomicInteger NEXT_ID = new AtomicInteger(1000);

    private static final float[] SRGB_PRIMARIES = {0.640f, 0.330f, 0.300f, 0.600f, 0.150f, 0.060f};
    private static final float[] P3_PRIMARIES = {0.680f, 0.320f, 0.265f, 0.690f, 0.150f, 0.060f};
    private static final float[] BT2020_PRIMARIES = {0.708f, 0.292f, 0.170f, 0.797f, 0.131f, 0.046f};
    private static final float[] ADOBE_PRIMARIES = {0.640f, 0.330f, 0.210f, 0.710f, 0.150f, 0.060f};
    private static final float[] D65 = {0.3127f, 0.3290f};

    /** Per-space overrides; -1 entries fall back to sRGB primaries. */
    private static final float[][] NAMED_PRIMARIES = new float[Named.values().length][];

    static {
        NAMED_PRIMARIES[Named.BT2020.ordinal()] = BT2020_PRIMARIES;
        NAMED_PRIMARIES[Named.BT2020_HLG.ordinal()] = BT2020_PRIMARIES;
        NAMED_PRIMARIES[Named.BT2020_PQ.ordinal()] = BT2020_PRIMARIES;
        NAMED_PRIMARIES[Named.DCI_P3.ordinal()] = P3_PRIMARIES;
        NAMED_PRIMARIES[Named.DISPLAY_P3.ordinal()] = P3_PRIMARIES;
        NAMED_PRIMARIES[Named.ADOBE_RGB.ordinal()] = ADOBE_PRIMARIES;
        NAMED_PRIMARIES[Named.ACES.ordinal()] = BT2020_PRIMARIES;
        NAMED_PRIMARIES[Named.ACESCG.ordinal()] = BT2020_PRIMARIES;
    }

    private static final ColorSpace[] NAMED_CACHE = new ColorSpace[Named.values().length];

    private final String name;
    private final int id;

    protected ColorSpace(String name, int id) {
        this.name = name;
        this.id = id;
    }

    /** Stable singleton per named space, AOSP contract. */
    public static ColorSpace get(Named named) {
        if (named == null) throw new IllegalArgumentException("named == null");
        synchronized (NAMED_CACHE) {
            ColorSpace cached = NAMED_CACHE[named.ordinal()];
            if (cached != null) return cached;
            ColorSpace space;
            switch (named) {
                case CIE_XYZ:
                case CIE_LAB:
                    space = new ColorSpace(named.name().toLowerCase(java.util.Locale.ROOT), named.ordinal());
                    break;
                default: {
                    float[] primaries = NAMED_PRIMARIES[named.ordinal()];
                    String lname = named.name().toLowerCase(java.util.Locale.ROOT);
                    if (isPurePower(named)) {
                        space = new ColorSpace.Rgb(lname, named.ordinal(), primaries, D65,
                                x -> Math.pow(x, 2.6), x -> Math.pow(x, 1.0 / 2.6), 0f, 1f);
                    } else if (isLinear(named)) {
                        space = new ColorSpace.Rgb(lname, named.ordinal(), primaries, D65,
                                x -> x, x -> x, 0f, 1f);
                    } else {
                        space = new ColorSpace.Rgb(lname, named.ordinal(), primaries, D65,
                                ColorSpace.Rgb.SRGB_TRANSFER);
                    }
                    break;
                }
            }
            NAMED_CACHE[named.ordinal()] = space;
            return space;
        }
    }

    private static boolean isLinear(Named named) {
        return named == Named.LINEAR_SRGB || named == Named.LINEAR_EXTENDED_SRGB;
    }

    private static boolean isPurePower(Named named) {
        return named == Named.DCI_P3;
    }

    public int getId() { return id; }
    public String getName() { return name; }

    public boolean isWideGamut() { return false; }

    public boolean isSrgb() { return id == Named.SRGB.ordinal(); }

    public int getComponentCount() { return 3; }

    public float[] toXyz(float[] v) { return v == null ? null : v.clone(); }
    public float[] fromXyz(float[] v) { return v == null ? null : v.clone(); }

    public static float[] connect(ColorSpace src, ColorSpace dst) {
        return new float[]{1f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 1f};
    }

    public static float[] adapt(float[] xyz, float[] whitepoint) {
        return xyz == null ? null : xyz.clone();
    }

    public static float[] convert(float[] v, ColorSpace src, ColorSpace dst) {
        return v == null ? null : v.clone();
    }

    public static float getMinValue(int component) { return 0f; }

    public static float getMaxValue(int component) { return 1f; }

    @Override public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof ColorSpace)) return false;
        return id == ((ColorSpace) o).id;
    }

    @Override public int hashCode() { return id; }

    @Override public String toString() { return name; }

    /** An RGB color space: primaries, white point and transfer functions. */
    public static class Rgb extends ColorSpace {
        /** IEC 61966-2-1 transfer parameters (sRGB). */
        public static final TransferParameters SRGB_TRANSFER =
                new TransferParameters(1.055, -0.055, 12.92, 0.04045, 0.0, 0.0, 2.4);

        /** Continuity parameters of a transfer function, AOSP shape. */
        public static class TransferParameters {
            public final double a;
            public final double b;
            public final double c;
            public final double d;
            public final double e;
            public final double f;
            public final double g;

            public TransferParameters(double a, double b, double c, double d,
                    double e, double f, double g) {
                this.a = a;
                this.b = b;
                this.c = c;
                this.d = d;
                this.e = e;
                this.f = f;
                this.g = g;
            }
        }

        private final float[] primaries;
        private final float[] whitePoint;
        private final float[] transform;
        private final TransferParameters transferParameters;
        private final DoubleUnaryOperator eotf;
        private final DoubleUnaryOperator oetf;
        private final float min;
        private final float max;
        private final boolean wideGamut;

        public Rgb(String name, float[] primaries, float[] whitePoint,
                TransferParameters transferParameters) {
            this(name, NEXT_ID.getAndIncrement(), primaries, whitePoint, transferParameters);
        }

        Rgb(String name, int id, float[] primaries, float[] whitePoint,
                TransferParameters transferParameters) {
            super(name, id);
            this.primaries = primaries == null ? SRGB_PRIMARIES.clone() : primaries.clone();
            this.whitePoint = whitePoint == null ? D65.clone() : whitePoint.clone();
            this.transferParameters = transferParameters;
            this.oetf = transferParameters == null ? x -> x : generateOetf(transferParameters);
            this.eotf = transferParameters == null ? x -> x : generateEotf(transferParameters);
            this.min = 0f;
            this.max = 1f;
            this.transform = identityTransform();
            this.wideGamut = computeWideGamut(this.primaries);
        }

        public Rgb(String name, float[] primaries, float[] whitePoint,
                DoubleUnaryOperator oetf, DoubleUnaryOperator eotf, float min, float max) {
            this(name, NEXT_ID.getAndIncrement(), primaries, whitePoint, oetf, eotf, min, max);
        }

        Rgb(String name, int id, float[] primaries, float[] whitePoint,
                DoubleUnaryOperator oetf, DoubleUnaryOperator eotf, float min, float max) {
            super(name, id);
            if (oetf == null || eotf == null) throw new IllegalArgumentException("oetf/eotf == null");
            this.primaries = primaries == null ? SRGB_PRIMARIES.clone() : primaries.clone();
            this.whitePoint = whitePoint == null ? D65.clone() : whitePoint.clone();
            this.transferParameters = null;
            this.oetf = oetf;
            this.eotf = eotf;
            this.min = min;
            this.max = max;
            this.transform = identityTransform();
            this.wideGamut = computeWideGamut(this.primaries);
        }

        public Rgb(String name, float[] primaries, float[] whitePoint, float gamma) {
            this(name, NEXT_ID.getAndIncrement(), primaries, whitePoint,
                    x -> Math.pow(x, gamma), x -> Math.pow(x, 1.0 / gamma), 0f, 1f);
        }

        private static float[] identityTransform() {
            float[] m = new float[16];
            m[0] = 1;
            m[5] = 1;
            m[10] = 1;
            m[15] = 1;
            return m;
        }

        private static boolean computeWideGamut(float[] primaries) {
            return primaries.length >= 6
                    && (Math.abs(primaries[0] - SRGB_PRIMARIES[0]) > 0.001f
                        || Math.abs(primaries[2] - SRGB_PRIMARIES[2]) > 0.001f);
        }

        private static DoubleUnaryOperator generateOetf(TransferParameters p) {
            return x -> x < p.d ? p.c * x + p.f : Math.pow(p.a * x + p.b, p.g) + p.e;
        }

        private static DoubleUnaryOperator generateEotf(TransferParameters p) {
            return x -> x < p.c * p.d + p.f
                    ? (x - p.f) / p.c
                    : Math.pow((x - p.e) / p.a, 1.0 / p.g) - p.b / p.a;
        }

        public float[] getPrimaries() { return primaries.clone(); }
        public float[] getWhitePoint() { return whitePoint.clone(); }
        public float[] getTransform() { return transform.clone(); }
        public TransferParameters getTransferParameters() { return transferParameters; }
        public DoubleUnaryOperator getEotf() { return eotf; }
        public DoubleUnaryOperator getOetf() { return oetf; }
        public float getMinValue() { return min; }
        public float getMaxValue() { return max; }

        @Override public boolean isWideGamut() { return wideGamut; }

        @Override public boolean isSrgb() { return !wideGamut && transferParameters != null; }

        @Override public float[] toXyz(float[] v) {
            if (v == null) return null;
            float[] out = new float[v.length];
            DoubleUnaryOperator e = eotf;
            for (int i = 0; i < v.length; i++) {
                float x = v[i];
                if (x < min) x = min;
                if (x > max) x = max;
                out[i] = (float) e.applyAsDouble(x);
            }
            return out;
        }

        @Override public float[] fromXyz(float[] v) {
            if (v == null) return null;
            float[] out = new float[v.length];
            DoubleUnaryOperator o = oetf;
            for (int i = 0; i < v.length; i++) out[i] = (float) o.applyAsDouble(v[i]);
            return out;
        }
    }
}
