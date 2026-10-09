// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.graphics.BitmapFont;
import android.graphics.Canvas;
import android.graphics.Color;
import android.text.Editable;
import android.view.KeyEvent;
import android.view.MotionEvent;

/**
 * Minimal single-line EditText: an Editable buffer with a cursor,
 * printable-key insert, DEL/BACKSPACE delete-before-cursor, DPAD move,
 * ENTER clears focus. No multiline, no selection, no IME in M3.
 */
public class EditText extends TextView {
    private final Editable editable = new Editable();
    private int cursor = 0;
    private int scrollX = 0;

    public EditText() {
        super();
        setFocusable(true);
        setClickable(true);
    }

    @Override
    public void setText(CharSequence text) {
        editable.replaceAll(text);
        cursor = editable.length();
        scrollX = 0;
        super.setText((CharSequence) editable.toString());
    }

    public void setText(String text) {
        setText((CharSequence) text);
    }

    @Override
    public Editable getText() {
        return editable;
    }

    public int getSelectionStart() {
        return cursor;
    }

    public int getSelectionEnd() {
        return cursor;
    }

    public void setSelection(int index) {
        cursor = Math.max(0, Math.min(index, editable.length()));
        ensureCursorVisible();
        invalidate();
    }

    @Override
    public boolean onTouchEvent(MotionEvent event) {
        if (!isEnabled()) return false;
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_DOWN
                && containsPoint(event.getX(), event.getY())) {
            requestFocus();
            return true;
        }
        return super.onTouchEvent(event);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        if (!isEnabled() || !isFocused()) return false;
        switch (keyCode) {
            case KeyEvent.KEYCODE_DEL:
                if (cursor > 0) {
                    editable.delete(cursor - 1, cursor);
                    cursor--;
                    refresh();
                }
                return true;
            case KeyEvent.KEYCODE_FORWARD_DEL:
                if (cursor < editable.length()) {
                    editable.delete(cursor, cursor + 1);
                    refresh();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_LEFT:
            case KeyEvent.KEYCODE_MOVE_HOME:
                if (cursor > 0) {
                    cursor--;
                    ensureCursorVisible();
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_DPAD_RIGHT:
            case KeyEvent.KEYCODE_MOVE_END:
                if (cursor < editable.length()) {
                    cursor++;
                    ensureCursorVisible();
                    invalidate();
                }
                return true;
            case KeyEvent.KEYCODE_ENTER:
            case KeyEvent.KEYCODE_NUMPAD_ENTER:
            case KeyEvent.KEYCODE_DPAD_CENTER:
                clearFocus();
                return true;
            default:
                break;
        }
        if (event.getAction() == KeyEvent.ACTION_DOWN) {
            int c = event.getUnicodeChar();
            if (c >= 32 && c != 127) {
                editable.insert(cursor, String.valueOf((char) c));
                cursor++;
                refresh();
                return true;
            }
        }
        return false;
    }

    private void refresh() {
        ensureCursorVisible();
        super.setText(editable.toString());
    }

    private void ensureCursorVisible() {
        int cell = cellStep();
        int inner = getWidth() - 8;
        if (inner <= 0 || cell <= 0) {
            scrollX = 0;
            return;
        }
        int cursorX = cursor * cell - scrollX;
        if (cursorX < 0) {
            scrollX = cursor * cell;
        } else if (cursorX >= inner) {
            scrollX = cursor * cell - inner + cell;
        }
        if (scrollX < 0) scrollX = 0;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        // TextView.onDraw would draw the CharSequence copy; draw the live
        // buffer with the horizontal scroll instead.
        canvas.drawColor(backgroundColor);
        String s = editable.toString();
        int from = 0;
        int cell = cellStep();
        if (cell > 0 && scrollX > 0) {
            from = scrollX / cell;
            if (from < 0) from = 0;
            if (from > s.length()) from = s.length();
        }
        int dx = 4 - (scrollX - from * cell);
        drawTextAt(canvas, from == 0 ? s : s.substring(from), dx, 4, getTextSize(), getTextColor());
        if (isFocused()) {
            int cx = getLeft() + 4 + cursor * cell - scrollX;
            int top = getTop() + 4;
            int scale = BitmapFont.scaleFor(getTextSize());
            canvas.fillRect(cx, top, cx + scale, top + BitmapFont.HEIGHT * scale, Color.BLACK);
        }
    }
}
