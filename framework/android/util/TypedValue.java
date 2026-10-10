// SPDX-License-Identifier: GPL-3.0-or-later
package android.util;

import android.content.res.Resources;

/** Typed resource value: type tag plus raw data. */
public class TypedValue {
    public static final int TYPE_NULL = 0x00;
    public static final int TYPE_REFERENCE = 0x01;
    public static final int TYPE_ATTRIBUTE = 0x02;
    public static final int TYPE_STRING = 0x03;
    public static final int TYPE_FLOAT = 0x04;
    public static final int TYPE_DIMENSION = 0x05;
    public static final int TYPE_FRACTION = 0x06;
    public static final int TYPE_FIRST_INT = 0x10;
    public static final int TYPE_INT_DEC = 0x10;
    public static final int TYPE_INT_HEX = 0x11;
    public static final int TYPE_INT_BOOLEAN = 0x12;
    public static final int TYPE_FIRST_COLOR_INT = 0x1c;
    public static final int TYPE_INT_COLOR_ARGB8 = 0x1c;
    public static final int TYPE_INT_COLOR_RGB8 = 0x1d;
    public static final int TYPE_INT_COLOR_ARGB4 = 0x1e;
    public static final int TYPE_INT_COLOR_RGB4 = 0x1f;
    public static final int TYPE_LAST_COLOR_INT = 0x1f;
    public static final int TYPE_LAST_INT = 0x1f;

    public static final int COMPLEX_UNIT_SHIFT = 0;
    public static final int COMPLEX_UNIT_MASK = 0xf;
    public static final int COMPLEX_UNIT_PX = 0;
    public static final int COMPLEX_UNIT_DIP = 1;
    public static final int COMPLEX_UNIT_SP = 2;
    public static final int COMPLEX_UNIT_PT = 3;
    public static final int COMPLEX_UNIT_IN = 4;
    public static final int COMPLEX_UNIT_MM = 5;
    public static final int COMPLEX_UNIT_FRACTION = 0;
    public static final int COMPLEX_UNIT_FRACTION_PARENT = 1;
    public static final int COMPLEX_RADIX_SHIFT = 4;
    public static final int COMPLEX_RADIX_MASK = 0x3;
    public static final int COMPLEX_RADIX_23p0 = 0;
    public static final int COMPLEX_RADIX_16p7 = 1;
    public static final int COMPLEX_RADIX_8p15 = 2;
    public static final int COMPLEX_RADIX_0p23 = 3;
    public static final int COMPLEX_MANTISSA_SHIFT = 8;
    public static final int COMPLEX_MANTISSA_MASK = 0xffffff;

    public int type;
    public CharSequence string;
    public int data;
    public int assetCookie;
    public int resourceId;
    public int changingConfigurations;
    public int density;

    public float getDimension(DisplayMetrics metrics) {
        return complexToDimension(data, metrics);
    }

    public static float complexToDimension(int data, DisplayMetrics metrics) {
        return applyDimension(complexToUnit(data), complexToFloat(data), metrics);
    }

    public static int complexToDimensionPixelSize(int data, DisplayMetrics metrics) {
        float v = complexToDimension(data, metrics);
        int res = (int) (v + 0.5f);
        if (res != 0) return res;
        if (v == 0) return 0;
        return v > 0 ? 1 : -1;
    }

    public static int complexToDimensionPixelOffset(int data, DisplayMetrics metrics) {
        return (int) complexToDimension(data, metrics);
    }

    private static int complexToUnit(int data) {
        return data & COMPLEX_UNIT_MASK;
    }

    /**
     * Decode a packed complex (dimension/fraction) to its float value.
     * AOSP form: the mantissa field (bits 8..31) holds the value scaled by
     * the radix (23p0/16p7/8p15/0p23); the low byte (unit+radix) carries no
     * value. Verified against OpenCalc: 16dp content inset (data 0x1001)
     * decodes to 16.0, 48dp (0x3001) to 48.0.
     */
    private static final float[] RADIX_MULTS = {
        1.0f / (1 << 8), 1.0f / ((1 << 7) * (1 << 8)),
        1.0f / ((1 << 15) * (1 << 8)), 1.0f / ((1 << 23) * (1 << 8)),
    };

    public static float complexToFloat(int data) {
        return (float) (data & (COMPLEX_MANTISSA_MASK << COMPLEX_MANTISSA_SHIFT))
                * RADIX_MULTS[(data >> COMPLEX_RADIX_SHIFT) & COMPLEX_RADIX_MASK];
    }

    public static float applyDimension(int unit, float value, DisplayMetrics metrics) {
        if (metrics == null) metrics = Resources.getSystem().getDisplayMetrics();
        switch (unit) {
            case COMPLEX_UNIT_PX: return value;
            case COMPLEX_UNIT_DIP: return value * metrics.density;
            case COMPLEX_UNIT_SP: return value * metrics.scaledDensity;
            case COMPLEX_UNIT_PT: return value * metrics.xdpi * (1.0f / 72);
            case COMPLEX_UNIT_IN: return value * metrics.xdpi;
            case COMPLEX_UNIT_MM: return value * metrics.xdpi * (1.0f / 25.4f);
            default: return 0;
        }
    }

    public float getFraction(float base, float parentBase) {
        return complexToFloat(data) * (((data >> COMPLEX_UNIT_SHIFT) & COMPLEX_UNIT_MASK) == COMPLEX_UNIT_FRACTION_PARENT ? parentBase : base);
    }
}
