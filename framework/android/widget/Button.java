// SPDX-License-Identifier: GPL-3.0-or-later
package android.widget;

import android.graphics.BitmapFont;
import android.graphics.Canvas;
import android.graphics.Color;

/**
 * Minimal Button: clickable + focusable, pressed fill, centered label.
 */
public class Button extends TextView {
    public Button() {
        super();
        setClickable(true);
        setFocusable(true);
        setBackgroundColor(Color.LTGRAY);
    }

    public Button(CharSequence label) {
        this();
        setText(label);
    }

    public Button(android.content.Context context) {
        super(context);
        setClickable(true);
        setFocusable(true);
        setBackgroundColor(Color.LTGRAY);
    }

    public Button(android.content.Context context, android.util.AttributeSet attrs) {
        this(context);
    }

    public Button(android.content.Context context, android.util.AttributeSet attrs, int defStyleAttr) {
        this(context);
    }

    @Override
    protected void onDraw(Canvas canvas) {
        drawButton(canvas, getLeft(), getTop());
    }

    @Override
    protected void onDrawAt(Canvas canvas, int offsetX, int offsetY) {
        drawButton(canvas, offsetX, offsetY);
    }

    private void drawButton(Canvas canvas, int originX, int originY) {
        int bg = isPressed() ? Color.DKGRAY : backgroundColor;
        canvas.drawColor(bg);
        String s = getText() != null ? getText().toString() : "";
        if (s.isEmpty()) return;
        int cell = BitmapFont.cellWidth(getTextPixelSize());
        int w = s.length() * cell;
        int inner = getWidth() - 8;
        int dx = 4;
        if (inner > w) dx = 4 + (inner - w) / 2;
        int lineH = BitmapFont.lineStep(getTextPixelSize());
        int innerH = getHeight() - 8;
        int dy = 4;
        if (innerH > lineH) dy = 4 + (innerH - lineH) / 2;
        drawTextAtOffset(canvas, s, dx, dy, getTextPixelSize(), getTextColor(), originX, originY);
    }
}
