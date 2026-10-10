// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/**
 * FocusFinder: directional focus search over the view tree.
 * Picks the nearest focusable in the given direction (beam overlap +
 * distance, AOSP heuristic simplified to center distance).
 */
public class FocusFinder {
    private static final FocusFinder INSTANCE = new FocusFinder();

    private FocusFinder() {}

    public static FocusFinder getInstance() {
        return INSTANCE;
    }

    public View findNextFocus(ViewGroup root, View focused, int direction) {
        if (root == null) return null;
        java.util.ArrayList<View> candidates = new java.util.ArrayList<>();
        collectFocusable(root, candidates);
        if (candidates.isEmpty()) return null;
        if (focused == null) return candidates.get(0);
        int[] loc = new int[2];
        focused.getLocationOnScreen(loc);
        int fx = loc[0] + focused.getWidth() / 2;
        int fy = loc[1] + focused.getHeight() / 2;
        View best = null;
        int bestDist = Integer.MAX_VALUE;
        for (View v : candidates) {
            if (v == focused) continue;
            v.getLocationOnScreen(loc);
            int vx = loc[0] + v.getWidth() / 2;
            int vy = loc[1] + v.getHeight() / 2;
            int dx = vx - fx;
            int dy = vy - fy;
            boolean inDir;
            switch (direction) {
                case View.FOCUS_LEFT: inDir = dx < 0; break;
                case View.FOCUS_RIGHT: inDir = dx > 0; break;
                case View.FOCUS_UP: inDir = dy < 0; break;
                case View.FOCUS_DOWN: inDir = dy > 0; break;
                case View.FOCUS_FORWARD:
                case View.FOCUS_BACKWARD:
                default: inDir = true; break;
            }
            if (!inDir) continue;
            int dist = dx * dx + dy * dy;
            if (dist < bestDist) {
                bestDist = dist;
                best = v;
            }
        }
        return best;
    }

    public View findNextFocusFromRect(ViewGroup root, android.graphics.Rect focusedRect,
            int direction) {
        if (root == null) return null;
        java.util.ArrayList<View> candidates = new java.util.ArrayList<>();
        collectFocusable(root, candidates);
        return candidates.isEmpty() ? null : candidates.get(0);
    }

    private void collectFocusable(View v, java.util.ArrayList<View> out) {
        if (v == null || v.getVisibility() != View.VISIBLE || !v.isEnabled()) return;
        if (v.isFocusable()) out.add(v);
        if (v instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) v;
            for (int i = 0; i < g.getChildCount(); i++) {
                collectFocusable(g.getChildAt(i), out);
            }
        }
    }
}
