// SPDX-License-Identifier: GPL-3.0-or-later
package android.view;

/** Window handle: flags plus decor view, no compositor link. */
public abstract class Window {
    public static final int FEATURE_NO_TITLE = 1;
    public static final int FEATURE_ACTION_BAR = 8;
    public static final int FEATURE_OPTIONS_PANEL = 0;

    public interface Callback {
        boolean dispatchKeyEvent(KeyEvent event);
        boolean dispatchKeyShortcutEvent(KeyEvent event);
        boolean dispatchTouchEvent(MotionEvent event);
        boolean dispatchTrackballEvent(MotionEvent event);
        boolean dispatchGenericMotionEvent(MotionEvent event);
        boolean dispatchPopulateAccessibilityEvent(android.view.accessibility.AccessibilityEvent event);
        View onCreatePanelView(int featureId);
        boolean onCreatePanelMenu(int featureId, Menu menu);
        boolean onPreparePanel(int featureId, View view, Menu menu);
        boolean onMenuOpened(int featureId, Menu menu);
        void onPanelClosed(int featureId, Menu menu);
        boolean onMenuItemSelected(int featureId, MenuItem item);
        void onWindowAttributesChanged(WindowManager.LayoutParams attrs);
        void onContentChanged();
        void onWindowFocusChanged(boolean hasFocus);
        void onAttachedToWindow();
        void onDetachedFromWindow();
        void onPointerCaptureChanged(boolean hasCapture);
        boolean onSearchRequested();
        boolean onSearchRequested(SearchEvent searchEvent);
        ActionMode onWindowStartingActionMode(ActionMode.Callback callback);
        ActionMode onWindowStartingActionMode(ActionMode.Callback callback, int type);
        void onActionModeStarted(ActionMode mode);
        void onActionModeFinished(ActionMode mode);
        void onProvideKeyboardShortcuts(java.util.List<KeyboardShortcutGroup> data, Menu menu, int deviceId);
    }

    public abstract void setContentView(View view);
    public abstract void setContentView(View view, ViewGroup.LayoutParams params);
    public abstract void addContentView(View view, ViewGroup.LayoutParams params);
    public abstract View getDecorView();
    public View peekDecorView() { return getDecorView(); }
    public abstract void addFlags(int flags);
    public abstract void clearFlags(int flags);
    public abstract void setFlags(int flags, int mask);
    public abstract WindowManager.LayoutParams getAttributes();
    public abstract void setAttributes(WindowManager.LayoutParams attrs);
    public abstract Callback getCallback();
    public abstract void setCallback(Callback callback);
    public abstract boolean requestFeature(int featureId);
    public View getCurrentFocus() { return null; }
    public void setSoftInputMode(int mode) {}
    public void setDecorFitsSystemWindows(boolean decorFitsSystemWindows) {}
    public void setNavigationBarContrastEnforced(boolean enforceContrast) {}
    private final WindowInsetsController insetsController = new WindowInsetsControllerImpl();
    public WindowInsetsController getInsetsController() { return insetsController; }
    public <T extends View> T findViewById(int id) { return null; }
    public abstract void setBackgroundDrawable(android.graphics.drawable.Drawable drawable);
    public void setStatusBarColor(int color) {}
    public void setNavigationBarColor(int color) {}
}
