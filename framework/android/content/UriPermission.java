// SPDX-License-Identifier: GPL-3.0-or-later
package android.content;

import android.net.Uri;
import android.os.Parcel;
import android.os.Parcelable;

/** Persisted URI grant shell. */
public final class UriPermission implements Parcelable {
    private final Uri uri;
    private final int modeFlags;
    private final long persistedTime;
    public static final Parcelable.Creator<UriPermission> CREATOR = new Parcelable.Creator<UriPermission>() {
        @Override public UriPermission createFromParcel(Parcel source) { return new UriPermission(null, 0, 0); }
        @Override public UriPermission[] newArray(int size) { return new UriPermission[size]; }
    };
    public UriPermission(Uri uri, int modeFlags, long persistedTime) { this.uri = uri; this.modeFlags = modeFlags; this.persistedTime = persistedTime; }
    public Uri getUri() { return uri; }
    public int getModeFlags() { return modeFlags; }
    public long getPersistedTime() { return persistedTime; }
    public boolean isReadPermission() { return (modeFlags & 1) != 0; }
    public boolean isWritePermission() { return (modeFlags & 2) != 0; }
    @Override public int describeContents() { return 0; }
    @Override public void writeToParcel(Parcel dest, int flags) { }
}
