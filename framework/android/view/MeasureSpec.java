// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Measure-spec packing: size plus mode in one int. */
public final class MeasureSpec {
    public static final int UNSPECIFIED = 0;
    public static final int EXACTLY = 1 << 30;
    public static final int AT_MOST = 2 << 30;

    private static final int MODE_MASK = 0x3 << 30;
    private static final int SIZE_MASK = 0x3FFFFFFF;

    private MeasureSpec() {}

    public static int makeMeasureSpec(int size, int mode) {
        return (size & SIZE_MASK) | (mode & MODE_MASK);
    }

    public static int getMode(int spec) {
        return spec & MODE_MASK;
    }

    public static int getSize(int spec) {
        return spec & SIZE_MASK;
    }
}
