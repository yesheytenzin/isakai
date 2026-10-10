// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** Locale span: stores the locale(s); applies the primary to the paint. */
public class LocaleSpan extends MetricAffectingSpan implements Parcelable {
    private final java.util.Locale locale;

    public LocaleSpan(java.util.Locale locale) {
        this.locale = locale;
    }

    public LocaleSpan(android.os.LocaleList locales) {
        this(locales == null || locales.isEmpty() ? null : locales.get(0));
    }

    public LocaleSpan(Parcel src) {
        String tag = src.readString();
        this.locale = tag == null ? null : java.util.Locale.forLanguageTag(tag);
    }

    public static final Parcelable.Creator<LocaleSpan> CREATOR =
            new Parcelable.Creator<LocaleSpan>() {
                @Override public LocaleSpan createFromParcel(Parcel source) {
                    return new LocaleSpan(source);
                }
                @Override public LocaleSpan[] newArray(int size) {
                    return new LocaleSpan[size];
                }
            };

    public java.util.Locale getLocale() {
        return locale;
    }

    public android.os.LocaleList getLocales() {
        return locale == null ? android.os.LocaleList.getEmptyLocaleList()
                : new android.os.LocaleList(locale);
    }

    @Override
    public void updateDrawState(TextPaint paint) {
        updateMeasureState(paint);
    }

    @Override
    public void updateMeasureState(TextPaint paint) {
        if (paint != null && locale != null) paint.setTextLocale(locale);
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(locale == null ? null : locale.toLanguageTag());
    }
}
