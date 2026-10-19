// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.translation;

/** A translated value for one view; text is the only kind this runtime carries. */
public final class TranslationResponseValue {
    private final CharSequence text;

    private TranslationResponseValue(CharSequence text) {
        this.text = text;
    }

    public CharSequence getText() {
        return text;
    }

    public static final class Builder {
        private CharSequence text;

        public Builder setText(CharSequence text) {
            this.text = text;
            return this;
        }

        public TranslationResponseValue build() {
            return new TranslationResponseValue(text);
        }
    }
}
