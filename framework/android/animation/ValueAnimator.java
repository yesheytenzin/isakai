// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

import android.view.View;

/** Value animator: update listeners fire once at the end value. */
public class ValueAnimator extends Animator {
    public interface AnimatorUpdateListener {
        void onAnimationUpdate(ValueAnimator animator);
    }

    private final java.util.ArrayList<AnimatorUpdateListener> updateListeners =
            new java.util.ArrayList<>();
    private float[] floatValues = new float[0];
    private int[] intValues = new int[0];
    private boolean isFloat = true;

    public static ValueAnimator ofFloat(float... values) {
        ValueAnimator a = new ValueAnimator();
        a.floatValues = values == null ? new float[0] : values.clone();
        a.isFloat = true;
        return a;
    }

    public static ValueAnimator ofInt(int... values) {
        ValueAnimator a = new ValueAnimator();
        a.intValues = values == null ? new int[0] : values.clone();
        a.isFloat = false;
        return a;
    }

    public void addUpdateListener(AnimatorUpdateListener listener) {
        if (listener != null && !updateListeners.contains(listener)) updateListeners.add(listener);
    }

    public void removeUpdateListener(AnimatorUpdateListener listener) {
        updateListeners.remove(listener);
    }

    public Object getAnimatedValue() {
        if (isFloat) return floatValues.length == 0 ? 0f : floatValues[floatValues.length - 1];
        return intValues.length == 0 ? 0 : intValues[intValues.length - 1];
    }

    public float getAnimatedFraction() {
        return 1.0f;
    }

    @Override
    public void start() {
        for (AnimatorUpdateListener l : updateListeners.toArray(new AnimatorUpdateListener[0])) {
            l.onAnimationUpdate(this);
        }
        super.start();
    }

    public static ValueAnimator ofPropertyValuesHolder(PropertyValuesHolder... values) {
        return new ValueAnimator();
    }

    public static ValueAnimator ofArgb(int... values) {
        return ofInt(values);
    }
}
