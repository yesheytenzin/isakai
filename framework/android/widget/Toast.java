// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/**
 * Toast: transient message (headless: logged, no popup surface).
 */
public class Toast {
    public static final int LENGTH_SHORT = 0;
    public static final int LENGTH_LONG = 1;

    private final Context context;
    private CharSequence text;
    private int duration = LENGTH_SHORT;
    private android.view.View view;

    public Toast(Context context) {
        this.context = context;
    }

    public static Toast makeText(Context context, CharSequence text, int duration) {
        Toast t = new Toast(context);
        t.text = text;
        t.duration = duration;
        return t;
    }

    public static Toast makeText(Context context, int resId, int duration) {
        CharSequence text = "";
        try {
            text = context == null ? "" : context.getResources().getText(resId);
        } catch (RuntimeException e) {
            text = "";
        }
        return makeText(context, text, duration);
    }

    public void show() {
        android.util.Log.i("Toast", text == null ? "" : text.toString());
    }

    public void cancel() {}

    public void setText(CharSequence text) {
        this.text = text;
    }

    public void setText(int resId) {
        try {
            this.text = context == null ? "" : context.getResources().getText(resId);
        } catch (RuntimeException e) {
            this.text = "";
        }
    }

    public void setDuration(int duration) {
        this.duration = duration;
    }

    public int getDuration() {
        return duration;
    }

    public void setView(android.view.View view) {
        this.view = view;
    }

    public android.view.View getView() {
        return view;
    }

    public void setGravity(int gravity, int xOffset, int yOffset) {}
}
