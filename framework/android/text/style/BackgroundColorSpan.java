// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Background color span: stores the color; drawing applies it where supported. */
public class BackgroundColorSpan extends CharacterStyle implements Parcelable {
    private final int color;

    public BackgroundColorSpan(int color) {
        this.color = color;
    }

    public BackgroundColorSpan(Parcel src) {
        this.color = src.readInt();
    }

    public static final Parcelable.Creator<BackgroundColorSpan> CREATOR =
            new Parcelable.Creator<BackgroundColorSpan>() {
                @Override public BackgroundColorSpan createFromParcel(Parcel source) {
                    return new BackgroundColorSpan(source);
                }
                @Override public BackgroundColorSpan[] newArray(int size) {
                    return new BackgroundColorSpan[size];
                }
            };

    public int getBackgroundColor() {
        return color;
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        // No background-color channel on this Paint; spans are queryable via the getter.
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeInt(color);
    }
}
