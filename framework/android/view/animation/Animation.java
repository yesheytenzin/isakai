// SPDX-License-Identifier: GPL-3.0-or-later
package android.view.animation;

/** Legacy animation handle: start/end hooks only. */
public class Animation {
    public static final int INFINITE = -1;
    public static final int RESTART = 1;
    public static final int REVERSE = 2;

    private long duration;
    private Interpolator interpolator;

    public void setDuration(long duration) {
        this.duration = duration;
    }

    public long getDuration() {
        return duration;
    }

    public void setInterpolator(Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    public void setRepeatCount(int count) {}
    public void setRepeatMode(int mode) {}
    public void setFillAfter(boolean fill) {}
    public void start() {}
    public void cancel() {}
    public boolean hasEnded() { return true; }

    public interface AnimationListener {
        void onAnimationStart(Animation animation);
        void onAnimationEnd(Animation animation);
        void onAnimationRepeat(Animation animation);
    }

    public void setAnimationListener(AnimationListener listener) {}
}
