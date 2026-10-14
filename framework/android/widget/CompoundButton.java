// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Rect;
import android.graphics.drawable.Drawable;
import android.view.MotionEvent;

/** Toggle button: checked state plus button drawable. */
public class CompoundButton extends TextView {
    public interface OnCheckedChangeListener {
        void onCheckedChanged(CompoundButton buttonView, boolean isChecked);
    }

    private boolean checked;
    private Drawable buttonDrawable;
    private OnCheckedChangeListener listener;
    private boolean broadcasting;

    public CompoundButton(Context context) {
        super(context);
    }

    public CompoundButton(Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public CompoundButton(Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public boolean isChecked() {
        return checked;
    }

    public void setChecked(boolean checked) {
        if (this.checked == checked) return;
        this.checked = checked;
        invalidate();
        if (broadcasting) return;
        broadcasting = true;
        try {
            if (listener != null) listener.onCheckedChanged(this, checked);
        } finally {
            broadcasting = false;
        }
    }

    public void toggle() {
        setChecked(!checked);
    }

    public void setOnCheckedChangeListener(OnCheckedChangeListener listener) {
        this.listener = listener;
    }

    public void setButtonDrawable(Drawable drawable) {
        this.buttonDrawable = drawable;
        invalidate();
    }

    public Drawable getButtonDrawable() {
        return buttonDrawable;
    }

    public void setButtonTintList(android.content.res.ColorStateList tint) {}
    public android.content.res.ColorStateList getButtonTintList() { return null; }
    public void setButtonTintMode(android.graphics.PorterDuff.Mode mode) {}
    public android.graphics.PorterDuff.Mode getButtonTintMode() { return null; }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        if (event.getActionMasked() == MotionEvent.ACTION_UP
                && containsPoint(event.getX(), event.getY())) {
            toggle();
            performClick();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        if (buttonDrawable != null && canvas != null) {
            int size = Math.min(getWidth(), getHeight());
            buttonDrawable.setBounds(getLeft(), getTop(), getLeft() + size, getTop() + size);
            buttonDrawable.draw(canvas);
        }
        super.onDraw(canvas);
    }

    @Override
    protected void onDrawAt(Canvas canvas, int offsetX, int offsetY) {
        if (buttonDrawable != null && canvas != null) {
            int size = Math.min(getWidth(), getHeight());
            buttonDrawable.setBounds(offsetX, offsetY, offsetX + size, offsetY + size);
            buttonDrawable.draw(canvas);
        }
        super.onDraw(canvas);
    }

    public int getCompoundPaddingLeft() { return getPaddingLeft(); }
    public int getCompoundPaddingRight() { return getPaddingRight(); }
}
