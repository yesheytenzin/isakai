// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;

/** Ease-out curve: 1 minus the remaining fraction raised to twice the factor. */
public class DecelerateInterpolator implements Interpolator {
    private final float factor;

    public DecelerateInterpolator() {
        this(1.0f);
    }

    public DecelerateInterpolator(float factor) {
        this.factor = factor;
    }

    public DecelerateInterpolator(Context context, AttributeSet attrs) {
        this(1.0f);
    }

    @Override
    public float getInterpolation(float input) {
        float result;
        if (factor == 1.0f) {
            result = 1.0f - (1.0f - input) * (1.0f - input);
        } else {
            result = (float) (1.0f - Math.pow(1.0f - input, 2.0 * factor));
        }
        return result;
    }
}
