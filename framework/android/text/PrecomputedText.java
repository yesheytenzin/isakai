// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;


/** Pre-measured text: CharSequence wrapper with paragraph queries. */
public class PrecomputedText implements Spanned {
    /** Layout parameters the text was measured with. */
    public static final class Params {
        private final TextPaint paint;
        private final int breakStrategy;
        private final int hyphenationFrequency;

        private Params(TextPaint paint, int breakStrategy, int hyphenationFrequency) {
            this.paint = paint;
            this.breakStrategy = breakStrategy;
            this.hyphenationFrequency = hyphenationFrequency;
        }

        public TextPaint getTextPaint() {
            return paint;
        }

        public int getBreakStrategy() {
            return breakStrategy;
        }

        public int getHyphenationFrequency() {
            return hyphenationFrequency;
        }

        /** Builder for Params. */
        public static final class Builder {
            private final TextPaint paint;
            private int breakStrategy;
            private int hyphenationFrequency;

            public Builder(TextPaint paint) {
                this.paint = paint;
            }

            public Builder setBreakStrategy(int breakStrategy) {
                this.breakStrategy = breakStrategy;
                return this;
            }

            public Builder setHyphenationFrequency(int hyphenationFrequency) {
                this.hyphenationFrequency = hyphenationFrequency;
                return this;
            }

            public Params build() {
                return new Params(paint, breakStrategy, hyphenationFrequency);
            }
        }
    }

    private final String text;
    private final Params params;
    private final int[] paraEnds;

    private PrecomputedText(CharSequence text, Params params) {
        this.text = text == null ? "" : text.toString();
        this.params = params;
        java.util.ArrayList<Integer> ends = new java.util.ArrayList<>();
        for (int i = 0; i < this.text.length(); i++) {
            if (this.text.charAt(i) == '\n') ends.add(i + 1);
        }
        if (ends.isEmpty() || ends.get(ends.size() - 1) != this.text.length()) {
            ends.add(this.text.length());
        }
        paraEnds = new int[ends.size()];
        for (int i = 0; i < ends.size(); i++) paraEnds[i] = ends.get(i);
    }

    public static PrecomputedText create(CharSequence text, Params params) {
        if (text instanceof PrecomputedText) return (PrecomputedText) text;
        return new PrecomputedText(text, params);
    }

    public Params getParams() {
        return params;
    }

    public int getParagraphCount() {
        return paraEnds.length;
    }

    public int getParagraphStart(int paraIndex) {
        return paraIndex == 0 ? 0 : paraEnds[paraIndex - 1];
    }

    public int getParagraphEnd(int paraIndex) {
        return paraEnds[paraIndex];
    }

    public int findParaIndex(int pos) {
        for (int i = 0; i < paraEnds.length; i++) {
            if (pos < paraEnds[i]) return i;
        }
        return paraEnds.length - 1;
    }

    @Override
    public int length() {
        return text.length();
    }

    @Override
    public char charAt(int index) {
        return text.charAt(index);
    }

    @Override
    public CharSequence subSequence(int start, int end) {
        return text.subSequence(start, end);
    }

    @Override
    public String toString() {
        return text;
    }

    @Override
    @SuppressWarnings("unchecked")
    public <T> T[] getSpans(int start, int end, Class<T> type) {
        return (T[]) java.lang.reflect.Array.newInstance(type, 0);
    }
    @Override
    public int getSpanStart(Object tag) {
        return -1;
    }

    @Override
    public int getSpanEnd(Object tag) {
        return -1;
    }

    @Override
    public int getSpanFlags(Object tag) {
        return 0;
    }

    @Override
    public int nextSpanTransition(int start, int limit, Class<?> type) {
        return limit;
    }
}
