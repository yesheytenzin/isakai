// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Chained property animation: applies end values immediately. */
public class ViewPropertyAnimator {
    private final View view;
    private long duration = 300;
    private long startDelay;
    private android.animation.TimeInterpolator interpolator;

    ViewPropertyAnimator(View view) {
        this.view = view;
    }

    public ViewPropertyAnimator setDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public ViewPropertyAnimator setStartDelay(long delay) {
        this.startDelay = delay;
        return this;
    }

    public ViewPropertyAnimator setInterpolator(android.animation.TimeInterpolator interpolator) {
        this.interpolator = interpolator;
        return this;
    }

    public ViewPropertyAnimator alpha(float value) {
        if (view != null) view.setAlpha(value);
        return this;
    }

    public ViewPropertyAnimator translationX(float value) {
        if (view != null) view.setTranslationX(value);
        return this;
    }

    public ViewPropertyAnimator translationY(float value) {
        if (view != null) view.setTranslationY(value);
        return this;
    }

    public ViewPropertyAnimator translationZ(float value) {
        if (view != null) view.setTranslationZ(value);
        return this;
    }

    public ViewPropertyAnimator scaleX(float value) {
        if (view != null) view.setScaleX(value);
        return this;
    }

    public ViewPropertyAnimator scaleY(float value) {
        if (view != null) view.setScaleY(value);
        return this;
    }

    public ViewPropertyAnimator rotation(float value) {
        if (view != null) view.setRotation(value);
        return this;
    }

    public ViewPropertyAnimator withEndAction(Runnable action) {
        if (action != null) action.run();
        return this;
    }

    public ViewPropertyAnimator withStartAction(Runnable action) {
        if (action != null) action.run();
        return this;
    }

    public void start() {}

    public void cancel() {}
}
