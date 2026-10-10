// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Property animator base: listeners fire, values jump to end. */
public class Animator {
    public interface AnimatorListener {
        void onAnimationStart(Animator animator);
        void onAnimationEnd(Animator animator);
        void onAnimationCancel(Animator animator);
        void onAnimationRepeat(Animator animator);
    }

    private final java.util.ArrayList<AnimatorListener> listeners = new java.util.ArrayList<>();
    private long duration = 300;
    private TimeInterpolator interpolator;

    public void addListener(AnimatorListener listener) {
        if (listener != null && !listeners.contains(listener)) listeners.add(listener);
    }

    public void removeListener(AnimatorListener listener) {
        listeners.remove(listener);
    }

    public Animator setDuration(long duration) {
        this.duration = duration;
        return this;
    }

    public long getDuration() {
        return duration;
    }

    public void setInterpolator(TimeInterpolator interpolator) {
        this.interpolator = interpolator;
    }

    public void start() {
        for (AnimatorListener l : listeners.toArray(new AnimatorListener[0])) l.onAnimationStart(this);
        for (AnimatorListener l : listeners.toArray(new AnimatorListener[0])) l.onAnimationEnd(this);
    }

    public void cancel() {
        for (AnimatorListener l : listeners.toArray(new AnimatorListener[0])) l.onAnimationCancel(this);
    }

    public void end() {}

    public boolean isRunning() {
        return false;
    }
}
