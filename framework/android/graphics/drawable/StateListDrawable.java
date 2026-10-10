// SPDX-License-Identifier: GPL-3.0-or-later
package android.graphics.drawable;

import android.graphics.Canvas;

/** Selects a child drawable by state set, drawing the current match. */
public class StateListDrawable extends DrawableContainer {
    private final java.util.ArrayList<int[]> stateSets = new java.util.ArrayList<>();
    private final java.util.ArrayList<Drawable> drawables = new java.util.ArrayList<>();

    public StateListDrawable() {}

    public void addState(int[] stateSet, Drawable drawable) {
        if (drawable == null) return;
        stateSets.add(stateSet == null ? new int[0] : stateSet.clone());
        drawables.add(drawable);
        addChild(drawable);
        onStateChange(getState());
    }

    private int matchIndex(int[] state) {
        int wild = -1;
        for (int i = 0; i < stateSets.size(); i++) {
            int[] want = stateSets.get(i);
            if (want.length == 0) {
                if (wild < 0) wild = i;
                continue;
            }
            if (matches(want, state)) return i;
        }
        return wild;
    }

    private static boolean matches(int[] want, int[] have) {
        for (int w : want) {
            boolean neg = w < 0;
            int attr = neg ? -w : w;
            boolean found = false;
            for (int h : have) {
                if (h == attr) {
                    found = true;
                    break;
                }
            }
            if (found == neg) return false;
        }
        return true;
    }

    @Override
    public boolean isStateful() { return true; }

    @Override
    protected boolean onStateChange(int[] state) {
        int i = matchIndex(state == null ? new int[0] : state);
        return selectDrawable(i < 0 ? -1 : indexOf(drawables.get(i)));
    }

    @Override
    public void draw(Canvas canvas) {
        Drawable d = getCurrent();
        if (canvas != null && d != null && d != this) {
            d.setBounds(getBounds());
            d.draw(canvas);
        }
    }
}
