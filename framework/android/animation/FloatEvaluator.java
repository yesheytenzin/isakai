// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Float interpolation. */
public class FloatEvaluator implements TypeEvaluator<Float> {
    @Override public Float evaluate(float fraction, Float startValue, Float endValue) {
        float s = startValue == null ? 0f : startValue;
        float e = endValue == null ? 0f : endValue;
        return s + fraction * (e - s);
    }
}
