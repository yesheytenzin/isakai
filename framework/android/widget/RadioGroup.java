// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;

/** Exclusive-check group: id dispatch to listener. */
public class RadioGroup extends LinearLayout {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(RadioGroup group, int checkedId);
    }
    private int checkedId = View.NO_ID;
    private OnCheckedChangeListener listener;
    private boolean suppress;
    public RadioGroup(Context context) { super(context); }
    public RadioGroup(Context context, android.util.AttributeSet attrs) { super(context, attrs); }
    public void check(int id) {
        if (checkedId == id) return;
        checkedId = id;
        if (!suppress && listener != null) listener.onCheckedChanged(this, id);
    }
    public int getCheckedRadioButtonId() { return checkedId; }
    public void clearCheck() { check(View.NO_ID); }
    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) { this.listener = listener; }
}
