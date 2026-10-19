// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.translation;

import android.view.autofill.AutofillId;
import java.util.HashMap;
import java.util.Map;

/** Translation request for one view with builder. */
public final class ViewTranslationRequest {
    private final AutofillId autofillId;
    private final Map<String, TranslationRequestValue> values;

    private ViewTranslationRequest(Builder b) {
        this.autofillId = b.autofillId;
        this.values = new HashMap<>(b.values);
    }

    public AutofillId getAutofillId() { return autofillId; }

    public TranslationRequestValue getValue(String key) { return values.get(key); }

    public static final class Builder {
        private final AutofillId autofillId;
        private final Map<String, TranslationRequestValue> values = new HashMap<>();

        public Builder(AutofillId autofillId) { this.autofillId = autofillId; }

        /** AOSP shape: requests carry the view's id and its virtual descendant. */
        public Builder(AutofillId autofillId, long virtualDescendantId) { this.autofillId = autofillId; }

        public Builder setValue(String key, TranslationRequestValue value) {
            values.put(key, value);
            return this;
        }

        public Builder setValue(String key, CharSequence value) {
            values.put(key, TranslationRequestValue.forText(value));
            return this;
        }

        public ViewTranslationRequest build() { return new ViewTranslationRequest(this); }
    }
}
