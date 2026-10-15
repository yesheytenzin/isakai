// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;
import android.os.Bundle;
import android.view.ContextThemeWrapper;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.WindowManager;

/** Dialog window: content view plus cancel/dismiss hooks. */
public class Dialog implements android.content.DialogInterface {
    private final Context context;
    private View contentView;
    private boolean showing;
    private boolean cancelable = true;
    private boolean canceledOnTouchOutside = true;
    private android.content.DialogInterface.OnCancelListener cancelListener;
    private android.content.DialogInterface.OnDismissListener dismissListener;
    private android.content.DialogInterface.OnKeyListener keyListener;
    private android.app.Activity ownerActivity;

    public Dialog(Context context) {
        this.context = context;
    }

    public Dialog(Context context, int themeResId) {
        // AOSP parity: the dialog holds a themed context so that
        // defStyleAttr lookups (e.g. alertDialogStyle) resolve against
        // the passed theme layered over the base context theme.
        // Previously the id was discarded and AlertController layouts
        // resolved to 0.
        this.context = themeResId == 0 ? context : new ContextThemeWrapper(context, themeResId);
    }

    public Context getContext() {
        return context;
    }

    /**
     * Headless decor/callback storage (no compositor link), mirroring the
     * activity window: AppCompat installs its delegate callback and content
     * view here. Previously null, which crashed delegate construction
     * ("We have not been given a Window") for any dialog.
     */
    private final HeadlessWindow window = new HeadlessWindow();

    {
        // AppCompat wraps the window callback at delegate construction and
        // rejects a null callback. Route key/touch dispatch to the dialog;
        // menu/panel/focus hooks keep inert defaults (dialogs are not
        // compositor windows here).
        final Dialog self = this;
        window.setCallback(new Window.Callback() {
            @Override public boolean dispatchKeyEvent(KeyEvent event) {
                return self.dispatchKeyEvent(event);
            }
            @Override public boolean dispatchKeyShortcutEvent(KeyEvent event) { return false; }
            @Override public boolean dispatchTouchEvent(MotionEvent event) {
                return self.onTouchEvent(event);
            }
            @Override public boolean dispatchTrackballEvent(MotionEvent event) { return false; }
            @Override public boolean dispatchGenericMotionEvent(MotionEvent event) { return false; }
            @Override public boolean dispatchPopulateAccessibilityEvent(
                    android.view.accessibility.AccessibilityEvent event) { return false; }
            @Override public View onCreatePanelView(int featureId) { return null; }
            @Override public boolean onCreatePanelMenu(int featureId, android.view.Menu menu) {
                return false;
            }
            @Override public boolean onPreparePanel(int featureId, View view, android.view.Menu menu) {
                return false;
            }
            @Override public boolean onMenuOpened(int featureId, android.view.Menu menu) {
                return false;
            }
            @Override public void onPanelClosed(int featureId, android.view.Menu menu) {}
            @Override public boolean onMenuItemSelected(int featureId, android.view.MenuItem item) {
                return false;
            }
            @Override public void onWindowAttributesChanged(WindowManager.LayoutParams attrs) {}
            @Override public void onContentChanged() {}
            @Override public void onWindowFocusChanged(boolean hasFocus) {}
            @Override public void onAttachedToWindow() {}
            @Override public void onDetachedFromWindow() {}
            @Override public void onPointerCaptureChanged(boolean hasCapture) {}
            @Override public boolean onSearchRequested() { return false; }
            @Override public boolean onSearchRequested(android.view.SearchEvent searchEvent) {
                return false;
            }
            @Override public android.view.ActionMode onWindowStartingActionMode(
                    android.view.ActionMode.Callback callback) { return null; }
            @Override public android.view.ActionMode onWindowStartingActionMode(
                    android.view.ActionMode.Callback callback, int type) { return null; }
            @Override public void onActionModeStarted(android.view.ActionMode mode) {}
            @Override public void onActionModeFinished(android.view.ActionMode mode) {}
            @Override public void onProvideKeyboardShortcuts(
                    java.util.List<android.view.KeyboardShortcutGroup> data,
                    android.view.Menu menu, int deviceId) {}
        });
    }

    public Window getWindow() {
        return window;
    }

    public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher() {
        return backDispatcher;
    }

    /**
     * Dialog-scoped back dispatcher. Registered callbacks are retained;
     * like the activity dispatcher, registration itself must never fail.
     */
    private final DialogBackDispatcher backDispatcher = new DialogBackDispatcher();

    private static final class DialogBackDispatcher
            implements android.window.OnBackInvokedDispatcher {
        private final java.util.ArrayList<android.window.OnBackInvokedCallback> callbacks =
                new java.util.ArrayList<>();
        @Override
        public synchronized void registerOnBackInvokedCallback(int priority,
                android.window.OnBackInvokedCallback callback) {
            if (callback == null) throw new NullPointerException("callback");
            callbacks.add(callback);
        }
        @Override
        public synchronized void unregisterOnBackInvokedCallback(
                android.window.OnBackInvokedCallback callback) {
            callbacks.remove(callback);
        }
    }

    public void setOwnerActivity(android.app.Activity activity) {
        this.ownerActivity = activity;
    }

    public android.app.Activity getOwnerActivity() {
        return ownerActivity;
    }

    public void setContentView(View view) {
        this.contentView = view;
    }

    public void setContentView(View view, ViewGroup.LayoutParams params) {
        this.contentView = view;
    }

    public void setContentView(int layoutResId) {}

    public void addContentView(View view, ViewGroup.LayoutParams params) {
        this.contentView = view;
    }

    public View findViewById(int id) {
        // AOSP searches the window decor; AppCompat installs dialog content
        // there rather than through setContentView. Keep the field as a
        // fallback for content set directly on the dialog.
        android.view.View decor = window.getDecorView();
        android.view.View found = decor == null ? null : decor.findViewById(id);
        if (found != null) return found;
        return contentView == null ? null : contentView.findViewById(id);
    }

    public void setTitle(CharSequence title) {}
    public void setTitle(int titleId) {}

    public boolean requestWindowFeature(int featureId) {
        return false;
    }

    public void setCancelable(boolean cancelable) {
        this.cancelable = cancelable;
    }

    public void setCanceledOnTouchOutside(boolean cancel) {
        this.canceledOnTouchOutside = cancel;
    }

    public void setOnCancelListener(android.content.DialogInterface.OnCancelListener listener) {
        this.cancelListener = listener;
    }

    public void setOnDismissListener(android.content.DialogInterface.OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public void setOnKeyListener(android.content.DialogInterface.OnKeyListener listener) {
        this.keyListener = listener;
    }

    protected void onCreate(Bundle savedInstanceState) {}
    protected void onStart() {}
    protected void onStop() {}
    public Bundle onSaveInstanceState() { return new Bundle(); }
    public void onRestoreInstanceState(Bundle savedInstanceState) {}

    private boolean created;

    public void show() {
        // AOSP parity: content (including AlertController buttons) is
        // installed in onCreate, which runs once before the first show.
        if (!created) {
            created = true;
            onCreate(null);
        }
        showing = true;
        onStart();
    }

    public void hide() {
        showing = false;
    }

    @Override
    public void dismiss() {
        if (!showing) return;
        showing = false;
        onStop();
        if (dismissListener != null) dismissListener.onDismiss(this);
    }

    @Override
    public void cancel() {
        if (cancelListener != null) cancelListener.onCancel(this);
        dismiss();
    }

    public boolean isShowing() {
        return showing;
    }

    public void onBackPressed() {
        if (cancelable) cancel();
    }

    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onKeyUp(int keyCode, KeyEvent event) {
        return false;
    }

    public boolean onTouchEvent(MotionEvent event) {
        return false;
    }

    public boolean dispatchKeyEvent(KeyEvent event) {
        if (keyListener != null && keyListener.onKey(this, event.getKeyCode(), event)) return true;
        if (event.getAction() == KeyEvent.ACTION_DOWN) return onKeyDown(event.getKeyCode(), event);
        return onKeyUp(event.getKeyCode(), event);
    }
}
