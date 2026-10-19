// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

/**
 * Adjusts a line's height while a layout is measured, AOSP-shaped. The
 * platform applies spans through StaticLayout; Isakai's layout pass never
 * calls back into spans, so implementations exist to be loadable and are
 * driven by the app (Compose measures line height itself).
 */
public interface LineHeightSpan {
    void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
            android.graphics.Paint.FontMetricsInt fm);

    /** Density-aware variant invoked when line height depends on font scale. */
    interface WithDensity extends LineHeightSpan {
        void chooseHeight(CharSequence text, int start, int end, int spanstartv, int lineHeight,
                android.graphics.Paint.FontMetricsInt fm, android.text.TextPaint paint);
    }
}
