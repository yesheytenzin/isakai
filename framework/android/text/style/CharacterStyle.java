// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.text.TextPaint;

/** Base for spans that change paint or metrics. */
public abstract class CharacterStyle {
    public abstract void updateDrawState(TextPaint paint);

    public static CharacterStyle wrap(CharacterStyle style) {
        return style;
    }

    public CharacterStyle getUnderlying() {
        return this;
    }
}
