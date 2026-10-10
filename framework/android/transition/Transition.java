// SPDX-License-Identifier: GPL-3.0-or-later
package android.transition;

/** Scene-transition handle: capture hooks only. */
public class Transition {
    public interface TransitionListener {
        void onTransitionStart(Transition transition);
        void onTransitionEnd(Transition transition);
        void onTransitionCancel(Transition transition);
    }

    public Transition addListener(TransitionListener listener) { return this; }
    public Transition removeListener(TransitionListener listener) { return this; }
    public Transition setDuration(long duration) { return this; }
    public long getDuration() { return 0; }
}
