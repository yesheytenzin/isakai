// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;

/** Frame animation: draws the first frame, timing ignored. */
public class AnimationDrawable extends DrawableContainer implements Animatable {
    private final java.util.ArrayList<Integer> durations = new java.util.ArrayList<>();
    private boolean oneShot;
    private boolean running;

    public AnimationDrawable() {}

    public void addFrame(Drawable frame, int duration) {
        if (frame == null) return;
        addChild(frame);
        durations.add(duration);
    }

    public int getNumberOfFrames() { return getChildCount(); }

    public Drawable getFrame(int index) { return getChild(index); }

    public int getDuration(int index) {
        return index >= 0 && index < durations.size() ? durations.get(index) : 0;
    }

    public boolean isOneShot() { return oneShot; }
    public void setOneShot(boolean oneShot) { this.oneShot = oneShot; }

    @Override
    public void start() {
        running = true;
        selectDrawable(getChildCount() > 0 ? 0 : -1);
    }

    @Override
    public void stop() { running = false; }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public void draw(Canvas canvas) {
        super.draw(canvas);
    }
}
