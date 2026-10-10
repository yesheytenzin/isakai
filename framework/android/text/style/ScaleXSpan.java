// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Horizontal scale span. */
public class ScaleXSpan extends MetricAffectingSpan implements Parcelable {
    private final float proportion;

    public ScaleXSpan(float proportion) {
        this.proportion = proportion;
    }

    public ScaleXSpan(Parcel src) {
        this.proportion = src.readFloat();
    }

    public static final Parcelable.Creator<ScaleXSpan> CREATOR =
            new Parcelable.Creator<ScaleXSpan>() {
                @Override public ScaleXSpan createFromParcel(Parcel source) {
                    return new ScaleXSpan(source);
                }
                @Override public ScaleXSpan[] newArray(int size) {
                    return new ScaleXSpan[size];
                }
            };

    public float getScaleX() {
        return proportion;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        updateMeasureState(paint);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        if (paint != null) paint.setTextScaleX(paint.getTextScaleX() * proportion);
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(proportion);
    }
}
