// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

/** Animatable with completion callbacks. */
public interface Animatable2 extends Animatable {
    void registerAnimationCallback(AnimationCallback callback);
    boolean unregisterAnimationCallback(AnimationCallback callback);
    void clearAnimationCallbacks();

    abstract class AnimationCallback {
        public void onAnimationStart(Drawable drawable) {}
        public void onAnimationEnd(Drawable drawable) {}
    }
}
