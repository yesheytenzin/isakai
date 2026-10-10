// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** No-op animator listener base. */
public class AnimatorListenerAdapter implements Animator.AnimatorListener {
    @Override
    public void onAnimationStart(Animator animator) {}

    @Override
    public void onAnimationEnd(Animator animator) {}

    @Override
    public void onAnimationCancel(Animator animator) {}

    @Override
    public void onAnimationRepeat(Animator animator) {}
}
