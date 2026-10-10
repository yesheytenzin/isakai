// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

import android.content.Context;
import android.util.AttributeSet;

/** Ease-in-out curve: cosine blend of the elapsed fraction. */
public class AccelerateDecelerateInterpolator implements Interpolator {
    public AccelerateDecelerateInterpolator() {
    }

    public AccelerateDecelerateInterpolator(Context context, AttributeSet attrs) {
    }

    @Override
    public float getInterpolation(float input) {
        return (float) ((Math.cos((input + 1) * Math.PI) / 2.0) + 0.5);
    }
}
