// SPDX-License-Identifier: GPL-3.0-or-later
package android.inputmethodservice;

import android.content.Context;
import android.graphics.Canvas;
import android.util.AttributeSet;
import android.view.View;

@Deprecated
public class KeyboardView extends View {
    public interface OnKeyboardActionListener {
        void onPress(int primaryCode);
        void onRelease(int primaryCode);
        void onKey(int primaryCode, int[] keyCodes);
        void onText(CharSequence text);
        void swipeLeft();
        void swipeRight();
        void swipeDown();
        void swipeUp();
    }

    private Keyboard keyboard;
    private OnKeyboardActionListener listener;

    public KeyboardView(Context context, AttributeSet attrs) { super(context, attrs); }
    public KeyboardView(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setKeyboard(Keyboard keyboard) {
        this.keyboard = keyboard;
        requestLayout();
        invalidate();
    }

    public Keyboard getKeyboard() { return keyboard; }
    public void setOnKeyboardActionListener(OnKeyboardActionListener listener) { this.listener = listener; }
    public OnKeyboardActionListener getOnKeyboardActionListener() { return listener; }
    public void setPreviewEnabled(boolean enabled) {}
    public boolean isPreviewEnabled() { return false; }
    public void invalidateKey(int keyIndex) { invalidate(); }
    public void invalidateAllKeys() { invalidate(); }

    @Override
    protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
        int width = keyboard == null ? 0 : keyboard.getMinWidth();
        int height = keyboard == null ? 0 : keyboard.getHeight();
        setMeasuredDimension(resolveSize(width, widthMeasureSpec), resolveSize(height, heightMeasureSpec));
    }

    @Override
    protected void onDraw(Canvas canvas) {}
}
