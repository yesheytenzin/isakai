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

    public void setText(CharSequence text) {
        this.text = text != null ? text : "";
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
