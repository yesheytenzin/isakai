// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/** Chronometer: elapsed-time text display (no ticking loop headless). */
public class Chronometer extends TextView {
    private long base;
    private boolean started;

    public Chronometer(Context context) {
        super(context);
    }

    public Chronometer(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public Chronometer(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setBase(long base) {
        this.base = base;
    }

    public long getBase() {
        return base;
    }

    public void start() {
        started = true;
    }

    public void stop() {
        started = false;
    }

    public boolean isStarted() {
        return started;
    }
}
