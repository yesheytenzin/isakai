// SPDX-License-Identifier: GPL-3.0-or-later
package android.animation;

import android.graphics.drawable.Drawable;
import java.util.ArrayList;
import java.util.List;

/** State-driven animator: records tuples, no playback. */
public class StateListAnimator implements Cloneable {
    private static final class Tuple { final int[] specs; final Animator anim; Tuple(int[] s, Animator a) { specs = s; anim = a; } }
    private final List<Tuple> tuples = new ArrayList<>();
    public void addState(int[] specs, Animator animation) { tuples.add(new Tuple(specs == null ? new int[0] : specs.clone(), animation)); }
    public void clear() { tuples.clear(); }
    public void setTarget(android.view.View view) { }
    public void jumpToCurrentState() { }
    @Override public StateListAnimator clone() { StateListAnimator c = new StateListAnimator(); c.tuples.addAll(tuples); return c; }
}
