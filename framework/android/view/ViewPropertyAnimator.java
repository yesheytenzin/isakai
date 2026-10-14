// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Chained property animation: applies end values immediately. */
public class ViewPropertyAnimator {
    private final View view;
    private long duration = 300;
    private long startDelay;
    private android.animation.TimeInterpolator interpolator;
    private android.animation.Animator.AnimatorListener listener;
    private android.animation.ValueAnimator.AnimatorUpdateListener updateListener;

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

    public ViewPropertyAnimator rotationX(float value) {
        return this;
    }

    public ViewPropertyAnimator rotationY(float value) {
        return this;
    }

    public ViewPropertyAnimator x(float value) {
        return translationX(value);
    }

    public ViewPropertyAnimator y(float value) {
        return translationY(value);
    }

    public ViewPropertyAnimator z(float value) {
        return translationZ(value);
    }

    public ViewPropertyAnimator scaleXBy(float value) {
        if (view != null) view.setScaleX(view.getScaleX() + value);
        return this;
    }

    public ViewPropertyAnimator scaleBy(float value) {
        if (view != null) view.setScaleX(view.getScaleX() + value);
        return this;
    }

    /**
     * Listener handoff. The animation is applied immediately, so start and
     * end are dispatched on {@link #start()} (AOSP dispatches them around the
     * animation); the listener must still be registered here because code
     * chains this call and expects the animator back.
     */
    public ViewPropertyAnimator setListener(android.animation.Animator.AnimatorListener listener) {
        this.listener = listener;
        return this;
    }

    public android.animation.Animator.AnimatorListener getListener() {
        return listener;
    }

    public ViewPropertyAnimator setUpdateListener(android.animation.ValueAnimator.AnimatorUpdateListener listener) {
        this.updateListener = listener;
        return this;
    }

    /** Tell the compositor the view is animating (no-op: synchronous model). */
    public ViewPropertyAnimator withLayer() {
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

    public void start() {
        if (listener != null) listener.onAnimationStart(null);
        if (listener != null) listener.onAnimationEnd(null);
    }

    public void cancel() {
        if (listener != null) listener.onAnimationCancel(null);
    }
}
