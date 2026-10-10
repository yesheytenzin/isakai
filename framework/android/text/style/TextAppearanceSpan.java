// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** TextAppearance style span: family/style/size/color/link getters. */
public class TextAppearanceSpan extends MetricAffectingSpan implements Parcelable {
    private final String family;
    private final int style;
    private final int textSize;
    private final ColorStateList textColor;
    private final ColorStateList textColorLink;

    public TextAppearanceSpan(Context context, int appearance) {
        this(null, 0, 0, null, null);
    }

    public TextAppearanceSpan(Context context, int appearance, int colorList) {
        this(null, 0, 0, null, null);
    }

    public TextAppearanceSpan(String family, int style, int textSize,
            ColorStateList textColor, ColorStateList textColorLink) {
        this.family = family;
        this.style = style;
        this.textSize = textSize;
        this.textColor = textColor;
        this.textColorLink = textColorLink;
    }

    public TextAppearanceSpan(Parcel src) {
        this.family = src.readString();
        this.style = src.readInt();
        this.textSize = src.readInt();
        this.textColor = src.readInt() != 0 ? ColorStateList.valueOf(src.readInt()) : null;
        this.textColorLink = src.readInt() != 0 ? ColorStateList.valueOf(src.readInt()) : null;
    }

    public static final Parcelable.Creator<TextAppearanceSpan> CREATOR =
            new Parcelable.Creator<TextAppearanceSpan>() {
                @Override public TextAppearanceSpan createFromParcel(Parcel source) {
                    return new TextAppearanceSpan(source);
                }
                @Override public TextAppearanceSpan[] newArray(int size) {
                    return new TextAppearanceSpan[size];
                }
            };

    public String getFamily() {
        return family;
    }

    public int getStyle() {
        return style;
    }

    public int getTextSize() {
        return textSize;
    }

    public ColorStateList getTextColor() {
        return textColor;
    }

    public ColorStateList getLinkTextColor() {
        return textColorLink;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        updateMeasureState(paint);
        if (paint == null) return;
        if (textColor != null) {
            paint.setColor(textColor.getColorForState(paint.drawableState, textColor.getDefaultColor()));
        }
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        if (paint == null) return;
        if (family != null || style != 0) {
            Typeface tf = paint.getTypeface();
            if (family != null) tf = Typeface.create(family, style);
            else tf = Typeface.create(tf, style);
            paint.setTypeface(tf);
        }
        if (textSize > 0) paint.setTextSize(textSize);
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(family);
        dest.writeInt(style);
        dest.writeInt(textSize);
        dest.writeInt(textColor != null ? 1 : 0);
        if (textColor != null) dest.writeInt(textColor.getDefaultColor());
        dest.writeInt(textColorLink != null ? 1 : 0);
        if (textColorLink != null) dest.writeInt(textColorLink.getDefaultColor());
    }
}
