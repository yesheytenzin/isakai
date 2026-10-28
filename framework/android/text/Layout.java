// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

import android.graphics.BitmapFont;
import android.graphics.Canvas;
import android.graphics.Paint;

/**
 * Laid-out text block. Lines break on '\n' and wrap at the outer width
 * using bitmap-font cell widths; draw() renders rows through Canvas.
 */
public class Layout {
    public enum Alignment {
        ALIGN_NORMAL, ALIGN_OPPOSITE, ALIGN_CENTER
    }

    public enum Directions {
        ;
        public static final int DIR_LEFT_TO_RIGHT = 1;
        public static final int DIR_RIGHT_TO_LEFT = -1;
    }

    protected final CharSequence text;
    protected final TextPaint paint;
    protected final int width;
    protected final Alignment alignment;
    protected final float spacingMult;
    protected final float spacingAdd;
    protected final boolean includePad;
    protected int[] lineStarts;

    protected Layout(CharSequence text, TextPaint paint, int width, Alignment alignment,
            float spacingMult, float spacingAdd, boolean includePad) {
        this.text = text == null ? "" : text;
        this.paint = paint == null ? new TextPaint() : paint;
        this.width = Math.max(0, width);
        this.alignment = alignment == null ? Alignment.ALIGN_NORMAL : alignment;
        this.spacingMult = spacingMult;
        this.spacingAdd = spacingAdd;
        this.includePad = includePad;
        relayout();
    }

    protected final void relayout() {
        java.util.ArrayList<Integer> starts = new java.util.ArrayList<>();
        starts.add(0);
        int n = text.length();
        if (width > 0) {
            // Break by true advances, not fixed cells: cells overestimate
            // wide runs and split a line that fits, whose tail the caller
            // then clips (e.g. a maxLines=1 header reading "GENER").
            // Advances repeat, so one measure per distinct code point.
            java.util.HashMap<Integer, Float> adv = new java.util.HashMap<>();
            float lineW = 0;
            for (int i = 0; i < n;) {
                int cp = Character.codePointAt(text, i);
                int len = Character.charCount(cp);
                if (cp == '\n') { starts.add(i + 1); lineW = 0; i += len; continue; }
                Float a = adv.get(cp);
                if (a == null) { a = paint.measureText(text, i, i + len); adv.put(cp, a); }
                if (lineW > 0 && lineW + a > width) { starts.add(i); lineW = 0; }
                lineW += a;
                i += len;
            }
        } else {
            for (int i = 0; i < n; i++) if (text.charAt(i) == '\n') starts.add(i + 1);
        }
        lineStarts = new int[starts.size()];
        for (int i = 0; i < starts.size(); i++) lineStarts[i] = starts.get(i);
    }

    public CharSequence getText() { return text; }
    public TextPaint getPaint() { return paint; }
    public int getWidth() { return width; }
    public Alignment getParagraphAlignment(int line) { return alignment; }
    public int getParagraphDirection(int line) { return Directions.DIR_LEFT_TO_RIGHT; }

    public int getLineCount() { return lineStarts.length; }

    public int getLineStart(int line) {
        return lineStarts[Math.max(0, Math.min(line, lineStarts.length - 1))];
    }

    public int getLineEnd(int line) {
        int start = getLineStart(line);
        int end = line + 1 < lineStarts.length ? lineStarts[line + 1] : text.length();
        if (end > start && text.charAt(end - 1) == '\n') end--;
        return end;
    }

    public int getLineTop(int line) {
        return line * lineHeight();
    }

    public int getLineBaseline(int line) {
        return getLineTop(line) + (int) (paint.ascent() * -1);
    }

    public int getLineDescent(int line) {
        return (int) (paint.descent() + 0.5f);
    }

    public int getLineAscent(int line) {
        return (int) paint.ascent();
    }

    /** Bottom edge of the line box: the next line's top (AOSP contract). */
    public int getLineBottom(int line) {
        return getLineTop(line + 1);
    }

    /** Line containing the character at {@code offset} (EOF lands on the last line). */
    public int getLineForOffset(int offset) {
        int clamped = Math.max(0, Math.min(offset, text.length()));
        int line = 0;
        for (int i = 1; i < lineStarts.length; i++) {
            if (lineStarts[i] > clamped) break;
            line = i;
        }
        return line;
    }

    private int alignmentX(int line) {
        int lineWidth = (int) getLineWidth(line);
        if (alignment == Alignment.ALIGN_CENTER) return Math.max(0, (width - lineWidth) / 2);
        if (alignment == Alignment.ALIGN_OPPOSITE) return Math.max(0, width - lineWidth);
        return 0;
    }

    public float getLineLeft(int line) {
        return alignmentX(line);
    }

    public float getLineRight(int line) {
        return alignmentX(line) + getLineWidth(line);
    }

    private int cellWidth() {
        return Math.max(1, BitmapFont.cellWidth(Math.max(1, (int) paint.getTextSize())));
    }

    /** Horizontal position of the character at {@code offset} (LTR direction). */
    public float getPrimaryHorizontal(int offset) {
        int line = getLineForOffset(offset);
        int clamped = Math.max(getLineStart(line), Math.min(offset, getLineEnd(line)));
        return alignmentX(line) + (clamped - getLineStart(line)) * cellWidth();
    }

    /** The engine lays text out left-to-right only. */
    public boolean isRtlCharAt(int offset) {
        return false;
    }

    /** Font padding is folded into the line boxes here, so there is none extra. */
    public int getTopPadding() { return 0; }

    public int getBottomPadding() { return 0; }

    public float getLineWidth(int line) {
        return paint.measureText(text.toString(), getLineStart(line), getLineEnd(line));
    }

    public int getEllipsisCount(int line) { return 0; }
    public int getEllipsisStart(int line) { return 0; }

    public int getHeight() {
        return lineStarts.length * lineHeight();
    }

    private int lineHeight() {
        int base = BitmapFont.lineStep(Math.max(1, (int) paint.getTextSize()));
        return Math.max(1, (int) (base * spacingMult + spacingAdd));
    }

    public void draw(Canvas canvas) {
        if (canvas == null) return;
        int size = Math.max(1, (int) paint.getTextSize());
        int cell = BitmapFont.cellWidth(size);
        for (int line = 0; line < lineStarts.length; line++) {
            int start = getLineStart(line);
            int end = getLineEnd(line);
            String s = text.subSequence(start, end).toString();
            int x = 0;
            int lineWidth = s.length() * cell;
            if (alignment == Alignment.ALIGN_CENTER) {
                x = Math.max(0, (width - lineWidth) / 2);
            } else if (alignment == Alignment.ALIGN_OPPOSITE) {
                x = Math.max(0, width - lineWidth);
            }
            canvas.drawTextRun(s, 0, s.length(), 0, s.length(), x, getLineTop(line), false, paint);
        }
    }

    public static float getDesiredWidth(CharSequence text, TextPaint paint) {
        if (text == null || text.length() == 0) return 0;
        TextPaint p = paint == null ? new TextPaint() : paint;
        String s = text.toString();
        int widest = 0;
        for (String line : s.split("\n", -1)) {
            widest = Math.max(widest, (int) (p.measureText(line) + 0.5f));
        }
        return widest;
    }

    public static float getDesiredWidth(CharSequence text, int start, int end, TextPaint paint) {
        if (text == null) return 0;
        TextPaint p = paint == null ? new TextPaint() : paint;
        return p.measureText(text, start, end);
    }
}
