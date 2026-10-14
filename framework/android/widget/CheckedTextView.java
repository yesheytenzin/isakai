// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.drawable.Drawable;

/** Checkable text row with a check-mark drawable. */
public class CheckedTextView extends TextView implements Checkable {
    private boolean checked;
    private Drawable checkMark;

    public CheckedTextView(Context context) {
        super(context);
    }

    public CheckedTextView(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public CheckedTextView(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @Override
    public boolean isChecked() {
        return checked;
    }

    @Override
    public void setChecked(boolean checked) {
        this.checked = checked;
        invalidate();
    }

    @Override
    public void toggle() {
        setChecked(!checked);
    }

    public void setCheckMarkDrawable(Drawable drawable) {
        this.checkMark = drawable;
        invalidate();
    }

    public Drawable getCheckMarkDrawable() {
        return checkMark;
    }

    public void setCheckMarkTintList(android.content.res.ColorStateList tint) {}
    public void setCheckMarkTintMode(android.graphics.PorterDuff.Mode mode) {}

    public void setTextAppearance(Context context, int resId) {}

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        if (checkMark != null && checked && canvas != null) {
            int size = Math.min(getWidth(), getHeight());
            checkMark.setBounds(getRight() - size, getTop(), getRight(), getTop() + size);
            checkMark.draw(canvas);
        }
    }

    @Override
    protected void onDrawAt(Canvas canvas, int offsetX, int offsetY) {
        super.onDrawAt(canvas, offsetX, offsetY);
        if (checkMark != null && checked && canvas != null) {
            int size = Math.min(getWidth(), getHeight());
            checkMark.setBounds(offsetX + getWidth() - size, offsetY, offsetX + getWidth(), offsetY + size);
            checkMark.draw(canvas);
        }
    }
}
