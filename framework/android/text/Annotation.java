// SPDX-License-Identifier: GPL-3.0-or-later
package android.text;

import android.os.Parcel;
import android.os.Parcelable;

/** Key/value annotation attached to a text range; parcelable value type. */
public class Annotation implements Parcelable {
    private final String key;
    private final String value;

    public Annotation(String key, CharSequence value) {
        this.key = key;
        this.value = value == null ? null : value.toString();
    }

    public Annotation(Parcel src) {
        this.key = src.readString();
        this.value = src.readString();
    }

    public static final Parcelable.Creator<Annotation> CREATOR =
            new Parcelable.Creator<Annotation>() {
                @Override public Annotation createFromParcel(Parcel source) {
                    return new Annotation(source);
                }
                @Override public Annotation[] newArray(int size) {
                    return new Annotation[size];
                }
            };

    public String getKey() {
        return key;
    }

    public String getValue() {
        return value;
    }

    @Override public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        dest.writeString(key);
        dest.writeString(value);
    }
}
