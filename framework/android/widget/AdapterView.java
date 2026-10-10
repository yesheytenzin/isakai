// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;

/** Adapter-bound view group with selection state. */
public abstract class AdapterView<T extends Adapter> extends android.view.ViewGroup {
    public interface OnItemClickListener {
        void onItemClick(AdapterView<?> parent, View view, int position, long id);
    }

    public interface OnItemSelectedListener {
        void onItemSelected(AdapterView<?> parent, View view, int position, long id);
        void onNothingSelected(AdapterView<?> parent);
    }

    public static final int INVALID_POSITION = -1;
    public static final long INVALID_ROW_ID = Long.MIN_VALUE;

    private T adapter;
    private int selectedPosition = INVALID_POSITION;
    private OnItemClickListener clickListener;
    private OnItemSelectedListener selectedListener;

    public AdapterView(Context context) {
        super(context);
    }

    public AdapterView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public AdapterView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public T getAdapter() {
        return adapter;
    }

    public void setAdapter(T adapter) {
        this.adapter = adapter;
        selectedPosition = INVALID_POSITION;
        removeAllViews();
        requestLayout();
    }

    public int getCount() {
        return adapter == null ? 0 : adapter.getCount();
    }

    public int getSelectedItemPosition() {
        return selectedPosition;
    }

    public Object getSelectedItem() {
        if (adapter == null || selectedPosition == INVALID_POSITION) return null;
        return adapter.getItem(selectedPosition);
    }

    public long getSelectedItemId() {
        if (adapter == null || selectedPosition == INVALID_POSITION) return INVALID_ROW_ID;
        return adapter.getItemId(selectedPosition);
    }

    public View getSelectedView() {
        return null;
    }

    public void setSelection(int position) {
        if (adapter == null || position < 0 || position >= adapter.getCount()) {
            selectedPosition = INVALID_POSITION;
            if (selectedListener != null) selectedListener.onNothingSelected(this);
            return;
        }
        selectedPosition = position;
        if (selectedListener != null) {
            selectedListener.onItemSelected(this, null, position, adapter.getItemId(position));
        }
    }

    public void setOnItemClickListener(OnItemClickListener listener) {
        this.clickListener = listener;
    }

    public OnItemClickListener getOnItemClickListener() {
        return clickListener;
    }

    public void setOnItemSelectedListener(OnItemSelectedListener listener) {
        this.selectedListener = listener;
    }

    public boolean performItemClick(View view, int position, long id) {
        if (clickListener != null) {
            clickListener.onItemClick(this, view, position, id);
            return true;
        }
        return false;
    }

    public int getPositionForView(View view) {
        return INVALID_POSITION;
    }

    public long getItemIdAtPosition(int position) {
        if (adapter == null || position < 0 || position >= adapter.getCount()) return INVALID_ROW_ID;
        return adapter.getItemId(position);
    }

    public int getFirstVisiblePosition() {
        return 0;
    }

    public int getLastVisiblePosition() {
        return getCount() - 1;
    }
}
