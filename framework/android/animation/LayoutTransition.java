// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

/** Layout-change animation config: recorded only. */
public class LayoutTransition {
    public static final int APPEARING = 0;
    public static final int DISAPPEARING = 1;
    public static final int CHANGE_APPEARING = 2;
    public static final int CHANGE_DISAPPEARING = 3;
    public static final int CHANGING = 4;

    public void setDuration(long duration) {}
    public void setAnimator(int transitionType, Animator animator) {}
    public void setStartDelay(int transitionType, long delay) {}
    public void enableTransitionType(int transitionType) {}
    public void disableTransitionType(int transitionType) {}
    public boolean isTransitionTypeEnabled(int transitionType) { return false; }
    public void setAnimateParentHierarchy(boolean animate) {}
    public long getDuration(int transitionType) { return 0; }
    public Animator getAnimator(int transitionType) { return null; }
    public long getStartDelay(int transitionType) { return 0; }
}
