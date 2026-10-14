// SPDX-License-Identifier: GPL-3.0-or-later
package android.os;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Set;

/** String-keyed value map with bundle nesting and parceling. */
public class Bundle extends BaseBundle implements Parcelable, Cloneable {
    public Bundle() {}

    public Bundle(Bundle src) {
        super(src);
    }

    public Bundle(int capacity) {}

    @Override
    public Bundle clone() {
        return new Bundle(this);
    }

    public void putBundle(String k, Bundle v) { map.put(k, v); }
    public void putAll(Bundle b) { if (b != null) map.putAll(b.map); }
    public Bundle getBundle(String k) { return cast(k, null, Bundle.class); }
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> T getParcelable(String k) { return (T) map.get(k); }
    public void putSparseParcelableArray(String key, android.util.SparseArray<? extends Parcelable> value) {
        map.put(key, value);
    }
    @SuppressWarnings("unchecked")
    public <T extends Parcelable> android.util.SparseArray<T> getSparseParcelableArray(String key) {
        Object value=map.get(key);
        return value instanceof android.util.SparseArray ? (android.util.SparseArray<T>)value : null;
    }

    @Override
    public int describeContents() { return 0; }

    @Override
    public void writeToParcel(Parcel dest, int flags) {}
}
