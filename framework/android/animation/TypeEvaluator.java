// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Value interpolation between keyframes. */
public interface TypeEvaluator<T> {
    T evaluate(float fraction, T startValue, T endValue);
}
