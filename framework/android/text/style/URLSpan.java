// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;
import android.view.View;

/** URL span: stores the URL; click dispatch is a no-op. */
public class URLSpan extends ClickableSpan implements Parcelable {
    private final String url;

    public URLSpan(String url) {
        this.url = url;
    }

    public URLSpan(Parcel src) {
        this.url = src.readString();
    }

    public static final Parcelable.Creator<URLSpan> CREATOR =
            new Parcelable.Creator<URLSpan>() {
                @Override public URLSpan createFromParcel(Parcel source) {
                    return new URLSpan(source);
                }
                @Override public URLSpan[] newArray(int size) {
                    return new URLSpan[size];
                }
            };

    public String getURL() {
        return url;
    }

    @Override
    public void onClick(View widget) {
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        if (paint != null) paint.setColor(paint.linkColor);
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(url);
    }
}
