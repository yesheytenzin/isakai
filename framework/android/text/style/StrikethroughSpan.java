// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Strikethrough span. No strike channel on this Paint; presence is the signal. */
public class StrikethroughSpan extends CharacterStyle implements Parcelable {
    public StrikethroughSpan() {
    }

    public StrikethroughSpan(Parcel src) {
    }

    public static final Parcelable.Creator<StrikethroughSpan> CREATOR =
            new Parcelable.Creator<StrikethroughSpan>() {
                @Override public StrikethroughSpan createFromParcel(Parcel source) {
                    return new StrikethroughSpan(source);
                }
                @Override public StrikethroughSpan[] newArray(int size) {
                    return new StrikethroughSpan[size];
                }
            };

    @Override
    public void updateDrawState(TextPaint paint) {
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
    }
}
