// SPDX-License-Identifier: GPL-3.0-or-later
package android.app;

import android.content.Context;
import android.os.Bundle;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

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
        this.context = context;
    }

    public Context getContext() {
        return context;
    }

    public Window getWindow() {
        return null;
    }

    public android.window.OnBackInvokedDispatcher getOnBackInvokedDispatcher() {
        return null;
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

    public void show() {
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
