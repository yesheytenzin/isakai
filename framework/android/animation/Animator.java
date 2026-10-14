// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/**
 * Property animator base: listeners fire, values jump to end.
 *
 * <p>This framework runs animations to completion synchronously (no frame
 * clock): start()/end() dispatch the listener callbacks and the animated
 * value is the end value. Durations, delays, repeat counts, interpolators
 * and evaluators are stored and reported back so apps that read them back
 * (RecyclerView's item animator calls {@code ValueAnimator.getInterpolator()})
 * see a consistent model instead of a missing method.
 */
public class Animator {
    public interface AnimatorListener {
        void onAnimationStart(Animator animator);
        void onAnimationEnd(Animator animator);
        void onAnimationCancel(Animator animator);
        void onAnimationRepeat(Animator animator);
    }

    public interface AnimatorPauseListener {
        void onAnimationPause(Animator animator);
        void onAnimationResume(Animator animator);
    }

    private final java.util.ArrayList<AnimatorListener> listeners = new java.util.ArrayList<>();
    private final java.util.ArrayList<AnimatorPauseListener> pauseListeners = new java.util.ArrayList<>();
    private long duration = 300;
    private long startDelay = 0;
    private TimeInterpolator interpolator;
    private Object target;
    private boolean started;

    public void addListener(AnimatorListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(AnimatorListener listener) {
        listeners.remove(listener);
    }

    public void removeAllListeners() {
        listeners.clear();
        pauseListeners.clear();
    }

    public void addPauseListener(AnimatorPauseListener listener) {
        if (listener != null && !pauseListeners.contains(listener)) pauseListeners.add(listener);
    }

    public void removePauseListener(AnimatorPauseListener listener) {
        pauseListeners.remove(listener);
    }

    protected java.util.List<AnimatorPauseListener> pauseListenersSnapshot() {
        return new java.util.ArrayList<>(pauseListeners);
    }

    public Animator setDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public long getDuration() {
        return duration;
    }

    public void setStartDelay(long startDelay) {
        this.startDelay = startDelay;
    }

    public long getStartDelay() {
        return startDelay;
    }

    public void setInterpolator(TimeInterpolator interpolator) {
        this.interpolator = interpolator;
    }

    /** Interpolator in effect; null means linear, matching AOSP. */
    public TimeInterpolator getInterpolator() {
        return interpolator;
    }

    public void setTarget(Object target) {
        this.target = target;
    }

    public Object getTarget() {
        return target;
    }

    public void start() {
        started = true;
        for (AnimatorListener l : listeners.toArray(new AnimatorListener[0])) l.onAnimationStart(this);
        for (AnimatorListener l : listeners.toArray(new AnimatorListener[0])) l.onAnimationEnd(this);
    }

    public void cancel() {
        for (AnimatorListener l : listeners.toArray(new AnimatorListener[0])) l.onAnimationCancel(this);
    }

    public void end() {}

    public void resume() {
        for (AnimatorPauseListener l : pauseListenersSnapshot()) l.onAnimationResume(this);
    }

    public void pause() {
        for (AnimatorPauseListener l : pauseListenersSnapshot()) l.onAnimationPause(this);
    }

    public boolean isRunning() {
        return false;
    }

    public boolean isStarted() {
        return started;
    }
}
