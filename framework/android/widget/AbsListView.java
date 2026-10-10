// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;
import android.view.View;

/** Scrollable list base: renders visible rows, selection optional. */
public abstract class AbsListView extends AdapterView<ListAdapter> {
    public static final int CHOICE_MODE_NONE = 0;
    public static final int CHOICE_MODE_SINGLE = 1;
    public static final int CHOICE_MODE_MULTIPLE = 2;

    public interface SelectionBoundsAdjuster {
        void adjustListItemSelectionBounds(android.graphics.Rect bounds);
    }

    public interface OnScrollListener {
        int SCROLL_STATE_IDLE = 0;
        int SCROLL_STATE_TOUCH_SCROLL = 1;
        int SCROLL_STATE_FLING = 2;

        void onScrollStateChanged(AbsListView view, int scrollState);
        void onScroll(AbsListView view, int firstVisibleItem, int visibleItemCount, int totalItemCount);
    }

    private int choiceMode = CHOICE_MODE_NONE;
    private final java.util.HashSet<Integer> checked = new java.util.HashSet<>();
    private Drawable selector;
    private OnScrollListener scrollListener;
    private int firstVisible;

    public AbsListView(Context context) {
        super(context);
    }

    public AbsListView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public AbsListView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setChoiceMode(int mode) {
        this.choiceMode = mode;
        if (mode == CHOICE_MODE_NONE) checked.clear();
    }

    public int getChoiceMode() {
        return choiceMode;
    }

    public void setItemChecked(int position, boolean checked) {
        if (this.checked == null) return;
        if (checked) this.checked.add(position);
        else this.checked.remove(position);
    }

    public boolean isItemChecked(int position) {
        return checked.contains(position);
    }

    public void clearChoices() {
        checked.clear();
    }

    public void setSelector(Drawable selector) {
        this.selector = selector;
    }

    public Drawable getSelector() {
        return selector;
    }

    public void setCacheColorHint(int color) {}
    public void setSelectedChildViewEnabled(boolean enabled) {}
    public boolean isSelectedChildViewEnabled() { return false; }

    public void setOnScrollListener(OnScrollListener listener) {
        this.scrollListener = listener;
    }

    @Override
    public int getFirstVisiblePosition() {
        return firstVisible;
    }

    public int getListPaddingTop() { return getPaddingTop(); }
    public int getListPaddingBottom() { return getPaddingBottom(); }

    public int pointToPosition(int x, int y) {
        return INVALID_POSITION;
    }

    public void setSelectionFromTop(int position, int y) {
        setSelection(position);
        firstVisible = Math.max(0, position);
    }

    public void scrollListBy(int amount) {}

    public void layoutChildren() {
        requestLayout();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        return super.onTouchEvent(event);
    }

    public void smoothScrollToPosition(int position) {
        setSelection(position);
    }
}
