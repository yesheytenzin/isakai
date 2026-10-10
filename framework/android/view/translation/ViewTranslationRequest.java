// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.translation;

import android.view.autofill.AutofillId;

/** Translation request for one view with builder. */
public final class ViewTranslationRequest {
    private final AutofillId autofillId;
    private final CharSequence value;
    private ViewTranslationRequest(Builder b) { this.autofillId = b.autofillId; this.value = b.value; }
    public AutofillId getAutofillId() { return autofillId; }
    public CharSequence getValue(String key) { return value; }
    public static final class Builder {
        private final AutofillId autofillId;
        private CharSequence value;
        public Builder(AutofillId autofillId) { this.autofillId = autofillId; }
        public Builder setValue(String key, CharSequence value) { this.value = value; return this; }
        public ViewTranslationRequest build() { return new ViewTranslationRequest(this); }
    }
}
