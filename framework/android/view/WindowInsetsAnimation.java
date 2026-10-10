// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

import android.view.animation.Interpolator;

/** Window-insets animation: fraction record, callbacks never fire. */
public class WindowInsetsAnimation {
    public abstract static class Callback {
        public static final int DISPATCH_MODE_STOP = 0;
        public static final int DISPATCH_MODE_CONTINUE_ON_SUBTREE = 1;

        private final int dispatchMode;

        public Callback(int dispatchMode) {
            this.dispatchMode = dispatchMode;
        }

        public int getDispatchMode() {
            return dispatchMode;
        }

        public void onPrepare(WindowInsetsAnimation animation) {}
        public WindowInsets onProgress(WindowInsets insets,
                java.util.List<WindowInsetsAnimation> runningAnimations) {
            return insets;
        }
        public void onEnd(WindowInsetsAnimation animation) {}
    }

    public static final class Bounds {
        private final android.graphics.Insets lower;
        private final android.graphics.Insets upper;

        public Bounds(android.graphics.Insets lower, android.graphics.Insets upper) {
            this.lower = lower == null ? android.graphics.Insets.NONE : lower;
            this.upper = upper == null ? android.graphics.Insets.NONE : upper;
        }

        public android.graphics.Insets getLowerBound() { return lower; }
        public android.graphics.Insets getUpperBound() { return upper; }
    }

    private final int typeMask;
    private final Interpolator interpolator;
    private final long durationMillis;
    private float fraction;

    public WindowInsetsAnimation(int typeMask, Interpolator interpolator, long durationMillis) {
        this.typeMask = typeMask;
        this.interpolator = interpolator;
        this.durationMillis = durationMillis;
    }

    public int getTypeMask() { return typeMask; }
    public Interpolator getInterpolator() { return interpolator; }
    public long getDurationMillis() { return durationMillis; }
    public float getFraction() { return fraction; }
    public void setFraction(float fraction) { this.fraction = fraction; }
    public float getInterpolatedFraction() {
        return interpolator == null ? fraction : interpolator.getInterpolation(fraction);
    }
}
