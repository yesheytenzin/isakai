// SPDX-License-Identifier: GPL-3.0-or-later
package android.text.style;

import android.os.Bundle;
import android.os.Parcel;
import android.os.Parcelable;
import android.text.TextPaint;

/** TTS annotation span: type plus argument bundle, with a builder. */
public class TtsSpan extends CharacterStyle implements Parcelable {
    public static final String TYPE_CARDINAL = "android.type.cardinal";
    public static final String TYPE_DATE = "android.type.date";
    public static final String TYPE_DECIMAL = "android.type.decimal";
    public static final String TYPE_DIGITS = "android.type.digits";
    public static final String TYPE_ELECTRONIC = "android.type.electronic";
    public static final String TYPE_FRACTION = "android.type.fraction";
    public static final String TYPE_MEASURE = "android.type.measure";
    public static final String TYPE_MONEY = "android.type.money";
    public static final String TYPE_ORDINAL = "android.type.ordinal";
    public static final String TYPE_TELEPHONE = "android.type.telephone";
    public static final String TYPE_TIME = "android.type.time";
    public static final String TYPE_VERBATIM = "android.type.verbatim";

    public static final String ARG_CARDINAL = "android.arg.cardinal";
    public static final String ARG_CURRENCY = "android.arg.money.currency";
    public static final String ARG_DATE = "android.arg.date";
    public static final String ARG_DAY = "android.arg.day";
    public static final String ARG_DECIMAL = "android.arg.decimal";
    public static final String ARG_DENOMINATOR = "android.arg.fraction.denominator";
    public static final String ARG_DIGITS = "android.arg.digits";
    public static final String ARG_ELECTRONIC = "android.arg.electronic";
    public static final String ARG_FRACTIONAL_PART = "android.arg.money.fractional_part";
    public static final String ARG_HOURS = "android.arg.hours";
    public static final String ARG_INTEGER_PART = "android.arg.money.integer_part";
    public static final String ARG_MINUTES = "android.arg.minutes";
    public static final String ARG_MONTH = "android.arg.month";
    public static final String ARG_NUMBER = "android.arg.number";
    public static final String ARG_NUMERATOR = "android.arg.fraction.numerator";
    public static final String ARG_QUANTITY = "android.arg.quantity";
    public static final String ARG_TEXT = "android.arg.text";
    public static final String ARG_UNIT = "android.arg.unit";
    public static final String ARG_VERBATIM = "android.arg.verbatim";
    public static final String ARG_WEEKDAY = "android.arg.weekday";
    public static final String ARG_YEAR = "android.arg.year";

    private final String type;
    private final Bundle args;

    public TtsSpan(String type, Bundle args) {
        this.type = type;
        this.args = args == null ? new Bundle() : new Bundle(args);
    }

    public TtsSpan(Parcel src) {
        this.type = src.readString();
        Bundle b = src.readBundle();
        this.args = b == null ? new Bundle() : b;
    }

    public static final Parcelable.Creator<TtsSpan> CREATOR =
            new Parcelable.Creator<TtsSpan>() {
                @Override public TtsSpan createFromParcel(Parcel source) {
                    return new TtsSpan(source);
                }
                @Override public TtsSpan[] newArray(int size) {
                    return new TtsSpan[size];
                }
            };

    public String getType() {
        return type;
    }

    public Bundle getArgs() {
        return new Bundle(args);
    }

    @Override
    public void updateDrawState(TextPaint paint) {
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(type);
        dest.writeBundle(args);
    }

    /** Builder for common TTS span types. */
    public static class Builder {
        private final String type;
        private final Bundle args = new Bundle();

        public Builder(String type) {
            this.type = type;
        }

        public Builder setStringArgument(String arg, String value) {
            args.putString(arg, value);
            return this;
        }

        public Builder setIntArgument(String arg, int value) {
            args.putInt(arg, value);
            return this;
        }

        public Builder setLongArgument(String arg, long value) {
            args.putLong(arg, value);
            return this;
        }

        public Builder setDoubleArgument(String arg, double value) {
            args.putDouble(arg, value);
            return this;
        }

        public TtsSpan build() {
            return new TtsSpan(type, args);
        }
    }
}
