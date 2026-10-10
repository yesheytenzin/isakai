// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Foreground color span. */
public class ForegroundColorSpan extends CharacterStyle implements Parcelable {
    private final int color;

    public ForegroundColorSpan(int color) {
        this.color = color;
    }

    public ForegroundColorSpan(Parcel src) {
        this.color = src.readInt();
    }

    public static final Parcelable.Creator<ForegroundColorSpan> CREATOR =
            new Parcelable.Creator<ForegroundColorSpan>() {
                @Override public ForegroundColorSpan createFromParcel(Parcel source) {
                    return new ForegroundColorSpan(source);
                }
                @Override public ForegroundColorSpan[] newArray(int size) {
                    return new ForegroundColorSpan[size];
                }
            };

    public int getForegroundColor() {
        return color;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        if (paint != null) paint.setColor(color);
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(color);
    }
}
