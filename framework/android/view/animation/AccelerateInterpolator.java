// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;

/** Ease-in curve: input raised to twice the factor (default quadratic). */
public class AccelerateInterpolator implements Interpolator {
    private final float factor;
    private final double doubled;

    public AccelerateInterpolator() {
        this(1.0f);
    }

    public AccelerateInterpolator(float factor) {
        this.factor = factor;
        this.doubled = 2.0 * factor;
    }

    public AccelerateInterpolator(Context context, AttributeSet attrs) {
        this(1.0f);
    }

    @Override
    public float getInterpolation(float input) {
        if (factor == 1.0f) {
            return input * input;
        }
        return (float) Math.pow(input, doubled);
    }
}
