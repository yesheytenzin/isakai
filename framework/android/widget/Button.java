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

    @Override
    protected void onDraw(Canvas canvas) {
        int bg = isPressed() ? Color.DKGRAY : backgroundColor;
        canvas.drawColor(bg);
        String s = getText() != null ? getText().toString() : "";
        if (s.isEmpty()) return;
        int cell = BitmapFont.cellWidth(getTextSize());
        int w = s.length() * cell;
        int inner = getWidth() - 8;
        int dx = 4;
        if (inner > w) dx = 4 + (inner - w) / 2;
        int lineH = BitmapFont.lineStep(getTextSize());
        int innerH = getHeight() - 8;
        int dy = 4;
        if (innerH > lineH) dy = 4 + (innerH - lineH) / 2;
        drawTextAt(canvas, s, dx, dy, getTextSize(), getTextColor());
    }
}
