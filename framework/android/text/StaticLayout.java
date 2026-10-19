// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

/**
 * Static layout over a fixed width. Ellipsis, hyphenation, and
 * break-strategy settings are accepted and ellipsizing applies.
 */
public class StaticLayout extends Layout {
    public static final int BREAK_STRATEGY_SIMPLE = 0;
    public static final int BREAK_STRATEGY_HIGH_QUALITY = 1;
    public static final int BREAK_STRATEGY_BALANCED = 2;
    public static final int HYPHENATION_FREQUENCY_NONE = 0;
    public static final int HYPHENATION_FREQUENCY_NORMAL = 1;
    public static final int HYPHENATION_FREQUENCY_FULL = 2;

    private StaticLayout(CharSequence text, TextPaint paint, int width, Alignment alignment,
            float spacingMult, float spacingAdd, boolean includePad,
            TextUtils.TruncateAt ellipsize, int maxLines) {
        super(ellipsize(text, paint, width, ellipsize, maxLines), paint, width,
                alignment, spacingMult, spacingAdd, includePad);
    }

    private boolean fallbackLineSpacing;

    /** AOSP carries the theme flag; the host text engine has no fallback spacing pass. */
    public boolean isFallbackLineSpacingEnabled() { return fallbackLineSpacing; }

    public StaticLayout(CharSequence text, TextPaint paint, int width, Alignment alignment,
            float spacingMult, float spacingAdd, boolean includePad) {
        super(text, paint, width, alignment, spacingMult, spacingAdd, includePad);
    }

    public StaticLayout(CharSequence text, int start, int end, TextPaint paint, int width,
            Alignment alignment, float spacingMult, float spacingAdd, boolean includePad) {
        super(text == null ? "" : text.subSequence(Math.max(0, start), Math.min(text.length(), end)),
                paint, width, alignment, spacingMult, spacingAdd, includePad);
    }

    public StaticLayout(CharSequence text, int start, int end, TextPaint paint, int width,
            Alignment alignment, float spacingMult, float spacingAdd, boolean includePad,
            TextUtils.TruncateAt ellipsize, int ellipsizedWidth) {
        super(ellipsize(text == null ? "" : text.subSequence(Math.max(0, start), Math.min(text.length(), end)),
                paint, ellipsizedWidth, ellipsize, Integer.MAX_VALUE),
                paint, width, alignment, spacingMult, spacingAdd, includePad);
    }

    private static CharSequence ellipsize(CharSequence text, TextPaint paint, int width,
            TextUtils.TruncateAt where, int maxLines) {
        if (text == null || where == null || width <= 0) return text == null ? "" : text;
        return TextUtils.ellipsize(text, paint == null ? new TextPaint() : paint, width, where);
    }

    public int getMaxLines() { return Integer.MAX_VALUE; }

    public static final class Builder {
        private CharSequence text = "";
        private int start;
        private int end;
        private TextPaint paint = new TextPaint();
        private int width;
        private Alignment alignment = Alignment.ALIGN_NORMAL;
        private float spacingMult = 1.0f;
        private float spacingAdd;
        private boolean includePad = true;
        private TextUtils.TruncateAt ellipsize;
        private int ellipsizedWidth;
        private int maxLines = Integer.MAX_VALUE;
        private int breakStrategy = BREAK_STRATEGY_SIMPLE;
        private int hyphenationFrequency = HYPHENATION_FREQUENCY_NONE;
        private android.graphics.text.LineBreakConfig lineBreakConfig;
        private boolean useLineSpacingFromFallbacks = true;

        private Builder() {}

        public static Builder obtain(CharSequence text, int start, int end,
                TextPaint paint, int width) {
            Builder b = new Builder();
            b.text = text == null ? "" : text;
            b.start = Math.max(0, start);
            b.end = Math.min(b.text.length(), end);
            if (paint != null) b.paint = paint;
            b.width = width;
            b.ellipsizedWidth = width;
            return b;
        }

        public Builder setAlignment(Alignment alignment) {
            if (alignment != null) this.alignment = alignment;
            return this;
        }

        public Builder setLineSpacing(float spacingAdd, float spacingMult) {
            this.spacingAdd = spacingAdd;
            this.spacingMult = spacingMult;
            return this;
        }

        public Builder setIncludePad(boolean includePad) {
            this.includePad = includePad;
            return this;
        }

        public Builder setEllipsize(TextUtils.TruncateAt ellipsize) {
            this.ellipsize = ellipsize;
            return this;
        }

        public Builder setEllipsizedWidth(int width) {
            this.ellipsizedWidth = width;
            return this;
        }

        public Builder setMaxLines(int maxLines) {
            this.maxLines = maxLines;
            return this;
        }

        public Builder setBreakStrategy(int strategy) {
            this.breakStrategy = strategy;
            return this;
        }

        public Builder setHyphenationFrequency(int frequency) {
            this.hyphenationFrequency = frequency;
            return this;
        }

        /** Line-break configuration is carried, not applied: host breaks lines simply. */
        public Builder setLineBreakConfig(android.graphics.text.LineBreakConfig lineBreakConfig) {
            this.lineBreakConfig = lineBreakConfig;
            return this;
        }

        /**
         * AOSP: whether fallback fonts participate in line-spacing metrics.
         * Carried for API parity; host text layout uses one font per run.
         */
        public Builder setUseLineSpacingFromFallbacks(boolean useLineSpacingFromFallbacks) {
            this.useLineSpacingFromFallbacks = useLineSpacingFromFallbacks;
            return this;
        }

        public Builder setTextDirection(TextDirectionHeuristic dir) {
            return this;
        }

        public StaticLayout build() {
            CharSequence slice = text.subSequence(Math.min(start, text.length()), Math.min(end, text.length()));
            return new StaticLayout(slice, paint, width, alignment, spacingMult, spacingAdd,
                    includePad, ellipsize, maxLines);
        }
    }
}
