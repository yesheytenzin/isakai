// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;

/** Identity curve: output equals input. */
public class LinearInterpolator implements Interpolator {
    public LinearInterpolator() {
    }

    public LinearInterpolator(Context context, AttributeSet attrs) {
    }

    @Override
    public float getInterpolation(float input) {
        return input;
    }
}
