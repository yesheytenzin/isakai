// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Animation clock mapping elapsed fraction to interpolated fraction. */
public interface TimeInterpolator {
    float getInterpolation(float input);
}
