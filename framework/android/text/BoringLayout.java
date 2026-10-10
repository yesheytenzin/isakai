// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/** Single-line metrics fast path over a fixed width. */
public class BoringLayout extends Layout {
    /** Cached single-line metrics. */
    public static class Metrics {
        public int width;
        public int top;
        public int ascent;
        public int descent;
        public int bottom;
        public int leading;
        public float maxWidth;

        @Override
        public String toString() {
            return "Metrics{width=" + width + "}";
        }
    }

    public static final Metrics EMPTY_METRICS = new Metrics();

    private BoringLayout(CharSequence text, TextPaint paint, int width, Alignment alignment,
            float spacingMult, float spacingAdd, boolean includePad) {
        super(text, paint, width, alignment, spacingMult, spacingAdd, includePad);
    }

    public static Metrics isBoring(CharSequence text, TextPaint paint) {
        return isBoring(text, paint, TextDirectionHeuristics.FIRSTSTRONG_LTR, null);
    }

    public static Metrics isBoring(CharSequence text, TextPaint paint,
            TextDirectionHeuristic textDir, Metrics metrics) {
        if (text == null || paint == null) return null;
        for (int i = 0; i < text.length(); i++) {
            if (text.charAt(i) == '\n') return null;
        }
        Metrics m = metrics == null ? new Metrics() : metrics;
        m.width = (int) (Layout.getDesiredWidth(text, paint) + 0.5f);
        m.top = (int) (paint.ascent() + paint.getFontMetrics().leading);
        m.ascent = (int) paint.ascent();
        m.descent = (int) paint.descent();
        m.bottom = (int) paint.descent();
        m.leading = 0;
        m.maxWidth = m.width;
        return m;
    }

    public static BoringLayout make(CharSequence text, TextPaint paint, int width,
            Alignment align, float spacingMult, float spacingAdd, Metrics metrics,
            boolean includePad) {
        return new BoringLayout(text == null ? "" : text, paint == null ? new TextPaint() : paint,
                width, align == null ? Alignment.ALIGN_NORMAL : align,
                spacingMult, spacingAdd, includePad);
    }

    public static BoringLayout make(CharSequence text, TextPaint paint, int width,
            Alignment align, float spacingMult, float spacingAdd, Metrics metrics,
            boolean includePad, TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        CharSequence shaped = text == null ? "" : text;
        if (ellipsize != null && paint != null) {
            shaped = TextUtils.ellipsize(shaped, paint, ellipsizedWidth, ellipsize);
        }
        return new BoringLayout(shaped, paint == null ? new TextPaint() : paint,
                width, align == null ? Alignment.ALIGN_NORMAL : align,
                spacingMult, spacingAdd, includePad);
    }
}
