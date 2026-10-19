// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.text;

/**
 * Line-break config: line-break style/word-style policy (headless: recorded
 * only, the host breaker always applies simple breaks).
 */
public final class LineBreakConfig {
    public static final int LINE_BREAK_STYLE_NONE = 0;
    public static final int LINE_BREAK_STYLE_LOOSE = 1;
    public static final int LINE_BREAK_STYLE_NORMAL = 2;
    public static final int LINE_BREAK_STYLE_STRICT = 3;

    public static final int LINE_BREAK_WORD_STYLE_NONE = 0;
    public static final int LINE_BREAK_WORD_STYLE_PHRASE = 1;
    public static final int LINE_BREAK_WORD_STYLE_AUTO = 2;

    public static final int HYPHENATION_DISABLED = 0;
    public static final int HYPHENATION_ENABLED = 1;

    private final int lineBreakStyle;
    private final int lineBreakWordStyle;
    private final int hyphenation;

    private LineBreakConfig(int lineBreakStyle, int lineBreakWordStyle, int hyphenation) {
        this.lineBreakStyle = lineBreakStyle;
        this.lineBreakWordStyle = lineBreakWordStyle;
        this.hyphenation = hyphenation;
    }

    public int getLineBreakStyle() { return lineBreakStyle; }
    public int getLineBreakWordStyle() { return lineBreakWordStyle; }
    public int getHyphenation() { return hyphenation; }

    /** Returns an empty builder (AOSP: {@code LineBreakConfig.Builder} "get" side). */
    public Builder newBuilder() {
        Builder b = new Builder();
        b.lineBreakStyle = lineBreakStyle;
        b.lineBreakWordStyle = lineBreakWordStyle;
        b.hyphenation = hyphenation;
        return b;
    }

    public static final class Builder {
        private int lineBreakStyle = LINE_BREAK_STYLE_NONE;
        private int lineBreakWordStyle = LINE_BREAK_WORD_STYLE_NONE;
        private int hyphenation = HYPHENATION_DISABLED;

        public Builder setLineBreakStyle(int lineBreakStyle) {
            this.lineBreakStyle = lineBreakStyle;
            return this;
        }

        public Builder setLineBreakWordStyle(int lineBreakWordStyle) {
            this.lineBreakWordStyle = lineBreakWordStyle;
            return this;
        }

        public Builder setHyphenation(int hyphenation) {
            this.hyphenation = hyphenation;
            return this;
        }

        public LineBreakConfig build() {
            return new LineBreakConfig(lineBreakStyle, lineBreakWordStyle, hyphenation);
        }
    }
}
