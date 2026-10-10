// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Relative (proportional) text size span. */
public class RelativeSizeSpan extends MetricAffectingSpan implements Parcelable {
    private final float proportion;

    public RelativeSizeSpan(float proportion) {
        this.proportion = proportion;
    }

    public RelativeSizeSpan(Parcel src) {
        this.proportion = src.readFloat();
    }

    public static final Parcelable.Creator<RelativeSizeSpan> CREATOR =
            new Parcelable.Creator<RelativeSizeSpan>() {
                @Override public RelativeSizeSpan createFromParcel(Parcel source) {
                    return new RelativeSizeSpan(source);
                }
                @Override public RelativeSizeSpan[] newArray(int size) {
                    return new RelativeSizeSpan[size];
                }
            };

    public float getSizeChange() {
        return proportion;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        updateMeasureState(paint);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        if (paint != null) paint.setTextSize(paint.getTextSize() * proportion);
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeFloat(proportion);
    }
}
