// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/** DateTimeView: static date/time text (internal widget, text only). */
public class DateTimeView extends TextView {
    public DateTimeView(Context context) {
        super(context);
    }

    public DateTimeView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public void setTime(long time) {
        setText(String.valueOf(time));
    }
}
