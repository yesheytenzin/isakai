// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.graphics.BitmapFont;
import android.graphics.Canvas;
import android.graphics.Color;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.View;

/**
 * Minimal TextView: text drawn over the view background at view-relative
 * (4,4) padding, word-wrapped to the view width. Wrapping is greedy per
 * word at the BitmapFont cell width; a word wider than the line still
 * advances to the next line and overflows.
 */
public class TextView extends View {
    private CharSequence text = "";
    private int textColor = Color.BLACK;
    private int textSize = 14;

    public TextView() {
        super();
    }

    public TextView(android.content.Context context) {
        super(context);
    }

    public TextView(android.content.Context context, android.util.AttributeSet attrs) {
        super(context, attrs);
    }

    public TextView(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    public void setTextSize(int unit, float size) {
        setTextSize((int) size);
    }

    public void setTextColor(android.content.res.ColorStateList colors) {
        if (colors != null) setTextColor(colors.getDefaultColor());
    }

    public android.text.TextPaint getPaint() {
        android.text.TextPaint paint = new android.text.TextPaint();
        paint.setColor(textColor);
        paint.setTextSize(textSize);
        return paint;
    }

    public void setPaintFlags(int flags) {}
    public int getPaintFlags() { return 0; }

    public void setAllCaps(boolean allCaps) {}
    public void setSingleLine(boolean singleLine) {}
    public void setSingleLine() { setSingleLine(true); }
    public void setLines(int lines) {}
    public void setMaxLines(int maxLines) {}
    public int getMaxLines() { return Integer.MAX_VALUE; }
    public void setMinLines(int minLines) {}
    public void setMinWidth(int minPixels) {}
    public void setMaxWidth(int maxPixels) {}
    public void setMinHeight(int minPixels) {}
    public void setMaxHeight(int maxPixels) {}
    public void setMinEms(int minEms) {}
    public void setMaxEms(int maxEms) {}
    public void setEms(int ems) {}
    public void setWidth(int pixels) {}
    public void setHeight(int pixels) {}
    public void setEllipsize(android.text.TextUtils.TruncateAt where) {}
    public void setGravity(int gravity) {}
    public int getGravity() { return 0; }
    public void setLineSpacing(float add, float mult) {}
    public void setIncludeFontPadding(boolean include) {}
    public void setCompoundDrawables(android.graphics.drawable.Drawable l,
            android.graphics.drawable.Drawable t, android.graphics.drawable.Drawable r,
            android.graphics.drawable.Drawable b) {}
    public void setCompoundDrawablesWithIntrinsicBounds(int l, int t, int r, int b) {}
    public void setCompoundDrawablesWithIntrinsicBounds(android.graphics.drawable.Drawable l,
            android.graphics.drawable.Drawable t, android.graphics.drawable.Drawable r,
            android.graphics.drawable.Drawable b) {}
    public void setCompoundDrawablesRelative(android.graphics.drawable.Drawable s,
            android.graphics.drawable.Drawable t, android.graphics.drawable.Drawable e,
            android.graphics.drawable.Drawable b) {}
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(int s, int t, int e, int b) {}
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(android.graphics.drawable.Drawable s,
            android.graphics.drawable.Drawable t, android.graphics.drawable.Drawable e,
            android.graphics.drawable.Drawable b) {}
    public void setFilters(android.text.InputFilter[] filters) {}
    public android.text.InputFilter[] getFilters() { return new android.text.InputFilter[0]; }
    public void setInputType(int type) {}
    public int getInputType() { return 0; }
    public void setRawInputType(int type) {}
    public void setShowSoftInputOnFocus(boolean show) {}
    public boolean getShowSoftInputOnFocus() { return true; }
    public void setTextIsSelectable(boolean selectable) {}
    private android.text.method.MovementMethod movementMethod;
    public void setMovementMethod(android.text.method.MovementMethod movement) {
        this.movementMethod = movement;
    }
    public android.text.method.MovementMethod getMovementMethod() {
        return movementMethod;
    }
    public void setHint(CharSequence hint) {}
    public CharSequence getHint() { return ""; }
    public void setHint(int resId) {}
    public void setText(int resId) {}
    public final void append(CharSequence text) {
        setText(textString() + (text == null ? "" : text.toString()));
    }
    public int length() { return textString().length(); }
    public boolean isTextSelectable() { return false; }
    public void setTextSelectable(boolean selectable) {}
    private int selectionStart;
    private int selectionEnd;

    public int getSelectionStart() { return selectionStart; }
    public int getSelectionEnd() { return selectionEnd; }
    public boolean hasSelection() { return selectionStart != selectionEnd; }

    public void setSelection(int start, int stop) {
        int len = textString().length();
        selectionStart = Math.max(0, Math.min(start, len));
        selectionEnd = Math.max(0, Math.min(stop, len));
    }

    public void setSelection(int index) {
        setSelection(index, index);
    }

    public void selectAll() {
        setSelection(0, textString().length());
    }

    public void setCustomSelectionActionModeCallback(android.view.ActionMode.Callback callback) {}
    public android.view.ActionMode.Callback getCustomSelectionActionModeCallback() { return null; }

    private final java.util.ArrayList<android.text.TextWatcher> watchers =
            new java.util.ArrayList<>();

    public void addTextChangedListener(android.text.TextWatcher watcher) {
        if (watcher != null && !watchers.contains(watcher)) watchers.add(watcher);
    }

    public void removeTextChangedListener(android.text.TextWatcher watcher) {
        watchers.remove(watcher);
    }

    public void setText(CharSequence text) {
        CharSequence old = this.text;
        CharSequence next = text != null ? text : "";
        for (android.text.TextWatcher w : watchers) {
            w.beforeTextChanged(old, 0, old.length(), next.length());
        }
        this.text = next;
        for (android.text.TextWatcher w : watchers) {
            w.onTextChanged(next, 0, old.length(), next.length());
        }
        android.text.Editable editable = new android.text.Editable(next.toString());
        for (android.text.TextWatcher w : watchers) {
            w.afterTextChanged(editable);
        }
        invalidate();
    }


    public void setText(String text) {
        setText((CharSequence) text);
    }

    public CharSequence getText() {
        return text;
    }

    public void setTextColor(int color) {
        this.textColor = color;
    }

    public int getTextColor() {
        return textColor;
    }

    public void setTextSize(int size) {
        this.textSize = size;
    }

    public int getTextSize() {
        return textSize;
    }

    private String textString() {
        return text != null ? text.toString() : "";
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawTextAt(canvas, textString(), 4, 4, textSize, textColor);
    }

    /** Shared text renderer used by subclasses (cursor, labels). */
    protected final void drawTextAt(Canvas canvas, String s, int dx, int dy, int size, int color) {
        if (s == null || s.isEmpty()) return;
        int cell = BitmapFont.cellWidth(size);
        int lineStep = BitmapFont.lineStep(size);
        int x0 = getLeft() + dx;
        int y0 = getTop() + dy;
        int maxX = getRight() - 4;
        if (maxX <= x0) maxX = x0 + cell;
        int cx = x0;
        int cy = y0;
        String[] words = s.split(" ", -1);
        for (int wi = 0; wi < words.length; wi++) {
            String word = words[wi];
            // Explicit newlines inside the word: draw each segment.
            String[] parts = word.split("\n", -1);
            for (int pi = 0; pi < parts.length; pi++) {
                if (pi > 0) {
                    cx = x0;
                    cy += lineStep;
                }
                String part = parts[pi];
                if (!part.isEmpty()) {
                    int w = part.length() * cell;
                    if (cx + w > maxX && cx > x0) {
                        cx = x0;
                        cy += lineStep;
                    }
                    canvas.drawText(part, cx, cy, size, color);
                    cx += w;
                }
            }
            if (wi + 1 < words.length) {
                if (cx + cell > maxX && cx > x0) {
                    cx = x0;
                    cy += lineStep;
                } else {
                    cx += cell;
                }
            }
        }
    }

    /** Cell step used by subclasses for cursor/scroll math. */
    protected final int cellStep() {
        return BitmapFont.cellWidth(textSize);
    }
}
