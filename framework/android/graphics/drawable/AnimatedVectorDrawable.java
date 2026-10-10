// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;

/** Animated vector: start/stop flip state, callbacks fire immediately. */
public class AnimatedVectorDrawable extends Drawable implements Animatable2 {
    private boolean running;
    private final java.util.ArrayList<AnimationCallback> callbacks = new java.util.ArrayList<>();

    public AnimatedVectorDrawable() {}

    @Override
    public void draw(Canvas canvas) {}

    @Override
    public void start() {
        running = true;
        for (AnimationCallback cb : callbacks.toArray(new AnimationCallback[0])) cb.onAnimationStart(this);
    }

    @Override
    public void stop() {
        running = false;
        for (AnimationCallback cb : callbacks.toArray(new AnimationCallback[0])) cb.onAnimationEnd(this);
    }

    @Override
    public boolean isRunning() { return running; }

    @Override
    public void registerAnimationCallback(AnimationCallback callback) {
        if (callback != null && !callbacks.contains(callback)) callbacks.add(callback);
    }

    @Override
    public boolean unregisterAnimationCallback(AnimationCallback callback) {
        return callbacks.remove(callback);
    }

    @Override
    public void clearAnimationCallbacks() {
        callbacks.clear();
    }
}
