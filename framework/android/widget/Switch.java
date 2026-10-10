// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/** Switch: two-state toggle with text labels. */
public class Switch extends CompoundButton {
    private CharSequence textOn;
    private CharSequence textOff;

    public Switch(Context context) {
        super(context);
    }

    public Switch(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public Switch(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setTextOn(CharSequence textOn) {
        this.textOn = textOn;
    }

    public CharSequence getTextOn() {
        return textOn;
    }

    public void setTextOff(CharSequence textOff) {
        this.textOff = textOff;
    }

    public CharSequence getTextOff() {
        return textOff;
    }
}
