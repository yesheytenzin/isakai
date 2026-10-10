// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.text;

/**
 * Line-break config: hyphenation/justify policy (headless: recorded only).
 */
public final class LineBreakConfig {
    public static final int HYPHENATION_DISABLED = 0;
    public static final int HYPHENATION_ENABLED = 1;
    public static final int JUSTIFICATION_NONE = 0;
    public static final int JUSTIFICATION_INTER_WORD = 1;

    private final int hyphenation;
    private final int justification;

    private LineBreakConfig(int hyphenation, int justification) {
        this.hyphenation = hyphenation;
        this.justification = justification;
    }

    public int getHyphenation() { return hyphenation; }
    public int getJustification() { return justification; }

    public static final class Builder {
        private int hyphenation = HYPHENATION_DISABLED;
        private int justification = JUSTIFICATION_NONE;

        public Builder setHyphenation(int hyphenation) {
            this.hyphenation = hyphenation;
            return this;
        }

        public Builder setJustification(int justification) {
            this.justification = justification;
            return this;
        }

        public LineBreakConfig build() {
            return new LineBreakConfig(hyphenation, justification);
        }
    }
}
