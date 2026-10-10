// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Fixed text size span, optionally density-independent. */
public class AbsoluteSizeSpan extends MetricAffectingSpan implements Parcelable {
    private final int size;
    private final boolean dip;

    public AbsoluteSizeSpan(int size) {
        this(size, false);
    }

    public AbsoluteSizeSpan(int size, boolean dip) {
        this.size = size;
        this.dip = dip;
    }

    public AbsoluteSizeSpan(Parcel src) {
        this.size = src.readInt();
        this.dip = src.readInt() != 0;
    }

    public static final Parcelable.Creator<AbsoluteSizeSpan> CREATOR =
            new Parcelable.Creator<AbsoluteSizeSpan>() {
                @Override public AbsoluteSizeSpan createFromParcel(Parcel source) {
                    return new AbsoluteSizeSpan(source);
                }
                @Override public AbsoluteSizeSpan[] newArray(int size) {
                    return new AbsoluteSizeSpan[size];
                }
            };

    public int getSize() {
        return size;
    }

    public boolean getDip() {
        return dip;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        updateMeasureState(paint);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        if (paint == null) return;
        if (dip) {
            paint.setTextSize(size * paint.density);
        } else {
            paint.setTextSize(size);
        }
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(size);
        dest.writeInt(dip ? 1 : 0);
    }
}
