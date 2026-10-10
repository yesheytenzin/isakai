// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Typeface style (bold/italic) span. */
public class StyleSpan extends MetricAffectingSpan implements Parcelable {
    private final int style;

    public StyleSpan(int style) {
        this.style = style;
    }

    public StyleSpan(Parcel src) {
        this.style = src.readInt();
    }

    public static final Parcelable.Creator<StyleSpan> CREATOR =
            new Parcelable.Creator<StyleSpan>() {
                @Override public StyleSpan createFromParcel(Parcel source) {
                    return new StyleSpan(source);
                }
                @Override public StyleSpan[] newArray(int size) {
                    return new StyleSpan[size];
                }
            };

    public int getStyle() {
        return style;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        apply(paint);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        apply(paint);
    }

    private void apply(TextPaint paint) {
        if (paint == null) return;
        if ((style & Typeface.BOLD) != 0) paint.setFakeBoldText(true);
        if ((style & Typeface.ITALIC) != 0) paint.setTextSkewX(-0.25f);
        paint.setTypeface(Typeface.create(paint.getTypeface(), style));
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(style);
    }
}
