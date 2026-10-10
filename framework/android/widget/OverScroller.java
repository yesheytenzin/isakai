// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.animation.Interpolator;

/**
 * OverScroller: overshoot-capable fling physics (overshoot distance
 * recorded but not rendered; headless has no edge glow).
 */
public class OverScroller {
    private final Scroller scroller;
    private int overX;
    private int overY;

    public OverScroller(Context context) {
        scroller = new Scroller(context);
    }

    public OverScroller(Context context, Interpolator interpolator) {
        scroller = new Scroller(context, interpolator);
    }

    public boolean isFinished() {
        return scroller.isFinished();
    }

    public void abortAnimation() {
        scroller.abortAnimation();
    }

    public void startScroll(int startX, int startY, int dx, int dy) {
        scroller.startScroll(startX, startY, dx, dy);
    }

    public void startScroll(int startX, int startY, int dx, int dy, int duration) {
        scroller.startScroll(startX, startY, dx, dy, duration);
    }

    public void fling(int startX, int startY, int velocityX, int velocityY,
            int minX, int maxX, int minY, int maxY) {
        fling(startX, startY, velocityX, velocityY, minX, maxX, minY, maxY, 0, 0);
    }

    public void fling(int startX, int startY, int velocityX, int velocityY,
            int minX, int maxX, int minY, int maxY, int overX, int overY) {
        this.overX = overX;
        this.overY = overY;
        scroller.fling(startX, startY, velocityX, velocityY,
                minX - overX, maxX + overX, minY - overY, maxY + overY);
    }

    public boolean computeScrollOffset() {
        return scroller.computeScrollOffset();
    }

    public int getCurrX() { return scroller.getCurrX(); }
    public int getCurrY() { return scroller.getCurrY(); }
    public int getFinalX() { return scroller.getFinalX(); }
    public int getFinalY() { return scroller.getFinalY(); }
    public int getStartX() { return scroller.getStartX(); }
    public int getStartY() { return scroller.getStartY(); }
}
