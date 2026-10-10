// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.view.View;

/** Spinner: dropdown selection over an adapter. */
public class Spinner extends AdapterView<SpinnerAdapter> {
    private SpinnerAdapter adapter;
    private CharSequence prompt;
    private int dropDownWidth;
    private int dropDownHorizontalOffset;
    private int dropDownVerticalOffset;
    private android.graphics.drawable.Drawable popupBackground;

    public Spinner(Context context) {
        super(context);
    }

    public Spinner(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public Spinner(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setAdapter(SpinnerAdapter adapter) {
        this.adapter = adapter;
        super.setAdapter(adapter);
    }

    @Override
    public SpinnerAdapter getAdapter() {
        return adapter;
    }

    public void setPrompt(CharSequence prompt) {
        this.prompt = prompt;
    }

    public CharSequence getPrompt() {
        return prompt;
    }

    public void setDropDownWidth(int width) {
        this.dropDownWidth = width;
    }

    public int getDropDownWidth() {
        return dropDownWidth;
    }

    public void setDropDownHorizontalOffset(int offset) {
        this.dropDownHorizontalOffset = offset;
    }

    public int getDropDownHorizontalOffset() {
        return dropDownHorizontalOffset;
    }

    public void setDropDownVerticalOffset(int offset) {
        this.dropDownVerticalOffset = offset;
    }

    public int getDropDownVerticalOffset() {
        return dropDownVerticalOffset;
    }

    public void setPopupBackgroundDrawable(android.graphics.drawable.Drawable background) {
        this.popupBackground = background;
    }

    public android.graphics.drawable.Drawable getPopupBackground() {
        return popupBackground;
    }

    @Override
    public boolean performClick() {
        return super.performClick();
    }
}
