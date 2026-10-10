// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.animation.Interpolator;

/**
 * Scroller: fling/scroll physics over ints (no rendering link).
 * Time base is uptimeMillis; Abort/startScroll/fling/computeScrollOffset
 * follow AOSP semantics with a viscous-fluid interpolator default.
 */
public class Scroller {
    private int startX;
    private int startY;
    private int finalX;
    private int finalY;
    private int currX;
    private int currY;
    private long startTime;
    private int duration;
    private boolean finished = true;
    private final Interpolator interpolator;
    private float durationReciprocal;

    public Scroller(Context context) {
        this(context, null);
    }

    public Scroller(Context context, Interpolator interpolator) {
        this.interpolator = interpolator;
    }

    public boolean isFinished() {
        return finished;
    }

    public void abortAnimation() {
        finished = true;
        currX = finalX;
        currY = finalY;
    }

    public void startScroll(int startX, int startY, int dx, int dy) {
        startScroll(startX, startY, dx, dy, 250);
    }

    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        this.startX = startX;
        this.startY = startY;
        this.finalX = startX + dx;
        this.finalY = startY + dy;
        this.startTime = android.os.SystemClock.uptimeMillis();
        this.duration = Math.max(0, duration);
        this.durationReciprocal = this.duration == 0 ? 0 : 1.0f / this.duration;
        this.finished = false;
        this.currX = startX;
        this.currY = startY;
    }

    public void fling(int startX, int startY, int velocityX, int velocityY,
            int minX, int maxX, int minY, int maxY) {
        // Decoupled axes with a fixed 500ms ease-out (no OverScroller spline).
        this.startX = startX;
        this.startY = startY;
        int dx = clampMag(velocityX / 4, minX - startX, maxX - startX);
        int dy = clampMag(velocityY / 4, minY - startY, maxY - startY);
        this.finalX = startX + dx;
        this.finalY = startY + dy;
        this.startTime = android.os.SystemClock.uptimeMillis();
        this.duration = 500;
        this.durationReciprocal = 1.0f / 500;
        this.finished = false;
        this.currX = startX;
        this.currY = startY;
    }

    private static int clampMag(int v, int lo, int hi) {
        if (v < lo) return lo;
        if (v > hi) return hi;
        return v;
    }

    public boolean computeScrollOffset() {
        if (finished) return false;
        long now = android.os.SystemClock.uptimeMillis();
        long elapsed = now - startTime;
        if (elapsed >= duration) {
            currX = finalX;
            currY = finalY;
            finished = true;
            return currX != startX || currY != startY;
        }
        float t = durationReciprocal == 0 ? 1.0f : elapsed * durationReciprocal;
        float it = interpolator == null ? viscous(t) : interpolator.getInterpolation(t);
        currX = startX + Math.round(it * (finalX - startX));
        currY = startY + Math.round(it * (finalY - startY));
        return true;
    }

    private static float viscous(float x) {
        x *= 8.0f;
        if (x < 1.0f) return x - (1.0f - (float) Math.exp(-x));
        return 0.36787944f + (1.0f - 0.36787944f) * (1.0f - (float) Math.exp(1.0f - x));
    }

    public int getCurrX() { return currX; }
    public int getCurrY() { return currY; }
    public int getFinalX() { return finalX; }
    public int getFinalY() { return finalY; }
    public int getStartX() { return startX; }
    public int getStartY() { return startY; }
}
