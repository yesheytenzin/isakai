// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

import android.graphics.Paint;

/** Paint plus text density and drawable state. */
public class TextPaint extends Paint {
    public float density = 1.0f;
    public int[] drawableState;
    public int linkColor;

    public TextPaint() {
        super();
    }

    public TextPaint(int flags) {
        super(flags);
    }

    public TextPaint(Paint src) {
        super(src);
    }

    public void set(TextPaint src) {
        if (src == null) {
            reset();
            return;
        }
        setFlags(src.getFlags());
        setColor(src.getColor());
        setAlpha(src.getAlpha());
        setStyle(src.getStyle());
        setStrokeWidth(src.getStrokeWidth());
        setStrokeMiter(src.getStrokeMiter());
        setStrokeCap(src.getStrokeCap());
        setStrokeJoin(src.getStrokeJoin());
        setTextSize(src.getTextSize());
        setTextScaleX(src.getTextScaleX());
        setTextSkewX(src.getTextSkewX());
        setLetterSpacing(src.getLetterSpacing());
        setAntiAlias(src.isAntiAlias());
        setFakeBoldText(src.isFakeBoldText());
        setLinearText(src.isLinearText());
        setFilterBitmap(src.isFilterBitmap());
        setElegantTextHeight(src.isElegantTextHeight());
        setTypeface(src.getTypeface());
        setFontFeatureSettings(src.getFontFeatureSettings());
        setTextLocale(src.getTextLocale());
        this.density = src.density;
        this.drawableState = src.drawableState;
        this.linkColor = src.linkColor;
    }
}
