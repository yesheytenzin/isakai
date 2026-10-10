// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.translation;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Translation response map with builder. */
public final class ViewTranslationResponse {
    private final Map<String, CharSequence> values;
    private ViewTranslationResponse(Builder b) { this.values = Collections.unmodifiableMap(new HashMap<>(b.values)); }
    public CharSequence getValue(String key) { return values.get(key); }
    public static final class Builder {
        private final Map<String, CharSequence> values = new HashMap<>();
        public Builder setValue(String key, CharSequence value) { values.put(key, value); return this; }
        public ViewTranslationResponse build() { return new ViewTranslationResponse(this); }
    }
}
