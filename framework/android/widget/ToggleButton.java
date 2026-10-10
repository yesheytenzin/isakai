// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/** On/off toggle button with text labels. */
public class ToggleButton extends CompoundButton {
    private CharSequence textOn = "ON";
    private CharSequence textOff = "OFF";

    public ToggleButton(Context context) {
        super(context);
        syncLabel();
    }

    public ToggleButton(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
        syncLabel();
    }

    public ToggleButton(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        syncLabel();
    }

    private void syncLabel() {
        setText(isChecked() ? textOn : textOff);
    }

    @Override
    public void setChecked(boolean checked) {
        super.setChecked(checked);
        syncLabel();
    }

    public void setTextOn(CharSequence textOn) {
        this.textOn = textOn;
        syncLabel();
    }

    public void setTextOff(CharSequence textOff) {
        this.textOff = textOff;
        syncLabel();
    }
}
