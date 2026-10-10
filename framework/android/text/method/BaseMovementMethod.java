// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.method;

import android.text.Spannable;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.widget.TextView;

/** Base movement: no-op navigation (headless has no cursor keys). */
public class BaseMovementMethod implements MovementMethod {
    @Override
    public void initialize(TextView widget, Spannable text) {}

    @Override
    public boolean onKeyDown(TextView widget, Spannable text, int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyUp(TextView widget, Spannable text, int keyCode, KeyEvent event) {
        return false;
    }

    @Override
    public boolean onKeyOther(TextView view, Spannable text, KeyEvent event) {
        return false;
    }

    @Override
    public void onTakeFocus(TextView widget, Spannable text, int direction) {}

    @Override
    public boolean onTouchEvent(TextView widget, Spannable text, MotionEvent event) {
        return false;
    }

    @Override
    public boolean onTrackballEvent(TextView widget, Spannable text, MotionEvent event) {
        return false;
    }

    @Override
    public boolean onGenericMotionEvent(TextView widget, Spannable text, MotionEvent event) {
        return false;
    }

    @Override
    public boolean canSelectArbitrarily() {
        return false;
    }

    protected boolean left(TextView widget, Spannable buffer) { return false; }
    protected boolean right(TextView widget, Spannable buffer) { return false; }
    protected boolean up(TextView widget, Spannable buffer) { return false; }
    protected boolean down(TextView widget, Spannable buffer) { return false; }
    protected boolean pageUp(TextView widget, Spannable buffer) { return false; }
    protected boolean pageDown(TextView widget, Spannable buffer) { return false; }
    protected boolean top(TextView widget, Spannable buffer) { return false; }
    protected boolean bottom(TextView widget, Spannable buffer) { return false; }
    protected boolean lineStart(TextView widget, Spannable buffer) { return false; }
    protected boolean lineEnd(TextView widget, Spannable buffer) { return false; }
}
