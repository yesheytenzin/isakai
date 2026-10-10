// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.text.TextPaint;

/** Base for spans that affect both metrics and draw state. */
public abstract class MetricAffectingSpan extends CharacterStyle {
    public abstract void updateMeasureState(TextPaint paint);

    public MetricAffectingSpan getUnderlying() {
        return this;
    }
}
