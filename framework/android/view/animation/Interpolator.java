// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

/** Easing curve mapping elapsed fraction to interpolated fraction. */
public interface Interpolator extends android.animation.TimeInterpolator {
    @Override
    float getInterpolation(float input);
}
