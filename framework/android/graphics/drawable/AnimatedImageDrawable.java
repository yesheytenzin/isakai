// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;

/** Animated-image shell: static first frame. */
public class AnimatedImageDrawable extends Drawable {
    private boolean running;
    private int repeatCount;
    @Override public void draw(Canvas canvas) { }
    public void start() { running = true; }
    public void stop() { running = false; }
    public boolean isRunning() { return running; }
    public void setRepeatCount(int count) { repeatCount = count; }
    public int getRepeatCount() { return repeatCount; }
    public void clearAnimationCallbacks() { }
}
