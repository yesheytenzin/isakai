// SPDX-License-Identifier: GPL-3.0-or-later
package android.transition;

/** Ordered/unordered group of child transitions. */
public class TransitionSet extends Transition {
    public static final int ORDERING_TOGETHER = 0;
    public static final int ORDERING_SEQUENTIAL = 1;
    private final java.util.List<Transition> children = new java.util.ArrayList<>();
    private int ordering = ORDERING_TOGETHER;
    public TransitionSet addTransition(Transition transition) { if (transition != null) children.add(transition); return this; }
    public TransitionSet removeTransition(Transition transition) { children.remove(transition); return this; }
    public int getTransitionCount() { return children.size(); }
    public Transition getTransitionAt(int index) { return children.get(index); }
    public TransitionSet setOrdering(int ordering) { this.ordering = ordering; return this; }
    public int getOrdering() { return ordering; }
}
