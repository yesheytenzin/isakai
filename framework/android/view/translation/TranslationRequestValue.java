// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.translation;

/** A value asked for translation; text is the only kind this runtime carries. */
public final class TranslationRequestValue {
    private final CharSequence text;

    private TranslationRequestValue(CharSequence text) {
        this.text = text;
    }

    public static TranslationRequestValue forText(CharSequence text) {
        return new TranslationRequestValue(text);
    }

    public CharSequence getText() {
        return text;
    }
}
