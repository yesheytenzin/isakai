// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

/** State list with transitions: transitions ignored. */
public class AnimatedStateListDrawable extends StateListDrawable {
    public AnimatedStateListDrawable() {}

    public void addTransition(int fromId, int toId, Drawable transition, boolean reversible) {}

    public void addState(int[] stateSet, Drawable drawable, int id) {
        addState(stateSet, drawable);
    }
}
