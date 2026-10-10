// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Underline span. No underline channel on this Paint; presence is the signal. */
public class UnderlineSpan extends CharacterStyle implements Parcelable {
    public UnderlineSpan() {
    }

    public UnderlineSpan(Parcel src) {
    }

    public static final Parcelable.Creator<UnderlineSpan> CREATOR =
            new Parcelable.Creator<UnderlineSpan>() {
                @Override public UnderlineSpan createFromParcel(Parcel source) {
                    return new UnderlineSpan(source);
                }
                @Override public UnderlineSpan[] newArray(int size) {
                    return new UnderlineSpan[size];
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
