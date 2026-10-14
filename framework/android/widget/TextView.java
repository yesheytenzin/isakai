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
 * word using host font advances; a word wider than the line still
 * advances to the next line and overflows.
 */
public class TextView extends View {
    public interface OnEditorActionListener {
        boolean onEditorAction(TextView view, int actionId, KeyEvent event);
    }
    private OnEditorActionListener editorActionListener;
    public void setOnEditorActionListener(OnEditorActionListener listener) { editorActionListener=listener; }
    public void onEditorAction(int actionCode) { dispatchEditorAction(actionCode,null); }
    protected final boolean dispatchEditorAction(int actionCode,KeyEvent event) {
        return editorActionListener!=null && editorActionListener.onEditorAction(this,actionCode,event);
    }
    private android.text.method.KeyListener keyListener;
    public android.text.method.KeyListener getKeyListener(){return keyListener;}
    public void setKeyListener(android.text.method.KeyListener listener){keyListener=listener;}
    private android.text.method.TransformationMethod transformationMethod;
    public void setTransformationMethod(android.text.method.TransformationMethod method) {
        transformationMethod=method;requestLayout();invalidate();
    }
    public android.text.method.TransformationMethod getTransformationMethod() { return transformationMethod; }
    protected android.text.Editable.Factory editableFactory=android.text.Editable.Factory.getInstance();
    public void setEditableFactory(android.text.Editable.Factory factory) {
        if(factory==null)throw new NullPointerException("factory");
        editableFactory=factory;
    }
    private CharSequence text = "";
    private int textColor = Color.BLACK;
    private float textSize = 14;
    private int minWidth, minHeight, minLines;
    private int maxWidth = Integer.MAX_VALUE, maxHeight = Integer.MAX_VALUE, maxLines = Integer.MAX_VALUE;
    private android.graphics.drawable.Drawable compoundLeft, compoundTop, compoundRight, compoundBottom;
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
        textSize=android.util.TypedValue.applyDimension(unit,size,getResources().getDisplayMetrics());
        requestLayout();invalidate();
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
    public void setMaxLines(int value) { maxLines = Math.max(0,value); requestLayout(); }
    public int getMaxLines() { return maxLines; }
    public void setMinLines(int value) { minLines = Math.max(0,value); requestLayout(); }
    public void setMinWidth(int value) { minWidth = Math.max(0,value); requestLayout(); }
    public void setMaxWidth(int value) { maxWidth = Math.max(0,value); requestLayout(); }
    public void setMinHeight(int value) { minHeight = Math.max(0,value); requestLayout(); }
    public void setMaxHeight(int value) { maxHeight = Math.max(0,value); requestLayout(); }
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
            android.graphics.drawable.Drawable b) {
        compoundLeft = l; compoundTop = t; compoundRight = r; compoundBottom = b;
        requestLayout(); invalidate();
    }
    public void setCompoundDrawablesWithIntrinsicBounds(int l, int t, int r, int b) {}
    public void setCompoundDrawablesWithIntrinsicBounds(android.graphics.drawable.Drawable l,
            android.graphics.drawable.Drawable t, android.graphics.drawable.Drawable r,
            android.graphics.drawable.Drawable b) {
        setCompoundDrawables(l, t, r, b);
    }
    public void setCompoundDrawablesRelative(android.graphics.drawable.Drawable s,
            android.graphics.drawable.Drawable t, android.graphics.drawable.Drawable e,
            android.graphics.drawable.Drawable b) {
        setCompoundDrawables(s, t, e, b);
    }
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(int s, int t, int e, int b) {}
    public void setCompoundDrawablesRelativeWithIntrinsicBounds(android.graphics.drawable.Drawable s,
            android.graphics.drawable.Drawable t, android.graphics.drawable.Drawable e,
            android.graphics.drawable.Drawable b) {
        setCompoundDrawables(s, t, e, b);
    }
    public android.graphics.drawable.Drawable[] getCompoundDrawables() {
        return new android.graphics.drawable.Drawable[]{compoundLeft, compoundTop, compoundRight, compoundBottom};
    }
    public void setFilters(android.text.InputFilter[] filters) {}
    public android.text.InputFilter[] getFilters() { return new android.text.InputFilter[0]; }
    private boolean cursorVisible = true;
    public void setCursorVisible(boolean visible) {
        if (cursorVisible != visible) { cursorVisible = visible; invalidate(); }
    }
    public boolean isCursorVisible() { return cursorVisible; }
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
    public void setText(int resId) { setText(getResources().getText(resId)); }
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
        if (!watchers.isEmpty()) {
            CharSequence current = getText();
            android.text.Editable editable = current instanceof android.text.Editable
                    ? (android.text.Editable) current : editableFactory.newEditable(next);
            for (android.text.TextWatcher w : watchers) w.afterTextChanged(editable);
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

    public android.content.res.ColorStateList getTextColors() { return android.content.res.ColorStateList.valueOf(textColor); }
    public int getCurrentTextColor() { return textColor; }
    public int getTextColor() {
        return textColor;
    }

    public void setTextSize(int size) { setTextSize((float)size); }
    public void setTextSize(float size) { setTextSize(2,size); }
    public float getTextSize() { return textSize; }
    protected int getTextPixelSize() { return Math.max(1,Math.round(textSize)); }

    private String textString() {
        CharSequence display=text;
        if(transformationMethod!=null)display=transformationMethod.getTransformation(text,this);
        return display != null ? display.toString() : "";
    }

    @Override
    protected void onMeasure(int widthSpec,int heightSpec) {
        String text=textString();
        int desiredWidth=8;
        for(String line:text.split("\n",-1))desiredWidth=Math.max(desiredWidth,Canvas.measureText(line,getTextPixelSize())+8);
        int lines=Math.max(1,text.split("\n",-1).length);
        int desiredHeight=lines*BitmapFont.lineStep(getTextPixelSize())+8;
        // Compound drawables (toolbar/menu icons) contribute their bounds.
        int compoundW = compoundWidth();
        int compoundH = compoundHeight();
        if (compoundW > 0) desiredWidth = Math.max(desiredWidth, compoundW + 8);
        if (compoundH > 0) desiredHeight = Math.max(desiredHeight, compoundH + 8);
        desiredWidth = Math.min(maxWidth,Math.max(minWidth,desiredWidth));
        desiredHeight = Math.min(maxHeight,Math.max(minHeight,Math.max(minLines,Math.min(lines,maxLines))*BitmapFont.lineStep(getTextPixelSize())+8));
        setMeasuredDimension(resolveSize(desiredWidth,widthSpec),resolveSize(desiredHeight,heightSpec));
    }

    private int compoundWidth() {
        int w = 0;
        if (compoundLeft != null) w += compoundBoundsWidth(compoundLeft);
        if (compoundRight != null) w += compoundBoundsWidth(compoundRight);
        if (compoundTop != null) w = Math.max(w, compoundBoundsWidth(compoundTop));
        if (compoundBottom != null) w = Math.max(w, compoundBoundsWidth(compoundBottom));
        return w;
    }

    private int compoundHeight() {
        int h = 0;
        if (compoundTop != null) h += compoundBoundsHeight(compoundTop);
        if (compoundBottom != null) h += compoundBoundsHeight(compoundBottom);
        if (compoundLeft != null) h = Math.max(h, compoundBoundsHeight(compoundLeft));
        if (compoundRight != null) h = Math.max(h, compoundBoundsHeight(compoundRight));
        return h;
    }

    private static int compoundBoundsWidth(android.graphics.drawable.Drawable d) {
        android.graphics.Rect b = d.getBounds();
        int w = b == null ? 0 : b.width();
        return w > 0 ? w : Math.max(0, d.getIntrinsicWidth());
    }

    private static int compoundBoundsHeight(android.graphics.drawable.Drawable d) {
        android.graphics.Rect b = d.getBounds();
        int h = b == null ? 0 : b.height();
        return h > 0 ? h : Math.max(0, d.getIntrinsicHeight());
    }
    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        drawCompounds(canvas, getLeft(), getTop());
        drawTextAt(canvas, textString(), 4, 4, getTextPixelSize(), textColor);
    }

    @Override
    protected void onDrawAt(Canvas canvas, int offsetX, int offsetY) {
        super.onDraw(canvas);
        drawCompounds(canvas, offsetX, offsetY);
        drawTextAtOffset(canvas, textString(), 4, 4, getTextPixelSize(), textColor, offsetX, offsetY);
    }

    /** Left compound icon at the view origin; others stacked by bounds. */
    private void drawCompounds(Canvas canvas, int originX, int originY) {
        if (compoundLeft != null) {
            android.graphics.Rect b = compoundLeft.getBounds();
            int w = b == null ? 0 : b.width();
            int h = b == null ? 0 : b.height();
            if (w <= 0) w = Math.max(0, compoundLeft.getIntrinsicWidth());
            if (h <= 0) h = Math.max(0, compoundLeft.getIntrinsicHeight());
            compoundLeft.setBounds(originX + 4, originY + 4, originX + 4 + w, originY + 4 + h);
            compoundLeft.draw(canvas);
        }
        if (compoundTop != null) {
            android.graphics.Rect b = compoundTop.getBounds();
            int w = b == null ? 0 : b.width();
            int h = b == null ? 0 : b.height();
            if (w <= 0) w = Math.max(0, compoundTop.getIntrinsicWidth());
            if (h <= 0) h = Math.max(0, compoundTop.getIntrinsicHeight());
            compoundTop.setBounds(originX + 4, originY + 4, originX + 4 + w, originY + 4 + h);
            compoundTop.draw(canvas);
        }
        if (compoundRight != null) {
            android.graphics.Rect b = compoundRight.getBounds();
            int w = b == null ? 0 : b.width();
            int h = b == null ? 0 : b.height();
            if (w <= 0) w = Math.max(0, compoundRight.getIntrinsicWidth());
            if (h <= 0) h = Math.max(0, compoundRight.getIntrinsicHeight());
            compoundRight.setBounds(originX + getWidth() - 4 - w, originY + 4,
                    originX + getWidth() - 4, originY + 4 + h);
            compoundRight.draw(canvas);
        }
        if (compoundBottom != null) {
            android.graphics.Rect b = compoundBottom.getBounds();
            int w = b == null ? 0 : b.width();
            int h = b == null ? 0 : b.height();
            if (w <= 0) w = Math.max(0, compoundBottom.getIntrinsicWidth());
            if (h <= 0) h = Math.max(0, compoundBottom.getIntrinsicHeight());
            compoundBottom.setBounds(originX + 4, originY + getHeight() - 4 - h,
                    originX + 4 + w, originY + getHeight() - 4);
            compoundBottom.draw(canvas);
        }
    }

    /** Shared text renderer used by subclasses (cursor, labels). */
    protected final void drawTextAt(Canvas canvas, String s, int dx, int dy, int size, int color) {
        drawTextAtOffset(canvas, s, dx, dy, size, color, getLeft(), getTop());
    }

    /** Offset text renderer: origin is the view's root position. */
    protected final void drawTextAtOffset(Canvas canvas, String s, int dx, int dy, int size, int color,
            int originX, int originY) {
        if (s == null || s.isEmpty()) return;
        int cell = Canvas.measureText(" ",size);
        int lineStep = BitmapFont.lineStep(size);
        int x0 = originX + dx;
        int y0 = originY + dy;
        int maxX = originX + getWidth() - 4;
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
                    int w = Canvas.measureText(part,size);
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
        return BitmapFont.cellWidth(getTextPixelSize());
    }
}
