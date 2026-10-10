// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Global view-tree listeners: recorded, never fired. */
public class ViewTreeObserver {
    public interface OnGlobalLayoutListener {
        void onGlobalLayout();
    }

    public interface OnPreDrawListener {
        boolean onPreDraw();
    }

    public interface OnDrawListener {
        void onDraw();
    }

    public interface OnGlobalFocusChangeListener {
        void onGlobalFocusChanged(View oldFocus, View newFocus);
    }

    private static final ViewTreeObserver INSTANCE = new ViewTreeObserver();

    static ViewTreeObserver getInstance() {
        return INSTANCE;
    }

    public boolean isAlive() {
        return true;
    }

    public void addOnGlobalLayoutListener(OnGlobalLayoutListener listener) {}

    public void removeOnGlobalLayoutListener(OnGlobalLayoutListener listener) {}

    public void removeGlobalOnLayoutListener(OnGlobalLayoutListener listener) {}

    public void addOnPreDrawListener(OnPreDrawListener listener) {}

    public void removeOnPreDrawListener(OnPreDrawListener listener) {}

    public void addOnDrawListener(OnDrawListener listener) {}

    public void removeOnDrawListener(OnDrawListener listener) {}

    public void addOnGlobalFocusChangeListener(OnGlobalFocusChangeListener listener) {}

    public void removeOnGlobalFocusChangeListener(OnGlobalFocusChangeListener listener) {}

    public void dispatchOnGlobalLayout() {}
    public void dispatchOnPreDraw() {}
}
