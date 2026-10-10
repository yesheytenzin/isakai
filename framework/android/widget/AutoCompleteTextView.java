// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;

/** Text completion field: adapter plus threshold, no popup. */
public class AutoCompleteTextView extends EditText {
    public interface OnDismissListener {
        void onDismiss();
    }

    private ListAdapter adapter;
    private int threshold = 2;
    private OnDismissListener dismissListener;

    public AutoCompleteTextView(Context context) {
        super(context);
    }

    public AutoCompleteTextView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public AutoCompleteTextView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAdapter(ListAdapter adapter) {
        this.adapter = adapter;
    }

    public ListAdapter getAdapter() {
        return adapter;
    }

    public void setThreshold(int threshold) {
        this.threshold = Math.max(1, threshold);
    }

    public int getThreshold() {
        return threshold;
    }

    public boolean enoughToFilter() {
        return length() >= threshold;
    }

    public boolean isPopupShowing() {
        return false;
    }

    public void showDropDown() {}
    public void dismissDropDown() {
        if (dismissListener != null) dismissListener.onDismiss();
    }

    public void setOnDismissListener(OnDismissListener listener) {
        this.dismissListener = listener;
    }

    public CharSequence convertSelectionToString(Object selectedItem) {
        return selectedItem == null ? "" : selectedItem.toString();
    }

    public android.graphics.drawable.Drawable getDropDownBackground() {
        return null;
    }
}
