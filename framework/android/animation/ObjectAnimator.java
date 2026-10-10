// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

import android.view.View;

/** Object animator: sets the end value on start. */
public class ObjectAnimator extends ValueAnimator {
    private Object target;
    private String propertyName;

    public static ObjectAnimator ofFloat(Object target, String propertyName, float... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.propertyName = propertyName;
        return a;
    }

    public static ObjectAnimator ofInt(Object target, String propertyName, int... values) {
        ObjectAnimator a = new ObjectAnimator();
        a.target = target;
        a.propertyName = propertyName;
        return a;
    }

    public Object getTarget() {
        return target;
    }

    public String getPropertyName() {
        return propertyName;
    }

    @Override
    public void start() {
        applyEndValue();
        super.start();
    }

    private void applyEndValue() {
        if (!(target instanceof View) || propertyName == null) return;
        View v = (View) target;
        Object end = getAnimatedValue();
        float f = end instanceof Number ? ((Number) end).floatValue() : 0;
        switch (propertyName) {
            case "alpha": v.setAlpha(f); break;
            case "translationX": v.setTranslationX(f); break;
            case "translationY": v.setTranslationY(f); break;
            case "translationZ": v.setTranslationZ(f); break;
            case "scaleX": v.setScaleX(f); break;
            case "scaleY": v.setScaleY(f); break;
            case "rotation": v.setRotation(f); break;
            default: break;
        }
    }
}
